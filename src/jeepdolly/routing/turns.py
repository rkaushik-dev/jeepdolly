"""The card index of corners, which is a record rather than a rule.

A hundred and twenty feet of combination will go under everything on US 2 and
still not get out of the village at Norridgewock, because the turn from US 2
onto SR 8 is a ninety degree corner with a granite kerb and a bank on the
outside. The office knows perhaps two hundred corners like it and has kept
them on a card index for thirty years.

A corner is recorded as the two segments and the node between them, with the
radius somebody measured off the plan. What a load needs to get round one is
not here. That is the swept path rule and it lives next door in ``sweep``,
because a corner changes when the town rebuilds a junction and the rule
changes when the capital sends an order, and the two have never moved
together.

A corner is the same corner both ways round. Where a junction really is
different in each direction, and two or three of them are, the office records
two corners rather than putting a direction on one.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..errors import Conflict, Impossible

#: The radius under which a corner goes on the list on the office wall.
#:
#: This is not the same number as the plain radius and it is deliberately
#: above it. A forty two foot corner gets an ordinary lowboy round and stops
#: everything longer, which is exactly the kind of corner the counter wants to
#: know about before it quotes a route rather than after.
TIGHT_FEET = 50


@dataclass(frozen=True)
class Corner:
    """One measured turn between two segments at one node."""

    node: str
    into: str
    out_of: str
    radius_feet: int
    note: str = ""

    def __post_init__(self) -> None:
        if self.radius_feet <= 0:
            raise Impossible(f"the corner at {self.node} has a radius")
        if self.into == self.out_of:
            raise Impossible(f"the corner at {self.node} joins two different segments")

    def joins(self, first: str, second: str) -> bool:
        """A corner is the same corner whichever way it is driven."""
        return {self.into, self.out_of} == {first, second}

    def __str__(self) -> str:
        note = f": {self.note}" if self.note else ""
        return f"{self.node} {self.into} to {self.out_of}, {self.radius_feet} ft{note}"


class TurnBook:
    """The card index of corners, keyed the way a route is walked."""

    def __init__(self) -> None:
        self._corners: list[Corner] = []

    def add(self, corner: Corner) -> Corner:
        for existing in self._corners:
            if existing.node == corner.node and existing.joins(corner.into, corner.out_of):
                raise Conflict(f"the corner at {corner.node} is already on file")
        self._corners.append(corner)
        return corner

    def between(self, node: str, first: str, second: str) -> Corner | None:
        for corner in self._corners:
            if corner.node == node and corner.joins(first, second):
                return corner
        return None

    def at(self, node: str) -> tuple[Corner, ...]:
        return tuple(one for one in self._corners if one.node == node)

    def tight(self, feet: int = TIGHT_FEET) -> tuple[Corner, ...]:
        found = [one for one in self._corners if one.radius_feet < feet]
        return tuple(sorted(found, key=lambda one: one.radius_feet))

    def __len__(self) -> int:
        return len(self._corners)


#: The corners the office has on the card index, as they stand in the code.
#:
#: These are here for the same reason the statutory tables have a coded copy:
#: a database that has not been seeded still has to answer the telephone, and
#: an office with no corners on file would wave a hundred and forty feet of
#: bridge beam through Norridgewock village.
CORNERS = [
    ("norridgewock", "US2-1", "SR8-1", 42, "ninety degrees at the square, granite kerb"),
    ("norridgewock", "US2-1", "SA148-1", 70, "wide but the bank is soft on the outside"),
    ("skowhegan", "US201-2", "US2-1", 65, "island in the middle, keep it on the left"),
    ("skowhegan", "US201-2", "SA148-2", 62, "off the bridge and hard right, watch the kerb"),
    ("skowhegan", "US201-2", "US201-3", 80, ""),
    ("canaan", "SA148-1", "SR43-1", 70, ""),
    ("canaan", "SA148-2", "SR43-1", 75, ""),
    ("madison", "SR8-1", "TR104-1", 35, "mill gate, and the gate posts are the limit"),
    ("pittsfield", "SR43-1", "US2-2", 68, ""),
    ("fairfield", "I95-1", "I95-2", 120, "interstate ramp, nothing gets stuck here"),
]


def coded_corners() -> TurnBook:
    """The card index as it stands in the code, for an office with no rows."""
    book = TurnBook()
    for node, into, out_of, radius, note in CORNERS:
        book.add(Corner(node, into, out_of, radius, note))
    return book
