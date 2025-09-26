"""The renewal run, which is the busiest fortnight the counter has.

An annual permit runs a year from the day it was issued, so renewals do not
arrive in January. They arrive every week of the year, four hundred of them,
and until now the office found them by looking at a screen sorted by expiry
and working down it.

This is that list, and then the two things nobody could do from the screen.

It writes the letters. One per carrier rather than one per permit, because a
crane company with nine annuals wants one envelope with nine lines in it, and
nine envelopes is how the office got a reputation.

It renews in a batch. A carrier who rings up and says renew all nine gets nine
new permits in one transaction, each priced off the schedule in force on the
day of renewal, each numbered off the counter in order. If any one of the nine
cannot be renewed the whole batch rolls back, because half a renewal is worse
than none: the carrier is told nine and gets four, and nobody finds out which
four until a truck is stopped.

What stops a renewal is the carrier rather than the permit. A suspended
carrier, a lapsed certificate, or an enforcement hold stops the whole batch,
and the letter says which. The permit itself cannot stop a renewal because a
renewal is a new permit and the old one is simply allowed to run out.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from sqlalchemy import select

from ..errors import NotAllowed
from ..permits.annual import issue_annual
from ..permits.numbering import Series
from ..permits.office import Office
from ..permits.permit import Permit, PermitState
from ..persistence.models.permits import PermitRow
from ..persistence.unitofwork import UnitOfWork
from . import audit

#: How far ahead the run looks when nobody says otherwise.
AHEAD_DAYS = 30

#: Who the run is recorded as, when it is run from cron rather than a counter.
ACTOR = "renewals"


@dataclass(frozen=True)
class Due:
    """One annual permit running out, with what the letter needs."""

    number: str
    usdot: int
    carrier: str
    expires: date

    def days_left(self, on: date) -> int:
        return (self.expires - on).days

    def __str__(self) -> str:
        return f"{self.number}  {self.expires.isoformat()}  {self.carrier}"


@dataclass(frozen=True)
class Letter:
    """One carrier's renewal letter, however many permits are on it."""

    usdot: int
    carrier: str
    due: tuple[Due, ...]

    @property
    def count(self) -> int:
        return len(self.due)

    @property
    def soonest(self) -> date:
        return min(one.expires for one in self.due)

    def lines(self) -> tuple[str, ...]:
        found = [f"{self.carrier} ({self.usdot})"]
        found.extend(f"  {one.number} expires {one.expires.isoformat()}" for one in self.due)
        found.append(f"  {self.count} permits, first on {self.soonest.isoformat()}")
        return tuple(found)

    def __str__(self) -> str:
        return f"{self.carrier}: {self.count} due by {self.soonest.isoformat()}"


def due_by(work: UnitOfWork, on: date, within: int = AHEAD_DAYS) -> tuple[Due, ...]:
    """Every live annual running out inside the window, soonest first."""
    horizon = on + timedelta(days=within)
    query = (
        select(PermitRow)
        .where(
            PermitRow.series == Series.ANNUAL.value,
            PermitRow.state == PermitState.ISSUED,
            PermitRow.valid_to >= on,
            PermitRow.valid_to <= horizon,
        )
        .order_by(PermitRow.valid_to, PermitRow.number)
    )
    return tuple(
        Due(one.number, one.usdot, one.carrier_name, one.valid_to)
        for one in work.db.scalars(query).all()
    )


def letters(work: UnitOfWork, on: date, within: int = AHEAD_DAYS) -> tuple[Letter, ...]:
    """One letter per carrier, soonest expiry first, however many permits."""
    grouped: dict[int, list[Due]] = {}
    names: dict[int, str] = {}
    for one in due_by(work, on, within):
        grouped.setdefault(one.usdot, []).append(one)
        names[one.usdot] = one.carrier
    found = [Letter(usdot, names[usdot], tuple(due)) for usdot, due in grouped.items()]
    return tuple(sorted(found, key=lambda one: (one.soonest, one.usdot)))


@dataclass(frozen=True)
class Renewed:
    """What a batch renewal produced."""

    usdot: int
    replaced: tuple[str, ...]
    issued: tuple[Permit, ...]

    @property
    def count(self) -> int:
        return len(self.issued)

    def __str__(self) -> str:
        return f"{self.usdot}: {self.count} renewed"


def renew(
    work: UnitOfWork,
    office: Office,
    usdot: int,
    numbers: tuple[str, ...],
    on: date,
    who: str = ACTOR,
) -> Renewed:
    """Renew a carrier's annuals in one transaction, or none of them.

    The old permits are left to run out rather than superseded. A superseded
    permit is one that was replaced because it was wrong, and these were not
    wrong: they were right for a year and the year is ending.
    """
    if not numbers:
        raise NotAllowed("a renewal names at least one permit")
    carrier = office.carriers.get(usdot)
    standing = carrier.standing(on, on, 0)
    if standing:
        raise NotAllowed(f"{usdot} cannot renew: {standing}", usdot=usdot)
    hold = office.enforcement.hold(usdot, on)
    if hold:
        raise NotAllowed(f"{usdot} cannot renew: {hold}", usdot=usdot)

    made: list[Permit] = []
    for number in numbers:
        old = work.permits.get(number)
        if old.usdot != usdot:
            raise NotAllowed(f"{number} does not belong to {usdot}", permit=number, usdot=usdot)
        if old.state is not PermitState.ISSUED:
            raise NotAllowed(f"{number} is {old.state} and is not renewable", permit=number)
        made.append(_one(work, office, old, on))
    audit.record(
        work.db,
        "permit.renewed",
        who,
        "carrier",
        str(usdot),
        detail=f"{len(made)} annuals",
    )
    return Renewed(usdot, numbers, tuple(made))


def _one(work: UnitOfWork, office: Office, old: Permit, on: date) -> Permit:
    """Issue one replacement annual off today's schedule and counter.

    The commodity is read off the application the old permit was written
    from. That is the one thing a permit does not carry: the paper says what
    is allowed rather than what is being moved, and the annual eligibility
    rules want the commodity.
    """
    application = work.applications.get(old.application)
    made = issue_annual(
        numbering=office.numbering,
        library=office.library,
        schedule=office.schedules.on(on),
        application_reference=old.application,
        usdot=old.usdot,
        carrier_name=old.carrier_name,
        envelope=old.envelope,
        gross=old.gross,
        commodity=office.commodities.get(application.commodity),
        on=on,
    )
    work.permits.add(made)
    return made
