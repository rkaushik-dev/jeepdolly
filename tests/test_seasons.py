"""Thaw orders and frozen road allowances, filed the way the district signs them."""

from datetime import date

import pytest

from jeepdolly.measure import Pounds
from jeepdolly.seasons import SeasonBook, SeasonKind, SeasonOrder

STATUTORY = Pounds(80000)


def thaw(number="25-04", region="north", start=date(2025, 3, 17), end=date(2025, 5, 2), share=65):
    return SeasonOrder(number, region, SeasonKind.THAW, start, end, share)


def frozen(number="25-01", region="north", start=date(2025, 1, 2), end=date(2025, 2, 28)):
    return SeasonOrder(number, region, SeasonKind.FROZEN, start, end, 110)


def test_an_order_covers_both_of_its_end_days():
    order = thaw()
    assert order.covers(date(2025, 3, 17))
    assert order.covers(date(2025, 4, 10))
    assert order.covers(date(2025, 5, 2))
    assert not order.covers(date(2025, 3, 16))
    assert not order.covers(date(2025, 5, 3))


def test_an_order_prints_as_the_clerk_reads_it_off_the_board():
    assert str(thaw()) == "25-04 north thaw 65%"
    assert str(frozen()) == "25-01 north frozen 110%"


def test_a_thaw_order_cuts_the_limit_and_truncates_to_whole_pounds():
    assert thaw().applied_to(STATUTORY) == Pounds(52000)
    assert thaw(share=65).applied_to(Pounds(34000)) == Pounds(22100)
    assert thaw(share=77).applied_to(Pounds(20000)) == Pounds(15400)


def test_a_frozen_order_raises_it_instead():
    assert frozen().applied_to(STATUTORY) == Pounds(88000)


def test_an_order_that_ends_before_it_starts_is_a_typing_mistake():
    with pytest.raises(ValueError):
        thaw(start=date(2025, 5, 2), end=date(2025, 3, 17))


def test_a_thaw_that_reduces_nothing_is_refused_and_so_is_the_reverse():
    with pytest.raises(ValueError):
        thaw(share=100)
    with pytest.raises(ValueError):
        thaw(share=115)
    with pytest.raises(ValueError):
        SeasonOrder("25-09", "north", SeasonKind.FROZEN, date(2025, 1, 2), date(2025, 2, 1), 90)


def test_a_share_nobody_could_have_meant_is_refused():
    with pytest.raises(ValueError):
        thaw(share=0)
    with pytest.raises(ValueError):
        SeasonOrder("25-09", "north", SeasonKind.FROZEN, date(2025, 1, 2), date(2025, 2, 1), 400)


def test_the_book_hands_back_the_order_in_force_on_the_day():
    book = SeasonBook()
    book.file(frozen())
    book.file(thaw())
    assert book.in_force("north", date(2025, 4, 1)).number == "25-04"
    assert book.in_force("north", date(2025, 1, 20)).number == "25-01"
    assert book.in_force("north", date(2025, 6, 1)) is None
    assert book.in_force("south", date(2025, 4, 1)) is None


def test_a_limit_asked_for_outside_any_order_is_the_statutory_one():
    book = SeasonBook()
    book.file(thaw())
    assert book.limit_on("north", date(2025, 4, 1), STATUTORY) == Pounds(52000)
    assert book.limit_on("north", date(2025, 6, 1), STATUTORY) == STATUTORY
    assert book.limit_on("south", date(2025, 4, 1), STATUTORY) == STATUTORY


def test_a_road_is_posted_only_while_a_thaw_order_is_running():
    book = SeasonBook()
    book.file(frozen())
    book.file(thaw())
    assert book.posted("north", date(2025, 4, 1))
    assert not book.posted("north", date(2025, 1, 20))
    assert not book.posted("north", date(2025, 7, 1))


def test_two_orders_of_one_region_may_not_overlap():
    book = SeasonBook()
    book.file(thaw())
    with pytest.raises(ValueError) as refusal:
        book.file(thaw(number="25-05", start=date(2025, 4, 20), end=date(2025, 5, 20)))
    assert "overlaps 25-04" in str(refusal.value)


def test_the_same_span_in_a_different_region_is_a_different_order():
    book = SeasonBook()
    book.file(thaw())
    book.file(thaw(number="25-06", region="downeast"))
    assert book.regions() == ("downeast", "north")
    assert len(book) == 2


def test_an_order_number_is_used_once():
    book = SeasonBook()
    book.file(thaw())
    with pytest.raises(ValueError) as refusal:
        book.file(thaw(region="downeast"))
    assert "already on file" in str(refusal.value)


def test_the_orders_of_a_region_come_back_in_the_order_they_run():
    book = SeasonBook()
    book.file(thaw())
    book.file(frozen())
    book.file(thaw(number="25-08", start=date(2025, 11, 3), end=date(2025, 12, 20), share=80))
    assert [order.number for order in book.orders_for("north")] == ["25-01", "25-04", "25-08"]
    assert book.orders_for("downeast") == ()
    assert repr(book) == "SeasonBook(3 orders)"
