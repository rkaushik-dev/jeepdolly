"""Chargeable mileage, which is not the same number as the trip length.

The fee is per mile and the mile it is per is a defined thing, not the odometer
reading. Four rules stand between the road log and the invoice.

Only state maintained road is chargeable. A trip that starts in a gravel pit on
a town way and runs four miles to the state road is charged from the state road.

Mileage is charged by class, because the rate differs. The interstate is
cheapest per mile and the state aid roads are dearest, which surprises people
until they see what a loaded lowboy does to a road built in 1954.

Each class is rounded up separately, then added. Rounding the total instead
would let a hauler shave a dollar by splitting the trip across two permits,
and the office has seen that tried.

There is a minimum. A permit for a two mile move costs what a permit for a ten
mile move costs, because the work of writing it is the same and the office is
not a toll booth.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..domain.highway import HighwayClass
from ..errors import Impossible
from ..measure import Miles
from .itinerary import Itinerary

#: No permit is charged for fewer miles than this.
MINIMUM_MILES = 10


@dataclass(frozen=True)
class ClassMiles:
    """Chargeable miles on one class of road."""

    highway_class: HighwayClass
    measured: Miles
    charged: int

    def __str__(self) -> str:
        return f"{self.highway_class}: {self.measured} measured, {self.charged} charged"


@dataclass(frozen=True)
class Mileage:
    """The whole trip, split by class, with the minimum applied once."""

    parts: tuple[ClassMiles, ...]
    charged: int
    measured: Miles
    minimum_applied: bool

    def on(self, highway_class: HighwayClass) -> int:
        for part in self.parts:
            if part.highway_class is highway_class:
                return part.charged
        return 0

    def classes(self) -> tuple[HighwayClass, ...]:
        return tuple(part.highway_class for part in self.parts)

    def __str__(self) -> str:
        note = " (minimum)" if self.minimum_applied else ""
        return f"{self.measured} measured, {self.charged} miles charged{note}"


def chargeable(itinerary: Itinerary, minimum: int = MINIMUM_MILES) -> Mileage:
    """Split the trip by class, round each part up, and apply the minimum."""
    if minimum < 0:
        raise Impossible("a minimum mileage is not negative")
    parts = []
    measured = Miles(0)
    charged = 0
    for highway_class in itinerary.classes():
        if not highway_class.permittable:
            continue
        distance = itinerary.distance_on(highway_class)
        miles = distance.rounded_up()
        parts.append(ClassMiles(highway_class, distance, miles))
        measured = measured + distance
        charged = charged + miles
    if charged >= minimum:
        return Mileage(tuple(parts), charged, measured, minimum_applied=False)
    return Mileage(tuple(parts), minimum, measured, minimum_applied=True)


def unpermittable_miles(itinerary: Itinerary) -> Miles:
    """What the hauler drives that this office is not charging for."""
    total = Miles(0)
    for highway_class in itinerary.classes():
        if not highway_class.permittable:
            total = total + itinerary.distance_on(highway_class)
    return total
