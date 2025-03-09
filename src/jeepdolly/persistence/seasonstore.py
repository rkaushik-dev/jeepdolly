"""Reading and writing seasonal orders, and building the book the router reads.

The season book is an in memory object with the overlap rule in it. This puts
rows behind it: filing an order runs the book's own check first, so the rule
that two orders may not overlap is enforced by the same code whether the order
arrives over the counter or out of a fixture.

The book is rebuilt for each request. There are perhaps thirty orders in a
year and the alternative is a cache that is stale on the one afternoon of the
year it matters, which is the afternoon the thaw order is signed.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..errors import NotOnFile
from ..seasons import SeasonBook, SeasonOrder
from .models.seasons import SeasonOrderRow


def book_from(session: Session) -> SeasonBook:
    """Every order on file, as the book the routing reads."""
    book = SeasonBook()
    query = select(SeasonOrderRow).order_by(SeasonOrderRow.starts, SeasonOrderRow.number)
    for row in session.scalars(query).all():
        book.file(row.to_domain())
    return book


def file_order(
    session: Session, order: SeasonOrder, signed_by: str = "", note: str = ""
) -> SeasonOrderRow:
    """File one order, refusing an overlap before anything is written."""
    book = book_from(session)
    book.file(order)
    row = SeasonOrderRow.of(order, signed_by=signed_by, note=note)
    session.add(row)
    return row


def row_for(session: Session, number: str) -> SeasonOrderRow:
    found = session.get(SeasonOrderRow, number)
    if found is None:
        raise NotOnFile(f"no order {number}", order=number)
    return found


def lift(session: Session, number: str, on: date) -> SeasonOrderRow:
    """End an order early. The row stays, with the day it lifted on it."""
    row = row_for(session, number)
    if on < row.starts:
        row.ends = row.starts
    else:
        row.ends = on
    return row


def in_force(session: Session, region: str, day: date) -> SeasonOrderRow | None:
    query = select(SeasonOrderRow).where(
        SeasonOrderRow.region == region,
        SeasonOrderRow.starts <= day,
        SeasonOrderRow.ends >= day,
    )
    return session.scalars(query).first()


def for_region(session: Session, region: str) -> tuple[SeasonOrderRow, ...]:
    query = (
        select(SeasonOrderRow)
        .where(SeasonOrderRow.region == region)
        .order_by(SeasonOrderRow.starts)
    )
    return tuple(session.scalars(query).all())
