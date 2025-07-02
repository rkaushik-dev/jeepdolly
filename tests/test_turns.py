"""The card index of corners, and what it will and will not file."""

import pytest

from jeepdolly.errors import Conflict, Impossible
from jeepdolly.routing.turns import Corner, TurnBook


def book(radius=40):
    made = TurnBook()
    made.add(Corner("skowhegan", "US201-2", "SA148-2", radius, "granite kerb and a bank"))
    made.add(Corner("canaan", "SA148-2", "SR43-1", 60))
    return made


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
