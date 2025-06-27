"""The unit of work: one session, one transaction, and the counter inside it.

Everything that changes the database goes through this. A caller opens one,
does its work through the repositories it hands out, and commits. If it does
not commit, nothing happened, and an exception on the way out rolls back
without anybody having to remember to.

The number counter lives here rather than in the numbering package because
that is the whole point of it. A permit number and the permit that carries it
have to be written in one transaction. The counter reads its row with a lock,
increments it and writes it, all inside the caller's transaction, so a crash
between taking the number and writing the permit leaves the counter where it
was and nobody has to explain a missing number to accounting.

On SQLite the lock is the database write lock, which is coarse and correct. On
Postgres it is ``select for update`` on one row, which is what the office will
run in production and which is why the query is written that way rather than
with a read and a separate write.
"""

from __future__ import annotations

from datetime import date
from types import TracebackType

from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from ..domain.commodity import CommodityBook
from ..errors import Conflict
from ..permits.numbering import LAST_SEQUENCE, PermitNumber, Series
from .models.permits import CounterRow
from .repositories import (
    ApplicationRepository,
    CarrierRepository,
    CommodityRepository,
    NetworkRepository,
    PermitRepository,
    PilotRepository,
)


class DatabaseCounter:
    """The permit number counter, as a row read under a lock."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def take(self, year: int, series: Series) -> int:
        row = self._locked(year, series)
        if row is None:
            row = CounterRow(year=year, series=series.value, next_sequence=1)
            self.session.add(row)
            self.session.flush()
        if row.next_sequence > LAST_SEQUENCE:
            raise Conflict(f"the {series.value} series is full for {year}")
        taken = row.next_sequence
        row.next_sequence = taken + 1
        self.session.flush()
        return taken

    def peek(self, year: int, series: Series) -> int:
        row = self.session.get(CounterRow, (year, series.value))
        return 1 if row is None else row.next_sequence

    def issued(self, year: int, series: Series) -> int:
        return self.peek(year, series) - 1

    def _locked(self, year: int, series: Series) -> CounterRow | None:
        query = (
            select(CounterRow)
            .where(CounterRow.year == year, CounterRow.series == series.value)
            .with_for_update()
        )
        return self.session.scalars(query).one_or_none()

    def number(self, on: date, series: Series = Series.SINGLE) -> PermitNumber:
        return PermitNumber(on.year, series, self.take(on.year, series))


class UnitOfWork:
    """One session, the repositories over it, and the transaction around it."""

    def __init__(
        self,
        factory: sessionmaker[Session],
        commodities: CommodityBook | None = None,
    ) -> None:
        self._factory = factory
        self._given_commodities = commodities
        self.session: Session | None = None

    def __enter__(self) -> UnitOfWork:
        self.session = self._factory()
        return self

    def __exit__(
        self,
        kind: type[BaseException] | None,
        value: BaseException | None,
        trace: TracebackType | None,
    ) -> None:
        opened = self._require()
        if kind is not None:
            opened.rollback()
        opened.close()
        self.session = None

    def _require(self) -> Session:
        if self.session is None:
            raise Conflict("this unit of work is not open")
        return self.session

    @property
    def db(self) -> Session:
        """The open session, for the few callers that write rows directly.

        The audit trail is the reason this exists. Everything else goes
        through a repository, and a caller reaching for this to run a query
        is a caller that wants a repository method instead.
        """
        return self._require()

    @property
    def carriers(self) -> CarrierRepository:
        return CarrierRepository(self._require())

    @property
    def commodities(self) -> CommodityRepository:
        return CommodityRepository(self._require())

    @property
    def applications(self) -> ApplicationRepository:
        book = self._given_commodities or self.commodities.book()
        return ApplicationRepository(self._require(), book)

    @property
    def permits(self) -> PermitRepository:
        return PermitRepository(self._require())

    @property
    def network(self) -> NetworkRepository:
        return NetworkRepository(self._require())

    @property
    def pilots(self) -> PilotRepository:
        return PilotRepository(self._require())

    @property
    def counter(self) -> DatabaseCounter:
        return DatabaseCounter(self._require())

    def commit(self) -> None:
        self._require().commit()

    def rollback(self) -> None:
        self._require().rollback()

    def flush(self) -> None:
        self._require().flush()
