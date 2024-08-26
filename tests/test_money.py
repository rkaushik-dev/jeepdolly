"""Money in whole cents, and the two divisions the fee schedule asks for."""

import pytest

from jeepdolly.errors import Impossible, Unreadable
from jeepdolly.money import Dollars, total


def test_an_amount_prints_with_a_dollar_sign_and_two_places():
    assert str(Dollars.of(1250)) == "$1,250.00"
    assert str(Dollars(4)) == "$0.04"
    assert str(Dollars(-1550)) == "-$15.50"
    assert repr(Dollars(1550)) == "Dollars(1550)"


def test_an_amount_reads_off_a_schedule_or_a_remittance():
    assert Dollars.parse("$1,250.00") == Dollars(125000)
    assert Dollars.parse("1250") == Dollars(125000)
    assert Dollars.parse("$40") == Dollars(4000)
    assert Dollars.parse("-$15.50") == Dollars(-1550)
    assert Dollars.parse(" 40.25 ") == Dollars(4025)


def test_an_amount_that_cannot_be_read_is_refused_and_names_what_arrived():
    with pytest.raises(Unreadable) as refusal:
        Dollars.parse("forty dollars")
    assert refusal.value.facts["written"] == "forty dollars"
    with pytest.raises(Unreadable):
        Dollars.parse("40.5")
    with pytest.raises(Unreadable):
        Dollars.parse("")


def test_money_is_never_built_from_a_float():
    with pytest.raises(TypeError):
        Dollars(40.25)
    with pytest.raises(TypeError):
        Dollars(True)


def test_amounts_add_subtract_and_multiply_by_a_count():
    assert Dollars.of(40) + Dollars.of(2, 50) == Dollars(4250)
    assert Dollars.of(40) - Dollars.of(2, 50) == Dollars(3750)
    assert Dollars.of(40) * 3 == Dollars(12000)
    assert -Dollars(4025) == Dollars(-4025)
    with pytest.raises(TypeError):
        Dollars.of(40) + 40


def test_whole_dollars_rounds_toward_zero_for_a_line_with_no_cents():
    assert Dollars(4099).whole_dollars == 40
    assert Dollars(-4099).whole_dollars == -40


def test_a_percent_of_an_amount_rounds_half_up():
    assert Dollars.of(100).percent(10) == Dollars(1000)
    assert Dollars(1005).percent(50) == Dollars(503)
    assert Dollars(1001).percent(50) == Dollars(501)
    assert Dollars.of(40).percent(0) == Dollars(0)


def test_a_negative_share_is_not_a_share():
    with pytest.raises(Impossible):
        Dollars.of(40).percent(-10)


def test_a_split_adds_back_to_what_was_split():
    parts = Dollars.of(10).split(3)
    assert [part.cents for part in parts] == [334, 333, 333]
    assert total(parts) == Dollars.of(10)


def test_a_split_that_divides_evenly_leaves_no_remainder_anywhere():
    parts = Dollars.of(90).split(3)
    assert [str(part) for part in parts] == ["$30.00", "$30.00", "$30.00"]


def test_a_split_one_way_is_the_whole_amount():
    assert Dollars.of(40).split(1) == (Dollars.of(40),)


def test_a_split_no_ways_at_all_is_refused():
    with pytest.raises(Impossible):
        Dollars.of(40).split(0)


def test_amounts_sort_and_a_total_of_nothing_is_nothing():
    fees = [Dollars.of(40), Dollars.of(15), Dollars.of(112, 50)]
    assert sorted(fees)[0] == Dollars.of(15)
    assert total(fees) == Dollars.of(167, 50)
    assert total([]) == Dollars(0)
