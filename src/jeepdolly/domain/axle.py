"""Axles and the groups they fall into.

Everything about weight in this system starts with one fact: the road does not
care what the truck weighs. The road cares what is pressed into it in one
place, and how far away the next thing pressed into it is. So an axle carries
its own weight, and axles standing close together are read as one group,
because to the pavement under them they are one.

Two limits sit on a single axle and both of them bite.

The first is the statutory limit for the group the axle is in, which lives in
the limits package because it is a number the legislature writes.

The second is the tire limit, which is physics with a number attached: so many
pounds per inch of tire on the ground. A tridem under a hundred thousand pounds
on narrow rubber will make its group limit and still cut the pavement, and the
office refuses it on the tire rule alone. This one lives here, because it is a
property of the axle and not of the road.

A liftable axle is the standing argument of the permit counter. It counts when
it is down and carrying, and haulers lift them on corners because a lifted axle
turns easier. The rule the office works to is that a liftable axle is assumed
down and carrying its declared share, and if the hauler cannot make the load
work with it down then the load does not work.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from ..errors import Impossible
from ..measure import Inches, Pounds

#: Axles standing this close or closer are read as one group.
GROUP_REACH = Inches.of(8)

#: What a group of a given size is called on the face of a permit.
GROUP_NAMES = {1: "single", 2: "tandem", 3: "tridem", 4: "quad", 5: "quint", 6: "six axle group"}


def group_name(count: int) -> str:
    """The word the office uses for a group of this many axles."""
    if count < 1:
        raise Impossible("a group holds at least one axle")
    return GROUP_NAMES.get(count, f"{count} axle group")


class AxleKind(Enum):
    """Where on the combination an axle sits."""

    STEER = "steer"
    DRIVE = "drive"
    TRAILER = "trailer"
    JEEP = "jeep"
    BOOSTER = "booster"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Axle:
    """One axle, its load, and the rubber it stands on."""

    kind: AxleKind
    weight: Pounds
    tires: int = 4
    tire_width: Inches = Inches(11)
    liftable: bool = False

    def __post_init__(self) -> None:
        if self.weight.value < 0:
            raise Impossible("an axle cannot carry a negative weight")
        if self.tires < 2 or self.tires % 2:
            raise Impossible(f"an axle stands on an even count of tires, not {self.tires}")
        if self.tire_width.value < 1:
            raise Impossible("an axle stands on tires with a width")
        if self.kind is AxleKind.STEER and self.liftable:
            raise Impossible("a steer axle does not lift")

    @property
    def tire_inches(self) -> Inches:
        """Total width of rubber on the ground under this axle."""
        return self.tire_width * self.tires

    def tire_limit(self, pounds_per_inch: int) -> Pounds:
        """What the rubber will carry at the rate the state publishes."""
        return Pounds(self.tire_inches.value * pounds_per_inch)

    def over_tire_limit(self, pounds_per_inch: int) -> Pounds:
        """How far past the tire limit this axle is loaded."""
        return self.weight.over(self.tire_limit(pounds_per_inch))

    def __str__(self) -> str:
        lift = " liftable" if self.liftable else ""
        return f"{self.kind} {self.weight} on {self.tires} tires{lift}"


@dataclass(frozen=True)
class AxleGroup:
    """Axles close enough together that the road reads them as one.

    ``spacings`` holds the gap between each axle and the next, so a group of
    three axles carries two spacings. A single axle carries none.
    """

    axles: tuple[Axle, ...]
    spacings: tuple[Inches, ...] = ()

    def __post_init__(self) -> None:
        if not self.axles:
            raise Impossible("a group holds at least one axle")
        if len(self.spacings) != len(self.axles) - 1:
            raise Impossible(
                f"{len(self.axles)} axles need {len(self.axles) - 1} spacings, "
                f"got {len(self.spacings)}"
            )
        for spacing in self.spacings:
            if spacing.value <= 0:
                raise Impossible("axles in a group stand apart")
            if spacing > GROUP_REACH:
                raise Impossible(f"{spacing} is too far apart to be one group")

    @property
    def count(self) -> int:
        return len(self.axles)

    @property
    def name(self) -> str:
        return group_name(self.count)

    @property
    def weight(self) -> Pounds:
        total = Pounds(0)
        for axle in self.axles:
            total = total + axle.weight
        return total

    @property
    def extent(self) -> Inches:
        """First axle to last axle, which is what the bridge formula wants."""
        total = Inches(0)
        for spacing in self.spacings:
            total = total + spacing
        return total

    @property
    def liftable_count(self) -> int:
        return sum(1 for axle in self.axles if axle.liftable)

    def tire_limit(self, pounds_per_inch: int) -> Pounds:
        total = Pounds(0)
        for axle in self.axles:
            total = total + axle.tire_limit(pounds_per_inch)
        return total

    def heaviest(self) -> Axle:
        return max(self.axles, key=lambda axle: axle.weight)

    def __len__(self) -> int:
        return len(self.axles)

    def __str__(self) -> str:
        return f"{self.name} {self.weight} over {self.extent}"
