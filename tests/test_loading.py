"""The window of axles a structure feels, which is rarely the whole train."""

import pytest

from jeepdolly.errors import Impossible
from jeepdolly.measure import Inches, Pounds
from jeepdolly.structures.loading import governing_weight, load_on, load_on_span, relief
from support import jeeped_lowboy, structure_book, tractor_lowboy

BOOK = structure_book()


def test_a_long_span_carries_the_whole_train():
    window = load_on_span(tractor_lowboy(), 120)
    assert window.whole_train
    assert window.count == 6
    assert window.weight == Pounds(94000)
    assert window.extent == Inches(538)


def test_a_short_span_carries_only_the_worst_run_that_fits():
    window = load_on_span(tractor_lowboy(), 14)
    assert window.count == 3
    assert window.first == 3
    assert window.weight == Pounds(48000)
    assert window.extent == Inches(104)
    assert not window.whole_train


def test_the_worst_run_is_searched_for_and_not_assumed_to_be_the_back():
    window = load_on_span(jeeped_lowboy(), 14)
    assert (window.first, window.last) == (5, 7)
    assert window.weight == Pounds(60000)


def test_a_run_that_ends_exactly_on_the_abutment_still_counts():
    window = load_on_span(jeeped_lowboy(), 48)
    assert window.extent == Inches.of(48)
    assert window.weight == Pounds(153000)
    assert (window.first, window.last) == (1, 8)


def test_a_run_one_inch_longer_than_the_span_does_not_count():
    window = load_on_span(jeeped_lowboy(), 47)
    assert window.extent < Inches.of(48)
    assert window.weight < Pounds(153000)


def test_a_window_prints_the_way_a_worksheet_line_reads():
    window = load_on_span(tractor_lowboy(), 14)
    assert str(window) == "axles 4 to 6, 48,000 lb over 8 ft 8 in"


def test_a_span_of_no_length_is_not_a_span():
    with pytest.raises(Impossible):
        load_on_span(tractor_lowboy(), 0)


def test_a_structure_is_governed_by_its_longest_single_span():
    kennebec = BOOK.get("1247")
    assert kennebec.longest_span == 120
    assert governing_weight(kennebec, tractor_lowboy()) == Pounds(94000)
    culvert = BOOK.get("3310")
    assert culvert.longest_span == 14
    assert governing_weight(culvert, tractor_lowboy()) == Pounds(48000)


def test_the_relief_is_what_the_structure_never_sees_at_once():
    culvert = BOOK.get("3310")
    assert relief(culvert, tractor_lowboy()) == Pounds(46000)
    assert relief(BOOK.get("1247"), tractor_lowboy()) == Pounds(0)


def test_a_structure_that_carries_no_traffic_is_not_asked_about_loading():
    with pytest.raises(Impossible) as refusal:
        load_on(BOOK.get("2201"), tractor_lowboy())
    assert "does not carry traffic" in str(refusal.value)


def test_a_span_too_short_for_two_axles_carries_the_heaviest_one():
    window = load_on_span(tractor_lowboy(), 1)
    assert window.count == 1
    assert window.weight == Pounds(17000)
    assert window.extent == Inches(0)
