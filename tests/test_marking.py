"""Banners, flags, lamps and beacons, and what the dark changes."""

from jeepdolly.domain.envelope import Envelope
from jeepdolly.escorts.marking import conditions, marking_for


def load(width="8'", length="70'", front="0", rear="0"):
    return Envelope.read(width, "13'6", length, front, rear)


def test_a_legal_load_wears_nothing():
    marking = marking_for(load())
    assert not marking.any
    assert str(marking) == "no marking"
    assert conditions(marking) == ()


def test_a_wide_load_wears_two_banners_two_flags_and_a_beacon():
    marking = marking_for(load(width="12'"))
    assert marking.banners == 2
    assert marking.flags == 2
    assert marking.beacons == 1
    assert marking.lamps == 0
    assert str(marking) == "2 oversize load banners, 2 flags, 1 beacons"


def test_a_long_load_wears_banners_but_no_flags_for_the_length_alone():
    marking = marking_for(load(length="90'"))
    assert marking.banners == 2
    assert marking.flags == 0
    assert marking.reasons == ("over legal length",)


def test_a_projection_gets_its_own_pair_of_flags_at_each_end():
    marking = marking_for(load(width="12'", front="6'", rear="14'"))
    assert marking.flags == 6
    assert marking.reasons == ("over legal width", "front overhang", "rear overhang")


def test_a_short_projection_is_not_flagged():
    marking = marking_for(load(width="12'", rear="3'"))
    assert marking.flags == 2


def test_after_dark_the_flags_become_lamps_rather_than_joining_them():
    marking = marking_for(load(width="12'", rear="14'"), at_night=True)
    assert marking.flags == 0
    assert marking.lamps == 4
    assert marking.after_dark
    assert not marking_for(load(width="12'")).after_dark


def test_a_superload_carries_two_beacons():
    marking = marking_for(load(width="18'"), superload=True)
    assert marking.beacons == 2
    assert "superload" in marking.reasons


def test_a_legal_size_superload_still_wears_no_banner():
    marking = marking_for(load(), superload=True)
    assert marking.banners == 0
    assert marking.beacons == 0


def test_the_conditions_say_the_sizes_the_enforcement_people_measure():
    printed = conditions(marking_for(load(width="12'", rear="14'")))
    assert "seven feet by eighteen inches" in printed[0]
    assert printed[1].startswith("4 eighteen inch red flags")
    assert printed[2].endswith("visible from the rear")


def test_the_night_conditions_name_lamps_and_not_flags():
    printed = conditions(marking_for(load(width="12'"), at_night=True))
    assert not any("red flags" in one for one in printed)
    assert any("amber lamps in place of flags" in one for one in printed)
