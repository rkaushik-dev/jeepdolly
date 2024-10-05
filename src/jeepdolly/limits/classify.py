"""What kind of permit this is, which decides who may issue it.

The ladder has four rungs and an application walks up until it stops.

Nothing. Inside legal size and legal weight. There is no permit to write and
the clerk says so, which happens more often than anybody expects because
haulers ask before they measure.

Ordinary. Past legal, inside the routine boxes, under the superload
thresholds. A clerk issues it.

Study. Past the routine box on size, or past the routine overage on weight.
An engineer reads it before anybody issues anything.

Superload. Past the thresholds the state publishes for the word. This is not
just a bigger study: it pulls in a structural review of every bridge on the
route, and it is the only kind that can be refused outright on the strength of
one span.

Two things sit outside the ladder and beat it.

A divisible load gets nothing, whatever its numbers, unless its commodity
carries an allowance. That test runs first because a load that should have
been two trips does not deserve an engineer's afternoon.

A trip that only wants a town way gets nothing either, because this office
does not write permits for roads it does not maintain. That one is checked on
the route rather than here, and this file only names the rung.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from ..domain.commodity import Commodity
from ..domain.envelope import Envelope
from ..measure import Inches, Pounds
from .size import SizeClass, SizeTable
from .weight import WeightReading


class PermitKind(Enum):
    """The rung, in the order the ladder is walked."""

    NONE = "none"
    ORDINARY = "ordinary"
    STUDY = "study"
    SUPERLOAD = "superload"

    @property
    def needs_engineer(self) -> bool:
        return self in (PermitKind.STUDY, PermitKind.SUPERLOAD)

    @property
    def issuable_at_the_counter(self) -> bool:
        return self is PermitKind.ORDINARY

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Thresholds:
    """The numbers that make a load a superload, published and dated."""

    gross: Pounds
    axle_group: Pounds
    width: Inches
    height: Inches
    length: Inches

    def crossed_by(self, envelope: Envelope, reading: WeightReading) -> tuple[str, ...]:
        """Which thresholds this load is past, in the order they are printed."""
        found = []
        if reading.gross > self.gross:
            found.append("gross")
        if any(group.weight > self.axle_group for group in reading.groups):
            found.append("axle group")
        if envelope.width > self.width:
            found.append("width")
        if envelope.height > self.height:
            found.append("height")
        if envelope.length > self.length:
            found.append("length")
        return tuple(found)


STANDARD = Thresholds(
    gross=Pounds(150000),
    axle_group=Pounds(60000),
    width=Inches.of(16),
    height=Inches.of(16),
    length=Inches.of(150),
)


@dataclass(frozen=True)
class Classification:
    """The rung, and every reason the load did not stop lower down."""

    kind: PermitKind
    size: SizeClass
    overweight: bool
    superload_reasons: tuple[str, ...] = ()
    review_reasons: tuple[str, ...] = ()
    refusal: str | None = None

    @property
    def permittable(self) -> bool:
        return self.refusal is None and self.kind is not PermitKind.NONE

    def __str__(self) -> str:
        if self.refusal:
            return f"no permit: {self.refusal}"
        if self.kind is PermitKind.NONE:
            return "no permit needed"
        reasons = self.superload_reasons or self.review_reasons
        if reasons:
            return f"{self.kind} on {', '.join(reasons)}"
        return str(self.kind)


def classify(
    envelope: Envelope,
    reading: WeightReading,
    size_table: SizeTable,
    commodity: Commodity,
    thresholds: Thresholds = STANDARD,
) -> Classification:
    """Walk the ladder once and report where it stopped and why."""
    size = size_table.classify(envelope)
    overweight = reading.overweight
    if size is SizeClass.LEGAL and not overweight:
        return Classification(PermitKind.NONE, size, overweight)
    if not commodity.permittable:
        return Classification(
            PermitKind.NONE,
            size,
            overweight,
            refusal=f"{commodity.code} could have been loaded to legal weight",
        )
    crossed = thresholds.crossed_by(envelope, reading)
    if crossed:
        return Classification(PermitKind.SUPERLOAD, size, overweight, superload_reasons=crossed)
    review = size_table.reasons_for_review(envelope)
    if review:
        return Classification(PermitKind.STUDY, size, overweight, review_reasons=review)
    return Classification(PermitKind.ORDINARY, size, overweight)
