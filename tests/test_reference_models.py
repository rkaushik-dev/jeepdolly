"""The reference tables, and the rows that go on and off them."""

from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from jeepdolly.domain.commodity import Family, standard_book
from jeepdolly.persistence.base import create_all, memory_engine, session_factory
from jeepdolly.persistence.models import CommodityRow, ConditionTextRow

MARCH = date(2025, 3, 3)


@pytest.fixture
def session():
    engine = memory_engine()
    create_all(engine)
    with session_factory(engine)() as opened:
        yield opened


def test_a_commodity_goes_to_a_row_and_comes_back_the_same(session):
    excavator = standard_book().get("EXCV")
    session.add(CommodityRow.of(excavator, by="clerk", on=MARCH))
    session.commit()
    session.expunge_all()
    row = session.get(CommodityRow, "EXCV")
    assert row.to_domain() == excavator
    assert row.changed_by == "clerk"
    assert row.changed_on == MARCH


def test_the_flags_that_later_rules_read_survive_the_round_trip(session):
    for code in ("XFMR", "WOOD", "GRVL", "BOAT"):
        session.add(CommodityRow.of(standard_book().get(code)))
    session.commit()
    session.expunge_all()
    transformer = session.get(CommodityRow, "XFMR").to_domain()
    wood = session.get(CommodityRow, "WOOD").to_domain()
    assert transformer.tip_limit_degrees == 3
    assert wood.divisible
    assert wood.allowance == "forest products"
    assert session.get(CommodityRow, "BOAT").to_domain().high_centre


def test_a_whole_table_can_be_written_and_read_back(session):
    book = standard_book()
    for code in book.codes():
        session.add(CommodityRow.of(book.get(code)))
    session.commit()
    rows = session.scalars(select(CommodityRow).order_by(CommodityRow.code)).all()
    assert len(rows) == len(book)
    assert [row.code for row in rows] == list(book.codes())


def test_a_retired_commodity_is_kept_and_marked_rather_than_deleted(session):
    session.add(CommodityRow.of(standard_book().get("CHIP")))
    session.commit()
    row = session.get(CommodityRow, "CHIP")
    assert row.live
    row.retired = MARCH
    session.commit()
    assert not session.get(CommodityRow, "CHIP").live
    assert session.scalars(select(CommodityRow)).all()


def test_the_family_is_stored_as_its_own_short_word(session):
    session.add(CommodityRow.of(standard_book().get("EXCV")))
    session.commit()
    row = session.connection().exec_driver_sql("select family from commodities").scalar()
    assert row == "machinery"
    assert session.get(CommodityRow, "EXCV").family is Family.MACHINERY


def test_an_impossible_tip_limit_is_refused_by_the_database(session):
    session.add(
        CommodityRow(
            code="BAD",
            description="nonsense",
            family=Family.MACHINERY,
            tip_limit_degrees=90,
        )
    )
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_commodity_with_no_code_is_refused_by_the_database(session):
    session.add(CommodityRow(code="", description="nonsense", family=Family.MACHINERY))
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_condition_is_keyed_by_its_code_and_the_day_it_took_effect(session):
    session.add(
        ConditionTextRow(
            code="T-02",
            effective=date(2024, 1, 1),
            category="travel",
            body="Daylight hours only.",
            print_order=20,
        )
    )
    session.add(
        ConditionTextRow(
            code="T-02",
            effective=date(2025, 6, 1),
            category="travel",
            body="Daylight hours only, half an hour either side.",
            print_order=20,
        )
    )
    session.commit()
    query = select(ConditionTextRow).where(ConditionTextRow.code == "T-02")
    rows = session.scalars(query).all()
    assert len(rows) == 2
    assert repr(rows[0]) == "<ConditionTextRow T-02 2024-01-01>"


def test_the_same_condition_on_the_same_day_twice_is_refused(session):
    for _ in range(2):
        session.add(
            ConditionTextRow(
                code="G-01",
                effective=date(2024, 1, 1),
                category="general",
                body="Carry this permit.",
            )
        )
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_condition_with_no_words_is_refused_by_the_database(session):
    session.add(ConditionTextRow(code="G-09", effective=MARCH, category="general", body=""))
    with pytest.raises(IntegrityError):
        session.commit()
