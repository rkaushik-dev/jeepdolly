"""Finding a route for a load, and finding none when there is none."""

from datetime import date

import pytest

from jeepdolly.domain.envelope import Envelope
from jeepdolly.domain.highway import HighwayClass
from jeepdolly.domain.restriction import Restriction, RestrictionBook, RestrictionKind
from jeepdolly.errors import NotOnFile
from jeepdolly.limits.statutory import standard_book as statutes
from jeepdolly.measure import Inches, Miles, Pounds
from jeepdolly.routing.select import PENALTY, Search, find, reachable
from jeepdolly.routing.turns import coded_corners
from jeepdolly.seasons import SeasonBook
from support import road_log

MARCH = date(2025, 3, 3)


def search(height="13'6", gross=94000, penalties=None, length="82'"):
    return Search(
        envelope=Envelope.read("12'", height, length),
        gross=Pounds(gross),
        day=MARCH,
        penalties=penalties if penalties is not None else dict(PENALTY),
    )


def route(origin="waterville", destination="pittsfield", restrictions=None, load=None, turns=None):
    return find(
        road_log(),
        origin,
        destination,
        load or search(),
        restrictions or RestrictionBook(),
        SeasonBook(),
        statutes(),
        turns=turns,
    )


def low_bridge(segment="I95-2", height="13'"):
    book = RestrictionBook()
    book.add(
        Restriction(
            "R-1", segment, RestrictionKind.CLEARANCE, "overpass", height=Inches.parse(height)
        )
    )
    return book


def test_the_router_takes_the_interstate_when_nothing_is_in_the_way():
    found = route()
    assert found is not None
    assert found.idents == ("I95-1", "I95-2")
    assert found.distance == Miles.of(18, 80)


def test_a_clearance_on_the_interstate_pushes_the_route_onto_the_state_roads():
    found = route(restrictions=low_bridge())
    assert found is not None
    assert found.idents == ("I95-1", "US201-2", "SA148-2", "SR43-1")
    assert found.distance == Miles.of(34, 70)


def test_a_load_that_fits_under_the_bridge_is_left_on_the_interstate():
    found = route(restrictions=low_bridge(), load=search(height="12'"))
    assert found is not None
    assert found.idents == ("I95-1", "I95-2")


def test_the_router_will_not_route_over_a_town_way_at_all():
    found = route(origin="madison", destination="anson")
    assert found is not None
    assert found.idents == ("SR8-1", "US2-1", "US201-3")


def test_a_place_with_nothing_but_a_town_way_into_it_can_be_unreachable():
    blocked = RestrictionBook()
    blocked.add(Restriction("R-2", "US201-3", RestrictionKind.CLOSURE, "bridge out"))
    assert route(origin="skowhegan", destination="anson", restrictions=blocked) is None


def test_asking_for_a_route_to_where_you_already_are_gets_nothing():
    assert route(origin="skowhegan", destination="skowhegan") is None


def test_a_place_that_is_not_on_the_log_is_refused_before_the_search_starts():
    with pytest.raises(NotOnFile):
        route(destination="bangor")


def test_the_penalties_are_data_and_zeroing_them_takes_the_short_way():
    plain = search(penalties={one: 0 for one in HighwayClass})
    found = route(restrictions=low_bridge(), load=plain)
    assert found is not None
    assert found.distance == Miles.of(34, 70)


def test_a_heavy_penalty_on_the_interstate_keeps_the_load_off_it_entirely():
    dear = search(penalties={**PENALTY, HighwayClass.INTERSTATE: 20})
    found = route(load=dear)
    assert found is not None
    assert found.idents == ("US201-1", "US201-2", "SA148-2", "SR43-1")
    assert found.distance == Miles.of(35, 20)


def test_the_same_question_gives_the_same_answer_twice():
    first = route(restrictions=low_bridge())
    second = route(restrictions=low_bridge())
    assert first is not None and second is not None
    assert first.idents == second.idents


def test_everywhere_this_load_can_get_to_comes_back_in_one_list():
    places = reachable(
        road_log(), "skowhegan", search(), RestrictionBook(), SeasonBook(), statutes()
    )
    assert "anson" in places
    assert "madison" in places
    assert "newport" in places
    assert "waterville" in places


def test_a_closure_cuts_off_everything_behind_it():
    blocked = RestrictionBook()
    blocked.add(Restriction("R-3", "US2-2", RestrictionKind.CLOSURE, "bridge out"))
    places = reachable(road_log(), "newport", search(), blocked, SeasonBook(), statutes())
    assert places == ("newport",)


def test_the_card_index_leaves_a_route_alone_when_nothing_has_trouble_on_it():
    found = route(turns=coded_corners())
    assert found is not None
    assert found.idents == ("I95-1", "I95-2")


def test_a_tight_corner_sends_a_long_load_round_the_back_rather_than_refusing_it():
    without = route(destination="madison")
    assert without is not None
    assert without.idents == ("I95-1", "US201-2", "US2-1", "SR8-1")
    found = route(destination="madison", turns=coded_corners())
    assert found is not None
    assert found.idents == ("I95-1", "I95-2", "SR43-1", "SA148-1", "SR8-1")


def test_a_load_inside_the_plain_length_still_gets_round_that_corner():
    short = search(length="70'")
    found = route(destination="madison", load=short, turns=coded_corners())
    assert found is not None
    assert found.idents == ("I95-1", "US201-2", "US2-1", "SR8-1")


def test_the_way_round_costs_more_and_the_router_pays_it():
    short = route(destination="madison", load=search(length="70'"), turns=coded_corners())
    long_way = route(destination="madison", turns=coded_corners())
    assert short is not None and long_way is not None
    assert long_way.distance > short.distance


def test_the_corner_is_read_off_the_road_the_load_arrived_on():
    # Into Norridgewock on US 2 is the square and it is too tight. Into
    # Norridgewock on SA 148 is the other end of the village and it is not.
    book = coded_corners()
    through = find(
        road_log(),
        "canaan",
        "madison",
        search(),
        RestrictionBook(),
        SeasonBook(),
        statutes(),
        turns=book,
    )
    assert through is not None
    assert through.idents == ("SA148-1", "SR8-1")


def test_a_closure_and_a_corner_together_can_cut_a_place_off_entirely():
    blocked = RestrictionBook()
    blocked.add(Restriction("R-4", "SA148-1", RestrictionKind.CLOSURE, "culvert out"))
    assert route(destination="madison", restrictions=blocked, turns=coded_corners()) is None
    assert route(destination="madison", restrictions=blocked) is not None


def test_the_reach_question_reads_the_corners_the_same_way():
    blocked = RestrictionBook()
    blocked.add(Restriction("R-5", "SA148-1", RestrictionKind.CLOSURE, "culvert out"))
    places = reachable(
        road_log(),
        "waterville",
        search(),
        blocked,
        SeasonBook(),
        statutes(),
        turns=coded_corners(),
    )
    assert "madison" not in places
    assert "norridgewock" in places
    assert "anson" in places


def test_without_the_card_index_the_reach_question_answers_as_it_always_did():
    blocked = RestrictionBook()
    blocked.add(Restriction("R-6", "SA148-1", RestrictionKind.CLOSURE, "culvert out"))
    places = reachable(road_log(), "waterville", search(), blocked, SeasonBook(), statutes())
    assert "madison" in places


def test_the_same_question_gives_the_same_answer_twice_with_corners_too():
    first = route(restrictions=low_bridge(), turns=coded_corners())
    second = route(restrictions=low_bridge(), turns=coded_corners())
    assert first is not None and second is not None
    assert first.idents == second.idents
