"""The published tables on rows, and the seed that puts today's numbers in."""

from datetime import date

import pytest

from jeepdolly.domain.highway import HighwayClass
from jeepdolly.errors import Conflict
from jeepdolly.limits.classify import PermitKind
from jeepdolly.measure import Inches, Pounds
from jeepdolly.money import Dollars
from jeepdolly.persistence.models import StatutoryRow
from jeepdolly.persistence.tariffstore import (
    FIRST,
    schedule_book,
    seed,
    seeded,
    size_book,
    statutory_book,
)
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_seeded_database

MARCH = date(2025, 3, 3)


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def filled(factory):
    with UnitOfWork(factory) as work:
        seed(work.db)
        work.commit()
    return factory


def test_a_fresh_database_has_no_tables_on_it(factory):
    with UnitOfWork(factory) as work:
        assert not seeded(work.db)


def test_seeding_writes_a_table_for_every_class_of_road(filled):
    with UnitOfWork(filled) as work:
        assert seeded(work.db)
        assert len(work.db.query(StatutoryRow).all()) == 4


def test_seeding_twice_is_refused(filled):
    with UnitOfWork(filled) as work, pytest.raises(Conflict):
        seed(work.db)


def test_the_statutory_book_reads_back_the_numbers_that_went_in(filled):
    with UnitOfWork(filled) as work:
        table = statutory_book(work.db).on(HighwayClass.ARTERIAL, MARCH)
    assert table.group_limit(1) == Pounds(22400)
    assert table.group_limit(2) == Pounds(41000)
    assert table.group_limit(5) == Pounds(63000)
    assert table.gross == Pounds(100000)
    assert table.tire_rate == 600


def test_the_interstate_table_is_still_the_federal_one(filled):
    with UnitOfWork(filled) as work:
        table = statutory_book(work.db).on(HighwayClass.INTERSTATE, MARCH)
    assert table.gross == Pounds(80000)
    assert table.effective == FIRST


def test_the_size_book_reads_back_both_boxes(filled):
    with UnitOfWork(filled) as work:
        table = size_book(work.db, MARCH).on(HighwayClass.ARTERIAL)
    assert table.legal.width == Inches.of(8, 6)
    assert table.routine.width == Inches.of(16)
    assert table.routine.height == Inches.of(15, 6)


def test_the_size_book_leaves_out_what_has_not_taken_effect(filled):
    with UnitOfWork(filled) as work:
        assert len(size_book(work.db, date(2023, 1, 1))) == 0
        assert len(size_book(work.db, MARCH)) == 4


def test_the_schedule_book_reads_back_the_fees_and_the_steps(filled):
    with UnitOfWork(filled) as work:
        schedule = schedule_book(work.db).on(MARCH)
    assert schedule.base_for(PermitKind.ORDINARY) == Dollars.of(40)
    assert schedule.mile_rate(HighwayClass.STATE_AID) == Dollars(26)
    assert schedule.annual == Dollars.of(500)
    assert len(schedule.steps) == 3


def test_the_weight_steps_come_back_in_order_with_the_last_one_open(filled):
    with UnitOfWork(filled) as work:
        schedule = schedule_book(work.db).on(MARCH)
    assert schedule.steps[0].up_to == Pounds(20000)
    assert schedule.steps[-1].up_to is None
    assert schedule.weight_charge(Pounds(25000)) == Dollars.of(140)


def test_the_seeded_tables_price_a_permit_the_same_as_the_coded_ones(filled):
    from jeepdolly.fees.schedule import standard_schedule

    with UnitOfWork(filled) as work:
        from_rows = schedule_book(work.db).on(MARCH)
    coded = standard_schedule()
    assert from_rows.weight_charge(Pounds(100000)) == coded.weight_charge(Pounds(100000))
    assert from_rows.base_for(PermitKind.SUPERLOAD) == coded.base_for(PermitKind.SUPERLOAD)


def test_the_seed_can_be_dated_from_a_day_the_caller_names(factory):
    with UnitOfWork(factory) as work:
        seed(work.db, date(2025, 7, 1))
        work.commit()
        table = statutory_book(work.db).on(HighwayClass.ARTERIAL, date(2025, 8, 1))
    assert table.effective == date(2025, 7, 1)
