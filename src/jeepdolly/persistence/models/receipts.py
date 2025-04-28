"""Receipts, which are the other half of a permit and were missing.

A permit has a fee on it. Until now nothing said whether anybody paid it, and
the office reconciled the till against the issuance report by hand, which is
an hour every evening and an argument every month.

A receipt is one payment against one permit. It is never edited and never
deleted. A payment taken twice is refunded with a second receipt for a
negative amount, so the till still adds up and the mistake is visible.

The method matters because the till only holds two of them. Cash and cheque
are in the drawer at the end of the day. An account payment is not, and the
nightly reconciliation counts them apart for exactly that reason.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ..base import CODE_LENGTH, NAME_LENGTH, TEXT_LENGTH, Base, CentsType

#: What the drawer holds at the end of the day.
IN_THE_DRAWER = ("cash", "cheque")

#: Everything the office will take.
METHODS = (*IN_THE_DRAWER, "account", "card", "refund")


class ReceiptRow(Base):
    """One payment against one permit."""

    __tablename__ = "receipts"
    __table_args__ = (
        CheckConstraint("length(method) > 0", name="method_not_empty"),
        CheckConstraint("amount <> 0", name="amount_not_nothing"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    permit: Mapped[str] = mapped_column(ForeignKey("permits.number"), index=True)
    taken: Mapped[date] = mapped_column(Date, index=True)
    method: Mapped[str] = mapped_column(String(CODE_LENGTH), index=True)
    amount = mapped_column(CentsType)
    taken_by: Mapped[str] = mapped_column(String(NAME_LENGTH))
    reference: Mapped[str] = mapped_column(String(CODE_LENGTH), default="")
    note: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")

    permit_row = relationship("PermitRow")

    @property
    def in_the_drawer(self) -> bool:
        return self.method in IN_THE_DRAWER

    def __repr__(self) -> str:
        return f"<ReceiptRow {self.permit} {self.method} {self.amount}>"
