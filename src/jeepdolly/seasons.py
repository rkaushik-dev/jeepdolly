"""Seasonal orders, which move a weight limit up and down twice a year.

Frost does two opposite things to a gravel based road. In February the ground
is frozen solid to four feet down and the road will carry more than the book
says. In April the top of it thaws while the bottom is still ice, the melt has
nowhere to go, and the same road turns to pudding under a loaded truck.

So the district engineer signs two kinds of order.

A thaw order cuts the limit on every posted road in a region, usually to some
share of the statutory weight, for the six or eight weeks the road is soft.
The date it starts is a judgement made on a Thursday afternoon looking at a
frost gauge, which is why it is stored as a signed order with a number on it
and not computed from the calendar.

A frozen order raises the limit for the deep winter, on the same roads, by a
smaller share. It is an allowance and a hauler has to ask for it.

Both are regional. The county line is not a frost line but it is where the
order stops, because that is how the orders are written.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from .errors import Conflict, Impossible
from .measure import Pounds


class SeasonKind(Enum):
    """What an order does to the limit it touches."""

    THAW = "thaw"
    FROZEN = "frozen"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class SeasonOrder:
    """One signed order, for one region, over one span of days.

    ``share`` is whole percent of the statutory limit that applies while the
    order is in force. A thaw order is below a hundred and a frozen order is
    above it, and an order that does neither is a mistake in the typing.
    """

    number: str
    region: str
    kind: SeasonKind
    start: date
    end: date
    share: int

    def __post_init__(self) -> None:
        if self.end < self.start:
            raise Impossible(f"order {self.number} ends before it starts", order=self.number)
        if not 1 <= self.share <= 200:
            raise Impossible(f"order {self.number} has an unreadable share {self.share}")
        if self.kind is SeasonKind.THAW and self.share >= 100:
            raise Impossible(f"thaw order {self.number} does not reduce anything")
        if self.kind is SeasonKind.FROZEN and self.share <= 100:
            raise Impossible(f"frozen order {self.number} does not allow anything")

    def covers(self, day: date) -> bool:
        return self.start <= day <= self.end

    def overlaps(self, other: SeasonOrder) -> bool:
        return self.start <= other.end and other.start <= self.end

    def applied_to(self, limit: Pounds) -> Pounds:
        """The limit while this order is in force, truncated to whole pounds."""
        return Pounds(limit.value * self.share // 100)

    def __str__(self) -> str:
        return f"{self.number} {self.region} {self.kind} {self.share}%"


class SeasonBook:
    """Every order on file, indexed the way the counter asks about them.

    Two orders of the same kind may not overlap in one region, because then
    nobody can say which limit is in force. A thaw and a frozen order may not
    overlap either, for the same reason and with more force.
    """

    def __init__(self) -> None:
        self._orders: list[SeasonOrder] = []

    def file(self, order: SeasonOrder) -> SeasonOrder:
        for existing in self._orders:
            if existing.number == order.number:
                raise Conflict(f"order {order.number} is already on file", order=order.number)
            if existing.region == order.region and existing.overlaps(order):
                raise Conflict(
                    f"order {order.number} overlaps {existing.number} in {order.region}",
                    order=order.number,
                    against=existing.number,
                )
        self._orders.append(order)
        return order

    def in_force(self, region: str, day: date) -> SeasonOrder | None:
        """The order in force in a region on a day, if any is."""
        for order in self._orders:
            if order.region == region and order.covers(day):
                return order
        return None

    def limit_on(self, region: str, day: date, statutory: Pounds) -> Pounds:
        """The statutory limit as the orders of the day leave it."""
        order = self.in_force(region, day)
        return statutory if order is None else order.applied_to(statutory)

    def posted(self, region: str, day: date) -> bool:
        """True while a thaw order is cutting the limit in this region."""
        order = self.in_force(region, day)
        return order is not None and order.kind is SeasonKind.THAW

    def regions(self) -> tuple[str, ...]:
        return tuple(sorted({order.region for order in self._orders}))

    def orders_for(self, region: str) -> tuple[SeasonOrder, ...]:
        found = [order for order in self._orders if order.region == region]
        return tuple(sorted(found, key=lambda order: order.start))

    def __len__(self) -> int:
        return len(self._orders)

    def __repr__(self) -> str:
        return f"SeasonBook({len(self._orders)} orders)"
