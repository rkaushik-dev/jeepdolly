"""The envelope: the box the load sweeps down the road.

An application gives four sizes and two overhangs, and every one of them is
measured differently, so they are worth naming carefully.

Width is the widest point of anything, load or equipment, mirrors excepted.
Mirrors are excepted because every truck has them and the exception is older
than anybody working here.

Height is measured off the road surface to the highest point, and it is the one
number a hauler is most likely to get wrong, because it is measured loaded and
they measure it in the yard on flat gravel.

Length is the whole combination, bumper to the end of the load. It is not the
trailer length and it is not the load length.

The overhangs are what sticks out past the equipment, front and rear. They are
not part of the length question, they are their own question, because a
sixteen foot rear overhang on a legal length combination still takes out a sign
on every corner it turns.

An envelope compared to another envelope gives an excess: the same six fields,
each one zero or the amount over. Nothing here decides whether an excess is
allowed. That is a table and a route and a season, and it lives elsewhere.
"""

from __future__ import annotations

from dataclasses import dataclass, fields

from ..measure import ZERO_INCHES, Inches

#: The six measures, in the order the application form prints them.
MEASURES = ("width", "height", "length", "front_overhang", "rear_overhang")


@dataclass(frozen=True)
class Excess:
    """How far past a limit each measure runs. Zero where it is inside."""

    width: Inches = ZERO_INCHES
    height: Inches = ZERO_INCHES
    length: Inches = ZERO_INCHES
    front_overhang: Inches = ZERO_INCHES
    rear_overhang: Inches = ZERO_INCHES

    @property
    def any(self) -> bool:
        return any(getattr(self, name).value for name in MEASURES)

    def over(self) -> tuple[str, ...]:
        """Which measures are past the limit, in form order."""
        return tuple(name for name in MEASURES if getattr(self, name).value)

    def worst(self) -> tuple[str, Inches] | None:
        """The measure furthest past its limit, for the refusal letter."""
        over = [(name, getattr(self, name)) for name in self.over()]
        if not over:
            return None
        return max(over, key=lambda pair: pair[1].value)

    def __str__(self) -> str:
        if not self.any:
            return "within limits"
        parts = [f"{name.replace('_', ' ')} over by {getattr(self, name)}" for name in self.over()]
        return ", ".join(parts)


@dataclass(frozen=True)
class Envelope:
    """The box, as the application describes it."""

    width: Inches
    height: Inches
    length: Inches
    front_overhang: Inches = ZERO_INCHES
    rear_overhang: Inches = ZERO_INCHES

    def __post_init__(self) -> None:
        for field in fields(self):
            value = getattr(self, field.name)
            if not isinstance(value, Inches):
                raise TypeError(f"{field.name} is a size, not {value!r}")
        if self.width.value <= 0 or self.height.value <= 0 or self.length.value <= 0:
            raise ValueError("a load has a width, a height and a length")
        if self.front_overhang.value < 0 or self.rear_overhang.value < 0:
            raise ValueError("an overhang is measured outward or it is nothing")
        if self.front_overhang + self.rear_overhang > self.length:
            raise ValueError("the overhangs are longer than the whole combination")

    @classmethod
    def read(
        cls,
        width: str,
        height: str,
        length: str,
        front_overhang: str = "0",
        rear_overhang: str = "0",
    ) -> Envelope:
        """Build from what is written on the form, in whatever format."""
        return cls(
            Inches.parse(width),
            Inches.parse(height),
            Inches.parse(length),
            Inches.parse(front_overhang),
            Inches.parse(rear_overhang),
        )

    @property
    def overhang(self) -> Inches:
        """Front and rear together, which is what the escort table reads."""
        return self.front_overhang + self.rear_overhang

    @property
    def projects(self) -> bool:
        return self.overhang.value > 0

    def excess_over(self, limit: Envelope) -> Excess:
        """Each measure against the same measure of a limiting envelope."""
        return Excess(
            self.width.over(limit.width),
            self.height.over(limit.height),
            self.length.over(limit.length),
            self.front_overhang.over(limit.front_overhang),
            self.rear_overhang.over(limit.rear_overhang),
        )

    def within(self, limit: Envelope) -> bool:
        return not self.excess_over(limit).any

    def grown(self, **by: Inches) -> Envelope:
        """A copy with some measures made larger, for a what if at the counter."""
        values = {name: getattr(self, name) for name in MEASURES}
        for name, amount in by.items():
            if name not in values:
                raise ValueError(f"an envelope has no {name}")
            values[name] = values[name] + amount
        return Envelope(**values)

    def __str__(self) -> str:
        base = f"{self.width} wide, {self.height} high, {self.length} long"
        if not self.projects:
            return base
        return f"{base}, {self.front_overhang} front and {self.rear_overhang} rear overhang"
