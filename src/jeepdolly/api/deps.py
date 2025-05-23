"""What a route is given: a unit of work, and an office built from it.

The unit of work is per request and it is closed when the request ends,
committed only if the route asks. Nothing commits on the way out by accident.

The office is assembled from the database on every request, which is honest
and slow. The road log is twelve thousand segments in production and reading
all of them to answer one route check is not something to be proud of. It is
correct, it is simple, and the shape of the fix is already clear: the reference
data changes twice a year and belongs behind a cache keyed on the day. That is
a later change and it wants a measurement in front of it, not a guess.

What is deliberately not cached is the restriction book, the carrier record
and the seasonal orders. Those change during the day, and a permit written
against yesterday's closure is exactly the failure this office cannot have.

The reading itself is not here. Turning rows into a Network or a carrier
register wants a session and nothing else, so it lives in the persistence
layer where a command line report can reach it without importing a web
framework. What is here is the choosing: which day, and what to do when a
published table has not been seeded yet.
"""

from __future__ import annotations

from collections.abc import Iterator
from datetime import date

from fastapi import Depends, Request
from sqlalchemy.orm import Session, sessionmaker

from .. import logs
from ..conditions.library import standard_library
from ..fees.schedule import ScheduleBook
from ..fees.schedule import standard_book as coded_schedules
from ..limits.size import SizeBook
from ..limits.size import standard_book as coded_sizes
from ..limits.statutory import StatutoryBook
from ..limits.statutory import standard_book as coded_statutes
from ..permits.numbering import Numbering
from ..permits.office import Office
from ..persistence.books import (
    carriers_from,
    network_from,
    restrictions_from,
    structures_from,
)
from ..persistence.seasonstore import book_from
from ..persistence.tariffstore import schedule_book, size_book, statutory_book
from ..persistence.unitofwork import UnitOfWork
from ..routing.turns import coded_corners


def sessions(request: Request) -> sessionmaker[Session]:
    """The session factory the application was built with."""
    factory: sessionmaker[Session] = request.app.state.sessions
    return factory


def unit_of_work(factory: sessionmaker[Session] = Depends(sessions)) -> Iterator[UnitOfWork]:
    """One unit of work per request, closed on the way out."""
    with UnitOfWork(factory) as work:
        yield work


def statutes_for(session: Session) -> StatutoryBook:
    """The statutory tables off the rows, or the coded ones on a bare database.

    The fallback is not a convenience. A database that has not been seeded is
    a database somebody is about to seed, and refusing every request until
    they do would mean the office cannot answer a telephone call during an
    install. It says so on the log, once per request, which is loud enough.
    """
    book = statutory_book(session)
    if len(book):
        return book
    logs.trouble("tariff.unseeded", table="statutory_limits")
    return coded_statutes()


def sizes_for(session: Session, on: date) -> SizeBook:
    """The size tables off the rows, or the coded ones on a bare database."""
    book = size_book(session, on)
    if len(book):
        return book
    logs.trouble("tariff.unseeded", table="size_limits")
    return coded_sizes()


def schedules_for(session: Session) -> ScheduleBook:
    """The fee schedules off the rows, or the coded ones on a bare database."""
    book = schedule_book(session)
    if len(book):
        return book
    logs.trouble("tariff.unseeded", table="fee_schedules")
    return coded_schedules()


def office_from(work: UnitOfWork, on: date | None = None) -> Office:
    """Assemble the office from what is on file today."""
    session = work.session
    if session is None:  # pragma: no cover - guarded by the unit of work
        raise RuntimeError("the unit of work is closed")
    day = on or date.today()
    return Office(
        network=network_from(session),
        restrictions=restrictions_from(session),
        seasons=book_from(session),
        statutes=statutes_for(session),
        sizes=sizes_for(session, day),
        commodities=work.commodities.book(day),
        carriers=carriers_from(session, day),
        structures=structures_from(session),
        # The card index has never been anywhere but the code. It moves
        # perhaps twice a year and nobody has asked to edit it on a screen.
        turns=coded_corners(),
        schedules=schedules_for(session),
        library=standard_library(),
        # The counter is the one in the database, inside this transaction, so
        # a number taken and not committed is handed out again.
        numbering=Numbering(work.counter),
    )


def office(work: UnitOfWork = Depends(unit_of_work)) -> Office:
    """The office, for a route that needs to decide something."""
    return office_from(work)
