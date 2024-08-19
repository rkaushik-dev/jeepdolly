"""The axle stream, its groups, and the runs the bridge formula wants."""

import pytest

from jeepdolly.domain.stream import AxleStream
from jeepdolly.measure import Inches, Pounds
from support import jeeped_lowboy, make_stream, tractor_lowboy


def test_a_stream_adds_up_to_the_number_on_the_scale_ticket():
    stream = tractor_lowboy()
    assert stream.count == 6
    assert len(stream) == 6
    assert stream.gross == Pounds(94000)
    assert stream.extent == Inches(538)


def test_a_stream_prints_itself_for_the_worksheet():
    assert repr(tractor_lowboy()) == "AxleStream(6 axles, 94,000 lb, 44 ft 10 in)"


def test_a_stream_needs_one_fewer_spacing_than_it_has_axles():
    with pytest.raises(ValueError) as refusal:
        make_stream([12000, 17000, 17000], [200])
    assert "need 2 spacings" in str(refusal.value)


def test_two_axles_cannot_stand_in_the_same_place():
    with pytest.raises(ValueError):
        make_stream([12000, 17000], [0])


def test_a_stream_with_no_axles_is_refused():
    with pytest.raises(ValueError):
        AxleStream((), ())


def test_the_distance_between_two_axles_is_the_spacings_between_them():
    stream = tractor_lowboy()
    assert stream.distance(0, 1) == Inches(200)
    assert stream.distance(1, 3) == Inches(234)
    assert stream.distance(3, 5) == Inches(104)
    assert stream.distance(2, 2) == Inches(0)


def test_asking_about_an_axle_that_is_not_there_is_an_index_error():
    stream = tractor_lowboy()
    with pytest.raises(IndexError):
        stream.distance(0, 6)
    with pytest.raises(IndexError):
        stream.distance(4, 2)


def test_the_offsets_place_every_axle_from_the_front_one():
    assert tractor_lowboy().offsets() == (
        Inches(0),
        Inches(200),
        Inches(254),
        Inches(434),
        Inches(486),
        Inches(538),
    )


def test_the_stream_splits_into_the_groups_the_statute_writes_limits_for():
    groups = tractor_lowboy().groups()
    assert [group.name for group in groups] == ["single", "tandem", "tridem"]
    assert [group.weight for group in groups] == [Pounds(12000), Pounds(34000), Pounds(48000)]
    assert [group.extent for group in groups] == [Inches(0), Inches(54), Inches(104)]


def test_a_jeep_and_the_drives_read_as_one_group_when_they_stand_close():
    groups = jeeped_lowboy().groups()
    assert [group.name for group in groups] == ["single", "quad", "tridem", "single"]
    assert groups[1].weight == Pounds(75000)
    assert groups[1].extent == Inches(176)


def test_a_gap_of_exactly_eight_feet_is_still_one_group():
    stream = make_stream([16000, 16000, 16000], [96, 97])
    assert [group.count for group in stream.groups()] == [2, 1]


def test_every_run_of_two_or_more_axles_is_offered_to_the_formula():
    spans = list(tractor_lowboy().spans())
    assert len(spans) == 15
    assert spans[0].first == 0
    assert spans[0].last == 1
    assert spans[0].weight == Pounds(29000)
    assert spans[0].extent == Inches(200)
    assert spans[-1].count == 6
    assert spans[-1].weight == Pounds(94000)


def test_the_runs_come_back_shortest_first_and_then_left_to_right():
    spans = list(tractor_lowboy().spans())
    counts = [span.count for span in spans]
    assert counts == [2] * 5 + [3] * 4 + [4] * 3 + [5] * 2 + [6]
    firsts = [span.first for span in spans[:5]]
    assert firsts == [0, 1, 2, 3, 4]


def test_asking_for_single_axle_runs_includes_every_axle_on_its_own():
    spans = list(tractor_lowboy().spans(minimum=1))
    assert len(spans) == 21
    assert spans[0].count == 1
    assert spans[0].extent == Inches(0)


def test_a_run_of_no_axles_is_not_a_thing_to_ask_for():
    with pytest.raises(ValueError):
        list(tractor_lowboy().spans(minimum=0))


def test_a_run_names_itself_the_way_a_refusal_letter_does():
    span = next(span for span in tractor_lowboy().spans() if span.first == 3 and span.last == 5)
    assert str(span) == "axles 4 to 6, 48,000 lb over 8 ft 8 in"
    assert span.name == "tridem"


def test_the_heaviest_axle_and_the_liftable_count_come_off_the_stream():
    stream = jeeped_lowboy()
    assert stream.heaviest_axle().weight == Pounds(20000)
    assert stream.liftable_count() == 0
    assert stream.gross == Pounds(165000)
    assert stream.extent == Inches(780)
