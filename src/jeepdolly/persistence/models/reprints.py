"""The reprint register, which is a list beside a permit and never on it.

One table and its primary key is the permit number and the copy number
together. That is not a convenience. It is the constraint that stops two
clerks at two counters both printing copy two of the same permit: the second
insert fails, and the second clerk is told to try again rather than handed a
duplicate number.

The alternative was a surrogate key with a unique index over the same two
columns, which is the same guarantee written twice. The office has no use for
a reprint id and nothing anywhere refers to one.

Nothing here cascades from the permit. A permit is never deleted, so the
question does not arise, and a cascade would quietly destroy the record of who
was handed a copy of what.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ...permits.reprint import Reason, Reprint
from ..base import NAME_LENGTH, TEXT_LENGTH, Base, EnumValue


class ReprintRow(Base):
    """One duplicate copy, as it was issued."""

    __tablename__ = "reprints"
    __table_args__ = (CheckConstraint("copy > 1", name="the_original_is_not_a_reprint"),)

    permit: Mapped[str] = mapped_column(ForeignKey("permits.number"), primary_key=True)
    copy: Mapped[int] = mapped_column(Integer, primary_key=True)
    issued_on: Mapped[date] = mapped_column(Date, index=True)
    reason: Mapped[Reason] = mapped_column(EnumValue(Reason), index=True)
    asked_by: Mapped[str] = mapped_column(String(NAME_LENGTH))
    issued_by: Mapped[str] = mapped_column(String(NAME_LENGTH))
    note: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")

    # Here to order the inserts rather than because anything reads it.
    permit_row = relationship("PermitRow")

    def to_domain(self) -> Reprint:
        return Reprint(
            permit=self.permit,
            copy=self.copy,
            on=self.issued_on,
            reason=self.reason,
            asked_by=self.asked_by,
            issued_by=self.issued_by,
            note=self.note or "",
        )

    @classmethod
    def of(cls, reprint: Reprint) -> ReprintRow:
        return cls(
            permit=reprint.permit,
            copy=reprint.copy,
            issued_on=reprint.on,
            reason=reprint.reason,
            asked_by=reprint.asked_by,
            issued_by=reprint.issued_by,
            note=reprint.note,
        )

    def __repr__(self) -> str:
        return f"<ReprintRow {self.permit} copy {self.copy}>"
