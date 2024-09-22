"""One reading of a stream against every weight rule that applies to it.

Four rules speak to weight and a load has to answer all four.

The gross limit is one number for the road. The group limits come off the
statutory table by how many axles stand together. The bridge formula speaks to
every run of consecutive axles. The tire rule speaks to each axle on its own,
off the rate the table publishes.

They are read together here because a refusal has to name all of them at once.
A hauler told his tandem is over, who moves a thousand pounds back and is then
told his tridem is over, is a hauler who calls his legislator, and he is right
to. One reading, every finding, one letter.

A seasonal order reaches this by scaling the limits before anything is
compared, and it scales the group limits and the gross together. It does not
scale the formula: the formula is about bridges and the bridges did not thaw.
That asymmetry is the thing to remember about this file.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..domain.axle import AxleGroup
from ..domain.stream import AxleStream
from ..measure import Pounds
from ..seasons import SeasonOrder
from .formula import Finding, measure
from .statutory import StatutoryTable


@dataclass(frozen=True)
class GroupFinding:
    """One group of the stream against the statutory table."""

    position: int
    group: AxleGroup
    limit: Pounds

    @property
    def weight(self) -> Pounds:
        return self.group.weight

    @property
    def over(self) -> Pounds:
        return self.weight.over(self.limit)

    @property
    def passes(self) -> bool:
        return self.over.value == 0

    def __str__(self) -> str:
        verdict = "within" if self.passes else f"over by {self.over}"
        return (
            f"group {self.position + 1}, {self.group.name} {self.weight}, "
            f"allowed {self.limit}, {verdict}"
        )


@dataclass(frozen=True)
class TireFinding:
    """One axle against the rate the table publishes for its rubber."""

    position: int
    weight: Pounds
    limit: Pounds

    @property
    def over(self) -> Pounds:
        return self.weight.over(self.limit)

    @property
    def passes(self) -> bool:
        return self.over.value == 0

    def __str__(self) -> str:
        return f"axle {self.position + 1} carries {self.weight} on tires rated {self.limit}"


@dataclass(frozen=True)
class WeightReading:
    """Everything the weight rules say about one stream on one road."""

    gross: Pounds
    gross_limit: Pounds
    groups: tuple[GroupFinding, ...]
    formula: tuple[Finding, ...]
    tires: tuple[TireFinding, ...]
    order: SeasonOrder | None = None

    @property
    def gross_over(self) -> Pounds:
        return self.gross.over(self.gross_limit)

    @property
    def group_failures(self) -> tuple[GroupFinding, ...]:
        return tuple(one for one in self.groups if not one.passes)

    @property
    def formula_failures(self) -> tuple[Finding, ...]:
        return tuple(one for one in self.formula if not one.passes)

    @property
    def tire_failures(self) -> tuple[TireFinding, ...]:
        return tuple(one for one in self.tires if not one.passes)

    @property
    def overweight(self) -> bool:
        return bool(
            self.gross_over.value
            or self.group_failures
            or self.formula_failures
            or self.tire_failures
        )

    @property
    def legal(self) -> bool:
        return not self.overweight

    def reasons(self) -> tuple[str, ...]:
        """Every finding, in the order the letter prints them."""
        found = []
        if self.gross_over.value:
            found.append(f"gross {self.gross} is {self.gross_over} over {self.gross_limit}")
        found.extend(str(one) for one in self.group_failures)
        found.extend(str(one) for one in self.formula_failures)
        found.extend(str(one) for one in self.tire_failures)
        return tuple(found)

    def worst_overage(self) -> Pounds:
        """The largest single overage, which is what the fee is priced on."""
        amounts = [self.gross_over]
        amounts.extend(one.over for one in self.group_failures)
        amounts.extend(one.over for one in self.formula_failures)
        amounts.extend(one.over for one in self.tire_failures)
        return max(amounts, key=lambda amount: amount.value)

    def __str__(self) -> str:
        if self.legal:
            return "legal weight"
        return f"overweight, worst overage {self.worst_overage()}"


def read(
    stream: AxleStream,
    table: StatutoryTable,
    order: SeasonOrder | None = None,
) -> WeightReading:
    """Read a stream against a table, with a seasonal order if one is running."""
    scale = order.applied_to if order else _unchanged
    gross_limit = scale(table.gross)
    groups = tuple(
        GroupFinding(position, group, scale(table.group_limit(group.count)))
        for position, group in enumerate(stream.groups())
    )
    tires = tuple(
        TireFinding(position, axle.weight, axle.tire_limit(table.tire_rate))
        for position, axle in enumerate(stream.axles)
    )
    return WeightReading(
        gross=stream.gross,
        gross_limit=gross_limit,
        groups=groups,
        formula=measure(stream, gross_limit),
        tires=tires,
        order=order,
    )


def _unchanged(limit: Pounds) -> Pounds:
    return limit
