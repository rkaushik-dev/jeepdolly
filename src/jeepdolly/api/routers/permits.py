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

Reprinting is here rather than in a register of its own because a copy is a
permit route: the caller wants the sheet back, not a receipt for having asked
for it. The copy number and the sheet come back together so the clerk can put
one on the printer and the other in the file.
"""

from __future__ import annotations

from datetime import date, timedelta

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from ... import logs
from ...errors import NotAllowed, Unreadable
from ...permits.permit import Permit
from ...permits.printing import sheet
from ...permits.reprint import FREELY, Reason, Reprint, reprint
from ...persistence.models.permits import PermitRow
from ...persistence.unitofwork import UnitOfWork
from ...services import audit, counter
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


class ReprintIn(BaseModel):
    """What the counter is told when somebody asks for another copy."""

    reason: str
    asked_by: str
    note: str = Field(default="", max_length=500)

    def kind(self) -> Reason:
        try:
            return Reason(self.reason)
        except ValueError:
            raise Unreadable(
                f"{self.reason!r} is not a reason this office records",
                reason=self.reason,
            ) from None


class ReprintOut(BaseModel):
    """The copy, and the sheet to put on the printer."""

    permit: str
    # Called copy_number rather than copy because pydantic's own copy method
    # is on every model and a field of that name shadows it. The register and
    # the printed banner both say copy.
    copy_number: int
    issued_on: date
    reason: str
    asked_by: str
    issued_by: str
    needed_a_supervisor: bool
    sheet: str

    @classmethod
    def of(cls, made: Reprint, written: str) -> ReprintOut:
        return cls(
            permit=made.permit,
            copy_number=made.copy,
            issued_on=made.on,
            reason=made.reason.value,
            asked_by=made.asked_by,
            issued_by=made.issued_by,
            needed_a_supervisor=made.needed_a_supervisor,
            sheet=written,
        )


class CopyOut(BaseModel):
    """One line of the register beside a permit."""

    copy_number: int
    issued_on: date
    reason: str
    asked_by: str
    issued_by: str
    note: str


@router.post("/{number}/copies", response_model=ReprintOut, status_code=201)
def reprint_permit(
    number: str,
    body: ReprintIn,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> ReprintOut:
    """Print another copy, numbered, with the reason on the register.

    A clerk may print up to the free threshold. Past it the route refuses and
    the same request from a supervisor goes through, which is the rung of the
    ladder doing the work rather than a flag on the body.
    """
    day = on or date.today()
    permit = work.permits.get(number)
    made = reprint(
        permit,
        day,
        body.kind(),
        body.asked_by,
        who.person,
        taken=work.reprints.copies_taken(number),
        supervisor=who.role is Role.SUPERVISOR,
        note=body.note,
    )
    work.reprints.add(made)
    audit.record(
        work.db,
        "permit.reprinted",
        who.person,
        "permit",
        number,
        detail=f"copy {made.copy}, {made.reason}",
    )
    work.commit()
    logs.event("permit.reprinted", number=number, copy=made.copy)
    return ReprintOut.of(made, sheet(permit, made.copy))


@router.get("/{number}/copies", response_model=list[CopyOut])
def copies_of(
    number: str,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[CopyOut]:
    """Every copy of a permit the office has handed out."""
    work.permits.row(number)
    return [
        CopyOut(
            copy_number=one.copy,
            issued_on=one.issued_on,
            reason=one.reason.value,
            asked_by=one.asked_by,
            issued_by=one.issued_by,
            note=one.note or "",
        )
        for one in work.reprints.for_permit(number)
    ]


@router.get("/copies/free-limit")
def free_limit(who: Caller = Depends(needs(Role.READER))) -> dict[str, int]:
    """How many copies a clerk may print before a supervisor is wanted."""
    return {"freely": FREELY}
