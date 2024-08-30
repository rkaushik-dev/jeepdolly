"""Roads, by the classes that decide which rules reach them.

Nearly every limit in this system is different on an interstate than it is on
a town way, so the class of the road is read before the rule is. Four classes
cover the state and they are not a hierarchy of importance, they are four
different rule sets that happen to be ordered by how much weight they carry.

The interstate is federal. The state may not raise a limit on it and the
formula applies without local variation.

An arterial is a numbered state or federal route off the interstate, the
through routes: US 2, US 1, State Route 27. These carry the permit traffic.

A state aid road is a numbered secondary the state maintains but did not build
to the same standard. Most of the posted structures in the state are on these,
and most of the thaw orders reach them.

A town way is maintained by the town, and the state cannot write a permit for
one at all. It can only note that the trip begins or ends on one, and the
hauler goes to the town office for the rest. This is the class that surprises
people: a mill yard on a town way needs two permits from two governments.

A route designation is parsed rather than typed free, because a route written
three ways is a route that cannot be joined to the road log.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum

from ..errors import Impossible, Unreadable

_ROUTE = re.compile(
    r"^\s*(?P<kind>I|US|SR|SA|TR)\s*[- ]?\s*(?P<number>\d{1,3})(?P<suffix>[A-Z])?\s*$",
    re.IGNORECASE,
)

#: How a designation prefix maps onto a class of road.
PREFIX_CLASS = {
    "I": "interstate",
    "US": "arterial",
    "SR": "arterial",
    "SA": "state aid",
    "TR": "town way",
}


class HighwayClass(Enum):
    """The four rule sets, ordered by what they will carry."""

    INTERSTATE = "interstate"
    ARTERIAL = "arterial"
    STATE_AID = "state aid"
    TOWN_WAY = "town way"

    @property
    def state_maintained(self) -> bool:
        return self is not HighwayClass.TOWN_WAY

    @property
    def permittable(self) -> bool:
        """Whether this office can write a permit over it at all."""
        return self.state_maintained

    @property
    def federal(self) -> bool:
        return self is HighwayClass.INTERSTATE

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, order=True)
class Route:
    """A route designation, parsed so two spellings become one."""

    kind: str
    number: int
    suffix: str = ""

    def __post_init__(self) -> None:
        if self.kind not in PREFIX_CLASS:
            raise Impossible(f"{self.kind!r} is not a route prefix")
        if not 1 <= self.number <= 999:
            raise Impossible(f"{self.number} is not a route number")

    @classmethod
    def parse(cls, text: str) -> Route:
        """Read I-95, US 2, SR-27, sr27, all to the same route."""
        found = _ROUTE.match(text)
        if not found:
            raise Unreadable(f"cannot read {text!r} as a route", written=text)
        return cls(
            found.group("kind").upper(),
            int(found.group("number")),
            (found.group("suffix") or "").upper(),
        )

    @property
    def highway_class(self) -> HighwayClass:
        return HighwayClass(PREFIX_CLASS[self.kind])

    def __str__(self) -> str:
        joiner = "-" if self.kind == "I" else " "
        return f"{self.kind}{joiner}{self.number}{self.suffix}"


@dataclass(frozen=True)
class Jurisdiction:
    """Who to ask about a piece of road, and who to bill for damage to it."""

    county: str
    town: str
    district: int

    def __post_init__(self) -> None:
        if not 1 <= self.district <= 9:
            raise Impossible(f"district {self.district} is not one of the state's")
        if not self.county.strip() or not self.town.strip():
            raise Impossible("a place is in a county and a town")

    @property
    def region(self) -> str:
        """The name a seasonal order is written against."""
        return f"district {self.district}"

    def __str__(self) -> str:
        return f"{self.town}, {self.county} county (district {self.district})"
