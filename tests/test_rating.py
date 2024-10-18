"""Rating a load over a structure, and the three bands the answer falls in."""

import pytest

from jeepdolly.errors import Impossible
from jeepdolly.measure import Pounds
from jeepdolly.structures.rating import (
    Verdict,
    crossing_conditions,
    factor_for,
    posting_for,
    posting_is_current,
    rate,
)
from support import structure_book

BOOK = structure_book()
KENNEBEC = BOOK.get("1247")
CARRABASSETT = BOOK.get("1533")
CULVERT = BOOK.get("3310")
OVERPASS = BOOK.get("2201")


def test_the_factor_is_capacity_over_the_load_in_whole_percent():
    assert factor_for(KENNEBEC, Pounds(94000)) == 140
    assert factor_for(KENNEBEC, Pounds(132000)) == 100
    assert factor_for(CULVERT, Pounds(82000)) == 109


def test_a_comfortable_factor_crosses_with_nothing_attached():
    rating = rate(KENNEBEC, Pounds(94000))
    assert rating.verdict is Verdict.CLEARS
    assert rating.verdict.crosses
    assert crossing_conditions(rating) == ()
    assert str(rating) == "1247 rates 140% for 94,000 lb, clears"


def test_a_factor_in_the_middle_band_crosses_under_three_conditions():
    rating = rate(KENNEBEC, Pounds(125000))
    assert rating.factor == 105
    assert rating.verdict is Verdict.CONDITIONAL
    conditions = crossing_conditions(rating)
    assert len(conditions) == 3
    assert conditions[0].startswith("cross Kennebec River alone")
    assert "walking pace" in conditions[1]
    assert "centred" in conditions[2]


def test_a_factor_under_one_hundred_does_not_cross_at_all():
    rating = rate(KENNEBEC, Pounds(140000))
    assert rating.factor == 94
    assert rating.verdict is Verdict.REFUSED
    assert not rating.verdict.crosses
    assert crossing_conditions(rating) == ()


def test_the_band_edges_are_where_the_office_says_they_are():
    assert rate(CULVERT, Pounds(82000)).verdict is Verdict.CONDITIONAL
    assert rate(CULVERT, Pounds(81000)).verdict is Verdict.CLEARS
    assert rate(CULVERT, Pounds(90000)).verdict is Verdict.CONDITIONAL
    assert rate(CULVERT, Pounds(90001)).verdict is Verdict.REFUSED


def test_a_posting_beats_the_arithmetic_and_says_so():
    rating = rate(CARRABASSETT, Pounds(94000))
    assert rating.posted_out
    assert rating.verdict is Verdict.REFUSED
    assert rating.factor == 0
    assert str(rating) == "1533 posted at 48,000 lb, load refused"


def test_a_load_inside_the_posting_is_rated_like_anything_else():
    rating = rate(CARRABASSETT, Pounds(40000))
    assert not rating.posted_out
    assert rating.factor == 150
    assert rating.verdict is Verdict.CLEARS
    assert rating.capacity == Pounds(60000)


def test_a_structure_with_no_operating_rating_cannot_be_rated():
    with pytest.raises(Impossible) as refusal:
        factor_for(OVERPASS, Pounds(94000))
    assert "no operating rating" in str(refusal.value)


def test_a_load_with_no_weight_is_not_a_rating_question():
    with pytest.raises(Impossible):
        factor_for(KENNEBEC, Pounds(0))


def test_the_sign_a_structure_would_carry_comes_off_the_inventory_rating():
    assert posting_for(KENNEBEC) == Pounds(88000)
    assert posting_for(CARRABASSETT) == Pounds(42000)


def test_a_sign_that_disagrees_with_the_last_inspection_is_reported():
    assert not posting_is_current(CARRABASSETT)
    assert posting_is_current(KENNEBEC)


def test_a_structure_with_no_inventory_rating_cannot_have_a_sign_derived():
    with pytest.raises(Impossible):
        posting_for(OVERPASS)
