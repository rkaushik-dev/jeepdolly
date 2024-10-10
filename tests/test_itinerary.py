"""An itinerary: segments in driven order, and what the trip touches."""

import pytest

from jeepdolly.domain.highway import HighwayClass
from jeepdolly.errors import Impossible, NotOnFile
from jeepdolly.measure import Miles
from jeepdolly.routing.itinerary import Itinerary
from support import road_log

ACROSS = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")


def trip(origin="waterville", idents=ACROSS, network=None):
    return Itinerary(network or road_log(), origin, idents)


def test_an_itinerary_joins_end_to_end_and_knows_where_it_finishes():
    route = trip()
    assert route.origin == "waterville"
    assert route.destination == "pittsfield"
    assert len(route) == 5
    assert repr(route) == "Itinerary(waterville to pittsfield, 5 legs)"


def test_the_legs_carry_the_direction_they_are_driven_in():
    legs = list(trip())
    assert legs[0].entered_at == "waterville"
    assert legs[0].left_at == "fairfield"
    assert str(legs[3]) == "SA 148 norridgewock to canaan"


def test_a_segment_that_does_not_join_the_one_before_it_is_refused():
    with pytest.raises(Impossible) as refusal:
        trip(idents=("US201-1", "SR43-1"))
    assert refusal.value.facts == {"segment": "SR43-1", "at": "fairfield"}


def test_an_itinerary_from_a_place_that_is_not_on_the_log_is_refused():
    with pytest.raises(NotOnFile):
        trip(origin="bangor")


def test_an_itinerary_naming_a_segment_that_is_not_on_the_log_is_refused():
    with pytest.raises(NotOnFile):
        trip(idents=("US201-1", "NOPE-1"))


def test_an_itinerary_with_no_segments_at_all_is_refused():
    with pytest.raises(Impossible):
        trip(idents=())


def test_the_distance_is_the_segments_added_up_off_the_road_log():
    assert trip().distance == Miles.of(43, 20)


def test_the_distance_is_kept_per_class_because_the_fee_is_priced_that_way():
    route = trip()
    assert route.distance_on(HighwayClass.ARTERIAL) == Miles.of(33, 80)
    assert route.distance_on(HighwayClass.STATE_AID) == Miles.of(9, 40)
    assert route.distance_on(HighwayClass.INTERSTATE) == Miles(0)


def test_the_classes_and_districts_touched_are_what_the_rules_are_read_from():
    route = trip()
    assert route.classes() == (HighwayClass.ARTERIAL, HighwayClass.STATE_AID)
    assert route.districts() == (3, 4)


def test_the_towns_run_from_the_origin_through_every_leg():
    assert trip().towns() == (
        "waterville",
        "fairfield",
        "skowhegan",
        "norridgewock",
        "canaan",
        "pittsfield",
    )


def test_a_trip_can_be_asked_whether_it_touches_a_segment():
    route = trip()
    assert route.touches("SA148-1")
    assert not route.touches("TR104-1")
    assert route.idents == ACROSS


def test_a_trip_over_a_town_way_names_the_segment_this_office_cannot_write():
    route = trip(origin="skowhegan", idents=("US201-3", "TR104-1", "SR8-1"))
    assert route.destination == "norridgewock"
    assert route.unpermittable() == ("TR104-1",)
    assert trip().unpermittable() == ()


def test_the_route_prints_on_the_permit_face_as_a_driver_would_read_it():
    assert trip().narrative() == (
        "from Waterville, US 201 to Fairfield, US 201 to Skowhegan, "
        "US 2 to Norridgewock, SA 148 to Canaan, SR 43 to Pittsfield"
    )


def test_a_one_way_segment_cannot_be_driven_backwards_in_an_itinerary():
    network = road_log()
    ramp = network.segment("I95-2")
    network.add_segment(
        type(ramp)(
            "RAMP-1",
            ramp.route,
            "pittsfield",
            "newport",
            Miles(120),
            ramp.jurisdiction,
            one_way=True,
        )
    )
    assert Itinerary(network, "pittsfield", ("RAMP-1",)).destination == "newport"
    with pytest.raises(Impossible):
        Itinerary(network, "newport", ("RAMP-1",))
