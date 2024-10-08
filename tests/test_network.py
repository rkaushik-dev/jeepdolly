"""The road log: segments, the nodes they join, and what leaves a place."""

import pytest

from jeepdolly.domain.highway import HighwayClass, Jurisdiction, Route
from jeepdolly.domain.network import Network, Node, Segment
from jeepdolly.errors import Conflict, Impossible, NotOnFile
from jeepdolly.measure import Miles
from support import place, road_log


def segment(ident="X-1", route="US 2", start="skowhegan", end="norridgewock", length=620, **kw):
    return Segment(ident, Route.parse(route), start, end, Miles(length), place(start), **kw)


def test_the_road_log_holds_every_segment_and_finds_one_by_its_ident():
    network = road_log()
    assert len(network) == 12
    assert "US201-2" in network
    piece = network.segment("US201-2")
    assert piece.start == "fairfield"
    assert piece.length == Miles.of(12, 40)
    assert piece.highway_class is HighwayClass.ARTERIAL


def test_a_segment_prints_the_way_the_road_log_lists_it():
    assert str(road_log().segment("SA148-1")) == "SA148-1 SA 148 norridgewock to canaan 9.40 mi"


def test_a_segment_that_starts_where_it_ends_is_a_typing_mistake():
    with pytest.raises(Impossible):
        segment(start="skowhegan", end="skowhegan")


def test_a_segment_with_no_length_is_not_a_segment():
    with pytest.raises(Impossible):
        segment(length=0)


def test_a_node_is_named_or_it_is_not_a_node():
    with pytest.raises(Impossible):
        Node("  ", "Skowhegan")


def test_the_other_end_of_a_segment_is_asked_for_from_either_end():
    piece = road_log().segment("US201-2")
    assert piece.other_end("fairfield") == "skowhegan"
    assert piece.other_end("skowhegan") == "fairfield"
    with pytest.raises(Impossible):
        piece.other_end("newport")


def test_everything_leaving_a_place_comes_back_in_one_list():
    leaving = road_log().leaving("skowhegan")
    assert [one.ident for one in leaving] == ["SA148-2", "US2-1", "US201-2", "US201-3"]


def test_a_one_way_segment_leaves_only_from_the_end_it_starts_at():
    network = road_log()
    network.add_segment(segment(ident="RAMP-1", start="pittsfield", end="newport", one_way=True))
    assert "RAMP-1" in [one.ident for one in network.leaving("pittsfield")]
    assert "RAMP-1" not in [one.ident for one in network.leaving("newport")]


def test_the_segments_joining_two_places_are_found_in_either_direction():
    network = road_log()
    assert [one.ident for one in network.between("waterville", "fairfield")] == ["I95-1", "US201-1"]
    assert [one.ident for one in network.between("fairfield", "waterville")] == ["I95-1", "US201-1"]
    assert network.between("waterville", "anson") == ()


def test_the_segments_of_one_route_come_back_in_road_log_order():
    on_201 = road_log().on_route(Route.parse("US 201"))
    assert [one.ident for one in on_201] == ["US201-1", "US201-2", "US201-3"]


def test_the_segments_of_one_district_are_what_a_thaw_order_reaches():
    fourth = road_log().in_district(4)
    assert len(fourth) == 7
    assert all(one.jurisdiction.district == 4 for one in fourth)


def test_a_segment_naming_a_node_nobody_filed_is_refused():
    network = road_log()
    with pytest.raises(NotOnFile) as refusal:
        network.add_segment(segment(ident="X-9", start="skowhegan", end="bangor"))
    assert "bangor" in str(refusal.value)


def test_the_same_segment_or_node_may_not_be_filed_twice():
    network = road_log()
    with pytest.raises(Conflict):
        network.add_segment(segment(ident="US2-1"))
    with pytest.raises(Conflict):
        network.add_node(Node("skowhegan", "Skowhegan"))


def test_asking_about_a_place_that_is_not_on_file_says_which_one():
    network = road_log()
    with pytest.raises(NotOnFile) as refusal:
        network.leaving("bangor")
    assert refusal.value.facts["node"] == "bangor"
    with pytest.raises(NotOnFile):
        network.segment("NOPE-1")


def test_the_log_lists_its_nodes_and_segments_in_a_stable_order():
    network = road_log()
    assert [one.name for one in network.nodes()][:3] == ["anson", "canaan", "fairfield"]
    assert [one.ident for one in network.segments()][0] == "I95-1"


def test_a_town_way_is_on_the_log_even_though_no_permit_names_it():
    piece = road_log().segment("TR104-1")
    assert piece.highway_class is HighwayClass.TOWN_WAY
    assert not piece.highway_class.permittable


def test_an_empty_network_can_be_built_up_a_piece_at_a_time():
    network = Network()
    network.add_node(Node("skowhegan", "Skowhegan"))
    network.add_node(Node("norridgewock", "Norridgewock"))
    network.add_segment(
        Segment(
            "US2-1",
            Route.parse("US 2"),
            "skowhegan",
            "norridgewock",
            Miles(620),
            Jurisdiction("Somerset", "Skowhegan", 4),
        )
    )
    assert len(network) == 1
    assert network.node("skowhegan").town == "Skowhegan"
