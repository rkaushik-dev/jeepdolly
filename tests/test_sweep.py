"""The swept path rule: what a load wants, and where it comes up short."""

from jeepdolly.domain.envelope import Envelope
from jeepdolly.routing.itinerary import Itinerary
from jeepdolly.routing.sweep import (
    close_run,
    corners_on,
    radius_wanted,
    too_tight,
)
from jeepdolly.routing.turns import Corner, TurnBook
from support import road_log

ACROSS = ("US201-1", "US201-2", "SA148-2", "SR43-1")


def load(length="75'", rear="0"):
    return Envelope.read("12'", "13'6", length, "0", rear)


def book(radius=40):
    made = TurnBook()
    made.add(Corner("skowhegan", "US201-2", "SA148-2", radius, "granite kerb and a bank"))
    made.add(Corner("canaan", "SA148-2", "SR43-1", 60))
    return made


def trip(idents=ACROSS, origin="waterville"):
    return Itinerary(road_log(), origin, idents)


def test_a_plain_length_wants_the_plain_radius():
    assert radius_wanted(load(length="75'")) == 40
    assert radius_wanted(load(length="70'")) == 40


def test_every_foot_past_the_plain_length_wants_another_foot_of_radius():
    assert radius_wanted(load(length="95'")) == 60


def test_a_rear_overhang_swings_wide_and_wants_two_feet_for_one():
    assert radius_wanted(load(rear="10'")) == 60
    assert radius_wanted(load(length="95'", rear="10'")) == 80


def test_only_the_corners_the_trip_actually_turns_at_are_read():
    found = corners_on(trip(), book())
    assert [one.node for one in found] == ["skowhegan", "canaan"]
    straight = corners_on(trip(idents=("US201-1", "US201-2")), book())
    assert straight == ()


def test_a_short_load_gets_round_everything_on_the_route():
    assert too_tight(trip(), load(), book()) == ()


def test_a_long_load_is_stopped_at_the_corner_that_is_too_tight():
    found = too_tight(trip(), load(length="95'"), book())
    assert [one.corner.node for one in found] == ["skowhegan"]
    assert found[0].wanted == 60
    assert found[0].short_by == 20
    assert "this load wants 60 ft" in str(found[0])


def test_a_load_long_enough_is_stopped_at_every_corner():
    found = too_tight(trip(), load(length="140'"), book())
    assert [one.corner.node for one in found] == ["skowhegan", "canaan"]


def test_a_corner_nobody_measured_is_not_in_the_way_of_anything():
    empty = TurnBook()
    assert too_tight(trip(), load(length="200'"), empty) == ()


def test_a_corner_that_clears_by_plenty_is_not_worth_a_condition():
    assert close_run(trip(), load(), book(radius=80)) == ()


def test_a_corner_that_clears_by_a_little_comes_back_as_a_close_run():
    found = close_run(trip(), load(), book(radius=46))
    assert [one.corner.node for one in found] == ["skowhegan"]
    assert found[0].wanted == 40
    assert found[0].spare == 6
    assert "clears by 6 ft" in str(found[0])


def test_a_corner_the_load_will_not_get_round_is_not_a_close_run():
    found = close_run(trip(), load(length="95'"), book())
    assert [one.corner.node for one in found] == ["canaan"]


def test_the_margin_is_an_argument_and_a_wider_one_catches_more():
    plain = close_run(trip(), load(length="95'"), book(radius=80))
    assert [one.corner.node for one in plain] == ["canaan"]
    wide = close_run(trip(), load(length="95'"), book(radius=80), margin=25)
    assert [one.corner.node for one in wide] == ["skowhegan", "canaan"]


def test_the_close_run_and_the_refusal_never_name_the_same_corner():
    envelope = load(length="95'")
    tight = {one.corner.node for one in too_tight(trip(), envelope, book())}
    near = {one.corner.node for one in close_run(trip(), envelope, book())}
    assert tight & near == set()


def test_the_wanted_radius_is_read_off_the_load_and_nothing_else():
    assert radius_wanted(load()) == radius_wanted(Envelope.read("8'6", "10'", "75'"))


def test_a_front_overhang_does_not_swing_the_way_a_rear_one_does():
    front = Envelope.read("12'", "13'6", "75'", "10'", "0")
    assert radius_wanted(front) == radius_wanted(load())


def test_a_corner_that_is_exactly_what_the_load_wants_is_a_close_run_not_a_refusal():
    exact = book(radius=radius_wanted(load()))
    assert too_tight(trip(), load(), exact) == ()
    assert [one.corner.node for one in close_run(trip(), load(), exact)] == ["skowhegan"]


def test_the_corners_on_a_route_come_back_in_the_order_they_are_driven():
    found = corners_on(trip(), book())
    assert [one.node for one in found] == ["skowhegan", "canaan"]
    backwards = corners_on(trip(idents=("SR43-1", "SA148-2"), origin="pittsfield"), book())
    assert [one.node for one in backwards] == ["canaan"]
