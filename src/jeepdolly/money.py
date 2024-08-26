"""Money, in whole cents, and the two ways it gets divided.

A permit fee is a small number of dollars and it still has to be exact, because
the office reconciles what it took in against what it wrote every night and a
cent adrift is a night somebody spends looking for it.

So money is an integer count of cents. No float ever holds a fee, not even for
a moment, and there is no constructor that takes one.

Two operations here are not obvious and both are about division.

``split`` divides a fee among several payers so that the parts add back to the
whole. The remainder goes to the earliest parts, one cent each, which is the
convention the accounting side already uses for its own splits. A fee of
``$10.00`` split three ways is ``$3.34``, ``$3.33``, ``$3.33``.

``percent`` takes a whole percent of an amount and rounds half up, which is
what the fee schedule means by a ten percent surcharge. Rounding down would let
a hauler shave a cent by asking for two permits instead of one.
"""

from __future__ import annotations

import re
from collections.abc import Iterable
from dataclasses import dataclass

from .errors import Impossible, Unreadable

CENTS_PER_DOLLAR = 100

_AMOUNT = re.compile(r"^\s*(?P<sign>-)?\$?\s*(?P<dollars>[\d,]+)(?:\.(?P<cents>\d{2}))?\s*$")


@dataclass(frozen=True, order=True)
class Dollars:
    """An amount of money, held as whole cents."""

    cents: int

    def __post_init__(self) -> None:
        if not isinstance(self.cents, int) or isinstance(self.cents, bool):
            raise TypeError(f"money is a whole number of cents, not {self.cents!r}")

    @classmethod
    def of(cls, dollars: int, cents: int = 0) -> Dollars:
        return cls(dollars * CENTS_PER_DOLLAR + cents)

    @classmethod
    def parse(cls, text: str) -> Dollars:
        """Read an amount off a schedule or a remittance."""
        found = _AMOUNT.match(text)
        if not found:
            raise Unreadable(f"cannot read {text!r} as an amount", written=text)
        dollars = int(found.group("dollars").replace(",", ""))
        cents = int(found.group("cents") or 0)
        total = dollars * CENTS_PER_DOLLAR + cents
        return cls(-total if found.group("sign") else total)

    @property
    def whole_dollars(self) -> int:
        """Dollars, rounded toward zero, for a line that prints no cents."""
        return int(self.cents / CENTS_PER_DOLLAR)

    def percent(self, share: int) -> Dollars:
        """A whole percent of this amount, rounded half up."""
        if share < 0:
            raise Impossible("a share of an amount is not negative")
        return Dollars((self.cents * share + 50) // 100)

    def split(self, ways: int) -> tuple[Dollars, ...]:
        """Divide into parts that add back to the whole, biggest first."""
        if ways < 1:
            raise Impossible("an amount is split at least one way")
        base, remainder = divmod(self.cents, ways)
        return tuple(Dollars(base + (1 if index < remainder else 0)) for index in range(ways))

    def __add__(self, other: Dollars) -> Dollars:
        if not isinstance(other, Dollars):
            return NotImplemented
        return Dollars(self.cents + other.cents)

    def __sub__(self, other: Dollars) -> Dollars:
        if not isinstance(other, Dollars):
            return NotImplemented
        return Dollars(self.cents - other.cents)

    def __mul__(self, count: int) -> Dollars:
        if not isinstance(count, int) or isinstance(count, bool):
            return NotImplemented
        return Dollars(self.cents * count)

    def __neg__(self) -> Dollars:
        return Dollars(-self.cents)

    def __str__(self) -> str:
        sign = "-" if self.cents < 0 else ""
        dollars, cents = divmod(abs(self.cents), CENTS_PER_DOLLAR)
        return f"{sign}${dollars:,}.{cents:02d}"

    def __repr__(self) -> str:
        return f"Dollars({self.cents})"


ZERO = Dollars(0)


def total(amounts: Iterable[Dollars]) -> Dollars:
    """Add up an iterable of amounts, which is done often enough to name."""
    running = ZERO
    for amount in amounts:
        running = running + amount
    return running
