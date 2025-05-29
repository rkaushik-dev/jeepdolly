"""Building the domain books off the rows, which nothing but a session needs.

These lived in the API dependency module for a long time, on the argument
that the API was the only thing that assembled an office. That stopped being
true the first time a command line report wanted the road log, and importing
a FastAPI module to get a Network is the kind of dependency that only ever
gets worse.

So they are here, in the persistence layer, where a caller with a session and
no web framework can have them. The API dependency module still owns the
choosing: which day, which fallbacks, what to do about an unseeded database.
This file owns only the reading.

None of these are cached. The reasoning is in the dependency module and it has
not changed: the reference data is slow and correct, and the live data must be
read every time or the office writes permits against yesterday.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy.orm import Session

from ..domain.carrier import CarrierRegister
from ..domain.network import Network, Node
from ..domain.restriction import Curfew, Restriction, RestrictionBook
from ..escorts.register import EscortRegister
from ..structures.inventory import StructureBook
from .models.carriers import CarrierRow
from .models.escorts import PilotCardRow, PilotCompanyRow
from .models.network import NodeRow, RestrictionRow, SegmentRow
from .models.structures import StructureRow


def network_from(session: Session) -> Network:
    """The whole road log, nodes first so the segments have ends to join."""
    network = Network()
    for node in session.query(NodeRow).all():
        network.add_node(Node(node.name, node.town))
    for segment in session.query(SegmentRow).all():
        network.add_segment(segment.to_domain())
    return network


def restrictions_from(session: Session) -> RestrictionBook:
    """Every restriction on file, expired ones included.

    The book filters by day when it is asked, not when it is built, because
    the same book answers questions about a trip next month.
    """
    book = RestrictionBook()
    for row in session.query(RestrictionRow).all():
        book.add(restriction_from(row))
    return book


def restriction_from(row: RestrictionRow) -> Restriction:
    """One restriction row, with its curfew reassembled if it has one."""
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
    """Every structure on file, postings and ratings as they stand."""
    book = StructureBook()
    for row in session.query(StructureRow).all():
        book.add(row.to_domain())
    return book


def carriers_from(session: Session, on: date) -> CarrierRegister:
    """Every carrier, with insurance and bond read as at one day.

    The day matters. A carrier row carries the certificate dates and the
    domain object carries whether they are good, so the same rows produce a
    different register for a trip in August than for one in December.
    """
    register = CarrierRegister()
    for row in session.query(CarrierRow).all():
        register.add(row.to_domain(on))
    return register


def escorts_from(session: Session) -> EscortRegister:
    """The pilot car register, companies first so the cards have somewhere to go.

    A card whose company has been deleted out from under it is dropped rather
    than raised. The foreign key means that should not happen, and a register
    that refuses to build at all because of one orphan row would take the
    whole office down over a piece of housekeeping.
    """
    register = EscortRegister()
    for row in session.query(PilotCompanyRow).all():
        register.add_company(row.to_domain())
    for card in session.query(PilotCardRow).all():
        operator = card.to_domain()
        if operator.company not in {one.ident for one in register.companies()}:
            continue
        register.certify(operator)
    return register
