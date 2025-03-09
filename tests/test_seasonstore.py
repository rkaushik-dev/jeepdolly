"""Seasonal orders on rows, and the overlap rule enforced once."""

from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from jeepdolly.errors import Conflict, NotOnFile
from jeepdolly.measure import Pounds
from jeepdolly.persistence.models.seasons import SeasonOrderRow
from jeepdolly.persistence.seasonstore import (
    book_from,
    file_order,
    for_region,
    in_force,
    lift,
    row_for,
)
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.seasons import SeasonKind, SeasonOrder
from support import a_seeded_database

MARCH = date(2025, 3, 20)
APRIL = date(2025, 4, 8)
MAY = date(2025, 5, 2)


@pytest.fixture
def factory():
    return a_seeded_database()


def thaw(number="25-04", region="district 4", start=MARCH, end=MAY, share=65):
    return SeasonOrder(number, region, SeasonKind.THAW, start, end, share)


def test_an_order_goes_on_file_and_comes_back_the_same(factory):
    with UnitOfWork(factory) as work:
        file_order(work.db, thaw(), signed_by="R Pelletier", note="frost gauge at four feet")
        work.commit()
        row = row_for(work.db, "25-04")
    assert row.to_domain() == thaw()
    assert row.signed_by == "R Pelletier"
    assert repr(row) == "<SeasonOrderRow 25-04 district 4>"


def test_the_book_built_from_the_rows_reads_the_way_the_router_wants(factory):
    with UnitOfWork(factory) as work:
        file_order(work.db, thaw())
        work.commit()
        book = book_from(work.db)
    assert book.posted("district 4", APRIL)
    assert not book.posted("district 4", date(2025, 6, 1))
    assert book.limit_on("district 4", APRIL, Pounds(94000)) == Pounds(61100)


def test_two_overlapping_orders_in_one_region_are_refused_before_anything_is_written(factory):
    with UnitOfWork(factory) as work:
        file_order(work.db, thaw())
        work.commit()
        with pytest.raises(Conflict):
            file_order(work.db, thaw(number="25-05", start=APRIL, end=date(2025, 5, 20)))
        work.rollback()
        assert len(for_region(work.db, "district 4")) == 1


def test_the_same_span_in_another_region_is_a_different_order(factory):
    with UnitOfWork(factory) as work:
        file_order(work.db, thaw())
        file_order(work.db, thaw(number="25-06", region="district 3"))
        work.commit()
        assert len(for_region(work.db, "district 3")) == 1
        assert len(book_from(work.db)) == 2


def test_an_order_number_is_used_once(factory):
    with UnitOfWork(factory) as work:
        file_order(work.db, thaw())
        work.commit()
        with pytest.raises(Conflict):
            file_order(work.db, thaw(region="district 3"))


def test_what_is_in_force_is_asked_by_region_and_day(factory):
    with UnitOfWork(factory) as work:
        file_order(work.db, thaw())
        work.commit()
        assert in_force(work.db, "district 4", APRIL).number == "25-04"
        assert in_force(work.db, "district 4", date(2025, 6, 1)) is None
        assert in_force(work.db, "district 3", APRIL) is None


def test_an_order_lifted_early_keeps_its_row_and_gets_the_day_it_lifted(factory):
    with UnitOfWork(factory) as work:
        file_order(work.db, thaw())
        work.commit()
        lift(work.db, "25-04", APRIL)
        work.commit()
        row = row_for(work.db, "25-04")
        assert row.ends == APRIL
        assert in_force(work.db, "district 4", date(2025, 4, 20)) is None
        assert len(for_region(work.db, "district 4")) == 1


def test_lifting_an_order_before_it_started_ends_it_where_it_began(factory):
    with UnitOfWork(factory) as work:
        file_order(work.db, thaw())
        work.commit()
        lift(work.db, "25-04", date(2025, 1, 1))
        work.commit()
        assert row_for(work.db, "25-04").ends == MARCH


def test_an_order_nobody_signed_says_so(factory):
    with UnitOfWork(factory) as work, pytest.raises(NotOnFile):
        row_for(work.db, "25-99")


def test_a_row_the_domain_would_refuse_is_still_refused_by_the_database(factory):
    with UnitOfWork(factory) as work:
        work.db.add(
            SeasonOrderRow(
                number="25-09",
                region="district 4",
                kind=SeasonKind.THAW,
                starts=MAY,
                ends=MARCH,
                share=65,
            )
        )
        with pytest.raises(IntegrityError):
            work.commit()
