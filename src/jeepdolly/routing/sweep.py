"""The swept path arithmetic: what radius a load wants, and where it is short.

This was in the card index file until the file had two subjects in it. The
card index is a record of what the corners are. This is the rule about what a
combination needs, and the two change for different reasons: a corner is
remeasured when the town rebuilds a junction, and the rule is changed by an
order out of the capital.

The rule is a swept path in the crudest form that is still honest. A
combination needs a radius that grows with its wheelbase and with what hangs
off the back, and past a certain length nothing gets round at all without
going up on the far kerb, which the office will not permit.

Two things this deliberately does not do.

It does not compute a real swept path. That wants the geometry of the corner
and the offtracking of every axle group, and the office has neither. What it
has is a measured radius and a table, and the table is set conservatively.

It does not read the direction of travel except to find the corner. A corner
is the same corner both ways round, and where it is not, the office records
two of them.

A corner comes back in one of two ways and never both. Either the load will
not get round it, which is a refusal, or it gets round with little to spare,
which is a condition and a man in the road with a flag.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..domain.envelope import Envelope
from ..measure import Inches
from .itinerary import Itinerary
from .turns import Corner, TurnBook

#: The radius a combination inside the plain length wants, in feet.
PLAIN_RADIUS = 40

#: Length at which a combination starts to need more than the plain corner.
LONG_ENOUGH = Inches.of(75)

#: Feet of radius wanted per foot of combination past the plain length.
RADIUS_PER_FOOT = 1

#: Feet of radius wanted per foot of rear overhang, which swings wide.
RADIUS_PER_OVERHANG_FOOT = 2


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
