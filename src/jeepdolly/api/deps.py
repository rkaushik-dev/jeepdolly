"""What a route is given: a unit of work, and an office built from it.

The unit of work is per request and it is closed when the request ends,
committed only if the route asks. Nothing commits on the way out by accident.

The office is assembled from the database on every request, which is honest
and slow. The road log is twelve thousand segments in production and reading
all of them to answer one route check is not something to be proud of. It is
correct, it is simple, and the shape of the fix is already clear: the reference
data changes twice a year and belongs behind a cache keyed on the day. That is
a later change and it wants a measurement in front of it, not a guess.

What is deliberately not cached is the restriction book and the carrier
record. Those change during the day, and a permit written against yesterday's
closure is exactly the failure this office cannot have.
"""

from __future__ import annotations

from collections.abc import Iterator
from datetime import date

from fastapi import Depends, Request
from sqlalchemy.orm import Session, sessionmaker

from ..conditions.library import standard_library
from ..domain.carrier import CarrierRegister
from ..domain.network import Network, Node
from ..domain.restriction import Curfew, Restriction, RestrictionBook
from ..fees.schedule import standard_book as standard_schedules
from ..limits.size import standard_book as standard_sizes
from ..limits.statutory import standard_book as standard_statutes
from ..permits.issue import Office
from ..permits.numbering import Numbering
from ..persistence.models.carriers import CarrierRow
from ..persistence.models.network import NodeRow, RestrictionRow, SegmentRow
from ..persistence.models.structures import StructureRow
from ..persistence.unitofwork import UnitOfWork
from ..seasons import SeasonBook
from ..structures.inventory import StructureBook


def sessions(request: Request) -> sessionmaker[Session]:
    """The session factory the application was built with."""
    factory: sessionmaker[Session] = request.app.state.sessions
    return factory


def unit_of_work(factory: sessionmaker[Session] = Depends(sessions)) -> Iterator[UnitOfWork]:
    """One unit of work per request, closed on the way out."""
    with UnitOfWork(factory) as work:
        yield work


def network_from(session: Session) -> Network:
    network = Network()
    for node in session.query(NodeRow).all():
        network.add_node(Node(node.name, node.town))
    for segment in session.query(SegmentRow).all():
        network.add_segment(segment.to_domain())
    return network


def restrictions_from(session: Session) -> RestrictionBook:
    book = RestrictionBook()
    for row in session.query(RestrictionRow).all():
        book.add(_restriction(row))
    return book


def _restriction(row: RestrictionRow) -> Restriction:
    curfew = None
    if row.curfew_days and row.curfew_from and row.curfew_to:
        days = frozenset(int(one) for one in row.curfew_days)
        curfew = Curfew(days, row.curfew_from, row.curfew_to)
    return Restriction(
        ident=row.ident,
        segment=row.segment_ident,
        kind=row.kind,
        note=row.note,
        height=row.height,
        width=row.width,
        weight=row.weight,
        region=row.region,
        curfew=curfew,
        effective=row.effective,
        expires=row.expires,
    )


def structures_from(session: Session) -> StructureBook:
    book = StructureBook()
    for row in session.query(StructureRow).all():
        book.add(row.to_domain())
    return book


def carriers_from(session: Session, on: date) -> CarrierRegister:
    register = CarrierRegister()
    for row in session.query(CarrierRow).all():
        register.add(row.to_domain(on))
    return register


def office_from(work: UnitOfWork, on: date | None = None) -> Office:
    """Assemble the office from what is on file today."""
    session = work.session
    if session is None:  # pragma: no cover - guarded by the unit of work
        raise RuntimeError("the unit of work is closed")
    day = on or date.today()
    return Office(
        network=network_from(session),
        restrictions=restrictions_from(session),
        seasons=SeasonBook(),
        statutes=standard_statutes(),
        sizes=standard_sizes(),
        commodities=work.commodities.book(day),
        carriers=carriers_from(session, day),
        structures=structures_from(session),
        schedules=standard_schedules(),
        library=standard_library(),
        numbering=Numbering(),
    )


def office(work: UnitOfWork = Depends(unit_of_work)) -> Office:
    """The office, for a route that needs to decide something."""
    return office_from(work)
