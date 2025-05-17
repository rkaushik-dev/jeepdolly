"""Turning every finding into the conditions the permit prints."""

from datetime import date

from jeepdolly.conditions.assemble import assemble
from jeepdolly.conditions.library import standard_library
from jeepdolly.domain.envelope import Envelope
from jeepdolly.domain.restriction import Restriction, RestrictionBook, RestrictionKind
from jeepdolly.escorts.marking import marking_for
from jeepdolly.escorts.rules import required
from jeepdolly.limits.statutory import standard_book as statutes
from jeepdolly.measure import Pounds
from jeepdolly.routing.evaluate import evaluate
from jeepdolly.routing.itinerary import Itinerary
from jeepdolly.routing.turns import coded_corners
from jeepdolly.seasons import SeasonBook, SeasonKind, SeasonOrder
from jeepdolly.structures.analysis import analyse_route
from jeepdolly.travel.windows import limits_for
from support import make_stream, road_log, structure_book

LIBRARY = standard_library()
APRIL = date(2025, 4, 8)
ACROSS = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")


def load(width="10'", height="13'6", length="80'", rear="0"):
    return Envelope.read(width, height, length, "0", rear)


def sheet(envelope=None, superload=False, night=False, route=None, structures=None, liftable=0):
    envelope = envelope or load()
    classes = ()
    return assemble(
        LIBRARY,
        "2025-04-08",
        "2025-04-12",
        limits_for(envelope, superload=superload),
        required(envelope, classes, superload=superload),
        marking_for(envelope, at_night=night, superload=superload),
        route=route,
        structures=structures,
        liftable_axles=liftable,
    )


def codes(conditions):
    return [one.code for one in conditions]


def test_every_permit_carries_the_dates_and_the_general_conditions():
    printed = sheet()
    assert codes(printed)[0] == "T-01"
    assert "the route as written" not in printed[0].text.lower()
    assert set(codes(printed)) >= {"T-01", "R-01", "Q-01", "G-01", "G-02", "G-03"}
    assert printed[0].text == "Valid from 2025-04-08 to 2025-04-12 inclusive."


def test_a_legal_size_load_carries_nothing_about_escorts_or_marking():
    printed = codes(sheet(load(width="8'", length="70'")))
    assert not any(one.startswith("E-") for one in printed)
    assert not any(one.startswith("M-") for one in printed)


def test_a_wide_load_picks_up_daylight_escorts_and_marking_together():
    printed = codes(sheet(load(width="13'")))
    assert "T-02" in printed
    assert "E-01" in printed
    assert "M-01" in printed
    assert "M-02" in printed


def test_the_escort_count_comes_from_the_escort_table_and_is_not_reasoned_about():
    printed = sheet(load(width="15'"))
    front = next(one for one in printed if one.code == "E-01")
    assert front.text == "1 certified pilot car ahead of the load."


def test_a_superload_says_daylight_only_once_and_not_twice():
    printed = codes(sheet(load(width="16'"), superload=True))
    assert printed.count("T-02") == 1
    assert "T-03" in printed
    assert "E-04" in printed


def test_the_night_marking_replaces_the_flags_line():
    printed = codes(sheet(load(width="13'"), night=True))
    assert "M-03" in printed
    assert "M-02" not in printed


def test_a_liftable_axle_adds_the_condition_that_it_stays_down():
    assert "Q-02" in codes(sheet(liftable=2))
    assert "Q-02" not in codes(sheet())


def test_a_posted_season_on_the_route_prints_the_order_it_runs_under():
    seasons = SeasonBook()
    seasons.file(SeasonOrder("25-04", "district 4", SeasonKind.THAW, APRIL, date(2025, 5, 2), 65))
    restrictions = RestrictionBook()
    restrictions.add(
        Restriction(
            "R-5", "SA148-1", RestrictionKind.SEASONAL, "spring posting", region="district 4"
        )
    )
    reading = evaluate(
        Itinerary(road_log(), "waterville", ACROSS),
        load(),
        Pounds(94000),
        APRIL,
        restrictions,
        seasons,
        statutes(),
    )
    printed = sheet(route=reading)
    posted = next(one for one in printed if one.code == "R-02")
    assert posted.text == "SA148-1 is posted at 61,100 lb while order 25-04 runs."


def test_a_structure_in_the_middle_band_prints_all_four_crossing_conditions():
    stream = make_stream([12000, 19000, 19000, 25000, 25000, 25000], [200, 54, 180, 52, 52])
    reading = analyse_route(
        structure_book(), Itinerary(road_log(), "waterville", ("US201-1", "US201-2")), stream
    )
    printed = sheet(structures=reading)
    lines = [one.text for one in printed if one.code.startswith("S-")]
    assert len(lines) == 4
    assert lines[0] == "Cross Kennebec River alone with no other vehicle on it."
    assert lines[3] == "Do not stop on Kennebec River for any reason."


def test_a_structure_that_clears_prints_nothing_about_itself():
    stream = make_stream([12000, 14000, 14000], [200, 54])
    reading = analyse_route(
        structure_book(), Itinerary(road_log(), "waterville", ("US201-1", "US201-2")), stream
    )
    assert not any(one.startswith("S-") for one in codes(sheet(structures=reading)))


def test_the_conditions_print_grouped_by_category_in_face_order():
    printed = codes(sheet(load(width="16'"), superload=True))
    assert printed.index("T-01") < printed.index("R-01")
    assert printed.index("R-01") < printed.index("E-01")
    assert printed.index("E-01") < printed.index("M-01")
    assert printed.index("M-01") < printed.index("G-01")


def corner_reading(length="95'"):
    return evaluate(
        Itinerary(road_log(), "waterville", ACROSS),
        load(length=length),
        Pounds(94000),
        APRIL,
        RestrictionBook(),
        SeasonBook(),
        statutes(),
        turns=coded_corners(),
    )


def test_a_corner_the_load_only_just_gets_round_prints_two_route_conditions():
    printed = sheet(route=corner_reading())
    stop = next(one for one in printed if one.code == "R-05")
    assert stop.text == "Stop traffic both ways to turn onto US2-1 at skowhegan."
    walk = next(one for one in printed if one.code == "R-06")
    assert walk.text == "The turn at skowhegan allows 65 ft radius. Walk it first."


def test_the_two_corner_conditions_print_in_the_order_the_driver_needs_them():
    printed = codes(sheet(route=corner_reading()))
    assert printed.index("R-05") < printed.index("R-06")
    assert printed.index("R-01") < printed.index("R-05")


def test_a_corner_with_room_to_spare_says_nothing_on_the_permit():
    assert "R-05" not in codes(sheet(route=corner_reading(length="80'")))


def test_a_corner_the_load_cannot_get_round_is_not_a_condition_at_all():
    # It is a refusal, and a refused application never reaches this file.
    assert "R-05" not in codes(sheet(route=corner_reading(length="150'")))
