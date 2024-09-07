"""The bridge formula, which is the rule that catches what the tables miss.

The tables in ``statutory`` limit what one group may carry. The formula limits
what any run of axles may carry over the distance it covers, and it is the
reason a legal tandem can still be refused: put two legal groups close enough
together and the pair of them is over.

    W = 500 * ( L * N / (N - 1) + 12 * N + 36 )

``N`` is the number of axles in the run and ``L`` is the distance in feet
between the first and the last of them. The published table rounds ``L`` to the
nearest foot and the answer to the nearest five hundred pounds, and this does
the same, because a permit that disagrees with the wall chart in the weigh
station is a permit somebody has to defend at the roadside.

The answer is capped at the gross limit for the road. The formula will happily
allow a hundred and ten thousand pounds over eighty feet of trailer, and the
gross limit says no anyway.

There is one exception in the federal rule and it is the reason ordinary five
axle semis are legal at all. Two consecutive sets of tandems may carry thirty
four thousand pounds each when the outer axles are thirty six feet apart or
more, which the formula on its own would refuse. The exception is written into
the check rather than into the arithmetic, so a report can say which rule let
a load through.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..domain.axle import GROUP_REACH
from ..domain.stream import AxleStream, Span
from ..errors import Impossible
from ..measure import Inches, Pounds

#: The distance the two tandem exception needs between the outer axles.
TANDEM_EXCEPTION_REACH = Inches.of(36)

#: What each of those two tandems may carry under the exception.
TANDEM_EXCEPTION_WEIGHT = Pounds(34000)

ROUNDING = 500


def feet_of(extent: Inches) -> int:
    """The distance in whole feet, rounded to the nearest one."""
    if extent.value < 0:
        raise Impossible("a run of axles does not cover a negative distance")
    return (extent.value + 6) // 12


def round_to_500(pounds: int) -> Pounds:
    """The published table rounds to the nearest five hundred."""
    return Pounds((pounds + ROUNDING // 2) // ROUNDING * ROUNDING)


def formula_limit(count: int, extent: Inches, cap: Pounds) -> Pounds:
    """What a run of ``count`` axles over ``extent`` may carry, capped."""
    if count < 2:
        raise Impossible("the formula speaks about two axles or more")
    length = feet_of(extent)
    raw = 500 * (length * count + (12 * count + 36) * (count - 1))
    allowed = round_to_500(raw // (count - 1))
    return min(allowed, cap)


@dataclass(frozen=True)
class Finding:
    """One run of axles measured against the formula."""

    span: Span
    allowed: Pounds
    exception: str | None = None

    @property
    def weight(self) -> Pounds:
        return self.span.weight

    @property
    def over(self) -> Pounds:
        return self.weight.over(self.allowed)

    @property
    def passes(self) -> bool:
        return self.over.value == 0

    def __str__(self) -> str:
        verdict = "within" if self.passes else f"over by {self.over}"
        note = f" under the {self.exception}" if self.exception else ""
        return f"{self.span}, allowed {self.allowed}, {verdict}{note}"


def two_tandem_exception(stream: AxleStream, span: Span) -> bool:
    """Whether the federal two tandem exception reaches this run.

    Four axles, standing as two tandems, thirty six feet or more apart, and
    neither tandem over thirty four thousand pounds.
    """
    if span.count != 4:
        return False
    if span.extent < TANDEM_EXCEPTION_REACH:
        return False
    first, last = span.first, span.last
    if stream.distance(first, first + 1) > GROUP_REACH:
        return False
    if stream.distance(last - 1, last) > GROUP_REACH:
        return False
    front = span.axles[0].weight + span.axles[1].weight
    back = span.axles[2].weight + span.axles[3].weight
    return front <= TANDEM_EXCEPTION_WEIGHT and back <= TANDEM_EXCEPTION_WEIGHT


def measure(stream: AxleStream, cap: Pounds) -> tuple[Finding, ...]:
    """Every run of the stream against the formula, in span order."""
    found = []
    for span in stream.spans():
        allowed = formula_limit(span.count, span.extent, cap)
        exception = None
        if span.weight > allowed and two_tandem_exception(stream, span):
            allowed = TANDEM_EXCEPTION_WEIGHT * 2
            exception = "two tandem exception"
        found.append(Finding(span, min(allowed, cap), exception))
    return tuple(found)


def failures(stream: AxleStream, cap: Pounds) -> tuple[Finding, ...]:
    """Only the runs that are over, which is what a refusal letter lists."""
    return tuple(finding for finding in measure(stream, cap) if not finding.passes)


def worst(stream: AxleStream, cap: Pounds) -> Finding | None:
    """The run furthest over, which is what the counter reads out first."""
    over = failures(stream, cap)
    if not over:
        return None
    return max(over, key=lambda finding: finding.over.value)
