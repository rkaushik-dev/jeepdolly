"""Taking money over the interface, and the two lists the office reads.

Taking a payment is a clerk's job and it lands on the trail like everything
else that moves money. Reading what is owed is a reader's job, and a carrier
key reading its own account is the case this was built for: the question "what
do I owe you" used to be a telephone call and a printed statement.

The drawer route is the one the clerk uses at half past four. It is a read,
it changes nothing, and it is the number that gets counted against the paper
before anybody goes home.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel

from ...money import Dollars
from ...persistence.unitofwork import UnitOfWork
from ...services import audit, till
from ..deps import unit_of_work
from ..security import Caller, Role, needs

router = APIRouter(prefix="/receipts", tags=["receipts"])


class ReceiptIn(BaseModel):
    """A payment as the clerk types it."""

    permit: str
    amount: str
    method: str
    reference: str = ""
    note: str = ""


class OwingOut(BaseModel):
    """One permit and what is left on it."""

    permit: str
    carrier: str
    issued: date
    charged: str
    paid: str
    owed: str
    settled: bool


class DrawerOut(BaseModel):
    """What was taken on a day, split the way the clerk counts it."""

    day: date
    in_the_drawer: str
    on_account: str
    taken: str
    receipts: int
    lines: list[str]


def _owing(found: till.Owing) -> OwingOut:
    return OwingOut(
        permit=found.permit,
        carrier=found.carrier,
        issued=found.issued,
        charged=str(found.charged),
        paid=str(found.paid),
        owed=str(found.owed),
        settled=found.settled,
    )


@router.post("", response_model=OwingOut, status_code=201)
def take_payment(
    body: ReceiptIn,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> OwingOut:
    """Write a receipt against a permit and say what is left on it."""
    day = on or date.today()
    till.take(
        work.db,
        body.permit,
        Dollars.parse(body.amount),
        body.method,
        who.person,
        day,
        reference=body.reference,
        note=body.note,
    )
    audit.record(
        work.db,
        "payment.taken",
        who.person,
        "permit",
        body.permit,
        detail=f"{body.method} {body.amount}",
    )
    found = till.owing_on(work.db, body.permit)
    work.commit()
    return _owing(found)


@router.get("/owing/{number}", response_model=OwingOut)
def owing_on_permit(
    number: str,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> OwingOut:
    """What one permit was charged and what is left on it."""
    return _owing(till.owing_on(work.db, number))


@router.get("/outstanding", response_model=list[OwingOut])
def outstanding_permits(
    day: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[OwingOut]:
    """Everything issued and not settled, oldest first."""
    return [_owing(one) for one in till.outstanding(work.db, day or date.today())]


@router.get("/carrier/{usdot}", response_model=dict)
def owed_by_carrier(
    usdot: int,
    day: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> dict[str, object]:
    """One number for one carrier, which is what the letter says."""
    when = day or date.today()
    return {"usdot": usdot, "owed": str(till.owed_by_carrier(work.db, usdot, when))}


@router.get("/drawer", response_model=DrawerOut)
def drawer(
    day: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> DrawerOut:
    """What is in the drawer at half past four."""
    found = till.drawer(work.db, day or date.today())
    return DrawerOut(
        day=found.day,
        in_the_drawer=str(found.in_the_drawer),
        on_account=str(found.on_account),
        taken=str(found.taken),
        receipts=found.receipts,
        lines=list(found.lines()),
    )
