"""The statutory weight tables, one per class of road.

These are the numbers a truck may carry with no permit at all. Everything the
permit office does is measured from them: an application is overweight if it
is over one of these, the fee is priced on how far over, and a route is chosen
partly on which table reaches it.

The tables are data and they are versioned by the date they took effect,
because a permit issued in March is judged by the March table even if it is
argued about in September. Nothing in this file is written as a constant in
somebody's function.

The interstate table is the federal one and the state cannot raise it. The
others are the state's own and they are higher, which is the usual surprise:
a six axle combination that is legal on US 2 becomes overweight the moment it
turns onto I-95.

A group of more axles than the table names is read as the largest group it
does name plus an allowance for each axle past it. That is how the published
table reads and it is why a nine axle trailer does not get its own line.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from ..domain.highway import HighwayClass
from ..errors import Impossible, NotOnFile
from ..measure import Pounds


@dataclass(frozen=True)
class StatutoryTable:
    """One class of road, one set of numbers, one effective date."""

    highway_class: HighwayClass
    effective: date
    by_count: dict[int, Pounds]
    beyond_table: Pounds
    gross: Pounds
    tire_rate: int

    def __post_init__(self) -> None:
        if 1 not in self.by_count or 2 not in self.by_count:
            raise Impossible("a table names at least the single and the tandem")
        counts = sorted(self.by_count)
        for smaller, larger in zip(counts, counts[1:], strict=False):
            if self.by_count[larger] <= self.by_count[smaller]:
                raise Impossible(f"the {larger} axle limit is not above the {smaller} one")
        if self.tire_rate < 1:
            raise Impossible("a tire rate is pounds per inch and it is not nothing")

    @property
    def largest_named(self) -> int:
        return max(self.by_count)

    def group_limit(self, count: int) -> Pounds:
        """The limit for a group of this many axles."""
        if count < 1:
            raise Impossible("a group holds at least one axle")
        if count in self.by_count:
            return self.by_count[count]
        largest = self.largest_named
        return self.by_count[largest] + self.beyond_table * (count - largest)

    def over_group(self, count: int, weight: Pounds) -> Pounds:
        return weight.over(self.group_limit(count))

    def over_gross(self, weight: Pounds) -> Pounds:
        return weight.over(self.gross)

    def __str__(self) -> str:
        return f"{self.highway_class} table of {self.effective.isoformat()}"


class StatutoryBook:
    """Every table on file, asked for by road class and by date."""

    def __init__(self) -> None:
        self._tables: list[StatutoryTable] = []

    def file(self, table: StatutoryTable) -> StatutoryTable:
        for existing in self._tables:
            if (
                existing.highway_class is table.highway_class
                and existing.effective == table.effective
            ):
                raise Impossible(f"a {table.highway_class} table already takes effect that day")
        self._tables.append(table)
        return table

    def on(self, highway_class: HighwayClass, day: date) -> StatutoryTable:
        """The table in force for a class of road on a day."""
        candidates = [
            table
            for table in self._tables
            if table.highway_class is highway_class and table.effective <= day
        ]
        if not candidates:
            raise NotOnFile(
                f"no {highway_class} table in force on {day.isoformat()}",
                highway_class=str(highway_class),
            )
        return max(candidates, key=lambda table: table.effective)

    def classes(self) -> tuple[HighwayClass, ...]:
        return tuple(sorted({table.highway_class for table in self._tables}, key=str))

    def __len__(self) -> int:
        return len(self._tables)


def _table(
    highway_class: HighwayClass,
    single: int,
    tandem: int,
    tridem: int,
    quad: int,
    beyond: int,
    gross: int,
    tire_rate: int,
    effective: date,
) -> StatutoryTable:
    return StatutoryTable(
        highway_class=highway_class,
        effective=effective,
        by_count={
            1: Pounds(single),
            2: Pounds(tandem),
            3: Pounds(tridem),
            4: Pounds(quad),
        },
        beyond_table=Pounds(beyond),
        gross=Pounds(gross),
        tire_rate=tire_rate,
    )


def standard_book(effective: date = date(2024, 1, 1)) -> StatutoryBook:
    """The tables as they stand, which is what the counter reads today."""
    book = StatutoryBook()
    book.file(
        _table(HighwayClass.INTERSTATE, 20000, 34000, 42500, 50000, 6000, 80000, 500, effective)
    )
    book.file(
        _table(HighwayClass.ARTERIAL, 22400, 41000, 48000, 56000, 7000, 100000, 600, effective)
    )
    book.file(
        _table(HighwayClass.STATE_AID, 22400, 38000, 44000, 50000, 6000, 94000, 600, effective)
    )
    book.file(
        _table(HighwayClass.TOWN_WAY, 20000, 34000, 42000, 48000, 6000, 80000, 500, effective)
    )
    return book
