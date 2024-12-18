"""The unit of work, and the counter that runs inside its transaction."""

from datetime import date

import pytest

from jeepdolly.domain.commodity import standard_book as commodities
from jeepdolly.errors import Conflict, NotOnFile
from jeepdolly.permits.numbering import Series
from jeepdolly.persistence.base import create_all, memory_engine, session_factory
from jeepdolly.persistence.models import CarrierRow, CommodityRow, CounterRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_carrier_register, an_application

BOOK = commodities()
FILED = date(2025, 4, 7)


@pytest.fixture
def factory():
    engine = memory_engine()
    create_all(engine)
    made = session_factory(engine)
    with made() as opened:
        for code in BOOK.codes():
            opened.add(CommodityRow.of(BOOK.get(code)))
        register = a_carrier_register()
        for usdot in (1204471, 2299001):
            opened.add(CarrierRow.of(register.get(usdot)))
        opened.commit()
    return made


def test_work_that_is_committed_is_there_afterwards(factory):
    with UnitOfWork(factory, BOOK) as work:
        work.applications.add(an_application())
        work.commit()
    with UnitOfWork(factory, BOOK) as work:
        assert work.applications.get("25-04471").origin == "waterville"


def test_work_that_is_not_committed_never_happened(factory):
    with UnitOfWork(factory, BOOK) as work:
        work.applications.add(an_application())
    with UnitOfWork(factory, BOOK) as work, pytest.raises(NotOnFile):
        work.applications.get("25-04471")


def test_an_exception_on_the_way_out_rolls_back_without_being_asked(factory):
    with pytest.raises(ValueError), UnitOfWork(factory, BOOK) as work:
        work.applications.add(an_application())
        work.flush()
        raise ValueError("something went wrong halfway through")
    with UnitOfWork(factory, BOOK) as work, pytest.raises(NotOnFile):
        work.applications.get("25-04471")


def test_a_rollback_leaves_the_work_open_for_more(factory):
    with UnitOfWork(factory, BOOK) as work:
        work.applications.add(an_application(reference="25-00001"))
        work.rollback()
        work.applications.add(an_application(reference="25-00002"))
        work.commit()
    with UnitOfWork(factory, BOOK) as work:
        assert work.applications.get("25-00002")
    with UnitOfWork(factory, BOOK) as work, pytest.raises(NotOnFile):
        work.applications.get("25-00001")


def test_a_unit_of_work_that_is_not_open_refuses_to_hand_out_anything(factory):
    work = UnitOfWork(factory, BOOK)
    with pytest.raises(Conflict):
        work.applications


def test_the_repositories_all_come_off_the_same_session(factory):
    with UnitOfWork(factory, BOOK) as work:
        assert work.carriers.session is work.session
        assert work.permits.session is work.session
        assert work.network.session is work.session
        assert work.commodities.session is work.session


def test_the_commodity_book_is_read_from_the_database_when_none_was_given(factory):
    with UnitOfWork(factory) as work:
        assert len(work.applications.commodities) == len(BOOK)


def test_the_counter_starts_at_one_and_climbs(factory):
    with UnitOfWork(factory, BOOK) as work:
        assert work.counter.take(2025, Series.SINGLE) == 1
        assert work.counter.take(2025, Series.SINGLE) == 2
        assert work.counter.peek(2025, Series.SINGLE) == 3
        assert work.counter.issued(2025, Series.SINGLE) == 2
        work.commit()


def test_each_series_counts_on_its_own(factory):
    with UnitOfWork(factory, BOOK) as work:
        work.counter.take(2025, Series.SINGLE)
        assert work.counter.take(2025, Series.SUPERLOAD) == 1
        assert work.counter.take(2026, Series.SINGLE) == 1
        work.commit()


def test_a_number_taken_and_not_committed_is_handed_out_again(factory):
    with UnitOfWork(factory, BOOK) as work:
        assert work.counter.take(2025, Series.SINGLE) == 1
    with UnitOfWork(factory, BOOK) as work:
        assert work.counter.take(2025, Series.SINGLE) == 1
        work.commit()
    with UnitOfWork(factory, BOOK) as work:
        assert work.counter.take(2025, Series.SINGLE) == 2
        work.commit()


def test_the_counter_builds_a_whole_permit_number_for_a_day(factory):
    with UnitOfWork(factory, BOOK) as work:
        number = work.counter.number(FILED)
        assert str(number) == "25-P-000001"
        assert str(work.counter.number(FILED, Series.SUPERLOAD)) == "25-S-000001"
        work.commit()


def test_a_full_series_refuses_rather_than_wrapping(factory):
    with UnitOfWork(factory, BOOK) as work:
        work.counter.take(2025, Series.SINGLE)
        row = work.session.get(CounterRow, (2025, "P"))
        row.next_sequence = 1000000
        work.flush()
        with pytest.raises(Conflict):
            work.counter.take(2025, Series.SINGLE)
