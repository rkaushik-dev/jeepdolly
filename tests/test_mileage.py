"""Chargeable mileage, which is rounded by class and never by the total."""

import pytest

from jeepdolly.domain.highway import HighwayClass
from jeepdolly.errors import Impossible
from jeepdolly.measure import Miles
from jeepdolly.routing.itinerary import Itinerary
from jeepdolly.routing.mileage import chargeable, unpermittable_miles
from support import road_log

ARTERIAL = HighwayClass.ARTERIAL
STATE_AID = HighwayClass.STATE_AID
ACROSS = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")


def trip(origin="waterville", idents=ACROSS):
    return Itinerary(road_log(), origin, idents)


def test_each_class_is_rounded_up_on_its_own_and_then_added():
    bill = chargeable(trip())
    assert bill.on(ARTERIAL) == 34
    assert bill.on(STATE_AID) == 10
    assert bill.charged == 44
    assert bill.measured == Miles.of(43, 20)


def test_rounding_the_total_instead_would_have_charged_a_mile_less():
    bill = chargeable(trip())
    assert bill.measured.rounded_up() == 44
    assert bill.charged == 44
    mixed = chargeable(trip(idents=("I95-1", "US201-2")))
    assert mixed.measured == Miles.of(17)
    assert mixed.measured.rounded_up() == 17
    assert mixed.charged == 18


def test_the_parts_name_the_classes_the_trip_touched():
    bill = chargeable(trip())
    assert bill.classes() == (ARTERIAL, STATE_AID)
    assert bill.on(HighwayClass.INTERSTATE) == 0
    assert str(bill.parts[1]) == "state aid: 9.40 mi measured, 10 charged"


def test_a_short_trip_is_charged_the_minimum_and_says_so():
    bill = chargeable(trip(origin="skowhegan", idents=("US2-1",)))
    assert bill.measured == Miles.of(6, 20)
    assert bill.charged == 10
    assert bill.minimum_applied
    assert str(bill) == "6.20 mi measured, 10 miles charged (minimum)"


def test_a_trip_past_the_minimum_is_charged_what_it_measures():
    bill = chargeable(trip())
    assert not bill.minimum_applied
    assert str(bill) == "43.20 mi measured, 44 miles charged"


def test_the_minimum_is_an_argument_because_it_is_in_the_schedule():
    bill = chargeable(trip(origin="skowhegan", idents=("US2-1",)), minimum=0)
    assert bill.charged == 7
    assert not bill.minimum_applied


def test_a_negative_minimum_is_refused():
    with pytest.raises(Impossible):
        chargeable(trip(), minimum=-1)


def test_a_town_way_is_driven_and_not_charged_for():
    route = trip(origin="skowhegan", idents=("US201-3", "TR104-1", "SR8-1"))
    bill = chargeable(route)
    assert bill.classes() == (ARTERIAL,)
    assert bill.on(HighwayClass.TOWN_WAY) == 0
    assert unpermittable_miles(route) == Miles.of(3, 20)
    assert unpermittable_miles(trip()) == Miles(0)


def test_the_interstate_is_charged_like_anything_else_at_its_own_rate():
    bill = chargeable(trip(idents=("I95-1", "I95-2")))
    assert bill.classes() == (HighwayClass.INTERSTATE,)
    assert bill.on(HighwayClass.INTERSTATE) == 19
    assert bill.charged == 19
