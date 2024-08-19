"""The axle stream: every axle under a combination, in the order it arrives.

This is the shape everything downstream reasons about. A permit application
describes a tractor, a jeep dolly, a lowboy and a booster, and the office does
not care about any of those words. It cares that eleven axles cross a given
point in a given order at given spacings carrying given weights.

Two readings come off a stream and they answer different questions.

Groups answer the statutory question. Axles within eight feet of each other are
one group, a tandem or a tridem or a quad, and the legislature writes a limit
for each. Splitting the stream into groups is a walk down the spacings.

Spans answer the bridge question. The bridge formula does not care about
groups at all: it asks about every run of consecutive axles, from the first two
to the whole train, and it tests each one against a limit that grows with how
far apart the ends are. An eleven axle stream has fifty five of those runs and
the formula wants all of them, because the one that fails is rarely the one a
person would have picked.

The extent of a run is measured first axle to last axle. Every rounding in the
formula is done on that number, so it stays a whole number of inches until the
last possible moment.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass

from ..measure import Inches, Pounds
from .axle import GROUP_REACH, Axle, AxleGroup, group_name


@dataclass(frozen=True)
class Span:
    """A run of consecutive axles, which is what the bridge formula asks about."""

    first: int
    last: int
    axles: tuple[Axle, ...]
    extent: Inches

    @property
    def count(self) -> int:
        return len(self.axles)

    @property
    def weight(self) -> Pounds:
        total = Pounds(0)
        for axle in self.axles:
            total = total + axle.weight
        return total

    @property
    def name(self) -> str:
        return group_name(self.count)

    def __str__(self) -> str:
        return f"axles {self.first + 1} to {self.last + 1}, {self.weight} over {self.extent}"


class AxleStream:
    """Axles in road order with the gap between each one and the next.

    ``spacings`` holds one fewer entry than ``axles``. A one axle stream is
    allowed to exist so that nothing downstream has to special case it, but
    nothing will let a permit be written for one.
    """

    def __init__(self, axles: tuple[Axle, ...], spacings: tuple[Inches, ...]) -> None:
        if not axles:
            raise ValueError("a stream holds at least one axle")
        if len(spacings) != len(axles) - 1:
            raise ValueError(
                f"{len(axles)} axles need {len(axles) - 1} spacings, got {len(spacings)}"
            )
        for spacing in spacings:
            if spacing.value <= 0:
                raise ValueError("two axles cannot stand in the same place")
        self.axles = axles
        self.spacings = spacings

    @property
    def count(self) -> int:
        return len(self.axles)

    @property
    def gross(self) -> Pounds:
        """Everything on the road, which is the number on the scale ticket."""
        total = Pounds(0)
        for axle in self.axles:
            total = total + axle.weight
        return total

    @property
    def extent(self) -> Inches:
        """First axle to last axle."""
        return self.distance(0, self.count - 1)

    def distance(self, first: int, last: int) -> Inches:
        """Centre to centre between two axles by index."""
        if not 0 <= first <= last < self.count:
            raise IndexError(f"no axles {first} to {last} in a stream of {self.count}")
        total = Inches(0)
        for spacing in self.spacings[first:last]:
            total = total + spacing
        return total

    def offsets(self) -> tuple[Inches, ...]:
        """Where each axle sits, measured from the first one."""
        running = Inches(0)
        found = [running]
        for spacing in self.spacings:
            running = running + spacing
            found.append(running)
        return tuple(found)

    def spans(self, minimum: int = 2) -> Iterator[Span]:
        """Every run of consecutive axles, shortest first, then left to right.

        The order matters to the reports rather than to the arithmetic: a
        clerk reading a refusal wants the tightest failing run named first.
        """
        if minimum < 1:
            raise ValueError("a span holds at least one axle")
        for length in range(minimum, self.count + 1):
            for first in range(0, self.count - length + 1):
                last = first + length - 1
                yield Span(first, last, self.axles[first : last + 1], self.distance(first, last))

    def groups(self) -> tuple[AxleGroup, ...]:
        """Split at every gap wider than the group reach."""
        found: list[AxleGroup] = []
        start = 0
        for index, spacing in enumerate(self.spacings):
            if spacing > GROUP_REACH:
                found.append(self._group(start, index))
                start = index + 1
        found.append(self._group(start, self.count - 1))
        return tuple(found)

    def _group(self, first: int, last: int) -> AxleGroup:
        return AxleGroup(self.axles[first : last + 1], self.spacings[first:last])

    def heaviest_axle(self) -> Axle:
        return max(self.axles, key=lambda axle: axle.weight)

    def liftable_count(self) -> int:
        return sum(1 for axle in self.axles if axle.liftable)

    def __len__(self) -> int:
        return len(self.axles)

    def __repr__(self) -> str:
        return f"AxleStream({self.count} axles, {self.gross}, {self.extent})"
