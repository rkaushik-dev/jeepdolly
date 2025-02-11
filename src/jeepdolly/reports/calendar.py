"""What is wrong with the road, day by day, for the fortnight ahead.

The counter needs one page that says which roads are shut this week and which
come back next week, because the question arrives as "can I get to Anson on
Thursday" and the answer lives in three tables.

Two shapes come out of here.

The calendar is by day: for each day in a span, what restrictions are in force
that were not in force the day before, and what lifted. That is the shape the
office pins up, because what people want to know is what changed.

The standing list is by segment: everything in force on one day, in road log
order, which is what gets faxed to the state police on a Monday morning.

Nothing here reads a load. A restriction is on the calendar whether or not it
is in the way of anything, because the person reading it is deciding what to
tell a hauler who has not rung yet.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..domain.restriction import RestrictionKind
from ..errors import Impossible
from ..persistence.models.network import RestrictionRow


@dataclass(frozen=True)
class Standing:
    """One restriction as it appears on the list."""

    ident: str
    segment: str
    kind: str
    note: str
    limit: str
    from_day: date | None
    to_day: date | None

    @property
    def permanent(self) -> bool:
        return self.from_day is None and self.to_day is None

    def __str__(self) -> str:
        span = (
            "permanent"
            if self.permanent
            else f"{_written(self.from_day)} to {_written(self.to_day)}"
        )
        return f"{self.segment}  {self.kind}  {self.limit}  {span}  {self.note}"


@dataclass(frozen=True)
class Day:
    """One day of the calendar, and what changed on it."""

    day: date
    started: tuple[Standing, ...]
    lifted: tuple[Standing, ...]

    @property
    def quiet(self) -> bool:
        return not self.started and not self.lifted

    def lines(self) -> tuple[str, ...]:
        found = []
        for one in self.started:
            found.append(f"{self.day.isoformat()}  closes  {one}")
        for one in self.lifted:
            found.append(f"{self.day.isoformat()}  opens   {one}")
        return tuple(found)


def _written(day: date | None) -> str:
    return "further notice" if day is None else day.isoformat()


def _limit_of(row: RestrictionRow) -> str:
    if row.kind is RestrictionKind.CLEARANCE and row.height is not None:
        return str(row.height)
    if row.kind is RestrictionKind.WIDTH and row.width is not None:
        return str(row.width)
    if row.kind is RestrictionKind.WEIGHT and row.weight is not None:
        return str(row.weight)
    if row.kind is RestrictionKind.SEASONAL:
        return row.region or ""
    if row.kind is RestrictionKind.CURFEW and row.curfew_from and row.curfew_to:
        return f"{row.curfew_from:%H:%M} to {row.curfew_to:%H:%M}"
    return ""


def _standing(row: RestrictionRow) -> Standing:
    return Standing(
        ident=row.ident,
        segment=row.segment_ident,
        kind=row.kind.value,
        note=row.note,
        limit=_limit_of(row),
        from_day=row.effective,
        to_day=row.expires,
    )


def _in_force(row: RestrictionRow, day: date) -> bool:
    if row.effective is not None and day < row.effective:
        return False
    return not (row.expires is not None and day > row.expires)


def standing_on(session: Session, day: date) -> tuple[Standing, ...]:
    """Everything in force on one day, in road log order."""
    query = select(RestrictionRow).order_by(RestrictionRow.segment_ident, RestrictionRow.ident)
    return tuple(_standing(one) for one in session.scalars(query).all() if _in_force(one, day))


def calendar(session: Session, start: date, days: int = 14) -> tuple[Day, ...]:
    """What changes each day over a span, which is what gets pinned up."""
    if days < 1:
        raise Impossible("a calendar covers at least one day")
    query = select(RestrictionRow).order_by(RestrictionRow.segment_ident, RestrictionRow.ident)
    rows = list(session.scalars(query).all())
    found = []
    for step in range(days):
        day = start + timedelta(days=step)
        before = day - timedelta(days=1)
        started = [
            _standing(one) for one in rows if _in_force(one, day) and not _in_force(one, before)
        ]
        lifted = [
            _standing(one) for one in rows if _in_force(one, before) and not _in_force(one, day)
        ]
        found.append(Day(day, tuple(started), tuple(lifted)))
    return tuple(found)


def changes(session: Session, start: date, days: int = 14) -> tuple[str, ...]:
    """The calendar as lines, with the quiet days left out."""
    found: list[str] = [f"road changes {start.isoformat()} for {days} days"]
    for one in calendar(session, start, days):
        found.extend(one.lines())
    if len(found) == 1:
        found.append("nothing changes")
    return tuple(found)
