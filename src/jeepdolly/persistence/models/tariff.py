"""The published tables, on rows, because the legislature changes them.

Until now the statutory limits, the size boxes and the fee schedule lived in
the code as functions that returned the current tables. That was honest while
there was one set of numbers and no way to file another, and it stops being
honest the first time the legislature moves a limit in June for July.

So the tables are rows now, keyed by class of road and by the day they take
effect, which is exactly how the published documents are keyed. The code that
built them is still there and it is what seeds an empty database, so a fresh
install comes up with today's numbers and an existing one keeps whatever was
filed.

Nothing is ever edited. A change is a new row with a new effective date, and
a permit issued in March is priced by the March row for the rest of its life.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from ...domain.highway import HighwayClass
from ..base import CODE_LENGTH, NAME_LENGTH, Base, CentsType, EnumValue, InchesType, PoundsType


class StatutoryRow(Base):
    """One class of road, one set of weight limits, one effective date."""

    __tablename__ = "statutory_limits"
    __table_args__ = (
        CheckConstraint("tire_rate > 0", name="tire_rate_positive"),
        CheckConstraint("tandem > single", name="tandem_above_single"),
    )

    highway_class: Mapped[HighwayClass] = mapped_column(EnumValue(HighwayClass), primary_key=True)
    effective: Mapped[date] = mapped_column(Date, primary_key=True)
    single = mapped_column(PoundsType)
    tandem = mapped_column(PoundsType)
    tridem = mapped_column(PoundsType)
    quad = mapped_column(PoundsType)
    beyond = mapped_column(PoundsType)
    gross = mapped_column(PoundsType)
    tire_rate: Mapped[int] = mapped_column(Integer)

    def __repr__(self) -> str:
        return f"<StatutoryRow {self.highway_class} {self.effective.isoformat()}>"


class SizeRow(Base):
    """The legal box and the routine box for one class of road."""

    __tablename__ = "size_limits"

    highway_class: Mapped[HighwayClass] = mapped_column(EnumValue(HighwayClass), primary_key=True)
    effective: Mapped[date] = mapped_column(Date, primary_key=True)
    legal_width = mapped_column(InchesType)
    legal_height = mapped_column(InchesType)
    legal_length = mapped_column(InchesType)
    legal_front = mapped_column(InchesType)
    legal_rear = mapped_column(InchesType)
    routine_width = mapped_column(InchesType)
    routine_height = mapped_column(InchesType)
    routine_length = mapped_column(InchesType)
    routine_front = mapped_column(InchesType)
    routine_rear = mapped_column(InchesType)

    def __repr__(self) -> str:
        return f"<SizeRow {self.highway_class} {self.effective.isoformat()}>"


class ScheduleRow(Base):
    """One fee schedule, effective from one day."""

    __tablename__ = "fee_schedules"

    effective: Mapped[date] = mapped_column(Date, primary_key=True)
    review = mapped_column(CentsType)
    amendment = mapped_column(CentsType)
    minimum = mapped_column(CentsType)
    annual = mapped_column(CentsType)

    def __repr__(self) -> str:
        return f"<ScheduleRow {self.effective.isoformat()}>"


class BaseFeeRow(Base):
    """What each kind of permit costs before anything is measured."""

    __tablename__ = "fee_base"

    effective: Mapped[date] = mapped_column(Date, primary_key=True)
    kind: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    amount = mapped_column(CentsType)

    def __repr__(self) -> str:
        return f"<BaseFeeRow {self.kind} {self.effective.isoformat()}>"


class MileFeeRow(Base):
    """What a mile costs on each class of road."""

    __tablename__ = "fee_per_mile"

    effective: Mapped[date] = mapped_column(Date, primary_key=True)
    highway_class: Mapped[HighwayClass] = mapped_column(EnumValue(HighwayClass), primary_key=True)
    amount = mapped_column(CentsType)

    def __repr__(self) -> str:
        return f"<MileFeeRow {self.highway_class} {self.effective.isoformat()}>"


class WeightStepRow(Base):
    """One band of overage and what each block in it costs."""

    __tablename__ = "fee_weight_steps"
    __table_args__ = (CheckConstraint("position >= 0", name="position_not_negative"),)

    effective: Mapped[date] = mapped_column(Date, primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    up_to = mapped_column(PoundsType, nullable=True)
    per_block = mapped_column(CentsType)
    note: Mapped[str] = mapped_column(String(NAME_LENGTH), default="")

    def __repr__(self) -> str:
        return f"<WeightStepRow {self.effective.isoformat()} {self.position}>"
