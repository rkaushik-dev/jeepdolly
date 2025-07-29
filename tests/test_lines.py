"""Overhead lines, the six inches of daylight, and the notice nobody allows for."""

from datetime import date

import pytest

from jeepdolly.domain.envelope import Envelope
from jeepdolly.errors import Conflict, NotOnFile
from jeepdolly.measure import Inches
from jeepdolly.routing.itinerary import Itinerary
from jeepdolly.utilities.lines import (
    SAFETY,
    Crossing,
    LineBook,
    Owner,
    under,
    wanted,
)
from support import road_log

ACROSS = ("US201-1", "US201-2", "US2-1")
# A Wednesday, which matters because the notice is counted in working days.
TRAVEL = date(2025, 4, 9)
FILED = date(2025, 4, 1)


def load(height="13'6"):
    return Envelope.read("12'", height, "82'")


def an_owner(ident="CMP", notice=5):
    return Owner(ident, "Central Maine Power", "207 555 0100", notice)


def a_crossing(ident="L-1", segment="US201-2", height="18'", lift_to="24'", owner="CMP"):
    return Crossing(
        ident=ident,
        segment=segment,
        place="Fairfield village",
        height=Inches.parse(height),
        owner=owner,
        lift_to=None if lift_to is None else Inches.parse(lift_to),
    )


def book(crossings=(), owners=None):
    made = LineBook()
    for one in owners or [an_owner()]:
        made.add_owner(one)
    for one in crossings:
        made.add(one)
    return made


def trip(idents=ACROSS, origin="waterville"):
    return Itinerary(road_log(), origin, idents)


def test_the_office_wants_six_inches_of_daylight():
    assert Inches.of(0, 6) == SAFETY
    assert wanted(load()) == Inches.of(14, 0)


def test_an_owner_cannot_want_negative_notice():
    with pytest.raises(Conflict):
        Owner("CMP", "Central Maine Power", "207 555 0100", -1)


def test_the_notice_is_counted_in_working_days_and_skips_the_weekend():
    # Five working days before Wednesday the ninth is Wednesday the second.
    assert an_owner().notice_by(TRAVEL) == date(2025, 4, 2)


def test_an_owner_wanting_no_notice_can_be_rung_on_the_day():
    assert an_owner(notice=0).notice_by(TRAVEL) == TRAVEL


def test_a_request_on_the_notice_day_itself_is_in_time():
    assert an_owner().in_time(date(2025, 4, 2), TRAVEL)
    assert not an_owner().in_time(date(2025, 4, 3), TRAVEL)


def test_the_owner_prints_the_way_the_counter_reads_it():
    assert str(an_owner()) == "CMP  Central Maine Power  207 555 0100  5 days notice"


def test_a_crossing_with_no_measured_height_is_refused():
    with pytest.raises(Conflict):
        a_crossing(height="0")


def test_a_crossing_that_lifts_to_below_where_it_hangs_is_refused():
    with pytest.raises(Conflict):
        a_crossing(height="18'", lift_to="16'")


def test_a_crossing_says_whether_it_can_be_lifted_at_all():
    assert a_crossing().liftable
    assert not a_crossing(lift_to=None).liftable


def test_a_load_well_under_the_wire_clears_it():
    assert a_crossing().clears(load())


def test_a_load_exactly_the_height_of_the_wire_does_not_clear_it():
    assert not a_crossing(height="13'6").clears(load())


def test_the_daylight_is_what_decides_a_close_one():
    assert a_crossing(height="14'").clears(load())
    assert not a_crossing(height="13'11").clears(load())


def test_a_load_that_clears_once_lifted_says_so():
    tall = load(height="20'")
    assert not a_crossing().clears(tall)
    assert a_crossing().clears_lifted(tall)


def test_a_load_too_tall_even_for_the_lift_says_so():
    assert not a_crossing().clears_lifted(load(height="26'"))


def test_a_crossing_that_cannot_be_lifted_never_clears_lifted():
    assert not a_crossing(lift_to=None).clears_lifted(load(height="12'"))


def test_the_crossing_prints_what_the_card_says():
    assert str(a_crossing()) == "L-1  US201-2 at Fairfield village  18 ft, lifts to 24 ft"
    assert str(a_crossing(lift_to=None)).endswith("cannot be lifted")


def test_a_crossing_cannot_be_filed_against_an_owner_nobody_has():
    with pytest.raises(NotOnFile):
        book(crossings=[a_crossing(owner="BHE")])


def test_the_same_crossing_is_not_filed_twice():
    made = book(crossings=[a_crossing()])
    with pytest.raises(Conflict):
        made.add(a_crossing())


def test_the_same_owner_is_not_filed_twice():
    made = book()
    with pytest.raises(Conflict):
        made.add_owner(an_owner())


def test_a_crossing_nobody_has_is_a_refusal_that_names_it():
    with pytest.raises(NotOnFile):
        book().get("L-9")


def test_the_crossings_of_one_segment_come_back_in_ident_order():
    made = book(crossings=[a_crossing(ident="L-2"), a_crossing(ident="L-1")])
    assert [one.ident for one in made.on_segment("US201-2")] == ["L-1", "L-2"]
    assert made.on_segment("I95-1") == ()


def test_the_lowest_wire_is_the_one_the_office_worries_about():
    made = book(crossings=[a_crossing(ident="L-1"), a_crossing(ident="L-2", height="16'")])
    assert made.lowest().ident == "L-2"
    assert len(made) == 2


def test_an_empty_book_has_no_lowest_wire():
    assert book().lowest() is None


def test_a_route_with_no_wires_on_it_is_clear():
    found = under(trip(), load(height="20'"), book(), FILED, TRAVEL)
    assert found.clear
    assert found.lifts == ()
    assert str(found) == "nothing overhead"


def test_a_load_that_clears_everything_needs_nobody_rung():
    found = under(trip(), load(), book(crossings=[a_crossing()]), FILED, TRAVEL)
    assert found.clear
    assert found.lifts == ()


def test_a_load_that_needs_a_lift_gets_a_name_and_a_number():
    found = under(trip(), load(height="20'"), book(crossings=[a_crossing()]), FILED, TRAVEL)
    assert found.clear
    assert len(found.lifts) == 1
    assert str(found.lifts[0]) == (
        "US201-2 at Fairfield village: ring Central Maine Power on 207 555 0100 by 2025-04-02"
    )
    assert str(found) == "1 lifts to arrange"


def test_a_lift_asked_for_too_late_is_a_refusal_that_says_it_in_days():
    late = date(2025, 4, 7)
    found = under(trip(), load(height="20'"), book(crossings=[a_crossing()]), late, TRAVEL)
    assert not found.clear
    assert found.refusals() == (
        "US201-2 at Fairfield village wants 5 working days notice to Central Maine Power",
    )


def test_a_wire_nothing_can_be_done_about_is_a_refusal_that_quotes_both_heights():
    made = book(crossings=[a_crossing(lift_to=None)])
    found = under(trip(), load(height="20'"), made, FILED, TRAVEL)
    assert found.refusals() == (
        "US201-2 at Fairfield village hangs at 18 ft and this load wants 20 ft 6 in",
    )


def test_a_wire_on_a_segment_the_route_misses_is_nobodys_business():
    made = book(crossings=[a_crossing(segment="SR8-1", lift_to=None)])
    assert under(trip(), load(height="20'"), made, FILED, TRAVEL).clear


def test_two_wires_on_one_route_both_come_back():
    made = book(
        crossings=[
            a_crossing(ident="L-1"),
            a_crossing(ident="L-2", segment="US2-1", height="17'"),
        ]
    )
    found = under(trip(), load(height="20'"), made, FILED, TRAVEL)
    assert len(found.lifts) == 2


def test_a_route_with_one_lift_and_one_refusal_reports_both():
    made = book(
        crossings=[
            a_crossing(ident="L-1"),
            a_crossing(ident="L-2", segment="US2-1", height="17'", lift_to=None),
        ]
    )
    found = under(trip(), load(height="20'"), made, FILED, TRAVEL)
    assert len(found.lifts) == 1
    assert len(found.blocked) == 1
    assert str(found) == "1 lines in the way"


def test_a_lift_the_owner_can_reach_says_it_is_possible():
    found = under(trip(), load(height="20'"), book(crossings=[a_crossing()]), FILED, TRAVEL)
    assert found.lifts[0].possible
