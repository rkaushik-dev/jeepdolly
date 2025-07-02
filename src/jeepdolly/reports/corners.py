"""The corner list, which is the one report that predates the computer.

There has been a list of corners on the wall of the counter for as long as
anybody remembers. It is the corners that have caused trouble, in order of how
tight they are, and the counter reads it before quoting a route rather than
after somebody rings up from the middle of a village.

This produces the same list off the card index, plus the two things the paper
one could never do: what a particular route meets, and what a particular load
is going to be stopped by.

The permit register comes into it for one number only. A corner that has had
forty permits turned at it this year is a corner that is going to have trouble
at it, and the bridge people are not the only ones who plan off traffic.

Nothing here decides anything. It reads the card index, the road log and the
register, and it prints. The deciding is in the router and in the assessment,
and a report that started deciding would be a second set of rules to keep in
step with the first.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..domain.envelope import Envelope
from ..permits.permit import PermitState
from ..persistence.models.permits import PermitRow
from ..routing.itinerary import Itinerary
from ..routing.sweep import close_run, corners_on, radius_wanted, too_tight
from ..routing.turns import Corner, TurnBook


@dataclass(frozen=True)
class Wall:
    """One corner as it appears on the list on the wall."""

    corner: Corner
    turned: int

    @property
    def node(self) -> str:
        return self.corner.node

    def __str__(self) -> str:
        traffic = f"{self.turned} permits" if self.turned else "nothing this year"
        return f"{self.corner}  ({traffic})"


def turned_at(session: Session, corner: Corner, start: date, end: date) -> int:
    """How many issued permits ran both of this corner's segments.

    Both segments on one route is not proof the load turned the corner. A
    route that runs US 201 the whole way and touches SA 148 at the far end
    counts here and should not. The office has always accepted that, because
    the number is used to rank corners rather than to bill anybody.
    """
    query = select(PermitRow).where(
        PermitRow.state == PermitState.ISSUED,
        PermitRow.valid_from <= end,
        PermitRow.valid_to >= start,
    )
    found = 0
    for row in session.scalars(query).all():
        idents = set(row.route_idents)
        if corner.into in idents and corner.out_of in idents:
            found += 1
    return found


def wall_list(
    session: Session,
    book: TurnBook,
    start: date,
    end: date,
    feet: int | None = None,
) -> tuple[Wall, ...]:
    """The tight corners, tightest first, with what turned at each."""
    corners = book.tight() if feet is None else book.tight(feet)
    return tuple(Wall(one, turned_at(session, one, start, end)) for one in corners)


@dataclass(frozen=True)
class OnRoute:
    """One corner on one route, and what this load makes of it."""

    corner: Corner
    wanted: int
    verdict: str

    @property
    def spare(self) -> int:
        return self.corner.radius_feet - self.wanted

    def __str__(self) -> str:
        return (
            f"{self.corner.node}  {self.corner.radius_feet} ft  "
            f"wants {self.wanted} ft  {self.verdict}"
        )


def along(itinerary: Itinerary, envelope: Envelope, book: TurnBook) -> tuple[OnRoute, ...]:
    """Every recorded corner on this route, in driven order, with a verdict."""
    wanted = radius_wanted(envelope)
    stopped = {one.corner for one in too_tight(itinerary, envelope, book)}
    near = {one.corner for one in close_run(itinerary, envelope, book)}
    found = []
    for corner in corners_on(itinerary, book):
        if corner in stopped:
            verdict = "will not get round"
        elif corner in near:
            verdict = "close"
        else:
            verdict = "clear"
        found.append(OnRoute(corner, wanted, verdict))
    return tuple(found)


def report(session: Session, book: TurnBook, start: date, end: date) -> tuple[str, ...]:
    """The wall list as lines, which is what the CLI prints."""
    lines = [f"corners on the wall {start.isoformat()} to {end.isoformat()}"]
    found = wall_list(session, book, start, end)
    if not found:
        lines.append("nothing on the card index is under the tight radius")
        return tuple(lines)
    lines.extend(str(one) for one in found)
    return tuple(lines)


def route_report(itinerary: Itinerary, envelope: Envelope, book: TurnBook) -> tuple[str, ...]:
    """What one load meets on one route, as lines."""
    lines = [f"corners on {itinerary.origin} to {itinerary.destination}"]
    found = along(itinerary, envelope, book)
    if not found:
        lines.append("no corner on this route is on the card index")
        return tuple(lines)
    lines.extend(str(one) for one in found)
    stopped = [one for one in found if one.verdict == "will not get round"]
    if stopped:
        lines.append(f"this load is stopped at {len(stopped)} of them")
    return tuple(lines)
