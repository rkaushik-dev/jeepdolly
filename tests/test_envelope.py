"""The envelope, and the excess it shows against a limiting box."""

import pytest

from jeepdolly.domain.envelope import Envelope, Excess
from jeepdolly.measure import Inches

#: Legal size on a state highway, which is the box most loads are compared to.
LEGAL = Envelope(Inches.of(8, 6), Inches.of(13, 6), Inches.of(75), Inches.of(3), Inches.of(4))


def box(width="12'", height="13'6\"", length="80'", front="0", rear="0"):
    return Envelope.read(width, height, length, front, rear)


def test_an_envelope_reads_off_the_form_in_whatever_the_hauler_wrote():
    load = Envelope.read("12'6\"", "14-2", "92 ft", "24", "10'")
    assert load.width == Inches(150)
    assert load.height == Inches(170)
    assert load.length == Inches(1104)
    assert load.front_overhang == Inches(24)
    assert load.rear_overhang == Inches(120)


def test_an_envelope_prints_the_way_the_permit_face_prints_it():
    assert str(box()) == "12 ft wide, 13 ft 6 in high, 80 ft long"
    assert str(box(front="2'", rear="9'")).endswith("2 ft front and 9 ft rear overhang")


def test_a_load_without_a_width_or_a_height_is_not_a_load():
    with pytest.raises(ValueError):
        Envelope(Inches(0), Inches.of(13), Inches.of(70))
    with pytest.raises(ValueError):
        Envelope(Inches.of(8), Inches(0), Inches.of(70))
    with pytest.raises(ValueError):
        Envelope(Inches.of(8), Inches.of(13), Inches(0))


def test_an_overhang_measured_inward_is_a_typing_mistake():
    with pytest.raises(ValueError):
        Envelope(Inches.of(8), Inches.of(13), Inches.of(70), Inches(-12))


def test_overhangs_longer_than_the_combination_are_refused():
    with pytest.raises(ValueError) as refusal:
        Envelope(Inches.of(8), Inches.of(13), Inches.of(20), Inches.of(12), Inches.of(12))
    assert "longer than the whole combination" in str(refusal.value)


def test_a_measure_that_is_not_a_size_is_refused_before_the_arithmetic():
    with pytest.raises(TypeError):
        Envelope(102, Inches.of(13), Inches.of(70))


def test_the_overhangs_add_up_and_say_whether_anything_projects():
    assert box(front="2'", rear="9'").overhang == Inches(132)
    assert box(front="2'", rear="9'").projects
    assert not box().projects
    assert box().overhang == Inches(0)


def test_a_load_inside_the_legal_box_shows_no_excess_at_all():
    legal_load = Envelope.read("8'", "13'", "70'", "1'", "3'")
    excess = legal_load.excess_over(LEGAL)
    assert not excess.any
    assert excess.over() == ()
    assert excess.worst() is None
    assert str(excess) == "within limits"
    assert legal_load.within(LEGAL)


def test_an_excess_names_every_measure_that_is_over_in_form_order():
    load = Envelope.read("14'", "15'", "110'", "6'", "20'")
    excess = load.excess_over(LEGAL)
    assert excess.over() == ("width", "height", "length", "front_overhang", "rear_overhang")
    assert excess.width == Inches(66)
    assert excess.height == Inches(18)
    assert excess.length == Inches(420)
    assert not load.within(LEGAL)


def test_a_measure_inside_the_limit_contributes_nothing_to_the_excess():
    load = Envelope.read("14'", "13'", "70'")
    excess = load.excess_over(LEGAL)
    assert excess.over() == ("width",)
    assert excess.height == Inches(0)
    assert str(excess) == "width over by 5 ft 6 in"


def test_the_worst_measure_is_the_one_the_refusal_letter_leads_with():
    load = Envelope.read("9'", "16'", "70'")
    worst = load.excess_over(LEGAL).worst()
    assert worst is not None
    name, amount = worst
    assert name == "height"
    assert amount == Inches(30)


def test_an_excess_prints_every_measure_it_holds():
    excess = Excess(width=Inches(18), rear_overhang=Inches(48))
    assert str(excess) == "width over by 1 ft 6 in, rear overhang over by 4 ft"
    assert excess.any


def test_growing_an_envelope_answers_a_what_if_without_editing_the_first_one():
    load = box()
    taller = load.grown(height=Inches(12), width=Inches(6))
    assert taller.height == Inches(174)
    assert taller.width == Inches(150)
    assert load.height == Inches(162)


def test_growing_a_measure_that_does_not_exist_is_refused():
    with pytest.raises(ValueError):
        box().grown(weight=Inches(12))
