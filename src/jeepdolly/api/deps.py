"""What a route is given: a unit of work, and an office built from it.

The unit of work is per request and it is closed when the request ends,
committed only if the route asks. Nothing commits on the way out by accident.

The office is assembled per request, but not from scratch. The published
books come out of a cache keyed on the day, which is the fix this docstring
used to describe as a later change. The cache is emptied by any flush that
touches a published table, so an office cannot serve a road log without a
segment somebody added a moment ago.

What is deliberately not cached is the restriction book, the carrier record
and the seasonal orders. Those change during the day, and a permit written
against yesterday's closure is exactly the failure this office cannot have.

The reading itself is not here. Turning rows into a Network or a carrier
register wants a session and nothing else, so it lives in the persistence
layer where a command line report can reach it without importing a web
framework. What is here is the choosing: which day, and which cache.
"""

from __future__ import annotations

from collections.abc import Iterator
from datetime import date

from fastapi import Depends, Request
from sqlalchemy.orm import Session, sessionmaker

from ..permits.numbering import Numbering
from ..permits.office import Office
from ..persistence.books import (
    carriers_from,
    enforcement_from,
    escorts_from,
    restrictions_from,
)
from ..persistence.reference import ReferenceCache
from ..persistence.reference import read as read_reference
from ..persistence.seasonstore import book_from
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


def office_from(
    work: UnitOfWork,
    on: date | None = None,
    cache: ReferenceCache | None = None,
) -> Office:
    """Assemble the office from what is on file today.

    The published books come out of the cache when there is one and are read
    fresh when there is not. Everything else is read every time, whether or
    not a cache was passed, because those are the books that change during the
    day and a permit written against yesterday's closure is the one failure
    this office cannot have.
    """
    session = work.session
    if session is None:  # pragma: no cover - guarded by the unit of work
        raise RuntimeError("the unit of work is closed")
    day = on or date.today()
    published = cache.books(session, day) if cache is not None else read_reference(session, day)
    return Office(
        network=published.network,
        restrictions=restrictions_from(session),
        seasons=book_from(session),
        statutes=published.statutes,
        sizes=published.sizes,
        commodities=published.commodities,
        carriers=carriers_from(session, day),
        structures=published.structures,
        # The card index has never been anywhere but the code. It moves
        # perhaps twice a year and nobody has asked to edit it on a screen.
        turns=coded_corners(),
        escorts=escorts_from(session),
        enforcement=enforcement_from(session),
        lines=published.lines,
        schedules=published.schedules,
        library=published.library,
        # The counter is the one in the database, inside this transaction, so
        # a number taken and not committed is handed out again.
        numbering=Numbering(work.counter),
    )


def reference_cache(request: Request) -> ReferenceCache | None:
    """The published book cache this application was built with, if any."""
    found: ReferenceCache | None = getattr(request.app.state, "reference", None)
    return found


def office(
    work: UnitOfWork = Depends(unit_of_work),
    cache: ReferenceCache | None = Depends(reference_cache),
) -> Office:
    """The office, for a route that needs to decide something."""
    return office_from(work, cache=cache)
