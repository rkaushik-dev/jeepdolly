"""Structure routes: the inventory, the postings, and the study queue.

Posting a structure is a supervisor's decision and it is the one write in the
service that can strand a permit that has already been issued. The office does
not void those automatically: a posting is often a precaution taken on a
Friday and lifted on the Monday, and voiding every permit over that bridge
would strand loads that are already halfway there.

What it does instead is report which live permits cross the structure, so the
supervisor can ring them. That list is the whole reason the route is on a
permit as segment idents rather than as prose.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel

from ...measure import Pounds
from ...persistence.models.structures import ReviewRow, StructureRow
from ...persistence.unitofwork import UnitOfWork
from ...services import audit, studies
from ..deps import unit_of_work
from ..security import Caller, Role, needs

router = APIRouter(prefix="/structures", tags=["structures"])


class StructureOut(BaseModel):
    """One structure as the inventory holds it."""

    ident: str
    name: str
    segment: str
    kind: str
    longest_span: int
    posted: str | None
    operating_rating: str | None
    inspected: date | None


class PostingOut(BaseModel):
    """What a posting did, and who is on the road under it."""

    structure: StructureOut
    permits_crossing: list[str]


class StudyOut(BaseModel):
    """One study in the queue."""

    reference: str
    state: str
    opened: date
    engineer: str | None
    structures: list[str]
    conditions: list[str]


def _structure(row: StructureRow) -> StructureOut:
    structure = row.to_domain()
    return StructureOut(
        ident=structure.ident,
        name=structure.name,
        segment=structure.segment,
        kind=structure.kind.value,
        longest_span=structure.longest_span,
        posted=str(structure.posted) if structure.posted else None,
        operating_rating=str(structure.operating_rating) if structure.operating_rating else None,
        inspected=structure.inspected,
    )


def _study(row: ReviewRow) -> StudyOut:
    return StudyOut(
        reference=row.reference,
        state=row.state.value,
        opened=row.opened,
        engineer=row.engineer,
        structures=[one for one in (row.structures or "").split(",") if one],
        conditions=[one for one in (row.conditions or "").split("|") if one],
    )


@router.get("/on/{segment}", response_model=list[StructureOut])
def structures_on_segment(
    segment: str,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[StructureOut]:
    """Everything on one piece of road."""
    return [_structure(one) for one in work.network.structures_on(segment)]


@router.get("/posted", response_model=list[StructureOut])
def posted_structures(
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[StructureOut]:
    """The posted list, which is the one the router reads."""
    return [_structure(one) for one in work.network.posted_structures()]


@router.post("/{ident}/posting", response_model=PostingOut)
def post_structure(
    ident: str,
    weight: str = Query(...),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> PostingOut:
    """Put a weight on a structure, and say who is already crossing it."""
    day = on or date.today()
    row = work.network.structure(ident)
    row.posted = Pounds.parse(weight)
    on_the_road = work.permits.on_the_road(day)
    crossing = [one.number for one in on_the_road if row.segment_ident in one.route_idents]
    audit.record(
        work.db,
        "structure.posted",
        who.person,
        "structure",
        ident,
        detail=f"posted at {row.posted}, {len(crossing)} permits crossing",
    )
    work.commit()
    return PostingOut(structure=_structure(row), permits_crossing=crossing)


@router.get("/studies/waiting", response_model=list[StudyOut])
def waiting_studies(
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[StudyOut]:
    """The queue, oldest first."""
    return [_study(one) for one in studies.waiting(work.db)]


@router.post("/studies/{reference}/assign", response_model=StudyOut)
def assign_study(
    reference: str,
    engineer: str = Query(...),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.ENGINEER)),
) -> StudyOut:
    """An engineer picks one up."""
    row = studies.assign(work.db, reference, engineer, on or date.today())
    audit.record(work.db, "review.assigned", who.person, "review", reference, detail=engineer)
    work.commit()
    return _study(row)


@router.post("/studies/{reference}/clear", response_model=StudyOut)
def clear_study(
    reference: str,
    condition: list[str] = Query(default=[]),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.ENGINEER)),
) -> StudyOut:
    """Signed, with whatever conditions the engineer wrote."""
    row = studies.clear(work.db, reference, on or date.today(), tuple(condition))
    audit.record(work.db, "review.cleared", who.person, "review", reference)
    work.commit()
    return _study(row)


@router.post("/studies/{reference}/refuse", response_model=StudyOut)
def refuse_study(
    reference: str,
    reason: str = Query(..., min_length=3),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.ENGINEER)),
) -> StudyOut:
    """Signed the other way."""
    row = studies.refuse(work.db, reference, on or date.today(), reason)
    audit.record(work.db, "review.refused", who.person, "review", reference, detail=reason)
    work.commit()
    return _study(row)
