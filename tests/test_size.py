"""The three boxes: legal, routine, and the one that needs a person."""

import pytest

from jeepdolly.domain.envelope import Envelope
from jeepdolly.domain.highway import HighwayClass
from jeepdolly.errors import Impossible, NotOnFile
from jeepdolly.limits.size import SizeBook, SizeClass, SizeTable, standard_book
from jeepdolly.measure import Inches

ARTERIAL = HighwayClass.ARTERIAL
STATE_AID = HighwayClass.STATE_AID


def load(width="12'", height="13'6", length="80'", front="0", rear="0"):
    return Envelope.read(width, height, length, front, rear)


def test_a_load_inside_the_legal_box_is_not_this_offices_business():
    table = standard_book().on(ARTERIAL)
    assert table.classify(load(width="8'", length="70'")) is SizeClass.LEGAL
    assert not table.over_legal(load(width="8'", length="70'")).any


def test_a_wide_excavator_is_an_ordinary_permit_over_the_counter():
    table = standard_book().on(ARTERIAL)
    assert table.classify(load()) is SizeClass.ROUTINE
    assert table.over_legal(load()).over() == ("width", "length")
    assert table.reasons_for_review(load()) == ()


def test_a_load_past_the_routine_box_goes_to_a_person():
    table = standard_book().on(ARTERIAL)
    wide = load(width="18'")
    assert table.classify(wide) is SizeClass.REVIEW
    assert table.reasons_for_review(wide) == ("width",)
    assert table.over_routine(wide).width == Inches(24)


def test_the_review_reasons_name_every_measure_that_put_it_there():
    table = standard_book().on(ARTERIAL)
    big = load(width="18'", height="17'", length="140'", rear="20'")
    assert table.reasons_for_review(big) == ("width", "height", "length", "rear_overhang")


def test_the_secondary_roads_hold_a_tighter_routine_box():
    book = standard_book()
    fifteen = load(width="15'")
    assert book.on(ARTERIAL).classify(fifteen) is SizeClass.ROUTINE
    assert book.on(STATE_AID).classify(fifteen) is SizeClass.REVIEW


def test_a_trip_over_several_classes_is_held_to_the_tightest_of_each():
    table = standard_book().tightest((ARTERIAL, STATE_AID))
    assert table.legal.length == Inches.of(68)
    assert table.routine.width == Inches.of(14)
    assert table.routine.height == Inches.of(14, 6)
    assert table.classify(load(width="15'")) is SizeClass.REVIEW


def test_the_tightest_of_one_class_is_that_class():
    table = standard_book().tightest((ARTERIAL,))
    assert table.routine.width == Inches.of(16)


def test_a_trip_over_no_roads_at_all_is_refused():
    with pytest.raises(Impossible):
        standard_book().tightest(())


def test_asking_for_a_class_with_no_table_says_which_one():
    with pytest.raises(NotOnFile) as refusal:
        SizeBook().on(ARTERIAL)
    assert refusal.value.facts["highway_class"] == "arterial"


def test_a_routine_box_tighter_than_the_legal_one_is_a_typing_mistake():
    legal = Envelope.read("8'6", "13'6", "75'", "3'", "4'")
    with pytest.raises(Impossible):
        SizeTable(ARTERIAL, legal, Envelope.read("8'", "16'", "120'", "10'", "15'"))
    with pytest.raises(Impossible):
        SizeTable(ARTERIAL, legal, Envelope.read("16'", "13'", "120'", "10'", "15'"))
    with pytest.raises(Impossible):
        SizeTable(ARTERIAL, legal, Envelope.read("16'", "16'", "70'", "10'", "15'"))


def test_the_book_covers_every_class_of_road():
    book = standard_book()
    assert len(book) == 4
    assert str(book.on(HighwayClass.TOWN_WAY)) == "town way size table"
    assert str(SizeClass.REVIEW) == "review"


def test_a_load_exactly_on_the_routine_edge_is_still_routine():
    table = standard_book().on(ARTERIAL)
    assert table.classify(load(width="16'")) is SizeClass.ROUTINE
    assert table.classify(load(width="16'1")) is SizeClass.REVIEW
