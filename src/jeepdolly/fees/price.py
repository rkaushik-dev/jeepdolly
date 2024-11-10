"""Pricing a permit, one line at a time, so the hauler can see the arithmetic.

A quote is a list of lines and a total. It is never a single number, because
the first thing a hauler does with a fee is ask what it is for, and an office
that answers with one number has an argument on its hands every time.

The lines come out in a fixed order: base, mileage by class, weight, extras.
That order is the order they are printed on the receipt and the order they
appear in the nightly revenue report, so a change here changes three things and
is meant to.

The minimum is applied at the end, as its own line, and only ever upward. A
permit that prices below the minimum is written up to it with a line saying so,
because a receipt that shows forty dollars against thirty one dollars of
arithmetic is a receipt somebody queries.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..limits.classify import PermitKind
from ..measure import Pounds
from ..money import Dollars, total
from ..routing.mileage import Mileage
from .schedule import FeeSchedule


@dataclass(frozen=True)
class Line:
    """One line of the quote."""

    description: str
    amount: Dollars

    def __str__(self) -> str:
        return f"{self.description}  {self.amount}"


@dataclass(frozen=True)
class Quote:
    """What a permit costs and what each part of it was for."""

    lines: tuple[Line, ...]
    schedule: str

    @property
    def total(self) -> Dollars:
        return total(line.amount for line in self.lines)

    @property
    def minimum_applied(self) -> bool:
        return any(line.description.startswith("minimum") for line in self.lines)

    def line(self, description: str) -> Line | None:
        for line in self.lines:
            if line.description == description:
                return line
        return None

    def __len__(self) -> int:
        return len(self.lines)

    def __str__(self) -> str:
        return f"{len(self.lines)} lines, {self.total}"


def price(
    schedule: FeeSchedule,
    kind: PermitKind,
    mileage: Mileage,
    overage: Pounds,
    review: bool = False,
) -> Quote:
    """Price one permit off one schedule."""
    lines = [Line(f"{kind} permit", schedule.base_for(kind))]
    dearest = Dollars(0)
    counted = 0
    for part in mileage.parts:
        rate = schedule.mile_rate(part.highway_class)
        dearest = max(dearest, rate)
        counted += part.charged
        if rate.cents:
            lines.append(Line(f"{part.charged} miles on {part.highway_class}", rate * part.charged))
    if mileage.minimum_applied and mileage.charged > counted:
        short = mileage.charged - counted
        lines.append(Line(f"{short} miles to the minimum mileage", dearest * short))
    charge = schedule.weight_charge(overage)
    if charge.cents:
        lines.append(Line(f"{overage} over legal gross", charge))
    if review:
        lines.append(Line("engineering review", schedule.review))
    running = total(line.amount for line in lines)
    if running < schedule.minimum:
        lines.append(Line("minimum fee adjustment", schedule.minimum - running))
    return Quote(tuple(lines), str(schedule))


def amendment(schedule: FeeSchedule, before: Quote, after: Quote) -> Quote:
    """What an amendment costs: the difference, never below the flat fee.

    An amendment that makes a permit cheaper is not a refund. The office keeps
    what it took and charges the amendment fee, because the work of amending is
    the same work whichever way the numbers moved.
    """
    difference = after.total - before.total
    lines = [Line("amendment fee", schedule.amendment)]
    if difference.cents > 0:
        lines.append(Line("difference in permit fee", difference))
    return Quote(tuple(lines), str(schedule))
