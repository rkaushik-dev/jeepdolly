"""Permit routes: issue one, read one, void one, see what is on the road.

Issuing is the only route in the service that takes money and hands out a
number, so it is the one to read carefully.

It assesses the application again rather than trusting an assessment the
client sends back. A client that could post an assessment could post a cheaper
one. The assessment endpoint exists to answer questions, and nothing it
returns is ever an input to this.

The number is taken from the counter inside this transaction, so a failure
anywhere after it rolls the number back with the permit. That is the whole
reason the counter is a table and the office is assembled per request.
"""

from __future__ import annotations

from datetime import date, timedelta

from fastapi import APIRouter, Depends, Query

from ...permits.issue import assess, issue
from ...persistence.unitofwork import UnitOfWork
from ..deps import office_from, unit_of_work
from ..schemas import PermitOut

router = APIRouter(prefix="/permits", tags=["permits"])


@router.post("", response_model=PermitOut, status_code=201)
def issue_permit(
    reference: str = Query(...),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
) -> PermitOut:
    """Assess an application again and issue against it."""
    application = work.applications.get(reference)
    day = on or date.today()
    desk = office_from(work, application.start)
    found = assess(desk, application, application.start)
    permit = issue(desk, found, day)
    work.applications.save_state(application)
    work.permits.add(permit)
    work.commit()
    return PermitOut.of(permit)


@router.get("/{number}", response_model=PermitOut)
def read_permit(number: str, work: UnitOfWork = Depends(unit_of_work)) -> PermitOut:
    """One permit, complete, as it was printed."""
    return PermitOut.of(work.permits.get(number))


@router.get("", response_model=list[PermitOut])
def permits_on_the_road(
    day: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
) -> list[PermitOut]:
    """Everything valid for travel on a day."""
    rows = work.permits.on_the_road(day or date.today())
    return [PermitOut.of(work.permits.get(one.number)) for one in rows]


@router.get("/expiring/soon", response_model=list[PermitOut])
def expiring_permits(
    day: date | None = Query(default=None),
    within: int = Query(default=2, ge=0, le=60),
    work: UnitOfWork = Depends(unit_of_work),
) -> list[PermitOut]:
    """What the office rings about on a Friday afternoon."""
    start = day or date.today()
    rows = work.permits.expiring(start, start + timedelta(days=within))
    return [PermitOut.of(work.permits.get(one.number)) for one in rows]


@router.post("/{number}/void", response_model=PermitOut)
def void_permit(
    number: str,
    reason: str = Query(..., min_length=3),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
) -> PermitOut:
    """Kill a permit, with the reason on the record."""
    permit = work.permits.get(number)
    permit.void(on or date.today(), reason)
    work.permits.save_state(permit)
    work.commit()
    return PermitOut.of(permit)


@router.get("/carrier/{usdot}", response_model=list[PermitOut])
def permits_for_carrier(usdot: int, work: UnitOfWork = Depends(unit_of_work)) -> list[PermitOut]:
    """What one carrier has out, newest first."""
    rows = work.permits.for_carrier(usdot)
    return [PermitOut.of(work.permits.get(one.number)) for one in rows]
