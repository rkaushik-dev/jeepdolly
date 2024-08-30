"""Route designations, the class they imply, and who maintains the road."""

import pytest

from jeepdolly.domain.highway import HighwayClass, Jurisdiction, Route
from jeepdolly.errors import Impossible, Unreadable


def test_a_route_reads_the_same_however_the_clerk_typed_it():
    for written in ["I-95", "I 95", "i95", " I-95 "]:
        assert Route.parse(written) == Route("I", 95), written
    for written in ["US 2", "us-2", "US2"]:
        assert Route.parse(written) == Route("US", 2), written


def test_a_route_keeps_a_letter_suffix_because_the_road_log_does():
    assert Route.parse("SR-27A") == Route("SR", 27, "A")
    assert str(Route.parse("sr27a")) == "SR 27A"


def test_a_route_prints_with_the_dash_only_where_the_signs_use_one():
    assert str(Route.parse("I-95")) == "I-95"
    assert str(Route.parse("US 2")) == "US 2"
    assert str(Route.parse("TR 104")) == "TR 104"


def test_something_that_is_not_a_route_is_refused_and_quoted_back():
    with pytest.raises(Unreadable) as refusal:
        Route.parse("the old county road")
    assert refusal.value.facts["written"] == "the old county road"
    with pytest.raises(Unreadable):
        Route.parse("US 1234")
    with pytest.raises(Unreadable):
        Route.parse("XX 27")


def test_a_route_built_by_hand_is_checked_as_hard_as_a_parsed_one():
    with pytest.raises(Impossible):
        Route("XX", 27)
    with pytest.raises(Impossible):
        Route("US", 0)
    with pytest.raises(Impossible):
        Route("US", 1000)


def test_the_prefix_decides_the_class_of_road():
    assert Route.parse("I-95").highway_class is HighwayClass.INTERSTATE
    assert Route.parse("US 2").highway_class is HighwayClass.ARTERIAL
    assert Route.parse("SR 27").highway_class is HighwayClass.ARTERIAL
    assert Route.parse("SA 148").highway_class is HighwayClass.STATE_AID
    assert Route.parse("TR 104").highway_class is HighwayClass.TOWN_WAY


def test_the_state_cannot_write_a_permit_over_a_town_way():
    assert HighwayClass.INTERSTATE.permittable
    assert HighwayClass.STATE_AID.permittable
    assert not HighwayClass.TOWN_WAY.permittable
    assert not HighwayClass.TOWN_WAY.state_maintained


def test_only_the_interstate_is_federal_and_it_prints_its_own_name():
    assert HighwayClass.INTERSTATE.federal
    assert not HighwayClass.ARTERIAL.federal
    assert str(HighwayClass.STATE_AID) == "state aid"


def test_routes_sort_by_prefix_then_number_for_a_report():
    routes = [Route.parse("US 2"), Route.parse("I-95"), Route.parse("SR 27"), Route.parse("SR 11")]
    assert [str(one) for one in sorted(routes)] == ["I-95", "SR 11", "SR 27", "US 2"]


def test_a_place_says_where_to_ask_and_which_seasonal_order_reaches_it():
    place = Jurisdiction("Somerset", "Skowhegan", 3)
    assert place.region == "district 3"
    assert str(place) == "Skowhegan, Somerset county (district 3)"


def test_a_district_the_state_does_not_have_is_refused():
    with pytest.raises(Impossible):
        Jurisdiction("Somerset", "Skowhegan", 0)
    with pytest.raises(Impossible):
        Jurisdiction("Somerset", "Skowhegan", 12)


def test_a_place_with_no_town_is_not_a_place():
    with pytest.raises(Impossible):
        Jurisdiction("Somerset", "  ", 3)
    with pytest.raises(Impossible):
        Jurisdiction("", "Skowhegan", 3)
