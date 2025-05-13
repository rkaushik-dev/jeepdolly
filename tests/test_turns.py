"""Corners, which stop a long load more often than a bridge does."""

import pytest

from jeepdolly.domain.envelope import Envelope
from jeepdolly.errors import Conflict, Impossible
from jeepdolly.routing.itinerary import Itinerary
from jeepdolly.routing.turns import (
    Corner,
    TurnBook,
    corners_on,
    radius_wanted,
    too_tight,
)
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


def test_a_corner_is_the_same_corner_driven_either_way():
    corner = Corner("skowhegan", "US201-2", "SA148-2", 40)
    assert corner.joins("US201-2", "SA148-2")
    assert corner.joins("SA148-2", "US201-2")
    assert not corner.joins("US201-2", "US2-1")


def test_a_corner_with_no_radius_or_one_segment_is_refused():
    with pytest.raises(Impossible):
        Corner("skowhegan", "US201-2", "SA148-2", 0)
    with pytest.raises(Impossible):
        Corner("skowhegan", "US201-2", "US201-2", 40)


def test_a_corner_prints_the_way_the_card_index_reads():
    corner = Corner("skowhegan", "US201-2", "SA148-2", 40, "granite kerb")
    assert str(corner) == "skowhegan US201-2 to SA148-2, 40 ft: granite kerb"


def test_the_same_corner_is_not_filed_twice_either_way_round():
    made = book()
    with pytest.raises(Conflict):
        made.add(Corner("skowhegan", "SA148-2", "US201-2", 45))
    assert len(made) == 2


def test_the_book_answers_by_node_and_by_the_two_segments():
    made = book()
    assert made.between("skowhegan", "US201-2", "SA148-2").radius_feet == 40
    assert made.between("skowhegan", "US201-2", "US2-1") is None
    assert len(made.at("skowhegan")) == 1


def test_the_tight_corners_are_the_list_the_office_keeps_on_the_wall():
    made = book(radius=32)
    assert [one.node for one in made.tight()] == ["skowhegan"]
    assert made.tight(feet=20) == ()


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
