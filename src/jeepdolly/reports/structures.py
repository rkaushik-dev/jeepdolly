"""What went over each structure, which nobody asked for until they did.

A bridge is inspected on a cycle and rated on what it carries. The rating is
an estimate of the traffic, and the office holds the one record in the state
that says what actually crossed: every permit names its route, and every route
names its segments, and every segment carries its structures.

So this report walks the permits and counts them onto the structures. It is
the report the bridge people ring about after a joint fails, and it is the one
that decides which structure gets inspected first next year.

The heaviest crossing is the number they want. Not the count, not the average,
the single heaviest load that was permitted over it and the day it went.

A voided permit is not counted, and that is the one place in the reports where
a void changes an answer. The money was taken whether or not the load moved,
but the bridge did not feel a load that never went.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..measure import Pounds
from ..permits.permit import PermitState
from ..persistence.models.permits import PermitRow
from ..persistence.models.structures import StructureRow


@dataclass(frozen=True)
class Crossing:
    """One permit over one structure."""

    permit: str
    day: date
    gross: Pounds
    carrier: str

    def __str__(self) -> str:
        return f"{self.permit}  {self.day.isoformat()}  {self.gross}  {self.carrier}"


@dataclass(frozen=True)
class Hits:
    """One structure and everything that was permitted over it."""

    ident: str
    name: str
    segment: str
    posted: Pounds | None
    crossings: tuple[Crossing, ...]

    @property
    def count(self) -> int:
        return len(self.crossings)

    @property
    def heaviest(self) -> Crossing | None:
        if not self.crossings:
            return None
        return max(self.crossings, key=lambda one: one.gross.value)

    @property
    def over_posting(self) -> tuple[Crossing, ...]:
        """Anything permitted over a posted weight, which should be nothing."""
        if self.posted is None:
            return ()
        return tuple(one for one in self.crossings if one.gross > self.posted)

    def lines(self) -> tuple[str, ...]:
        heaviest = self.heaviest
        found = [f"{self.ident}  {self.name}  {self.count} crossings"]
        if heaviest is not None:
            found.append(f"  heaviest  {heaviest}")
        for one in self.over_posting:
            found.append(f"  OVER THE POSTING  {one}")
        return tuple(found)

    def __str__(self) -> str:
        return f"{self.ident} {self.name}, {self.count} crossings"


def crossings(session: Session, start: date, end: date) -> tuple[Hits, ...]:
    """Every structure with what crossed it between two days."""
    query = (
        select(PermitRow)
        .where(
            PermitRow.state == PermitState.ISSUED,
            PermitRow.valid_from <= end,
            PermitRow.valid_to >= start,
        )
        .order_by(PermitRow.number)
    )
    permits = list(session.scalars(query).all())
    found = []
    for structure in session.scalars(select(StructureRow).order_by(StructureRow.ident)).all():
        over = tuple(
            Crossing(one.number, one.valid_from, one.gross, one.carrier_name)
            for one in permits
            if structure.segment_ident in one.route_idents
        )
        found.append(
            Hits(
                ident=structure.ident,
                name=structure.name,
                segment=structure.segment_ident,
                posted=structure.posted,
                crossings=over,
            )
        )
    return tuple(found)


def busiest(session: Session, start: date, end: date, limit: int = 10) -> tuple[Hits, ...]:
    """The structures that carried the most, which is where inspection goes."""
    found = [one for one in crossings(session, start, end) if one.count]
    return tuple(sorted(found, key=lambda one: one.count, reverse=True)[:limit])


def anything_over_a_posting(session: Session, start: date, end: date) -> tuple[Hits, ...]:
    """Structures a permit was written over in spite of the sign."""
    return tuple(one for one in crossings(session, start, end) if one.over_posting)


def report(session: Session, start: date, end: date) -> tuple[str, ...]:
    """The whole thing as lines, quiet structures left out."""
    lines = [f"structure crossings {start.isoformat()} to {end.isoformat()}"]
    for one in crossings(session, start, end):
        if one.count:
            lines.extend(one.lines())
    if len(lines) == 1:
        lines.append("nothing crossed anything")
    return tuple(lines)
