"""The line survey: what is low, what is old, and what the office cannot lift.

The survey runs on a five year cycle and the crew that runs it has one van.
So the question is never whether to remeasure everything. It is which two
hundred spans to look at this summer, and that is answered three ways.

Lowest first is the obvious one and it is the least useful, because the wires
at eighteen feet are the ones already on every card and nobody has forgotten
them. What matters is low and old together: a span measured in 1998 at
nineteen feet is a span that may be at seventeen now, and nobody would know.

The other list is the wires that cannot be lifted. Those are the ones that
decide routes rather than delay them, and when one of them is remeasured
higher than it was, a route opens up that has been refused for a decade. That
happened twice on the last cycle and is the reason this report exists.

A crossing with no measured day is treated as the oldest thing on the list.
It came off a paper card somebody keyed in, nobody knows when it was walked,
and the honest answer to how old it is has always been that it is older than
anything with a date on it.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from sqlalchemy.orm import Session

from ..measure import Inches
from ..persistence.models.utilities import LineCrossingRow
from ..persistence.repositories import LineRepository

#: How long a measurement stands before the office wants it walked again.
CYCLE_YEARS = 5

#: What counts as a low wire when nobody says otherwise.
LOW = Inches.of(20)


@dataclass(frozen=True)
class Span:
    """One crossing as the survey list reads it."""

    ident: str
    segment: str
    place: str
    height: Inches
    owner: str
    measured: date | None
    liftable: bool

    def age_days(self, on: date) -> int | None:
        return None if self.measured is None else (on - self.measured).days

    def stale_on(self, day: date, years: int = CYCLE_YEARS) -> bool:
        if self.measured is None:
            return True
        return self.measured < day - timedelta(days=365 * years)

    def __str__(self) -> str:
        when = self.measured.isoformat() if self.measured else "never"
        lift = "" if self.liftable else "  fixed"
        return f"{self.ident}  {self.segment} at {self.place}  {self.height}  {when}{lift}"


def spans(session: Session, under: Inches | None = None) -> tuple[Span, ...]:
    """Every crossing on file, lowest first, optionally only the low ones."""
    book = LineRepository(session)
    rows = book.under(under) if under is not None else book.crossings()
    found = [_span(one) for one in rows]
    return tuple(sorted(found, key=lambda one: (one.height.value, one.ident)))


def _span(row: LineCrossingRow) -> Span:
    return Span(
        ident=row.ident,
        segment=row.segment_ident,
        place=row.place,
        height=row.height,
        owner=row.owner_ident,
        measured=row.measured,
        liftable=row.lift_to is not None,
    )


def overdue(
    session: Session,
    day: date,
    under: Inches = LOW,
    years: int = CYCLE_YEARS,
) -> tuple[Span, ...]:
    """The van's list: low wires nobody has walked inside the cycle.

    Ordered by height rather than by age. A very old measurement of a wire at
    twenty six feet is not urgent, because nothing the office permits is
    twenty six feet, and the crew's day is better spent under the low ones.
    """
    return tuple(one for one in spans(session, under) if one.stale_on(day, years))


def fixed(session: Session) -> tuple[Span, ...]:
    """Wires nothing can lift, which are the ones that decide routes."""
    return tuple(one for one in spans(session) if not one.liftable)


def report(session: Session, day: date, under: Inches = LOW) -> tuple[str, ...]:
    """The survey list as lines, which is what the command prints."""
    lines = [f"line survey {day.isoformat()}, wires under {under}"]
    walk = overdue(session, day, under)
    if not walk:
        lines.append("nothing low is out of cycle")
    else:
        for one in walk:
            lines.append(str(one))
    standing = fixed(session)
    if standing:
        lines.append("wires that cannot be lifted")
        lines.extend(f"  {one}" for one in standing)
    return tuple(lines)
