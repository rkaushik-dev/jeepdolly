"""Reprint routes, which were in the permits file until it had two subjects.

A copy is a permit route in the sense that the caller wants the sheet back.
It is not a permit route in any other sense: it decides nothing about what is
allowed, it touches no permit record, and the only thing it shares with
issuing is the number in the path.

Keeping them together meant the permits file imported the printer, the reprint
rules and the trail, none of which issuing needs, and it had grown two
docstring paragraphs about copies before anybody noticed.

The rung of the ladder does the work here. A clerk may print up to the free
limit and a supervisor may print past it, which is one rule expressed as two
roles rather than as a flag on the body.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from ... import logs
from ...errors import Unreadable
from ...permits.printing import sheet
from ...permits.reprint import FREELY, Reason, Reprint, reprint
from ...persistence.unitofwork import UnitOfWork
from ...services import audit
from ..deps import unit_of_work
from ..security import Caller, Role, needs

router = APIRouter(prefix="/permits", tags=["reprints"])


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
