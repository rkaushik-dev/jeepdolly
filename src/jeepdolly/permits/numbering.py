"""Permit numbers, which have to be gapless and have to be unique.

A permit number is quoted on the phone, written on a receipt, read off a fax
by an enforcement officer at a weigh station, and reconciled against the bank
at the end of the month. So it has four properties and all four are load
bearing.

It is short enough to say out loud. Two digits of year, one letter of series,
six digits of sequence.

It carries the year, because the sequence starts again in January and a bare
sequence would collide within eighteen months.

It carries the series, because a single trip permit and an annual permit are
different documents with different rules and the office wants to know which one
it is holding before it looks anything up.

It is gapless. That is the hard one. Accounting reconciles the money against
the numbers issued, so a missing number is an hour of somebody's afternoon.
The counter here hands out one at a time under a lock, and the database
implementation later does the same thing with a locked row rather than a
mutex. Nothing anywhere is allowed to reserve a block and hand back what it
did not use.
"""

from __future__ import annotations

import re
import threading
from dataclasses import dataclass
from datetime import date
from enum import Enum

from ..errors import Conflict, Impossible, Unreadable

_NUMBER = re.compile(r"^(?P<year>\d{2})-(?P<series>[A-Z])-(?P<sequence>\d{6})$")

#: The sequence starts here so that a six digit number is always six digits.
FIRST_SEQUENCE = 1

#: Nothing this office issues will reach this in a year.
LAST_SEQUENCE = 999999


class Series(str, Enum):
    """Which document this number is for.

    The string mixin is here so that numbers sort, which the ledger needs.
    """

    SINGLE = "P"
    ANNUAL = "A"
    SUPERLOAD = "S"
    REPLACEMENT = "R"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, order=True)
class PermitNumber:
    """One issued number, which never changes and is never reused."""

    year: int
    series: Series
    sequence: int

    def __post_init__(self) -> None:
        if not 2000 <= self.year <= 2099:
            raise Impossible(f"{self.year} is outside the century this office numbers in")
        if not FIRST_SEQUENCE <= self.sequence <= LAST_SEQUENCE:
            raise Impossible(f"{self.sequence} is not a sequence number")

    @classmethod
    def parse(cls, text: str) -> PermitNumber:
        found = _NUMBER.match(text.strip())
        if not found:
            raise Unreadable(f"cannot read {text!r} as a permit number", written=text)
        return cls(
            2000 + int(found.group("year")),
            Series(found.group("series")),
            int(found.group("sequence")),
        )

    def __str__(self) -> str:
        return f"{self.year % 100:02d}-{self.series}-{self.sequence:06d}"

    def __repr__(self) -> str:
        return f"PermitNumber({self})"


class Counter:
    """Hands out the next sequence for a series and a year, one at a time."""

    def __init__(self, floors: dict[tuple[int, Series], int] | None = None) -> None:
        self._lock = threading.Lock()
        self._next: dict[tuple[int, Series], int] = dict(floors or {})

    def take(self, year: int, series: Series) -> int:
        with self._lock:
            key = (year, series)
            sequence = self._next.get(key, FIRST_SEQUENCE)
            if sequence > LAST_SEQUENCE:
                raise Conflict(f"the {series} series is full for {year}")
            self._next[key] = sequence + 1
            return sequence

    def peek(self, year: int, series: Series) -> int:
        return self._next.get((year, series), FIRST_SEQUENCE)

    def issued(self, year: int, series: Series) -> int:
        return self.peek(year, series) - FIRST_SEQUENCE


class Numbering:
    """The office numbering, which is a counter and the rules around it."""

    def __init__(self, counter: Counter | None = None) -> None:
        self.counter = counter or Counter()

    def next_number(self, on: date, series: Series = Series.SINGLE) -> PermitNumber:
        return PermitNumber(on.year, series, self.counter.take(on.year, series))

    def replacement_for(self, number: PermitNumber, on: date) -> PermitNumber:
        """A reissued permit takes a new number in the replacement series."""
        if number.series is Series.REPLACEMENT:
            raise Impossible(f"{number} is already a replacement")
        return self.next_number(on, Series.REPLACEMENT)

    def issued_in(self, year: int, series: Series = Series.SINGLE) -> int:
        return self.counter.issued(year, series)
