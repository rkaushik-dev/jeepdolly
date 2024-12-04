"""The reference tables: what can be hauled, and what can be printed.

These two tables have the same shape of life. They are edited rarely, by one
person, from a form, and everything else in the system reads them. So they
carry their own audit columns rather than leaning on the trail: who changed
this line and when, right there on the row, because the question is always
asked about one line and never about the table.

Neither table is ever deleted from. A commodity that is no longer hauled is
retired with a date, and it keeps working for the permits that already name it.
A condition that is reworded gets a new row with a new effective date, so a
permit issued last year still prints the words it was issued with.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import Boolean, CheckConstraint, Date, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from ...domain.commodity import Commodity, Family
from ..base import CODE_LENGTH, NAME_LENGTH, TEXT_LENGTH, Base, EnumValue


class CommodityRow(Base):
    """One line of the commodity table."""

    __tablename__ = "commodities"
    __table_args__ = (
        CheckConstraint("length(code) > 0", name="code_not_empty"),
        CheckConstraint(
            "tip_limit_degrees is null or (tip_limit_degrees between 1 and 45)",
            name="tip_limit_is_an_angle",
        ),
    )

    code: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    description: Mapped[str] = mapped_column(String(NAME_LENGTH))
    family: Mapped[Family] = mapped_column(EnumValue(Family))
    divisible: Mapped[bool] = mapped_column(Boolean, default=False)
    allowance: Mapped[str | None] = mapped_column(String(NAME_LENGTH), nullable=True)
    tip_limit_degrees: Mapped[int | None] = mapped_column(Integer, nullable=True)
    high_centre: Mapped[bool] = mapped_column(Boolean, default=False)
    retired: Mapped[date | None] = mapped_column(Date, nullable=True)
    changed_by: Mapped[str] = mapped_column(String(NAME_LENGTH), default="")
    changed_on: Mapped[date | None] = mapped_column(Date, nullable=True)

    @property
    def live(self) -> bool:
        return self.retired is None

    def to_domain(self) -> Commodity:
        return Commodity(
            code=self.code,
            description=self.description,
            family=self.family,
            divisible=self.divisible,
            allowance=self.allowance,
            tip_limit_degrees=self.tip_limit_degrees,
            high_centre=self.high_centre,
        )

    @classmethod
    def of(cls, commodity: Commodity, by: str = "", on: date | None = None) -> CommodityRow:
        return cls(
            code=commodity.code,
            description=commodity.description,
            family=commodity.family,
            divisible=commodity.divisible,
            allowance=commodity.allowance,
            tip_limit_degrees=commodity.tip_limit_degrees,
            high_centre=commodity.high_centre,
            changed_by=by,
            changed_on=on,
        )

    def __repr__(self) -> str:
        return f"<CommodityRow {self.code}>"


class ConditionTextRow(Base):
    """One sentence that a permit can print, as it stood on a date."""

    __tablename__ = "condition_texts"
    __table_args__ = (
        CheckConstraint("length(body) > 0", name="body_not_empty"),
        CheckConstraint("print_order >= 0", name="print_order_not_negative"),
    )

    code: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    effective: Mapped[date] = mapped_column(Date, primary_key=True)
    category: Mapped[str] = mapped_column(String(CODE_LENGTH))
    body: Mapped[str] = mapped_column(Text(TEXT_LENGTH))
    print_order: Mapped[int] = mapped_column(Integer, default=50)
    changed_by: Mapped[str] = mapped_column(String(NAME_LENGTH), default="")

    def __repr__(self) -> str:
        return f"<ConditionTextRow {self.code} {self.effective.isoformat()}>"
