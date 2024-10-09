"""Restrictions, the six kinds of them, and when they are in force."""

from datetime import date, time

import pytest

from jeepdolly.domain.restriction import (
    Curfew,
    Restriction,
    RestrictionBook,
    RestrictionKind,
)
from jeepdolly.errors import Conflict, Impossible
from jeepdolly.measure import Inches, Pounds

MARCH = date(2025, 3, 3)
JULY = date(2025, 7, 1)
WEEKDAYS = frozenset({0, 1, 2, 3, 4})
LOW_BRIDGE = Inches.of(13, 9)


def clearance(ident="R-1", segment="US2-1", height=LOW_BRIDGE, **kw):
    return Restriction(
        ident,
        segment,
        RestrictionKind.CLEARANCE,
        "Norridgewock railroad bridge",
        height=height,
        **kw,
    )


def closure(ident="R-9", segment="SA148-1", **kw):
    return Restriction(ident, segment, RestrictionKind.CLOSURE, "culvert replacement", **kw)


def test_a_clearance_carries_the_height_and_the_thing_it_is_under():
    one = clearance()
    assert one.height == Inches(165)
    assert one.kind is RestrictionKind.CLEARANCE
    assert str(one) == "US2-1 clearance 13 ft 9 in: Norridgewock railroad bridge"


def test_a_posted_structure_carries_a_weight_and_a_closure_carries_nothing():
    posted = Restriction(
        "R-2", "SA148-2", RestrictionKind.WEIGHT, "Carrabassett bridge", weight=Pounds(48000)
    )
    assert posted.weight == Pounds(48000)
    assert str(closure()) == "SA148-1 closure: culvert replacement"
    assert closure().kind.stops_everything
    assert not posted.kind.stops_everything


def test_a_restriction_without_the_number_it_compares_against_is_refused():
    with pytest.raises(Impossible) as refusal:
        Restriction("R-3", "US2-1", RestrictionKind.CLEARANCE, "no number on it")
    assert "compare against" in str(refusal.value)
    with pytest.raises(Impossible):
        Restriction("R-4", "US2-1", RestrictionKind.WEIGHT, "posted, but no number")
    with pytest.raises(Impossible):
        Restriction("R-5", "US2-1", RestrictionKind.SEASONAL, "posted road, but no region")


def test_a_seasonal_restriction_names_the_region_and_not_a_weight():
    posted = Restriction(
        "R-6", "SA148-1", RestrictionKind.SEASONAL, "spring posting", region="district 4"
    )
    assert posted.region == "district 4"
    assert posted.weight is None
    assert str(posted).endswith("seasonal district 4: spring posting")


def test_a_permanent_restriction_applies_on_any_day_at_all():
    one = clearance()
    assert one.permanent()
    assert one.applies_on(MARCH)
    assert one.applies_on(date(2031, 1, 1))


def test_a_dated_restriction_applies_only_inside_its_dates():
    work = closure(effective=date(2025, 6, 1), expires=date(2025, 8, 15))
    assert not work.permanent()
    assert not work.applies_on(MARCH)
    assert work.applies_on(JULY)
    assert not work.applies_on(date(2025, 9, 1))


def test_a_restriction_that_lifts_before_it_starts_is_refused():
    with pytest.raises(Impossible):
        closure(effective=date(2025, 8, 1), expires=date(2025, 6, 1))


def test_a_restriction_with_only_a_start_never_lifts():
    work = closure(effective=date(2025, 6, 1))
    assert not work.applies_on(MARCH)
    assert work.applies_on(date(2030, 1, 1))


def test_a_curfew_covers_the_days_and_the_hours_it_names():
    rush = Curfew(WEEKDAYS, time(6, 30), time(8, 30))
    assert rush.covers(date(2025, 3, 3), time(7, 0))
    assert not rush.covers(date(2025, 3, 3), time(9, 0))
    assert not rush.covers(date(2025, 3, 8), time(7, 0))
    assert str(rush) == "MTWTF 06:30 to 08:30"


def test_a_curfew_with_no_days_or_a_backwards_clock_is_refused():
    with pytest.raises(Impossible):
        Curfew(frozenset(), time(6, 30), time(8, 30))
    with pytest.raises(Impossible):
        Curfew(WEEKDAYS, time(8, 30), time(6, 30))
    with pytest.raises(Impossible):
        Curfew(frozenset({9}), time(6, 30), time(8, 30))


def test_the_book_hands_back_what_is_in_force_on_a_segment_that_day():
    book = RestrictionBook()
    book.add(clearance())
    book.add(closure(ident="R-8", segment="US2-1", effective=date(2025, 6, 1)))
    assert len(book.on_segment("US2-1", MARCH)) == 1
    assert len(book.on_segment("US2-1", JULY)) == 2
    assert book.on_segment("SR43-1", MARCH) == ()


def test_the_book_lists_one_kind_at_a_time_for_the_calendar():
    book = RestrictionBook()
    book.add(clearance())
    book.add(clearance(ident="R-2", segment="SR43-1", height=Inches.of(14, 2)))
    book.add(closure())
    assert [one.ident for one in book.of_kind(RestrictionKind.CLEARANCE, MARCH)] == ["R-1", "R-2"]
    assert [one.ident for one in book.closures(MARCH)] == ["R-9"]


def test_a_restriction_can_be_lifted_when_the_work_finishes_early():
    book = RestrictionBook()
    book.add(clearance())
    book.add(closure())
    assert len(book) == 2
    book.lift("R-9")
    assert len(book) == 1
    assert book.on_segment("SA148-1", MARCH) == ()
    with pytest.raises(Conflict):
        book.lift("R-9")


def test_the_same_restriction_may_not_be_filed_twice():
    book = RestrictionBook()
    book.add(clearance())
    with pytest.raises(Conflict):
        book.add(clearance())


def test_the_restricted_segments_of_a_day_are_what_the_router_avoids():
    book = RestrictionBook()
    book.add(clearance())
    book.add(closure(effective=date(2025, 6, 1)))
    assert book.segments_restricted(MARCH) == ("US2-1",)
    assert book.segments_restricted(JULY) == ("SA148-1", "US2-1")
