"""Permit routes: issue one, read one, void one, see what is on the road.

Issuing is the only route in the service that takes money and hands out a
number, so it is the one to read carefully.

It assesses the application again rather than trusting an assessment the
client sends back. A client that could post an assessment could post a cheaper
one. The assessment endpoint exists to answer questions, and nothing it
returns is ever an input to this.

The number is taken from the counter inside this transaction, so a failure
anywhere after it rolls the number back with the permit.

The walk in route is the one the counter screen actually uses. A hauler at the
window with a load on the truck gets a permit or a reason in one request, and
the office does not want a half filed application left behind either way.
"""

from __future__ import annotations

from datetime import date, timedelta

from fastapi import APIRouter, Depends, Query

from ... import logs
from ...errors import NotAllowed
from ...permits.permit import Permit
from ...persistence.models.permits import PermitRow
from ...persistence.unitofwork import UnitOfWork
from ...services import counter
from ..deps import office_from, unit_of_work
from ..schemas import ApplicationIn, PermitOut
from ..security import Caller, Role, needs

router = APIRouter(prefix="/permits", tags=["permits"])


def _shape(work: UnitOfWork, rows: tuple[PermitRow, ...]) -> list[PermitOut]:
    """Rows to permits to bodies, which four routes were each doing."""
    return [PermitOut.of(work.permits.get(one.number)) for one in rows]


def _issued(permit: Permit, who: Caller) -> PermitOut:
    """Log the line the office greps for, then shape the permit."""
    logs.event(
        "permit.issued",
        number=str(permit.number),
        usdot=permit.usdot,
        gross=permit.gross.value,
        miles=permit.distance.rounded_up(),
        by=who.person,
    )
    return PermitOut.of(permit)


@router.post("", response_model=PermitOut, status_code=201)
def issue_permit(
    reference: str = Query(...),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> PermitOut:
    """Assess an application again and issue against it."""
    day = on or date.today()
    application = work.applications.get(reference)
    desk = office_from(work, application.start)
    written = counter.write_permit(work, desk, reference, who.person, day)
    work.commit()
    return _issued(written.permit, who)


@router.post("/walk-in", response_model=PermitOut, status_code=201)
def walk_in(
    body: ApplicationIn,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> PermitOut:
    """File, submit and issue in one request, which is what a window is."""
    day = on or date.today()
    reference = counter.next_reference(work, day)
    application = body.to_domain(reference, day, work.commodities.book(day))
    desk = office_from(work, application.start)
    try:
        written = counter.over_the_counter(work, desk, application, who.person, day)
    except NotAllowed:
        # The refusal is part of the record. Commit it and then let the
        # refusal out, or the office loses its count of what it turned away.
        work.commit()
        raise
    work.commit()
    return _issued(written.permit, who)


@router.get("/{number}", response_model=PermitOut)
def read_permit(
    number: str,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> PermitOut:
    """One permit, complete, as it was printed."""
    return PermitOut.of(work.permits.get(number))


@router.get("", response_model=list[PermitOut])
def permits_on_the_road(
    day: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[PermitOut]:
    """Everything valid for travel on a day."""
    return _shape(work, work.permits.on_the_road(day or date.today()))


@router.get("/expiring/soon", response_model=list[PermitOut])
def expiring_permits(
    day: date | None = Query(default=None),
    within: int = Query(default=2, ge=0, le=60),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[PermitOut]:
    """What the office rings about on a Friday afternoon."""
    start = day or date.today()
    return _shape(work, work.permits.expiring(start, start + timedelta(days=within)))


@router.post("/{number}/void", response_model=PermitOut)
def void_permit(
    number: str,
    reason: str = Query(..., min_length=3),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> PermitOut:
    """Kill a permit, with the reason on the record."""
    permit = counter.void_permit(work, number, who.person, on or date.today(), reason)
    work.commit()
    logs.event("permit.voided", number=number, by=who.person)
    return PermitOut.of(permit)


@router.get("/carrier/{usdot}", response_model=list[PermitOut])
def permits_for_carrier(
    usdot: int,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[PermitOut]:
    """What one carrier has out, newest first."""
    return _shape(work, work.permits.for_carrier(usdot))
