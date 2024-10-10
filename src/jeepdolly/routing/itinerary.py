"""An itinerary: the segments of a trip, in the order they are driven.

A permit names a route and the route is a list of segments. That sounds
obvious and it is worth being strict about, because the two other ways of
naming a route both fail.

Naming the roads fails. Take US 201 to US 2 to SA 148 does not say where the
turns are, and a hauler who joins US 2 at the wrong end drives forty miles the
wrong way and over a posted bridge.

Naming the towns fails for the same reason with fewer words.

So an itinerary is segment idents, checked to join end to end, from a stated
origin. It knows what it touches: the classes of road, the districts, the total
distance, and each class of road separately, because the fee is priced per mile
by class and the seasonal orders are read by district.

An itinerary is built once and not edited. A detour makes a new one. That
matters when a permit is amended, because the old itinerary has to stay exactly
as it was for the copy that was already printed and handed over.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..domain.highway import HighwayClass
from ..domain.network import Network, Segment
from ..errors import Impossible
from ..measure import ZERO_MILES, Miles


@dataclass(frozen=True)
class Leg:
    """One segment of the trip with the way it is driven."""

    segment: Segment
    entered_at: str

    @property
    def left_at(self) -> str:
        return self.segment.other_end(self.entered_at)

    @property
    def length(self) -> Miles:
        return self.segment.length

    def __str__(self) -> str:
        return f"{self.segment.route} {self.entered_at} to {self.left_at}"


class Itinerary:
    """A trip as segments in driven order, checked to join."""

    def __init__(self, network: Network, origin: str, idents: tuple[str, ...]) -> None:
        if not idents:
            raise Impossible("an itinerary has at least one segment")
        network.node(origin)
        legs: list[Leg] = []
        standing = origin
        for ident in idents:
            segment = network.segment(ident)
            if not segment.runs_from(standing):
                raise Impossible(
                    f"segment {ident} cannot be driven from {standing}",
                    segment=ident,
                    at=standing,
                )
            legs.append(Leg(segment, standing))
            standing = segment.other_end(standing)
        self.origin = origin
        self.destination = standing
        self.legs = tuple(legs)

    @property
    def idents(self) -> tuple[str, ...]:
        return tuple(leg.segment.ident for leg in self.legs)

    @property
    def distance(self) -> Miles:
        total = ZERO_MILES
        for leg in self.legs:
            total = total + leg.length
        return total

    def distance_on(self, highway_class: HighwayClass) -> Miles:
        total = ZERO_MILES
        for leg in self.legs:
            if leg.segment.highway_class is highway_class:
                total = total + leg.length
        return total

    def classes(self) -> tuple[HighwayClass, ...]:
        found = {leg.segment.highway_class for leg in self.legs}
        return tuple(sorted(found, key=lambda one: one.value))

    def districts(self) -> tuple[int, ...]:
        return tuple(sorted({leg.segment.jurisdiction.district for leg in self.legs}))

    def towns(self) -> tuple[str, ...]:
        found = [self.origin]
        found.extend(leg.left_at for leg in self.legs)
        return tuple(found)

    def touches(self, ident: str) -> bool:
        return ident in self.idents

    def unpermittable(self) -> tuple[str, ...]:
        """Segments this office cannot write a permit over, in driven order."""
        return tuple(
            leg.segment.ident for leg in self.legs if not leg.segment.highway_class.permittable
        )

    def narrative(self) -> str:
        """The route as it prints on the permit face."""
        parts: list[str] = []
        for leg in self.legs:
            step = f"{leg.segment.route} to {leg.left_at.title()}"
            if not parts or parts[-1] != step:
                parts.append(step)
        return f"from {self.origin.title()}, " + ", ".join(parts)

    def __len__(self) -> int:
        return len(self.legs)

    def __iter__(self):
        return iter(self.legs)

    def __repr__(self) -> str:
        return f"Itinerary({self.origin} to {self.destination}, {len(self.legs)} legs)"
