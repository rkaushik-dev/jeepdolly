"""The structure inventory, which is the list the whole office is afraid of.

Everything else in this system can be argued about. A structure cannot. If a
hundred and eighty thousand pounds goes over a 1937 through truss that was
rated for less, the bridge is either damaged or it is not, and the office does
not get to find out which twice.

A structure record is short because the office holds only what it needs to
route around one and to know when to send it to an engineer.

The spans matter because the bridge formula and the rating both care about
length. A ten span bridge of forty foot spans behaves like a forty foot bridge
under a truck and like a four hundred foot bridge under a train of them.

The ratings come from the last inspection. Inventory rating is what the
structure will carry indefinitely. Operating rating is what it will carry
occasionally, which is exactly what a permit load is, so operating rating is
the number a permit is measured against and inventory rating is the number a
posting is set from.

Posting is a decision rather than a measurement. A structure is posted when
somebody signed an order to post it, and the posted weight is what is on the
sign, which is not always what the arithmetic said.

Clearance lives here too when the structure goes over a road rather than under
one, because the office wants one place to look for the low bridge on US 2 and
not two.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from ..errors import Conflict, Impossible, NotOnFile
from ..measure import Inches, Pounds


class StructureKind(Enum):
    """What sort of structure this is, which decides how it is checked."""

    BRIDGE = "bridge"
    CULVERT = "culvert"
    OVERPASS = "overpass"
    GANTRY = "sign gantry"

    @property
    def carries_traffic(self) -> bool:
        return self in (StructureKind.BRIDGE, StructureKind.CULVERT)

    @property
    def crosses_over(self) -> bool:
        return self in (StructureKind.OVERPASS, StructureKind.GANTRY)

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Structure:
    """One structure on one segment, as the last inspection left it."""

    ident: str
    name: str
    segment: str
    kind: StructureKind
    spans_feet: tuple[int, ...] = ()
    built: int = 0
    inspected: date | None = None
    inventory_rating: Pounds | None = None
    operating_rating: Pounds | None = None
    posted: Pounds | None = None
    clearance: Inches | None = None
    deck_width: Inches | None = None

    def __post_init__(self) -> None:
        if not self.ident.strip():
            raise Impossible("a structure carries an ident")
        if self.kind.carries_traffic:
            if not self.spans_feet:
                raise Impossible(f"{self.ident} carries traffic and has spans")
            if any(span <= 0 for span in self.spans_feet):
                raise Impossible(f"{self.ident} has a span of no length")
            if self.operating_rating is None:
                raise Impossible(f"{self.ident} carries traffic and has an operating rating")
        if self.kind.crosses_over and self.clearance is None:
            raise Impossible(f"{self.ident} crosses over the road and has a clearance")
        if (
            self.inventory_rating is not None
            and self.operating_rating is not None
            and self.inventory_rating > self.operating_rating
        ):
            raise Impossible(f"{self.ident} rates higher for daily traffic than for a permit")

    @property
    def longest_span(self) -> int:
        return max(self.spans_feet) if self.spans_feet else 0

    @property
    def length_feet(self) -> int:
        return sum(self.spans_feet)

    @property
    def span_count(self) -> int:
        return len(self.spans_feet)

    @property
    def is_posted(self) -> bool:
        return self.posted is not None

    def age(self, on: date) -> int:
        return on.year - self.built if self.built else 0

    def carries(self, gross: Pounds) -> bool:
        """Whether the posted sign lets this weight on at all."""
        return self.posted is None or gross <= self.posted

    def __str__(self) -> str:
        posted = f", posted {self.posted}" if self.posted else ""
        return f"{self.ident} {self.name} on {self.segment}{posted}"


class StructureBook:
    """Every structure on file, asked for by segment and by ident."""

    def __init__(self) -> None:
        self._by_ident: dict[str, Structure] = {}
        self._by_segment: dict[str, list[str]] = {}

    def add(self, structure: Structure) -> Structure:
        if structure.ident in self._by_ident:
            raise Conflict(f"structure {structure.ident} is already on file")
        self._by_ident[structure.ident] = structure
        self._by_segment.setdefault(structure.segment, []).append(structure.ident)
        return structure

    def get(self, ident: str) -> Structure:
        try:
            return self._by_ident[ident]
        except KeyError:
            raise NotOnFile(f"no structure {ident}", structure=ident) from None

    def on_segment(self, segment: str) -> tuple[Structure, ...]:
        idents = self._by_segment.get(segment, [])
        return tuple(self._by_ident[one] for one in sorted(idents))

    def on_segments(self, segments: tuple[str, ...]) -> tuple[Structure, ...]:
        found: list[Structure] = []
        for segment in segments:
            found.extend(self.on_segment(segment))
        return tuple(found)

    def posted(self) -> tuple[Structure, ...]:
        found = [one for one in self._by_ident.values() if one.is_posted]
        return tuple(sorted(found, key=lambda one: one.ident))

    def older_than(self, year: int) -> tuple[Structure, ...]:
        found = [one for one in self._by_ident.values() if one.built and one.built < year]
        return tuple(sorted(found, key=lambda one: one.built))

    def __len__(self) -> int:
        return len(self._by_ident)

    def __contains__(self, ident: object) -> bool:
        return ident in self._by_ident
