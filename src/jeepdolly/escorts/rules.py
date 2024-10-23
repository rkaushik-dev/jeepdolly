"""How many pilot cars, on which end, and when a trooper comes too.

The escort table is the part of a permit haulers argue about, because escorts
cost more than the permit does. It is also the part that keeps people alive, so
the office does not negotiate it at the counter.

Three things drive it and they interact, which is why this is a table and not
three independent rules.

Width drives most of it. Past twelve feet a load needs somebody ahead of it on
an undivided road, because the oncoming traffic is the danger. Past fourteen it
needs somebody behind as well. Past sixteen it needs a trooper, because at that
width the load is using both lanes and somebody has to stop the traffic.

Length and overhang drive the rear. A hundred foot combination needs a rear
escort whatever it is wide, and a long rear overhang needs one whether or not
the combination is long, because the overhang is what a following car runs
under.

Height drives a pole car, which is a pilot car with a fibreglass wand set an
inch above the load. It goes in front and it goes in front on every class of
road, including the interstate, which is the exception people forget.

The road class changes the answer twice. On a divided highway the front escort
is not needed for width alone, because there is no oncoming traffic to warn.
And on the interstate a trooper is ordered through a different office, so the
permit says one is required and does not pretend to have arranged it.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..domain.envelope import Envelope
from ..domain.highway import HighwayClass
from ..measure import Inches


@dataclass(frozen=True)
class EscortRules:
    """The thresholds, which are published and change by order."""

    front_width: Inches
    rear_width: Inches
    police_width: Inches
    rear_length: Inches
    both_length: Inches
    pole_height: Inches
    rear_overhang: Inches


STANDARD = EscortRules(
    front_width=Inches.of(12),
    rear_width=Inches.of(14),
    police_width=Inches.of(16),
    rear_length=Inches.of(100),
    both_length=Inches.of(125),
    pole_height=Inches.of(14, 6),
    rear_overhang=Inches.of(10),
)


@dataclass(frozen=True)
class Escorts:
    """What the trip has to have with it."""

    front: int
    rear: int
    police: bool
    height_pole: bool
    reasons: tuple[str, ...] = ()

    @property
    def total(self) -> int:
        return self.front + self.rear

    @property
    def any(self) -> bool:
        return bool(self.total or self.police)

    def __str__(self) -> str:
        if not self.any:
            return "no escorts"
        parts = []
        if self.front:
            pole = " with height pole" if self.height_pole else ""
            parts.append(f"{self.front} front{pole}")
        if self.rear:
            parts.append(f"{self.rear} rear")
        if self.police:
            parts.append("police escort")
        return ", ".join(parts)


def required(
    envelope: Envelope,
    classes: tuple[HighwayClass, ...],
    rules: EscortRules = STANDARD,
    superload: bool = False,
) -> Escorts:
    """Read the table for this load on these classes of road."""
    divided_only = bool(classes) and all(one.federal for one in classes)
    front = 0
    rear = 0
    police = False
    pole = False
    reasons: list[str] = []

    if envelope.width > rules.front_width and not divided_only:
        front = 1
        reasons.append(f"width over {rules.front_width}")
    if envelope.width > rules.rear_width:
        rear = max(rear, 1)
        reasons.append(f"width over {rules.rear_width}")
    if envelope.width > rules.police_width:
        police = True
        reasons.append(f"width over {rules.police_width}")
    if envelope.length > rules.rear_length:
        rear = max(rear, 1)
        reasons.append(f"length over {rules.rear_length}")
    if envelope.length > rules.both_length and not divided_only:
        front = max(front, 1)
        reasons.append(f"length over {rules.both_length}")
    if envelope.rear_overhang > rules.rear_overhang:
        rear = max(rear, 1)
        reasons.append(f"rear overhang over {rules.rear_overhang}")
    if envelope.height > rules.pole_height:
        front = max(front, 1)
        pole = True
        reasons.append(f"height over {rules.pole_height}")
    if superload:
        police = True
        reasons.append("superload")

    return Escorts(front, rear, police, pole, tuple(reasons))


def conditions(escorts: Escorts) -> tuple[str, ...]:
    """The lines that go on the permit face, in the order they are printed."""
    if not escorts.any:
        return ()
    found = []
    if escorts.front:
        pole = " carrying a height pole set one inch above the load" if escorts.height_pole else ""
        found.append(f"{escorts.front} certified pilot car ahead of the load{pole}")
    if escorts.rear:
        found.append(f"{escorts.rear} certified pilot car behind the load")
    if escorts.police:
        found.append("police escort, arranged by the carrier with the state police")
    return tuple(found)
