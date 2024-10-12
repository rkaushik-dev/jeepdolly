"""Walk an itinerary with a load on a day and report what is in the way.

This is where the load meets the road, and it is the only place in the system
that reads a restriction against a measurement. Everything before it measures.
Everything after it prices, prints or refuses.

An obstruction is either fatal or it is a condition.

Fatal means no permit over this route, today, for this load. A closure, a
clearance the load will not fit under, a structure posted below the gross, a
town way in the middle of the trip. The hauler gets a different route or a
different day or a smaller load.

A condition means the trip can happen if something is done. A curfew means it
cannot happen between seven and nine. A seasonal posting means it cannot happen
at this weight until the order lifts, which reads like a fatal finding and is
reported as a condition, because the answer is a date rather than a refusal and
the hauler wants the date.

The seasonal case is the one worth reading twice. A segment carries a seasonal
restriction naming a region. The restriction does not carry a weight. The
weight comes from whichever thaw order the season book has in force for that
region on the day of travel, applied to the statutory gross for that class of
road. Three tables have to agree before this rule produces a number, and the
number changes when any of them does.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, time

from ..domain.envelope import Envelope
from ..domain.network import Segment
from ..domain.restriction import Restriction, RestrictionBook, RestrictionKind
from ..limits.statutory import StatutoryBook
from ..measure import Pounds
from ..seasons import SeasonBook
from .itinerary import Itinerary


@dataclass(frozen=True)
class Obstruction:
    """One thing in the way, on one segment."""

    segment: str
    kind: str
    note: str
    needed: str
    allowed: str
    fatal: bool = True

    def __str__(self) -> str:
        weight = "no permit" if self.fatal else "condition"
        return (
            f"{self.segment} {self.kind}: {self.note}, needs {self.needed}, "
            f"allows {self.allowed} ({weight})"
        )


@dataclass(frozen=True)
class RouteReading:
    """Everything in the way of one load on one route on one day."""

    day: date
    itinerary: Itinerary
    obstructions: tuple[Obstruction, ...]

    @property
    def fatal(self) -> tuple[Obstruction, ...]:
        return tuple(one for one in self.obstructions if one.fatal)

    @property
    def conditions(self) -> tuple[Obstruction, ...]:
        return tuple(one for one in self.obstructions if not one.fatal)

    @property
    def clear(self) -> bool:
        return not self.fatal

    def segments_in_the_way(self) -> tuple[str, ...]:
        return tuple(sorted({one.segment for one in self.fatal}))

    def __str__(self) -> str:
        if self.clear and not self.conditions:
            return "route is clear"
        if self.clear:
            return f"route is clear with {len(self.conditions)} conditions"
        return f"route is blocked at {', '.join(self.segments_in_the_way())}"


def evaluate(
    itinerary: Itinerary,
    envelope: Envelope,
    gross: Pounds,
    day: date,
    restrictions: RestrictionBook,
    seasons: SeasonBook,
    statutes: StatutoryBook,
    departure: time | None = None,
) -> RouteReading:
    """Read every leg of the trip against every restriction on it."""
    found: list[Obstruction] = []
    for leg in itinerary:
        found.extend(
            segment_obstructions(
                leg.segment, envelope, gross, day, restrictions, seasons, statutes, departure
            )
        )
    return RouteReading(day, itinerary, tuple(found))


def segment_obstructions(
    segment: Segment,
    envelope: Envelope,
    gross: Pounds,
    day: date,
    restrictions: RestrictionBook,
    seasons: SeasonBook,
    statutes: StatutoryBook,
    departure: time | None = None,
) -> tuple[Obstruction, ...]:
    """What is in the way on one segment, which is what the router asks."""
    found = []
    if not segment.highway_class.permittable:
        found.append(
            Obstruction(
                segment=segment.ident,
                kind="jurisdiction",
                note="the state does not maintain this road",
                needed="a permit from the town",
                allowed="nothing from this office",
            )
        )
    for restriction in restrictions.on_segment(segment.ident, day):
        trouble = _read(restriction, envelope, gross, day, seasons, statutes, segment, departure)
        if trouble is not None:
            found.append(trouble)
    return tuple(found)


def _read(
    restriction: Restriction,
    envelope: Envelope,
    gross: Pounds,
    day: date,
    seasons: SeasonBook,
    statutes: StatutoryBook,
    segment: Segment,
    departure: time | None,
) -> Obstruction | None:
    ident = restriction.segment
    if restriction.kind is RestrictionKind.CLOSURE:
        return Obstruction(ident, "closure", restriction.note, "open road", "closed")
    if restriction.kind is RestrictionKind.CLEARANCE and restriction.height is not None:
        if envelope.height > restriction.height:
            return Obstruction(
                ident, "clearance", restriction.note, str(envelope.height), str(restriction.height)
            )
        return None
    if restriction.kind is RestrictionKind.WIDTH and restriction.width is not None:
        if envelope.width > restriction.width:
            return Obstruction(
                ident, "width", restriction.note, str(envelope.width), str(restriction.width)
            )
        return None
    if restriction.kind is RestrictionKind.WEIGHT and restriction.weight is not None:
        if gross > restriction.weight:
            return Obstruction(
                ident, "posting", restriction.note, str(gross), str(restriction.weight)
            )
        return None
    if restriction.kind is RestrictionKind.SEASONAL and restriction.region is not None:
        return _seasonal(restriction, gross, day, seasons, statutes, segment)
    if restriction.kind is RestrictionKind.CURFEW and restriction.curfew is not None:
        if departure is None or restriction.curfew.covers(day, departure):
            return Obstruction(
                ident,
                "curfew",
                restriction.note,
                "travel outside the curfew",
                str(restriction.curfew),
                fatal=False,
            )
        return None
    return None


def _seasonal(
    restriction: Restriction,
    gross: Pounds,
    day: date,
    seasons: SeasonBook,
    statutes: StatutoryBook,
    segment: Segment,
) -> Obstruction | None:
    region = restriction.region or ""
    order = seasons.in_force(region, day)
    if order is None:
        return None
    statutory = statutes.on(segment.highway_class, day).gross
    allowed = order.applied_to(statutory)
    if gross <= allowed:
        return None
    return Obstruction(
        restriction.segment,
        "posted season",
        f"{restriction.note} under order {order.number}",
        str(gross),
        str(allowed),
        fatal=False,
    )
