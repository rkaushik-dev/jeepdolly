"""Corners, which are the thing that stops a long load rather than a bridge.

A hundred and twenty feet of combination will go under everything on US 2 and
still not get out of the village at Norridgewock, because the turn from US 2
onto SR 8 is a ninety degree corner with a granite kerb and a bank on the
outside. The office knows perhaps two hundred corners like it and has kept
them on a card index for thirty years.

A corner is recorded as the two segments and the node between them, with the
radius somebody measured off the plan. The rule is a swept path in the crudest
form that is still honest: a combination needs a radius that grows with its
wheelbase and with what hangs off the back, and past a certain length nothing
gets round at all without going up on the far kerb, which the office will not
permit.

Two things this deliberately does not do.

It does not compute a real swept path. That wants the geometry of the corner
and the offtracking of every axle group, and the office has neither. What it
has is a measured radius and a table, and the table is set conservatively.

It does not read the direction of travel except to find the corner. A corner
is the same corner both ways round, and where it is not, the office records
two of them.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..domain.envelope import Envelope
from ..errors import Conflict, Impossible
from ..measure import Inches
from .itinerary import Itinerary

#: The radius a combination inside the plain length wants, in feet.
PLAIN_RADIUS = 40

#: The radius under which a corner goes on the list on the office wall.
#:
#: This is not the same number as the plain radius and it is deliberately
#: above it. A forty two foot corner gets an ordinary lowboy round and stops
#: everything longer, which is exactly the kind of corner the counter wants to
#: know about before it quotes a route rather than after.
TIGHT_FEET = 50

#: Length at which a combination starts to need more than the plain corner.
LONG_ENOUGH = Inches.of(75)

#: Feet of radius wanted per foot of combination past the plain length.
RADIUS_PER_FOOT = 1

#: Feet of radius wanted per foot of rear overhang, which swings wide.
RADIUS_PER_OVERHANG_FOOT = 2


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


def radius_wanted(envelope: Envelope) -> int:
    """The radius this combination wants, in whole feet."""
    over = envelope.length.over(LONG_ENOUGH)
    from_length = (over.value // 12) * RADIUS_PER_FOOT
    from_overhang = (envelope.rear_overhang.value // 12) * RADIUS_PER_OVERHANG_FOOT
    return PLAIN_RADIUS + from_length + from_overhang


@dataclass(frozen=True)
class TooTight:
    """One corner a load will not get round."""

    corner: Corner
    wanted: int

    @property
    def short_by(self) -> int:
        return self.wanted - self.corner.radius_feet

    def __str__(self) -> str:
        return (
            f"{self.corner.node}: the corner is {self.corner.radius_feet} ft and this "
            f"load wants {self.wanted} ft"
        )


#: A corner that clears by less than this gets a condition rather than silence.
#:
#: Ten feet is not a comfort margin. It is the width of the mistake a driver
#: can make on a corner he has not seen before, and the office would rather
#: put a man in the road with a flag than have the trailer find the kerb.
MARGIN_FEET = 10


@dataclass(frozen=True)
class CloseRun:
    """One corner a load gets round, but not by much."""

    corner: Corner
    wanted: int

    @property
    def spare(self) -> int:
        return self.corner.radius_feet - self.wanted

    def __str__(self) -> str:
        return (
            f"{self.corner.node}: the corner is {self.corner.radius_feet} ft and this "
            f"load wants {self.wanted} ft, so it clears by {self.spare} ft"
        )


def corners_on(itinerary: Itinerary, book: TurnBook) -> tuple[Corner, ...]:
    """Every recorded corner the trip actually turns at, in driven order."""
    found = []
    legs = list(itinerary)
    for first, second in zip(legs, legs[1:], strict=False):
        corner = book.between(first.left_at, first.segment.ident, second.segment.ident)
        if corner is not None:
            found.append(corner)
    return tuple(found)


def too_tight(itinerary: Itinerary, envelope: Envelope, book: TurnBook) -> tuple[TooTight, ...]:
    """The corners on this route that this load will not get round."""
    wanted = radius_wanted(envelope)
    return tuple(
        TooTight(one, wanted) for one in corners_on(itinerary, book) if one.radius_feet < wanted
    )


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


def close_run(
    itinerary: Itinerary,
    envelope: Envelope,
    book: TurnBook,
    margin: int = MARGIN_FEET,
) -> tuple[CloseRun, ...]:
    """The corners on this route the load gets round with little to spare."""
    wanted = radius_wanted(envelope)
    return tuple(
        CloseRun(one, wanted)
        for one in corners_on(itinerary, book)
        if wanted <= one.radius_feet < wanted + margin
    )
