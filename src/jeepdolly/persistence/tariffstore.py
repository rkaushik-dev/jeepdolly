"""Reading the published tables off the database, and seeding an empty one.

Three books come out of here and they are the same three types the rules
already read: a statutory book, a size book and a schedule book. Nothing
downstream of this knows the tables came from rows rather than from a
function, which is the whole point: the rules were written against the books
and the books are the interface.

Seeding writes the tables the code already carried, dated from a day the
caller names. It refuses to seed a database that already has tables, because
seeding twice would put two sets of numbers on one effective date and nobody
could say which was in force.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..domain.envelope import Envelope
from ..domain.highway import HighwayClass
from ..errors import Conflict
from ..fees.schedule import FeeSchedule, ScheduleBook, WeightStep
from ..fees.schedule import standard_schedule as coded_schedule
from ..limits.classify import PermitKind
from ..limits.size import SizeBook, SizeTable
from ..limits.size import standard_book as coded_sizes
from ..limits.statutory import StatutoryBook, StatutoryTable
from ..limits.statutory import standard_book as coded_statutes
from ..money import Dollars
from .models.tariff import (
    BaseFeeRow,
    MileFeeRow,
    ScheduleRow,
    SizeRow,
    StatutoryRow,
    WeightStepRow,
)

FIRST = date(2024, 1, 1)


def seeded(session: Session) -> bool:
    """Whether anybody has filed a table yet."""
    return session.scalars(select(StatutoryRow)).first() is not None


def seed(session: Session, effective: date = FIRST) -> int:
    """Write the tables the code carries, dated from a day."""
    if seeded(session):
        raise Conflict("the tariff tables are already on file")
    written = 0
    statutes = coded_statutes(effective)
    for highway_class in HighwayClass:
        table = statutes.on(highway_class, effective)
        session.add(
            StatutoryRow(
                highway_class=highway_class,
                effective=effective,
                single=table.group_limit(1),
                tandem=table.group_limit(2),
                tridem=table.group_limit(3),
                quad=table.group_limit(4),
                beyond=table.beyond_table,
                gross=table.gross,
                tire_rate=table.tire_rate,
            )
        )
        written += 1
    sizes = coded_sizes()
    for highway_class in HighwayClass:
        box = sizes.on(highway_class)
        session.add(
            SizeRow(
                highway_class=highway_class,
                effective=effective,
                legal_width=box.legal.width,
                legal_height=box.legal.height,
                legal_length=box.legal.length,
                legal_front=box.legal.front_overhang,
                legal_rear=box.legal.rear_overhang,
                routine_width=box.routine.width,
                routine_height=box.routine.height,
                routine_length=box.routine.length,
                routine_front=box.routine.front_overhang,
                routine_rear=box.routine.rear_overhang,
            )
        )
        written += 1
    schedule = coded_schedule(effective)
    session.add(
        ScheduleRow(
            effective=effective,
            review=schedule.review,
            amendment=schedule.amendment,
            minimum=schedule.minimum,
            annual=schedule.annual,
        )
    )
    for kind, amount in schedule.base.items():
        session.add(BaseFeeRow(effective=effective, kind=kind.value, amount=amount))
    for highway_class, amount in schedule.per_mile.items():
        session.add(MileFeeRow(effective=effective, highway_class=highway_class, amount=amount))
    for position, step in enumerate(schedule.steps):
        session.add(
            WeightStepRow(
                effective=effective,
                position=position,
                up_to=step.up_to,
                per_block=step.per_block,
            )
        )
    return written + 1


def statutory_book(session: Session) -> StatutoryBook:
    """Every statutory table on file, as the book the rules read."""
    book = StatutoryBook()
    query = select(StatutoryRow).order_by(StatutoryRow.effective)
    for row in session.scalars(query).all():
        book.file(
            StatutoryTable(
                highway_class=row.highway_class,
                effective=row.effective,
                by_count={
                    1: row.single,
                    2: row.tandem,
                    3: row.tridem,
                    4: row.quad,
                },
                beyond_table=row.beyond,
                gross=row.gross,
                tire_rate=row.tire_rate,
            )
        )
    return book


def size_book(session: Session, on: date) -> SizeBook:
    """The size tables in force on a day."""
    book = SizeBook()
    query = select(SizeRow).where(SizeRow.effective <= on).order_by(SizeRow.effective)
    for row in session.scalars(query).all():
        book.file(
            SizeTable(
                highway_class=row.highway_class,
                legal=Envelope(
                    row.legal_width,
                    row.legal_height,
                    row.legal_length,
                    row.legal_front,
                    row.legal_rear,
                ),
                routine=Envelope(
                    row.routine_width,
                    row.routine_height,
                    row.routine_length,
                    row.routine_front,
                    row.routine_rear,
                ),
            )
        )
    return book


def schedule_book(session: Session) -> ScheduleBook:
    """Every fee schedule on file, as the book the pricing reads."""
    book = ScheduleBook()
    for row in session.scalars(select(ScheduleRow).order_by(ScheduleRow.effective)).all():
        book.file(
            FeeSchedule(
                effective=row.effective,
                base=_base_fees(session, row.effective),
                per_mile=_mile_fees(session, row.effective),
                steps=_steps(session, row.effective),
                review=row.review,
                amendment=row.amendment,
                minimum=row.minimum,
                annual=row.annual,
            )
        )
    return book


def _base_fees(session: Session, effective: date) -> dict[PermitKind, Dollars]:
    query = select(BaseFeeRow).where(BaseFeeRow.effective == effective)
    return {PermitKind(one.kind): one.amount for one in session.scalars(query).all()}


def _mile_fees(session: Session, effective: date) -> dict[HighwayClass, Dollars]:
    query = select(MileFeeRow).where(MileFeeRow.effective == effective)
    return {one.highway_class: one.amount for one in session.scalars(query).all()}


def _steps(session: Session, effective: date) -> tuple[WeightStep, ...]:
    query = (
        select(WeightStepRow)
        .where(WeightStepRow.effective == effective)
        .order_by(WeightStepRow.position)
    )
    return tuple(
        WeightStep(up_to=one.up_to, per_block=one.per_block) for one in session.scalars(query).all()
    )
