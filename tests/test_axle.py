"""Axles, the rubber under them, and what counts as one group."""

import pytest

from jeepdolly.domain.axle import Axle, AxleGroup, AxleKind, group_name
from jeepdolly.measure import Inches, Pounds


def drive(weight=17000, **kw):
    return Axle(AxleKind.DRIVE, Pounds(weight), **kw)


def trailer(weight=16000, **kw):
    return Axle(AxleKind.TRAILER, Pounds(weight), **kw)


def test_a_group_is_named_for_how_many_axles_stand_in_it():
    assert group_name(1) == "single"
    assert group_name(2) == "tandem"
    assert group_name(3) == "tridem"
    assert group_name(4) == "quad"
    assert group_name(5) == "quint"
    assert group_name(8) == "8 axle group"


def test_a_group_of_no_axles_is_not_a_group():
    with pytest.raises(ValueError):
        group_name(0)


def test_an_axle_prints_the_way_the_counter_reads_it_aloud():
    assert str(drive()) == "drive 17,000 lb on 4 tires"
    assert str(trailer(liftable=True)) == "trailer 16,000 lb on 4 tires liftable"
    assert str(Axle(AxleKind.STEER, Pounds(12000), tires=2)) == "steer 12,000 lb on 2 tires"


def test_the_rubber_on_the_ground_is_the_width_times_the_count():
    assert drive().tire_inches == Inches(44)
    assert drive(tires=8, tire_width=Inches(11)).tire_inches == Inches(88)
    steer = Axle(AxleKind.STEER, Pounds(12000), tires=2, tire_width=Inches(16))
    assert steer.tire_inches == Inches(32)


def test_the_tire_limit_is_the_published_rate_times_that_width():
    assert drive().tire_limit(600) == Pounds(26400)
    assert drive(tires=2, tire_width=Inches(16)).tire_limit(600) == Pounds(19200)


def test_an_axle_over_its_tire_limit_reports_the_overage_and_not_a_verdict():
    narrow = Axle(AxleKind.JEEP, Pounds(22000), tires=4, tire_width=Inches(8))
    assert narrow.tire_limit(600) == Pounds(19200)
    assert narrow.over_tire_limit(600) == Pounds(2800)
    assert drive().over_tire_limit(600) == Pounds(0)


def test_an_axle_needs_an_even_count_of_at_least_two_tires():
    with pytest.raises(ValueError):
        drive(tires=3)
    with pytest.raises(ValueError):
        drive(tires=0)


def test_an_axle_cannot_carry_a_negative_weight_or_stand_on_nothing():
    with pytest.raises(ValueError):
        drive(weight=-1)
    with pytest.raises(ValueError):
        drive(tire_width=Inches(0))


def test_a_steer_axle_does_not_lift_and_saying_so_is_refused():
    with pytest.raises(ValueError):
        Axle(AxleKind.STEER, Pounds(12000), tires=2, liftable=True)


def test_a_group_carries_one_spacing_fewer_than_it_has_axles():
    tandem = AxleGroup((drive(), drive()), (Inches(54),))
    assert tandem.count == 2
    assert len(tandem) == 2
    assert tandem.name == "tandem"
    assert tandem.extent == Inches(54)


def test_a_single_axle_is_a_group_with_no_spacing_at_all():
    single = AxleGroup((Axle(AxleKind.STEER, Pounds(12000), tires=2),))
    assert single.name == "single"
    assert single.extent == Inches(0)
    assert single.weight == Pounds(12000)


def test_a_group_adds_up_its_axles_and_says_so():
    tridem = AxleGroup((trailer(), trailer(), trailer()), (Inches(52), Inches(52)))
    assert tridem.weight == Pounds(48000)
    assert tridem.extent == Inches(104)
    assert str(tridem) == "tridem 48,000 lb over 8 ft 8 in"


def test_axles_further_apart_than_eight_feet_are_not_one_group():
    with pytest.raises(ValueError) as refusal:
        AxleGroup((trailer(), trailer()), (Inches.of(9),))
    assert "too far apart" in str(refusal.value)


def test_axles_standing_in_the_same_place_are_not_two_axles():
    with pytest.raises(ValueError):
        AxleGroup((trailer(), trailer()), (Inches(0),))


def test_the_spacing_count_has_to_match_the_axle_count():
    with pytest.raises(ValueError) as refusal:
        AxleGroup((trailer(), trailer(), trailer()), (Inches(52),))
    assert "need 2 spacings" in str(refusal.value)


def test_an_empty_group_is_refused_before_anything_else_is_asked_of_it():
    with pytest.raises(ValueError):
        AxleGroup(())


def test_a_group_knows_how_much_of_it_can_be_lifted_off_the_road():
    quad = AxleGroup(
        (trailer(), trailer(liftable=True), trailer(liftable=True), trailer()),
        (Inches(52), Inches(52), Inches(52)),
    )
    assert quad.liftable_count == 2
    assert quad.name == "quad"


def test_a_group_tire_limit_is_the_sum_of_what_its_axles_will_carry():
    tandem = AxleGroup((drive(tires=8), drive(tires=8)), (Inches(54),))
    assert tandem.tire_limit(600) == Pounds(105600)


def test_the_heaviest_axle_in_a_group_is_the_one_the_scale_reads():
    tandem = AxleGroup((trailer(16000), trailer(19500)), (Inches(54),))
    assert tandem.heaviest().weight == Pounds(19500)
