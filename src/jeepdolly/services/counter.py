"""The counter, which is the sequence of steps a clerk actually performs.

Until now each route did its own version of the same four steps: get the
application, build the office for the right day, assess it, and write down
what happened. Four routes doing that is four places to forget the trail line
or to build the office for the wrong day, and both of those have happened.

So the sequence lives here, once, and the routes call it. What a route still
owns is the http: which rung of the ladder it needs, what shape comes back,
and what status code goes with it. What it no longer owns is the order of the
office procedure.

The day is the parameter that mattered. Every function here takes the day it
is deciding for, and none of them reaches for today, because a permit assessed
against today's road when it travels next Tuesday is the bug this office
cannot afford twice.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from ..errors import NotAllowed
from ..permits.application import Application
from ..permits.issue import Assessment, Office, assess, issue
from ..permits.numbering import Series
from ..permits.permit import Permit
from ..persistence.unitofwork import UnitOfWork
from . import audit


@dataclass(frozen=True)
class Written:
    """What the counter produced: a permit and the assessment behind it."""

    permit: Permit
    assessment: Assessment

    @property
    def number(self) -> str:
        return str(self.permit.number)


def next_reference(work: UnitOfWork, on: date) -> str:
    """The office reference for a new application, off the same counter."""
    return f"{on.year % 100:02d}-F-{work.counter.take(on.year, Series.FILE):06d}"


def file_application(work: UnitOfWork, application: Application, who: str, on: date) -> Application:
    """Take an application in and record that it was taken in."""
    work.applications.add(application)
    audit.record(
        work.db,
        "application.filed",
        who,
        "application",
        application.reference,
        detail=f"{application.origin} to {application.destination}",
    )
    return application


def submit(work: UnitOfWork, reference: str, who: str, on: date) -> Application:
    """Move an application into the queue."""
    application = work.applications.get(reference)
    application.submit(on)
    work.applications.save_state(application)
    audit.record(work.db, "application.submitted", who, "application", reference)
    return application


def withdraw(work: UnitOfWork, reference: str, who: str, on: date) -> Application:
    """The carrier rang up and cancelled."""
    application = work.applications.get(reference)
    application.withdraw(on)
    work.applications.save_state(application)
    audit.record(work.db, "application.withdrawn", who, "application", reference)
    return application


def look_at(office: Office, application: Application, on: date | None = None) -> Assessment:
    """Assess an application for the day it says it is travelling."""
    return assess(office, application, on or application.start)


def write_permit(
    work: UnitOfWork,
    office: Office,
    reference: str,
    who: str,
    on: date,
) -> Written:
    """Assess again and issue, which is the only path to a permit number."""
    application = work.applications.get(reference)
    found = look_at(office, application)
    permit = issue(office, found, on)
    work.applications.save_state(application)
    work.permits.add(permit)
    audit.record(
        work.db,
        "permit.issued",
        who,
        "permit",
        str(permit.number),
        detail=f"{permit.gross} to {permit.carrier_name}",
    )
    return Written(permit, found)


def refuse(work: UnitOfWork, reference: str, who: str, on: date, reason: str) -> Application:
    """Close an application the office will not write."""
    application = work.applications.get(reference)
    application.refuse(on, reason)
    work.applications.save_state(application)
    audit.record(work.db, "application.refused", who, "application", reference, detail=reason)
    return application


def void_permit(work: UnitOfWork, number: str, who: str, on: date, reason: str) -> Permit:
    """Kill a permit, with the reason on the record."""
    permit = work.permits.get(number)
    permit.void(on, reason)
    work.permits.save_state(permit)
    audit.record(work.db, "permit.voided", who, "permit", number, detail=reason)
    return permit


def over_the_counter(
    work: UnitOfWork,
    office: Office,
    application: Application,
    who: str,
    on: date,
) -> Written:
    """File, submit and issue in one go, which is what a walk in is.

    Anything that refuses along the way refuses the whole thing, because a
    hauler standing at the counter with a load on the truck wants a permit or
    a reason, and not an application reference to ring about tomorrow.
    """
    file_application(work, application, who, on)
    work.flush()
    submit(work, application.reference, who, on)
    found = look_at(office, application)
    if not found.permittable:
        refuse(work, application.reference, who, on, found.refusals[0])
        raise NotAllowed(
            f"{application.reference} cannot be issued",
            reasons=found.refusals,
            reference=application.reference,
        )
    return write_permit(work, office, application.reference, who, on)
