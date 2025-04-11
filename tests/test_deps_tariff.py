"""Where the office gets its tables from, now that they are on rows."""

import io
from datetime import date

import pytest

from jeepdolly import logs
from jeepdolly.api.deps import office_from, schedules_for, sizes_for, statutes_for
from jeepdolly.domain.highway import HighwayClass
from jeepdolly.limits.classify import PermitKind
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars
from jeepdolly.persistence.base import create_all, memory_engine, session_factory
from jeepdolly.persistence.models import StatutoryRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_seeded_database

MARCH = date(2025, 3, 3)
APRIL = date(2025, 4, 8)


@pytest.fixture
def bare():
    engine = memory_engine()
    create_all(engine)
    return session_factory(engine)


@pytest.fixture
def filled():
    return a_seeded_database()


def test_the_office_reads_the_statutory_tables_off_the_rows(filled):
    with UnitOfWork(filled) as work:
        table = statutes_for(work.db).on(HighwayClass.ARTERIAL, MARCH)
        row = work.db.get(StatutoryRow, (HighwayClass.ARTERIAL, date(2024, 1, 1)))
        row.gross = Pounds(104000)
        work.flush()
        after = statutes_for(work.db).on(HighwayClass.ARTERIAL, MARCH)
    assert table.gross == Pounds(100000)
    assert after.gross == Pounds(104000)


def test_the_office_reads_the_size_tables_off_the_rows(filled):
    with UnitOfWork(filled) as work:
        book = sizes_for(work.db, MARCH)
    assert len(book) == 4
    assert book.on(HighwayClass.STATE_AID).routine.width.value == 168


def test_the_office_reads_the_fee_schedules_off_the_rows(filled):
    with UnitOfWork(filled) as work:
        schedule = schedules_for(work.db).on(MARCH)
    assert schedule.base_for(PermitKind.ORDINARY) == Dollars.of(40)


def test_a_bare_database_falls_back_to_the_coded_tables_and_says_so(bare):
    stream = io.StringIO()
    logs.configure(stream=stream)
    with UnitOfWork(bare) as work:
        table = statutes_for(work.db).on(HighwayClass.ARTERIAL, MARCH)
    assert table.gross == Pounds(100000)
    assert "tariff.unseeded table=statutory_limits" in stream.getvalue()


def test_the_fallback_covers_all_three_books(bare):
    stream = io.StringIO()
    logs.configure(stream=stream)
    with UnitOfWork(bare) as work:
        assert len(sizes_for(work.db, MARCH)) == 4
        assert len(schedules_for(work.db)) == 1
    written = stream.getvalue()
    assert "table=size_limits" in written
    assert "table=fee_schedules" in written


def test_the_whole_office_comes_up_on_a_seeded_database(filled):
    with UnitOfWork(filled) as work:
        desk = office_from(work, APRIL)
    assert len(desk.statutes) == 4
    assert len(desk.sizes) == 4
    assert len(desk.schedules) == 1


def test_a_change_to_a_row_changes_what_the_office_prices_with(filled):
    from jeepdolly.persistence.models import BaseFeeRow

    with UnitOfWork(filled) as work:
        row = work.db.get(BaseFeeRow, (date(2024, 1, 1), "ordinary"))
        row.amount = Dollars.of(55)
        work.flush()
        schedule = schedules_for(work.db).on(MARCH)
    assert schedule.base_for(PermitKind.ORDINARY) == Dollars.of(55)
