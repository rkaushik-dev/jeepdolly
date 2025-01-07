"""Application routes: file one, look at it, submit it, ask what it costs.

The routes here are thin on purpose. Each one reads a body, calls one thing in
the domain, commits if that worked, and shapes the answer. There is no rule in
this file and there is no arithmetic. Anything that looks like a decision is a
mistake that should have been made in the domain.

Assessing is a GET and not a POST, and that is deliberate. Asking the office
what a load would cost changes nothing, it is asked over and over while a
hauler juggles routes on the telephone, and a client should be free to repeat
it. Submitting changes the state of a document and is a POST.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query

from ...errors import Unreadable
from ...permits.application import Application, ApplicationState
from ...permits.issue import Assessment, assess
from ...permits.numbering import Series
from ...persistence.unitofwork import UnitOfWork
from ..deps import office_from, unit_of_work
from ..schemas import ApplicationIn, ApplicationOut, AssessmentOut, ConditionOut, QuoteOut

router = APIRouter(prefix="/applications", tags=["applications"])


def next_reference(work: UnitOfWork, on: date) -> str:
    """The office reference for a new application, off the same counter."""
    return f"{on.year % 100:02d}-F-{work.counter.take(on.year, Series.FILE):06d}"


@router.post("", response_model=ApplicationOut, status_code=201)
def file_application(
    body: ApplicationIn,
    filed: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
) -> ApplicationOut:
    """Take an application in and give it a reference."""
    day = filed or date.today()
    book = work.commodities.book(day)
    reference = next_reference(work, day)
    application = body.to_domain(reference, day, book)
    work.applications.add(application)
    work.commit()
    return ApplicationOut.of(application)


@router.get("/{reference}", response_model=ApplicationOut)
def read_application(
    reference: str,
    work: UnitOfWork = Depends(unit_of_work),
) -> ApplicationOut:
    """One application as it was submitted."""
    return ApplicationOut.of(work.applications.get(reference))


@router.get("", response_model=list[ApplicationOut])
def list_applications(
    state: str = Query(default="submitted"),
    work: UnitOfWork = Depends(unit_of_work),
) -> list[ApplicationOut]:
    """The queue, by state, oldest first."""
    try:
        wanted = ApplicationState(state)
    except ValueError:
        raise Unreadable(f"{state!r} is not a state an application can be in") from None
    rows = work.applications.in_state(wanted)
    return [ApplicationOut.of(work.applications.get(one.reference)) for one in rows]


@router.post("/{reference}/submit", response_model=ApplicationOut)
def submit_application(
    reference: str,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
) -> ApplicationOut:
    """Move an application out of draft and into the queue."""
    application = work.applications.get(reference)
    application.submit(on or date.today())
    work.applications.save_state(application)
    work.commit()
    return ApplicationOut.of(application)


@router.post("/{reference}/withdraw", response_model=ApplicationOut)
def withdraw_application(
    reference: str,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
) -> ApplicationOut:
    """The carrier rang up and cancelled."""
    application = work.applications.get(reference)
    application.withdraw(on or date.today())
    work.applications.save_state(application)
    work.commit()
    return ApplicationOut.of(application)


@router.get("/{reference}/assessment", response_model=AssessmentOut)
def assess_application(
    reference: str,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
) -> AssessmentOut:
    """What the office makes of it, without deciding anything yet.

    The office is built for the day being assessed rather than for today,
    because a certificate that lapsed last week did not lapse on the day of
    the move, and the answer has to be the answer for that day.
    """
    application = work.applications.get(reference)
    day = on or application.start
    return shape(application, assess(office_from(work, day), application, day))


def shape(application: Application, found: Assessment) -> AssessmentOut:
    """The assessment as it goes over the wire."""
    return AssessmentOut(
        reference=application.reference,
        permittable=found.permittable,
        kind=found.kind.value,
        refusals=list(found.refusals),
        route=list(found.itinerary.idents) if found.itinerary else [],
        distance=str(found.itinerary.distance) if found.itinerary else None,
        conditions=[ConditionOut.of(one) for one in found.conditions],
        fees=QuoteOut.of(found.quote) if found.quote else None,
    )
