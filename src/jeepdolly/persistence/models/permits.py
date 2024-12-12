"""The permit tables, which copy rather than join.

A permit row holds the printed narrative, the rendered condition text and the
fee lines as words and numbers, not as references to the library and the
schedule they came from. That is deliberate duplication and it is the whole
reason the office can answer a question about a permit written four years ago
without keeping four years of tables identical to what they were.

The counter table is one row per year and series holding the next sequence.
It is read for update, incremented and written in the same transaction as the
permit that takes the number, so a crash between the two leaves both undone.
That is why the counter is a table and not a sequence: a sequence hands out a
number that is gone whether or not the transaction commits, and this office
reconciles its money against a gapless run of numbers.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ...permits.permit import PermitState
from ..base import (
    CODE_LENGTH,
    NAME_LENGTH,
    TEXT_LENGTH,
    Base,
    CentsType,
    EnumValue,
    InchesType,
    MilesType,
    PoundsType,
)


class PermitRow(Base):
    """One issued permit, complete on its own."""

    __tablename__ = "permits"
    __table_args__ = (
        CheckConstraint("valid_to >= valid_from", name="span_ends_after_it_starts"),
        CheckConstraint("valid_from >= issued", name="not_valid_before_issue"),
        CheckConstraint("length(route) > 0", name="route_not_empty"),
    )

    number: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    series: Mapped[str] = mapped_column(String(2), index=True)
    application: Mapped[str] = mapped_column(ForeignKey("applications.reference"), index=True)
    usdot: Mapped[int] = mapped_column(ForeignKey("carriers.usdot"), index=True)
    carrier_name: Mapped[str] = mapped_column(String(NAME_LENGTH))
    issued: Mapped[date] = mapped_column(Date, index=True)
    valid_from: Mapped[date] = mapped_column(Date, index=True)
    valid_to: Mapped[date] = mapped_column(Date, index=True)
    origin: Mapped[str] = mapped_column(String(CODE_LENGTH))
    destination: Mapped[str] = mapped_column(String(CODE_LENGTH))
    route: Mapped[str] = mapped_column(String(TEXT_LENGTH))
    narrative: Mapped[str] = mapped_column(Text(TEXT_LENGTH))
    gross = mapped_column(PoundsType)
    width = mapped_column(InchesType)
    height = mapped_column(InchesType)
    length = mapped_column(InchesType)
    front_overhang = mapped_column(InchesType)
    rear_overhang = mapped_column(InchesType)
    distance = mapped_column(MilesType)
    state: Mapped[PermitState] = mapped_column(
        EnumValue(PermitState), default=PermitState.ISSUED, index=True
    )
    void_reason: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")
    superseded_by: Mapped[str | None] = mapped_column(String(CODE_LENGTH), nullable=True)
    supersedes: Mapped[str | None] = mapped_column(String(CODE_LENGTH), nullable=True)
    schedule: Mapped[str] = mapped_column(String(NAME_LENGTH), default="")

    # Here to order the inserts rather than because anything reads them.
    application_row = relationship("ApplicationRow")
    carrier = relationship("CarrierRow")

    conditions: Mapped[list[PermitConditionRow]] = relationship(
        back_populates="permit",
        cascade="all, delete-orphan",
        order_by="PermitConditionRow.position",
    )
    fees: Mapped[list[FeeLineRow]] = relationship(
        back_populates="permit", cascade="all, delete-orphan", order_by="FeeLineRow.position"
    )

    @property
    def route_idents(self) -> tuple[str, ...]:
        return tuple(one for one in (self.route or "").split(",") if one)

    @property
    def days(self) -> int:
        return (self.valid_to - self.valid_from).days + 1

    def __repr__(self) -> str:
        return f"<PermitRow {self.number} {self.state}>"


class PermitConditionRow(Base):
    """One rendered condition, in the words it was printed in."""

    __tablename__ = "permit_conditions"
    __table_args__ = (CheckConstraint("length(body) > 0", name="body_not_empty"),)

    number: Mapped[str] = mapped_column(ForeignKey("permits.number"), primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    code: Mapped[str] = mapped_column(String(CODE_LENGTH))
    category: Mapped[str] = mapped_column(String(CODE_LENGTH))
    body: Mapped[str] = mapped_column(Text(TEXT_LENGTH))

    permit: Mapped[PermitRow] = relationship(back_populates="conditions")

    def __repr__(self) -> str:
        return f"<PermitConditionRow {self.number} {self.code}>"


class FeeLineRow(Base):
    """One line of what the permit cost, in the words the receipt used."""

    __tablename__ = "permit_fees"

    number: Mapped[str] = mapped_column(ForeignKey("permits.number"), primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    description: Mapped[str] = mapped_column(String(NAME_LENGTH))
    amount = mapped_column(CentsType)

    permit: Mapped[PermitRow] = relationship(back_populates="fees")

    def __repr__(self) -> str:
        return f"<FeeLineRow {self.number} {self.description}>"


class CounterRow(Base):
    """The next sequence for one year and one series."""

    __tablename__ = "number_counters"
    __table_args__ = (CheckConstraint("next_sequence >= 1", name="sequence_starts_at_one"),)

    year: Mapped[int] = mapped_column(Integer, primary_key=True)
    series: Mapped[str] = mapped_column(String(2), primary_key=True)
    next_sequence: Mapped[int] = mapped_column(Integer, default=1)

    def __repr__(self) -> str:
        return f"<CounterRow {self.year} {self.series} at {self.next_sequence}>"
