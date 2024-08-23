"""The measures, and the fax formats they have to survive."""

import pytest

from jeepdolly.errors import Unreadable
from jeepdolly.measure import Inches, Miles, Pounds


def test_a_size_reads_back_as_feet_and_inches():
    assert str(Inches.of(14, 6)) == "14 ft 6 in"
    assert str(Inches.of(12)) == "12 ft"
    assert str(Inches(8)) == "8 in"
    assert repr(Inches(174)) == "Inches(174)"


def test_every_format_the_office_receives_means_the_same_number():
    for written in ["14'6\"", "14' 6\"", "14 ft 6 in", "14ft6in", "14-6", "174"]:
        assert Inches.parse(written) == Inches(174), written


def test_a_size_written_in_whole_feet_needs_no_inches_part():
    assert Inches.parse("13'") == Inches(156)
    assert Inches.parse("13 ft") == Inches(156)
    assert Inches.parse("13 feet") == Inches(156)


def test_a_size_that_cannot_be_read_is_refused_rather_than_guessed():
    with pytest.raises(Unreadable):
        Inches.parse("about fourteen feet")
    with pytest.raises(Unreadable):
        Inches.parse("14.5")
    with pytest.raises(Unreadable):
        Inches.parse("")


def test_a_size_is_whole_inches_and_a_float_is_not_a_size():
    with pytest.raises(TypeError):
        Inches(14.5)
    with pytest.raises(TypeError):
        Inches(True)


def test_feet_and_the_spare_inches_carry_the_sign_together():
    rear = Inches(-30)
    assert rear.feet == -2
    assert rear.spare_inches == -6
    assert str(rear) == "-2 ft 6 in"
    assert abs(rear) == Inches(30)


def test_sizes_add_and_subtract_but_not_with_bare_numbers():
    assert Inches.of(8, 6) + Inches(6) == Inches.of(9)
    assert Inches.of(9) - Inches(6) == Inches.of(8, 6)
    assert Inches(15) * 3 == Inches(45)
    with pytest.raises(TypeError):
        Inches(96) + 6
    with pytest.raises(TypeError):
        Inches(96) * 1.5


def test_over_a_limit_is_the_excess_and_never_a_negative_excess():
    legal = Inches.of(8, 6)
    assert Inches.of(12).over(legal) == Inches(42)
    assert Inches.of(8).over(legal) == Inches(0)


def test_sizes_sort_by_the_inches_they_hold():
    loads = [Inches.of(14), Inches.of(8, 6), Inches.of(11, 3)]
    assert sorted(loads) == [Inches.of(8, 6), Inches.of(11, 3), Inches.of(14)]
    assert max(loads) == Inches(168)


def test_a_weight_reads_back_with_the_commas_the_office_writes():
    assert str(Pounds(132400)) == "132,400 lb"
    assert str(Pounds(0)) == "0 lb"
    assert repr(Pounds(80000)) == "Pounds(80000)"


def test_a_weight_parses_out_of_the_shapes_that_arrive():
    assert Pounds.parse("132400") == Pounds(132400)
    assert Pounds.parse("132,400") == Pounds(132400)
    assert Pounds.parse("132,400 lb") == Pounds(132400)
    assert Pounds.parse("132400lbs") == Pounds(132400)
    assert Pounds.parse("132400#") == Pounds(132400)


def test_tons_are_read_and_then_never_stored_again():
    assert Pounds.parse("40 tons") == Pounds(80000)
    assert Pounds.parse("1 ton") == Pounds(2000)
    assert Pounds.of_tons(66) == Pounds(132000)


def test_a_weight_that_cannot_be_read_is_refused():
    with pytest.raises(Unreadable):
        Pounds.parse("heavy")
    with pytest.raises(Unreadable):
        Pounds.parse("40.5 tons")
    with pytest.raises(Unreadable):
        Pounds.parse("132.4 lb")


def test_a_weight_is_whole_pounds():
    with pytest.raises(TypeError):
        Pounds(132400.5)


def test_weights_add_subtract_and_multiply_by_a_count_of_axles():
    assert Pounds(20000) + Pounds(14000) == Pounds(34000)
    assert Pounds(34000) - Pounds(20000) == Pounds(14000)
    assert Pounds(20000) * 4 == Pounds(80000)
    assert -Pounds(500) == Pounds(-500)
    with pytest.raises(TypeError):
        Pounds(20000) + 500


def test_a_weight_over_a_limit_is_the_overage_the_permit_prices():
    assert Pounds(99000).over(Pounds(80000)) == Pounds(19000)
    assert Pounds(72000).over(Pounds(80000)) == Pounds(0)


def test_a_distance_holds_hundredths_because_the_road_log_does():
    assert Miles.of(12, 40) == Miles(1240)
    assert str(Miles(1240)) == "12.40 mi"
    assert str(Miles(7)) == "0.07 mi"
    assert repr(Miles(1240)) == "Miles(1240)"


def test_a_distance_parses_off_the_road_log():
    assert Miles.parse("12.40") == Miles(1240)
    assert Miles.parse("12.4") == Miles(1240)
    assert Miles.parse("12") == Miles(1200)
    assert Miles.parse("12.40 mi") == Miles(1240)
    assert Miles.parse("12.40 miles") == Miles(1240)


def test_a_distance_with_more_than_two_places_is_not_off_the_road_log():
    with pytest.raises(Unreadable):
        Miles.parse("12.400")
    with pytest.raises(Unreadable):
        Miles.parse("twelve")
    with pytest.raises(TypeError):
        Miles(12.4)


def test_pricing_rounds_the_distance_up_to_the_next_whole_mile():
    assert Miles.of(12, 40).rounded_up() == 13
    assert Miles.of(12).rounded_up() == 12
    assert Miles(1).rounded_up() == 1
    assert Miles(0).rounded_up() == 0


def test_distances_add_along_a_route():
    legs = [Miles.of(4, 20), Miles.of(11, 90), Miles.of(0, 30)]
    total = sum(legs, Miles(0))
    assert total == Miles.of(16, 40)
    assert (total - Miles.of(4, 20)) == Miles.of(12, 20)
