"""Holding the slow books between requests, and letting go of them safely.

The office is assembled from the database on every request. That was written
down as honest and slow in the dependency module a long time ago, with the
shape of the fix already clear: the reference data changes twice a year and
belongs behind a cache keyed on the day. This is that fix, and the reason it
is here rather than in the api package is that the command line wants it too.

Which books are in and which are out is the whole of the design.

In are the road log, the structure inventory, the commodity table, the size
tables, the statutory tables, the fee schedules and the condition library.
Every one of them is published, changes on a schedule, and is read whole to
answer one question. The road log alone is twelve thousand segments in
production.

Out are the restrictions, the carriers, the seasonal orders and the
enforcement accounts. Every one of those changes during the day, and a permit
written against yesterday's closure is exactly the failure this office cannot
have. They are read fresh every time and always will be.

Correctness comes from two things and not from a clock.

The key is the day, because the size and fee tables differ by day and a cache
that ignored that would price a Tuesday move off Monday's schedule.

The invalidation is a flush hook rather than an expiry. Any flush that touched
a watched table empties the whole cache, for every day, immediately and in the
same process. That is blunt and it is right: the alternative is a cache that
serves a road log without the segment somebody added thirty seconds ago, and
an office that cannot see its own edits is worse than a slow one.

What this does not solve is a second process. A road log loaded by the command
line does not empty the web process's cache, and the web process will serve
the old log until it is restarted or something local writes. That is a known
shortcoming, it is written on the operations card, and the fix when it is
needed is a version row rather than a longer expiry.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from sqlalchemy import event
from sqlalchemy.orm import Session, sessionmaker

from ..conditions.library import ConditionLibrary, standard_library
from ..domain.commodity import CommodityBook
from ..domain.network import Network
from ..fees.schedule import ScheduleBook
from ..limits.size import SizeBook
from ..limits.statutory import StatutoryBook
from ..structures.inventory import StructureBook
from ..utilities.lines import LineBook
from .books import lines_from, network_from, structures_from
from .models.network import NodeRow, SegmentRow
from .models.reference import CommodityRow, ConditionTextRow
from .models.structures import StructureRow
from .models.tariff import (
    BaseFeeRow,
    MileFeeRow,
    ScheduleRow,
    SizeRow,
    StatutoryRow,
    WeightStepRow,
)
from .models.utilities import LineCrossingRow, LineOwnerRow
from .repositories import CommodityRepository
from .tariffstore import schedule_book, size_book, statutory_book

#: Writing any of these empties the cache. Reading none of them leaves it.
WATCHED: tuple[type, ...] = (
    NodeRow,
    SegmentRow,
    StructureRow,
    CommodityRow,
    ConditionTextRow,
    LineCrossingRow,
    LineOwnerRow,
    BaseFeeRow,
    MileFeeRow,
    ScheduleRow,
    SizeRow,
    StatutoryRow,
    WeightStepRow,
)


@dataclass(frozen=True)
class Reference:
    """The published books, as they stood on one day."""

    day: date
    network: Network
    structures: StructureBook
    commodities: CommodityBook
    sizes: SizeBook
    statutes: StatutoryBook
    schedules: ScheduleBook
    library: ConditionLibrary
    lines: LineBook


def read(session: Session, day: date) -> Reference:
    """Build every published book off the rows, which is the slow path."""
    return Reference(
        day=day,
        network=network_from(session),
        structures=structures_from(session),
        commodities=CommodityRepository(session).book(day),
        sizes=size_book(session, day),
        statutes=statutory_book(session),
        schedules=schedule_book(session),
        library=standard_library(),
        lines=lines_from(session),
    )


class ReferenceCache:
    """One cache per process, keyed on the day, emptied by any watched write."""

    def __init__(self) -> None:
        self._books: dict[date, Reference] = {}
        self.hits = 0
        self.misses = 0
        self.emptied = 0

    def books(self, session: Session, day: date) -> Reference:
        found = self._books.get(day)
        if found is not None:
            self.hits += 1
            return found
        self.misses += 1
        built = read(session, day)
        self._books[day] = built
        return built

    def clear(self) -> None:
        if self._books:
            self.emptied += 1
        self._books.clear()

    def days(self) -> tuple[date, ...]:
        return tuple(sorted(self._books))

    def __len__(self) -> int:
        return len(self._books)


def touched(session: Session) -> bool:
    """Whether this flush is about to change a published table.

    Deletes count and so do updates. An application row moving to submitted
    is not a published change and this says so, which is the whole reason the
    watched list is a list rather than everything.
    """
    changing = list(session.new) + list(session.dirty) + list(session.deleted)
    return any(isinstance(one, WATCHED) for one in changing)


def watch(factory: sessionmaker[Session], cache: ReferenceCache) -> None:
    """Empty the cache after any flush that touched a published table.

    The hook fires after the flush rather than before it, so a flush that
    fails on a constraint leaves the cache alone. It fires on the flush and
    not on the commit because a route reads the office again inside the same
    transaction, and waiting for the commit would serve it stale books it
    wrote itself.
    """

    @event.listens_for(factory, "after_flush")
    def _empty(session: Session, context: object) -> None:
        if touched(session):
            cache.clear()
