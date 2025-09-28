"""Renewal routes: what is due, who to write to, and the batch itself.

The batch route takes a list of permit numbers rather than renewing everything
a carrier holds. That is deliberate and it is what the counter asked for: a
crane company with nine annuals renews seven of them and lets two go, because
two of the cranes were sold, and a route that renewed everything would have
the office selling permits nobody wanted.

It is a POST to a carrier rather than to each permit because it is one
decision. Nine permits renewed in nine requests is nine transactions and eight
chances to stop halfway, which is the thing the service exists to prevent.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from ...persistence.reference import ReferenceCache
from ...persistence.unitofwork import UnitOfWork
from ...services import renewals
from ...services.renewals import AHEAD_DAYS
from ..deps import office_from, reference_cache, unit_of_work
from ..schemas import PermitOut
from ..security import Caller, Role, needs

router = APIRouter(prefix="/renewals", tags=["renewals"])


class DueOut(BaseModel):
    """One annual running out."""

    number: str
    usdot: int
    carrier: str
    expires: date
    days_left: int


class LetterOut(BaseModel):
    """One carrier's letter, however many permits are on it."""

    usdot: int
    carrier: str
    count: int
    soonest: date
    numbers: list[str]
    lines: list[str]


class RenewIn(BaseModel):
    """The permits a carrier has asked to renew."""

    numbers: list[str] = Field(min_length=1)


class RenewedOut(BaseModel):
    """What the batch produced."""

    usdot: int
    replaced: list[str]
    issued: list[PermitOut]


@router.get("", response_model=list[DueOut])
def due(
    on: date | None = Query(default=None),
    within: int = Query(default=AHEAD_DAYS, ge=0, le=365),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[DueOut]:
    """Every live annual running out inside the window, soonest first."""
    day = on or date.today()
    return [
        DueOut(
            number=one.number,
            usdot=one.usdot,
            carrier=one.carrier,
            expires=one.expires,
            days_left=one.days_left(day),
        )
        for one in renewals.due_by(work, day, within)
    ]


@router.get("/letters", response_model=list[LetterOut])
def letters(
    on: date | None = Query(default=None),
    within: int = Query(default=AHEAD_DAYS, ge=0, le=365),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[LetterOut]:
    """One letter per carrier, which is what goes in the mail merge."""
    day = on or date.today()
    return [
        LetterOut(
            usdot=one.usdot,
            carrier=one.carrier,
            count=one.count,
            soonest=one.soonest,
            numbers=[each.number for each in one.due],
            lines=list(one.lines()),
        )
        for one in renewals.letters(work, day, within)
    ]


@router.post("/{usdot}", response_model=RenewedOut, status_code=201)
def renew(
    usdot: int,
    body: RenewIn,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    cache: ReferenceCache | None = Depends(reference_cache),
    who: Caller = Depends(needs(Role.CLERK)),
) -> RenewedOut:
    """Renew a carrier's annuals in one transaction, or none of them."""
    day = on or date.today()
    office = office_from(work, day, cache=cache)
    found = renewals.renew(work, office, usdot, tuple(body.numbers), day, who.person)
    work.commit()
    return RenewedOut(
        usdot=found.usdot,
        replaced=list(found.replaced),
        issued=[PermitOut.of(one) for one in found.issued],
    )
