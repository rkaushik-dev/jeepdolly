"""What a structure actually feels, which is not what the scale ticket says.

A hundred and eighty thousand pound combination is ninety feet long. A forty
eight foot single span bridge never has all of it on at once. What that bridge
feels is the heaviest ninety inches per foot of itself that the train can
present, which is a window sliding down the axle stream.

So the load on a span is the heaviest run of consecutive axles that fits within
the span length. Nine axles on a four hundred foot bridge is all nine. The same
nine on a forty eight foot culvert is whichever four of them are worst.

Two details matter and both have bitten somebody.

The window is measured axle to axle, not bumper to bumper. An axle sitting one
inch past the abutment is off the span, and the office does not give itself
credit for the inch.

The worst window is not always at the back. Haulers load a lowboy heavy at the
rear to keep weight off the steer axle, and a jeep dolly puts four heavy axles
at the front, so the answer moves with the equipment and has to be searched
for rather than assumed.

For a multi span structure the governing length is the longest single span,
not the whole bridge. That is a simplification and it is the right one for a
screening tool: it is conservative for continuous spans and exact for the
simple spans that most of the inventory is made of.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..domain.stream import AxleStream
from ..errors import Impossible
from ..measure import Inches, Pounds
from .inventory import Structure


@dataclass(frozen=True)
class Window:
    """The run of axles a structure carries at the worst moment."""

    first: int
    last: int
    weight: Pounds
    extent: Inches

    @property
    def count(self) -> int:
        return self.last - self.first + 1

    @property
    def whole_train(self) -> bool:
        return self.first == 0

    def __str__(self) -> str:
        return f"axles {self.first + 1} to {self.last + 1}, {self.weight} over {self.extent}"


def load_on_span(stream: AxleStream, span_feet: int) -> Window:
    """The heaviest run of axles that fits inside a span of this length."""
    if span_feet <= 0:
        raise Impossible("a span has a length")
    reach = Inches.of(span_feet)
    best = Window(0, 0, stream.axles[0].weight, Inches(0))
    for first in range(stream.count):
        for last in range(first, stream.count):
            extent = stream.distance(first, last)
            if extent > reach:
                break
            weight = Pounds(0)
            for axle in stream.axles[first : last + 1]:
                weight = weight + axle.weight
            if weight > best.weight:
                best = Window(first, last, weight, extent)
    return best


def load_on(structure: Structure, stream: AxleStream) -> Window:
    """What this structure feels, governed by its longest single span."""
    if not structure.kind.carries_traffic:
        raise Impossible(f"{structure.ident} does not carry traffic")
    return load_on_span(stream, structure.longest_span)


def governing_weight(structure: Structure, stream: AxleStream) -> Pounds:
    """The weight to rate this structure against."""
    return load_on(structure, stream).weight


def relief(structure: Structure, stream: AxleStream) -> Pounds:
    """How much of the combination the structure never sees at once."""
    return stream.gross - governing_weight(structure, stream)
