"""The statutory tables, and which one is in force when."""

from datetime import date

import pytest

from jeepdolly.domain.highway import HighwayClass
from jeepdolly.errors import Impossible, NotOnFile
from jeepdolly.limits.statutory import StatutoryTable, standard_book
from jeepdolly.measure import Pounds

MARCH = date(2025, 3, 3)


def test_the_interstate_table_is_the_federal_one():
    table = standard_book().on(HighwayClass.INTERSTATE, MARCH)
    assert table.group_limit(1) == Pounds(20000)
    assert table.group_limit(2) == Pounds(34000)
    assert table.gross == Pounds(80000)
    assert table.tire_rate == 500


def test_the_state_roads_carry_more_than_the_interstate_does():
    book = standard_book()
    arterial = book.on(HighwayClass.ARTERIAL, MARCH)
    interstate = book.on(HighwayClass.INTERSTATE, MARCH)
    assert arterial.group_limit(2) == Pounds(41000)
    assert arterial.gross == Pounds(100000)
    assert arterial.group_limit(2) > interstate.group_limit(2)


def test_a_group_bigger_than_the_table_names_gets_the_published_allowance():
    table = standard_book().on(HighwayClass.ARTERIAL, MARCH)
    assert table.largest_named == 4
    assert table.group_limit(5) == Pounds(63000)
    assert table.group_limit(7) == Pounds(77000)


def test_a_group_of_no_axles_is_not_a_question_the_table_answers():
    with pytest.raises(Impossible):
        standard_book().on(HighwayClass.ARTERIAL, MARCH).group_limit(0)


def test_the_overage_on_a_group_is_the_amount_and_never_a_verdict():
    table = standard_book().on(HighwayClass.ARTERIAL, MARCH)
    assert table.over_group(2, Pounds(45000)) == Pounds(4000)
    assert table.over_group(2, Pounds(41000)) == Pounds(0)
    assert table.over_gross(Pounds(132400)) == Pounds(32400)
    assert table.over_gross(Pounds(80000)) == Pounds(0)


def test_a_table_prints_the_class_and_the_day_it_took_effect():
    table = standard_book(date(2024, 7, 1)).on(HighwayClass.STATE_AID, MARCH)
    assert str(table) == "state aid table of 2024-07-01"


def test_a_table_whose_limits_do_not_climb_with_the_axles_is_refused():
    with pytest.raises(Impossible) as refusal:
        StatutoryTable(
            HighwayClass.ARTERIAL,
            MARCH,
            {1: Pounds(22400), 2: Pounds(22400)},
            Pounds(7000),
            Pounds(100000),
            600,
        )
    assert "not above" in str(refusal.value)


def test_a_table_that_names_no_single_axle_is_not_a_table():
    with pytest.raises(Impossible):
        StatutoryTable(
            HighwayClass.ARTERIAL, MARCH, {2: Pounds(41000)}, Pounds(7000), Pounds(100000), 600
        )


def test_a_tire_rate_of_nothing_is_refused():
    with pytest.raises(Impossible):
        StatutoryTable(
            HighwayClass.ARTERIAL,
            MARCH,
            {1: Pounds(22400), 2: Pounds(41000)},
            Pounds(7000),
            Pounds(100000),
            0,
        )


def test_the_table_in_force_is_the_latest_one_that_had_taken_effect():
    book = standard_book(date(2024, 1, 1))
    later = book.on(HighwayClass.ARTERIAL, MARCH)
    assert later.effective == date(2024, 1, 1)
    book.file(
        StatutoryTable(
            HighwayClass.ARTERIAL,
            date(2025, 2, 1),
            {1: Pounds(23000), 2: Pounds(42000)},
            Pounds(7000),
            Pounds(104000),
            600,
        )
    )
    assert book.on(HighwayClass.ARTERIAL, MARCH).gross == Pounds(104000)
    assert book.on(HighwayClass.ARTERIAL, date(2025, 1, 1)).gross == Pounds(100000)


def test_a_permit_argued_about_later_is_judged_by_the_table_of_its_own_day():
    book = standard_book(date(2024, 1, 1))
    book.file(
        StatutoryTable(
            HighwayClass.ARTERIAL,
            date(2025, 6, 1),
            {1: Pounds(23000), 2: Pounds(42000)},
            Pounds(7000),
            Pounds(104000),
            600,
        )
    )
    issued = book.on(HighwayClass.ARTERIAL, date(2025, 3, 3))
    assert issued.group_limit(2) == Pounds(41000)


def test_asking_before_any_table_took_effect_says_so():
    book = standard_book(date(2024, 1, 1))
    with pytest.raises(NotOnFile) as refusal:
        book.on(HighwayClass.ARTERIAL, date(2023, 5, 1))
    assert refusal.value.facts["highway_class"] == "arterial"


def test_two_tables_for_one_class_on_one_day_is_refused():
    book = standard_book(date(2024, 1, 1))
    with pytest.raises(Impossible):
        book.file(
            StatutoryTable(
                HighwayClass.ARTERIAL,
                date(2024, 1, 1),
                {1: Pounds(23000), 2: Pounds(42000)},
                Pounds(7000),
                Pounds(104000),
                600,
            )
        )


def test_the_book_knows_which_classes_it_covers():
    book = standard_book()
    assert len(book) == 4
    assert HighwayClass.TOWN_WAY in book.classes()
