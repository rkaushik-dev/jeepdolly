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
from jeepdolly.seasons import SeasonBook
from support import road_log

MARCH = date(2025, 3, 3)


def search(height="13'6", gross=94000, penalties=None):
    return Search(
        envelope=Envelope.read("12'", height, "82'"),
        gross=Pounds(gross),
        day=MARCH,
        penalties=penalties if penalties is not None else dict(PENALTY),
    )


def route(origin="waterville", destination="pittsfield", restrictions=None, load=None):
    return find(
        road_log(),
        origin,
        destination,
        load or search(),
        restrictions or RestrictionBook(),
        SeasonBook(),
        statutes(),
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
