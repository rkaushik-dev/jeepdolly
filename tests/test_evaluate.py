"""Walking a route with a load on a day, and what comes back in the way."""

from datetime import date, time

from jeepdolly.domain.envelope import Envelope
from jeepdolly.domain.restriction import Curfew, Restriction, RestrictionBook, RestrictionKind
from jeepdolly.limits.statutory import standard_book as statutes
from jeepdolly.measure import Inches, Pounds
from jeepdolly.routing.evaluate import corner_obstructions, evaluate
from jeepdolly.routing.itinerary import Itinerary
from jeepdolly.routing.turns import coded_corners
from jeepdolly.seasons import SeasonBook, SeasonKind, SeasonOrder
from support import road_log

MARCH = date(2025, 3, 3)
APRIL = date(2025, 4, 8)
ACROSS = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")
LOAD = Envelope.read("12'", "13'9", "82'")
GROSS = Pounds(94000)


def trip(network=None, origin="waterville", idents=ACROSS):
    return Itinerary(network or road_log(), origin, idents)


def thaw_book():
    book = SeasonBook()
    book.file(
        SeasonOrder("25-04", "district 4", SeasonKind.THAW, date(2025, 3, 20), date(2025, 5, 2), 65)
    )
    return book


def run(
    restrictions=None,
    seasons=None,
    envelope=LOAD,
    gross=GROSS,
    day=MARCH,
    departure=None,
    turns=None,
):
    return evaluate(
        trip(),
        envelope,
        gross,
        day,
        restrictions or RestrictionBook(),
        seasons or SeasonBook(),
        statutes(),
        departure=departure,
        turns=turns,
    )


def test_a_route_with_nothing_on_it_comes_back_clear():
    reading = run()
    assert reading.clear
    assert reading.obstructions == ()
    assert str(reading) == "route is clear"


def test_a_load_that_fits_under_a_clearance_is_not_an_obstruction():
    book = RestrictionBook()
    book.add(
        Restriction(
            "R-1", "US2-1", RestrictionKind.CLEARANCE, "railroad bridge", height=Inches.of(14)
        )
    )
    assert run(book).clear


def test_a_load_that_does_not_fit_stops_the_route_and_names_both_numbers():
    book = RestrictionBook()
    book.add(
        Restriction(
            "R-1", "US2-1", RestrictionKind.CLEARANCE, "railroad bridge", height=Inches.of(13, 6)
        )
    )
    reading = run(book)
    assert not reading.clear
    assert reading.segments_in_the_way() == ("US2-1",)
    trouble = reading.fatal[0]
    assert trouble.needed == "13 ft 9 in"
    assert trouble.allowed == "13 ft 6 in"
    assert "no permit" in str(trouble)


def test_a_closure_stops_everything_whatever_the_load_measures():
    book = RestrictionBook()
    book.add(Restriction("R-2", "SA148-1", RestrictionKind.CLOSURE, "culvert work"))
    reading = run(book, envelope=Envelope.read("8'", "10'", "40'"), gross=Pounds(30000))
    assert not reading.clear
    assert reading.fatal[0].kind == "closure"
    assert str(reading) == "route is blocked at SA148-1"


def test_a_narrow_structure_is_read_against_the_width_and_nothing_else():
    book = RestrictionBook()
    book.add(
        Restriction("R-3", "SR43-1", RestrictionKind.WIDTH, "stone arch", width=Inches.of(11)),
    )
    assert not run(book).clear
    assert run(book, envelope=Envelope.read("10'", "13'", "60'")).clear


def test_a_posted_structure_is_read_against_the_gross():
    book = RestrictionBook()
    book.add(
        Restriction(
            "R-4", "SA148-1", RestrictionKind.WEIGHT, "Carrabassett bridge", weight=Pounds(60000)
        )
    )
    reading = run(book)
    assert not reading.clear
    assert reading.fatal[0].kind == "posting"
    assert run(book, gross=Pounds(58000)).clear


def test_a_town_way_in_the_middle_of_a_trip_is_fatal_on_its_own():
    reading = evaluate(
        trip(origin="skowhegan", idents=("US201-3", "TR104-1", "SR8-1")),
        LOAD,
        GROSS,
        MARCH,
        RestrictionBook(),
        SeasonBook(),
        statutes(),
    )
    assert not reading.clear
    assert reading.fatal[0].kind == "jurisdiction"
    assert reading.fatal[0].segment == "TR104-1"


def test_a_seasonal_posting_says_nothing_while_no_order_is_running():
    book = RestrictionBook()
    book.add(
        Restriction(
            "R-5", "SA148-1", RestrictionKind.SEASONAL, "spring posting", region="district 4"
        )
    )
    assert run(book, seasons=thaw_book(), day=MARCH).clear
    assert run(book, seasons=thaw_book(), day=MARCH).conditions == ()


def test_a_seasonal_posting_under_a_thaw_order_is_a_condition_and_not_a_refusal():
    book = RestrictionBook()
    book.add(
        Restriction(
            "R-5", "SA148-1", RestrictionKind.SEASONAL, "spring posting", region="district 4"
        )
    )
    reading = run(book, seasons=thaw_book(), day=APRIL)
    assert reading.clear
    assert len(reading.conditions) == 1
    trouble = reading.conditions[0]
    assert trouble.kind == "posted season"
    assert trouble.allowed == "61,100 lb"
    assert "order 25-04" in trouble.note
    assert str(reading) == "route is clear with 1 conditions"


def test_a_load_under_the_thawed_limit_passes_the_posting_without_a_word():
    book = RestrictionBook()
    book.add(
        Restriction(
            "R-5", "SA148-1", RestrictionKind.SEASONAL, "spring posting", region="district 4"
        )
    )
    assert run(book, seasons=thaw_book(), day=APRIL, gross=Pounds(60000)).conditions == ()


def test_a_curfew_is_a_condition_when_no_departure_time_was_given():
    book = RestrictionBook()
    book.add(
        Restriction(
            "R-6",
            "US201-1",
            RestrictionKind.CURFEW,
            "Waterville rush hour",
            curfew=Curfew(frozenset({0, 1, 2, 3, 4}), time(6, 30), time(8, 30)),
        )
    )
    reading = run(book)
    assert reading.clear
    assert reading.conditions[0].kind == "curfew"


def test_a_departure_outside_the_curfew_clears_it_and_inside_it_does_not():
    book = RestrictionBook()
    book.add(
        Restriction(
            "R-6",
            "US201-1",
            RestrictionKind.CURFEW,
            "Waterville rush hour",
            curfew=Curfew(frozenset({0, 1, 2, 3, 4}), time(6, 30), time(8, 30)),
        )
    )
    assert run(book, departure=time(10, 0)).conditions == ()
    assert len(run(book, departure=time(7, 0)).conditions) == 1


def test_every_leg_is_walked_so_two_troubles_both_come_back():
    book = RestrictionBook()
    book.add(
        Restriction(
            "R-1", "US2-1", RestrictionKind.CLEARANCE, "railroad bridge", height=Inches.of(13, 6)
        )
    )
    book.add(Restriction("R-2", "SR43-1", RestrictionKind.CLOSURE, "washout"))
    reading = run(book)
    assert reading.segments_in_the_way() == ("SR43-1", "US2-1")


def test_an_office_with_no_card_index_reads_no_corners():
    assert run(envelope=Envelope.read("12'", "13'9", "150'")).clear


def test_a_load_that_will_not_get_round_the_square_is_refused_there():
    long_one = Envelope.read("12'", "13'9", "150'")
    reading = run(envelope=long_one, turns=coded_corners())
    corners = [one for one in reading.obstructions if one.kind == "corner"]
    assert [one.segment for one in corners] == ["US2-1", "SA148-1", "SR43-1"]
    assert all(one.fatal for one in corners)
    assert not reading.clear


def test_the_corner_says_what_it_has_and_what_the_load_wanted():
    long_one = Envelope.read("12'", "13'9", "150'")
    first = corner_obstructions(trip(), long_one, coded_corners())[0]
    assert first.needed == "115 ft radius"
    assert first.allowed == "65 ft radius"
    assert "island in the middle" in first.note


def test_a_corner_with_nothing_written_against_it_names_the_place():
    load = Envelope.read("12'", "13'9", "120'")
    found = corner_obstructions(
        trip(origin="fairfield", idents=("US201-2", "US201-3")), load, coded_corners()
    )
    assert found[0].note == "the turn at skowhegan"


def test_the_ordinary_permit_load_gets_round_every_corner_on_the_route():
    assert run(turns=coded_corners()).clear


def test_the_coded_card_index_is_the_office_list_and_not_empty():
    book = coded_corners()
    assert len(book) == 10
    assert [one.node for one in book.tight()] == ["madison", "norridgewock"]


def test_a_corner_the_load_only_just_gets_round_is_a_condition_not_a_refusal():
    near = Envelope.read("12'", "13'9", "95'")
    reading = run(envelope=near, turns=coded_corners())
    corners = [one for one in reading.obstructions if one.kind == "tight corner"]
    assert [one.place for one in corners] == ["skowhegan"]
    assert not any(one.fatal for one in corners)
    assert reading.clear
    assert str(reading) == "route is clear with 1 conditions"


def test_the_same_corner_is_never_both_a_refusal_and_a_condition():
    long_one = Envelope.read("12'", "13'9", "150'")
    reading = run(envelope=long_one, turns=coded_corners())
    refused = {one.place for one in reading.obstructions if one.kind == "corner"}
    noted = {one.place for one in reading.obstructions if one.kind == "tight corner"}
    assert refused
    assert refused & noted == set()


def test_an_obstruction_that_is_not_a_corner_names_no_place():
    book = RestrictionBook()
    book.add(Restriction("R-9", "SA148-1", RestrictionKind.CLOSURE, "culvert work"))
    assert all(one.place == "" for one in run(book).obstructions)
