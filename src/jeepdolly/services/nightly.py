"""The nightly sweep, which is the only thing that runs without a person.

Six passes, in this order, and the order matters because each one reads what
the one before it left.

Expiring permits are noticed first. Nothing is changed: expiry is a fact about
the calendar and a permit that ran out yesterday is already expired. What this
produces is the list the office rings about in the morning.

Applications that were never submitted are withdrawn after a fortnight. A
draft that has sat untouched since the hauler rang off is not going to be
submitted, and leaving it in the queue makes the queue useless.

Studies that have been waiting too long are counted, not moved. An engineer
decides what happens to a study and a cron job does not.

Carriers whose insurance lapses inside the week are listed, because the office
would rather ring them than refuse them on the Thursday.

Pilot car cards running out inside the month are listed for the same reason,
and the same list is printed in full once a quarter by the renewal command.

Carriers newly held over an unpaid citation are last, and they are the one
pass that produces a letter rather than a telephone call. Newly is the word
that does the work: a carrier held yesterday is held today and does not want
a second letter, so the sweep reports only the ones that crossed the line
since the day before.

The sweep writes to the trail as one actor called ``nightly``, so a supervisor
reading the trail can tell what a person did from what the machine did.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..errors import Impossible
from ..permits.application import ApplicationState
from ..permits.permit import PermitState
from ..persistence.books import enforcement_from
from ..persistence.models.applications import ApplicationRow
from ..persistence.models.carriers import CarrierRow
from ..persistence.models.permits import PermitRow
from ..persistence.repositories import PilotRepository
from . import audit, studies

ACTOR = "nightly"

#: A draft nobody submitted is withdrawn after this many days.
STALE_DRAFT_DAYS = 14

#: A study waiting longer than this is on the supervisor's list.
SLOW_STUDY_DAYS = 14

#: Insurance lapsing inside this many days is worth a telephone call.
LAPSE_WARNING_DAYS = 7

#: A pilot car card running out inside this many days is worth a letter.
CARD_WARNING_DAYS = 30


@dataclass
class Sweep:
    """What the night found, and what it did about it."""

    day: date
    expiring: list[str] = field(default_factory=list)
    withdrawn: list[str] = field(default_factory=list)
    slow_studies: list[str] = field(default_factory=list)
    lapsing: list[str] = field(default_factory=list)
    cards: list[str] = field(default_factory=list)
    held: list[str] = field(default_factory=list)

    @property
    def changed(self) -> int:
        return len(self.withdrawn)

    def lines(self) -> tuple[str, ...]:
        found = [f"nightly {self.day.isoformat()}"]
        for number in self.expiring:
            found.append(f"expiring  {number}")
        for reference in self.withdrawn:
            found.append(f"withdrawn  {reference}")
        for reference in self.slow_studies:
            found.append(f"slow study  {reference}")
        for name in self.lapsing:
            found.append(f"insurance lapsing  {name}")
        for card in self.cards:
            found.append(f"card lapsing  {card}")
        for name in self.held:
            found.append(f"held  {name}")
        found.append(
            f"{len(self.expiring)} expiring  {len(self.withdrawn)} withdrawn  "
            f"{len(self.slow_studies)} slow  {len(self.lapsing)} lapsing  "
            f"{len(self.cards)} cards  {len(self.held)} held"
        )
        return tuple(found)

    def __str__(self) -> str:
        return f"nightly {self.day.isoformat()}, {self.changed} changed"


def _expiring(session: Session, day: date, within: int) -> list[str]:
    horizon = day + timedelta(days=within)
    query = (
        select(PermitRow)
        .where(
            PermitRow.state == PermitState.ISSUED,
            PermitRow.valid_to >= day,
            PermitRow.valid_to <= horizon,
        )
        .order_by(PermitRow.valid_to, PermitRow.number)
    )
    return [one.number for one in session.scalars(query).all()]


def _stale_drafts(session: Session, day: date, older_than: int) -> list[ApplicationRow]:
    cutoff = day - timedelta(days=older_than)
    query = (
        select(ApplicationRow)
        .where(ApplicationRow.state == ApplicationState.DRAFT, ApplicationRow.filed <= cutoff)
        .order_by(ApplicationRow.filed, ApplicationRow.reference)
    )
    return list(session.scalars(query).all())


def _lapsing(session: Session, day: date, within: int) -> list[str]:
    horizon = day + timedelta(days=within)
    found = []
    for row in session.scalars(select(CarrierRow).order_by(CarrierRow.name)).all():
        cover = row.current_insurance(day)
        if cover is not None and day <= cover.expires <= horizon:
            found.append(row.name)
    return found


def _cards(session: Session, day: date, within: int) -> list[str]:
    horizon = day + timedelta(days=within)
    found = PilotRepository(session).expiring(day, horizon)
    return [f"{one.card} {one.name}" for one in found]


def _newly_held(session: Session, day: date) -> list[str]:
    """Carriers over the line today that were not over it yesterday.

    Built by reading the book twice, which is two passes over a table that
    holds a few thousand rows in the worst year this office has had. When it
    stops being a few thousand this becomes a query with a date on it, and
    the shape of the answer does not change.
    """
    book = enforcement_from(session)
    yesterday = day - timedelta(days=1)
    names = {row.usdot: row.name for row in session.scalars(select(CarrierRow)).all()}
    found = []
    for account in book.held(day):
        if book.hold(account.usdot, yesterday):
            continue
        name = names.get(account.usdot, str(account.usdot))
        found.append(f"{account.usdot} {name} {account.owing}")
    return found


def sweep(session: Session, day: date, expiring_within: int = 2) -> Sweep:
    """Run the six passes and report what each one found."""
    if expiring_within < 0:
        raise Impossible("a sweep looks forward, not back")
    found = Sweep(day=day)
    found.expiring = _expiring(session, day, expiring_within)
    for row in _stale_drafts(session, day, STALE_DRAFT_DAYS):
        row.state = ApplicationState.WITHDRAWN
        audit.record(
            session,
            "application.withdrawn",
            ACTOR,
            "application",
            row.reference,
            detail=f"draft untouched since {row.filed.isoformat()}",
        )
        found.withdrawn.append(row.reference)
    found.slow_studies = [one.reference for one in studies.overdue(session, day, SLOW_STUDY_DAYS)]
    found.lapsing = _lapsing(session, day, LAPSE_WARNING_DAYS)
    found.cards = _cards(session, day, CARD_WARNING_DAYS)
    found.held = _newly_held(session, day)
    return found
