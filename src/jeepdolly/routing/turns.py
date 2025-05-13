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

#: Below this radius nothing longer than the short table gets round.
TIGHT_FEET = 40

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
    return TIGHT_FEET + from_length + from_overhang


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
