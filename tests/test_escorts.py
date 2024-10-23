"""The escort table, and the two places the class of road changes the answer."""

from jeepdolly.domain.envelope import Envelope
from jeepdolly.domain.highway import HighwayClass
from jeepdolly.escorts.rules import conditions, required

ARTERIAL = (HighwayClass.ARTERIAL,)
INTERSTATE = (HighwayClass.INTERSTATE,)
MIXED = (HighwayClass.INTERSTATE, HighwayClass.ARTERIAL)


def load(width="10'", height="13'6", length="80'", rear="0"):
    return Envelope.read(width, height, length, "0", rear)


def test_a_small_load_needs_nobody():
    escorts = required(load(), ARTERIAL)
    assert not escorts.any
    assert escorts.total == 0
    assert str(escorts) == "no escorts"
    assert conditions(escorts) == ()


def test_past_twelve_feet_somebody_rides_in_front():
    escorts = required(load(width="13'"), ARTERIAL)
    assert escorts.front == 1
    assert escorts.rear == 0
    assert not escorts.police
    assert str(escorts) == "1 front"
    assert escorts.reasons == ("width over 12 ft",)


def test_past_fourteen_feet_somebody_rides_behind_as_well():
    escorts = required(load(width="15'"), ARTERIAL)
    assert (escorts.front, escorts.rear) == (1, 1)
    assert not escorts.police
    assert str(escorts) == "1 front, 1 rear"


def test_past_sixteen_feet_a_trooper_comes_too():
    escorts = required(load(width="17'"), ARTERIAL)
    assert escorts.police
    assert "police escort" in str(escorts)
    assert "width over 16 ft" in escorts.reasons


def test_a_divided_highway_does_not_need_the_front_car_for_width():
    escorts = required(load(width="15'"), INTERSTATE)
    assert escorts.front == 0
    assert escorts.rear == 1


def test_a_trip_that_leaves_the_interstate_needs_the_front_car_again():
    escorts = required(load(width="15'"), MIXED)
    assert escorts.front == 1
    assert escorts.rear == 1


def test_a_long_combination_needs_a_rear_car_however_narrow_it_is():
    escorts = required(load(length="110'"), ARTERIAL)
    assert (escorts.front, escorts.rear) == (0, 1)
    assert escorts.reasons == ("length over 100 ft",)


def test_a_very_long_combination_needs_one_at_each_end():
    escorts = required(load(length="130'"), ARTERIAL)
    assert (escorts.front, escorts.rear) == (1, 1)


def test_a_long_rear_overhang_needs_a_rear_car_on_its_own():
    escorts = required(load(rear="12'"), ARTERIAL)
    assert escorts.rear == 1
    assert escorts.reasons == ("rear overhang over 10 ft",)


def test_a_tall_load_needs_a_pole_car_and_it_goes_in_front():
    escorts = required(load(height="15'"), ARTERIAL)
    assert escorts.front == 1
    assert escorts.height_pole
    assert "with height pole" in str(escorts)


def test_the_pole_car_is_needed_on_the_interstate_too():
    escorts = required(load(height="15'"), INTERSTATE)
    assert escorts.front == 1
    assert escorts.height_pole


def test_a_superload_carries_a_trooper_whatever_it_measures():
    escorts = required(load(), ARTERIAL, superload=True)
    assert escorts.police
    assert escorts.total == 0
    assert escorts.reasons == ("superload",)


def test_two_reasons_for_the_same_car_do_not_order_two_cars():
    escorts = required(load(width="15'", length="110'", rear="12'"), ARTERIAL)
    assert escorts.rear == 1
    assert len(escorts.reasons) == 4


def test_the_conditions_print_in_the_order_the_permit_face_wants():
    escorts = required(load(width="17'", height="15'", length="130'"), ARTERIAL)
    printed = conditions(escorts)
    assert printed[0].startswith("1 certified pilot car ahead")
    assert "height pole set one inch above the load" in printed[0]
    assert printed[1] == "1 certified pilot car behind the load"
    assert printed[2].startswith("police escort")


def test_the_thresholds_are_read_from_the_table_and_not_written_in_the_code():
    just_under = required(load(width="12'"), ARTERIAL)
    just_over = required(load(width="12'1"), ARTERIAL)
    assert not just_under.any
    assert just_over.front == 1
