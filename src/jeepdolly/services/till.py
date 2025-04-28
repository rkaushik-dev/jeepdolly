"""Taking money, and proving at the end of the day that it adds up.

Three questions and the whole file answers only these.

What is owed on a permit. The fee that was printed, less every receipt against
it. A permit with nothing against it is owed in full, and one paid twice is
owed a negative amount, which is the office's own way of finding the mistake.

What is in the drawer. Cash and cheques taken today, which is the number the
clerk counts against the paper and initials.

What is outstanding. Permits issued and not paid, oldest first, which is the
list that goes to the carrier before anybody is suspended for it.

Nothing here decides whether to issue a permit. A hauler on account drives out
with a permit and an invoice, and a hauler who never pays gets suspended by a
supervisor, not by this.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..errors import Impossible, NotOnFile
from ..money import Dollars, total
from ..persistence.models.permits import FeeLineRow, PermitRow
from ..persistence.models.receipts import METHODS, ReceiptRow


@dataclass(frozen=True)
class Owing:
    """One permit and what is still owed on it."""

    permit: str
    carrier: str
    issued: date
    charged: Dollars
    paid: Dollars

    @property
    def owed(self) -> Dollars:
        return self.charged - self.paid

    @property
    def settled(self) -> bool:
        return self.owed.cents == 0

    def __str__(self) -> str:
        return f"{self.permit}  {self.carrier}  {self.charged}  {self.paid}  {self.owed}"


@dataclass(frozen=True)
class Drawer:
    """What was taken on one day, split by whether it is in the drawer."""

    day: date
    in_the_drawer: Dollars
    on_account: Dollars
    receipts: int

    @property
    def taken(self) -> Dollars:
        return self.in_the_drawer + self.on_account

    def lines(self) -> tuple[str, ...]:
        return (
            f"till {self.day.isoformat()}",
            f"in the drawer  {self.in_the_drawer}",
            f"on account  {self.on_account}",
            f"{self.receipts} receipts  {self.taken}",
        )

    def __str__(self) -> str:
        return f"{self.receipts} receipts, {self.in_the_drawer} in the drawer"


def charged_on(session: Session, number: str) -> Dollars:
    query = select(FeeLineRow).where(FeeLineRow.number == number)
    return total(one.amount for one in session.scalars(query).all())


def paid_on(session: Session, number: str) -> Dollars:
    query = select(ReceiptRow).where(ReceiptRow.permit == number)
    return total(one.amount for one in session.scalars(query).all())


def take(
    session: Session,
    number: str,
    amount: Dollars,
    method: str,
    by: str,
    on: date,
    reference: str = "",
    note: str = "",
) -> ReceiptRow:
    """Write a receipt against a permit."""
    if method not in METHODS:
        raise Impossible(f"{method!r} is not a way this office takes money", method=method)
    if amount.cents == 0:
        raise Impossible("a receipt is for an amount")
    if session.get(PermitRow, number) is None:
        raise NotOnFile(f"no permit {number}", number=number)
    row = ReceiptRow(
        permit=number,
        taken=on,
        method=method,
        amount=amount,
        taken_by=by,
        reference=reference,
        note=note,
    )
    session.add(row)
    return row


def owing_on(session: Session, number: str) -> Owing:
    """What one permit was charged and what is left on it."""
    permit = session.get(PermitRow, number)
    if permit is None:
        raise NotOnFile(f"no permit {number}", number=number)
    return Owing(
        permit=number,
        carrier=permit.carrier_name,
        issued=permit.issued,
        charged=charged_on(session, number),
        paid=paid_on(session, number),
    )


def outstanding(session: Session, on: date) -> tuple[Owing, ...]:
    """Every permit issued and not settled, oldest first."""
    query = (
        select(PermitRow).where(PermitRow.issued <= on).order_by(PermitRow.issued, PermitRow.number)
    )
    found = []
    for row in session.scalars(query).all():
        owing = owing_on(session, row.number)
        if not owing.settled:
            found.append(owing)
    return tuple(found)


def drawer(session: Session, day: date) -> Drawer:
    """What was taken on one day, split the way the clerk counts it."""
    query = select(ReceiptRow).where(ReceiptRow.taken == day)
    rows = list(session.scalars(query).all())
    return Drawer(
        day=day,
        in_the_drawer=total(one.amount for one in rows if one.in_the_drawer),
        on_account=total(one.amount for one in rows if not one.in_the_drawer),
        receipts=len(rows),
    )


def owed_by_carrier(session: Session, usdot: int, on: date) -> Dollars:
    """What one carrier still owes, which is what the letter says."""
    query = select(PermitRow).where(PermitRow.usdot == usdot, PermitRow.issued <= on)
    return total(owing_on(session, one.number).owed for one in session.scalars(query).all())
