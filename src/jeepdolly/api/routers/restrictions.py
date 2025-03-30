"""Closing a road and opening it again, which is the busiest write there is.

A closure is typed by whoever takes the telephone call from the district, at
any hour, and it has to be one request with no ceremony. So this route takes a
segment, a note and two optional dates, and everything else is defaulted.

What it gives back is the list of live permits whose route names that segment.
That list is the reason the route exists: somebody has to ring those carriers,
and until this was built they were found by reading the day's permits on
paper.

Opening a road again deletes the row. That is the one place in this system
that deletes anything, and it is right: a lifted closure is not history, the
trail already says who closed it and who opened it, and a restriction table
full of dead rows makes every route check slower for nothing.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel

from ...domain.restriction import RestrictionKind
from ...errors import Conflict, NotOnFile
from ...measure import Inches, Pounds
from ...persistence.models.network import RestrictionRow
from ...persistence.unitofwork import UnitOfWork
from ...services import audit
from ..deps import unit_of_work
from ..security import Caller, Role, needs

router = APIRouter(prefix="/restrictions", tags=["restrictions"])


class RestrictionIn(BaseModel):
    """What the district said on the telephone."""

    ident: str
    segment: str
    kind: str
    note: str
    height: str | None = None
    width: str | None = None
    weight: str | None = None
    region: str | None = None
    effective: date | None = None
    expires: date | None = None


class RestrictionOut(BaseModel):
    """One restriction, and who is already under it."""

    ident: str
    segment: str
    kind: str
    note: str
    effective: date | None
    expires: date | None
    permits_crossing: list[str]


def _crossing(work: UnitOfWork, segment: str, day: date) -> list[str]:
    return [one.number for one in work.permits.touching_segment(segment) if one.valid_to >= day]


@router.post("", response_model=RestrictionOut, status_code=201)
def close_road(
    body: RestrictionIn,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> RestrictionOut:
    """Put a restriction on a segment and say who is already crossing it."""
    day = on or date.today()
    if work.db.get(RestrictionRow, body.ident) is not None:
        raise Conflict(f"restriction {body.ident} is already on file", restriction=body.ident)
    work.network.segment(body.segment)
    row = RestrictionRow(
        ident=body.ident,
        segment_ident=body.segment,
        kind=RestrictionKind(body.kind),
        note=body.note,
        height=Inches.parse(body.height) if body.height else None,
        width=Inches.parse(body.width) if body.width else None,
        weight=Pounds.parse(body.weight) if body.weight else None,
        region=body.region,
        effective=body.effective,
        expires=body.expires,
    )
    work.db.add(row)
    crossing = _crossing(work, body.segment, day)
    audit.record(
        work.db,
        "road.closed",
        who.person,
        "segment",
        body.segment,
        detail=f"{body.kind}: {body.note}, {len(crossing)} permits crossing",
    )
    work.commit()
    return RestrictionOut(
        ident=row.ident,
        segment=row.segment_ident,
        kind=row.kind.value,
        note=row.note,
        effective=row.effective,
        expires=row.expires,
        permits_crossing=crossing,
    )


@router.delete("/{ident}", response_model=RestrictionOut)
def open_road(
    ident: str,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> RestrictionOut:
    """Take a restriction off. The trail keeps the record of it."""
    day = on or date.today()
    row = work.db.get(RestrictionRow, ident)
    if row is None:
        raise NotOnFile(f"no restriction {ident}", restriction=ident)
    answer = RestrictionOut(
        ident=row.ident,
        segment=row.segment_ident,
        kind=row.kind.value,
        note=row.note,
        effective=row.effective,
        expires=row.expires,
        permits_crossing=_crossing(work, row.segment_ident, day),
    )
    audit.record(work.db, "road.opened", who.person, "segment", row.segment_ident, detail=ident)
    work.db.delete(row)
    work.commit()
    return answer


@router.get("/on/{segment}", response_model=list[RestrictionOut])
def restrictions_on(
    segment: str,
    day: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[RestrictionOut]:
    """What is on one segment on a day."""
    when = day or date.today()
    return [
        RestrictionOut(
            ident=one.ident,
            segment=one.segment_ident,
            kind=one.kind.value,
            note=one.note,
            effective=one.effective,
            expires=one.expires,
            permits_crossing=[],
        )
        for one in work.network.restrictions_on(segment, when)
    ]
