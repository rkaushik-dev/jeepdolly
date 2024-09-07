"""The bridge formula, checked against the published table and the exception."""

import pytest

from jeepdolly.errors import Impossible
from jeepdolly.limits.formula import (
    Finding,
    failures,
    feet_of,
    formula_limit,
    measure,
    round_to_500,
    two_tandem_exception,
    worst,
)
from jeepdolly.measure import Inches, Pounds
from support import make_stream, tractor_lowboy

ARTERIAL = Pounds(100000)
INTERSTATE = Pounds(80000)


def semi(gap=324):
    """Five axle semi at eighty thousand, tandems ``gap`` inches apart."""
    return make_stream([12000, 17000, 17000, 17000, 17000], [200, 54, gap, 54])


def test_a_distance_rounds_to_the_nearest_whole_foot():
    assert feet_of(Inches(48)) == 4
    assert feet_of(Inches(53)) == 4
    assert feet_of(Inches(54)) == 5
    assert feet_of(Inches(0)) == 0


def test_a_negative_distance_is_not_a_run_of_axles():
    with pytest.raises(Impossible):
        feet_of(Inches(-12))


def test_an_answer_rounds_to_the_nearest_five_hundred_pounds():
    assert round_to_500(79875) == Pounds(80000)
    assert round_to_500(79700) == Pounds(79500)
    assert round_to_500(34000) == Pounds(34000)


def test_the_formula_agrees_with_the_wall_chart():
    assert formula_limit(2, Inches.of(4), ARTERIAL) == Pounds(34000)
    assert formula_limit(3, Inches.of(10), ARTERIAL) == Pounds(43500)
    assert formula_limit(4, Inches.of(30), ARTERIAL) == Pounds(62000)
    assert formula_limit(5, Inches.of(51), ARTERIAL) == Pounds(80000)


def test_the_gross_limit_caps_whatever_the_formula_would_allow():
    assert formula_limit(9, Inches.of(80), INTERSTATE) == Pounds(80000)
    assert formula_limit(9, Inches.of(80), ARTERIAL) == Pounds(100000)


def test_the_formula_has_nothing_to_say_about_one_axle():
    with pytest.raises(Impossible):
        formula_limit(1, Inches.of(4), ARTERIAL)


def test_a_five_axle_semi_at_eighty_thousand_needs_the_exception():
    stream = semi()
    tandems = next(one for one in stream.spans() if one.first == 1 and one.last == 4)
    assert tandems.extent == Inches.of(36)
    assert formula_limit(4, tandems.extent, INTERSTATE) == Pounds(66000)
    assert two_tandem_exception(stream, tandems)
    assert failures(stream, INTERSTATE) == ()


def test_the_finding_that_used_the_exception_says_which_rule_let_it_through():
    findings = measure(semi(), INTERSTATE)
    used = [one for one in findings if one.exception]
    assert len(used) == 1
    assert used[0].allowed == Pounds(68000)
    assert "under the two tandem exception" in str(used[0])


def test_the_same_semi_with_the_tandems_pulled_in_is_over():
    stream = semi(gap=200)
    over = failures(stream, INTERSTATE)
    assert over
    assert worst(stream, INTERSTATE).span.count == 4


def test_the_exception_does_not_reach_a_run_that_is_not_two_tandems():
    stream = tractor_lowboy()
    spans = {(one.first, one.last): one for one in stream.spans()}
    assert not two_tandem_exception(stream, spans[(0, 3)])
    assert not two_tandem_exception(stream, spans[(3, 5)])


def test_the_exception_does_not_reach_tandems_that_are_over_on_their_own():
    stream = make_stream([12000, 19000, 19000, 19000, 19000], [200, 54, 324, 54])
    tandems = next(one for one in stream.spans() if one.first == 1 and one.last == 4)
    assert not two_tandem_exception(stream, tandems)


def test_a_lowboy_tridem_is_the_run_that_fails_first():
    stream = tractor_lowboy()
    findings = {(one.span.first, one.span.last): one for one in measure(stream, ARTERIAL)}
    tridem = findings[(3, 5)]
    assert tridem.allowed == Pounds(43000)
    assert tridem.weight == Pounds(48000)
    assert tridem.over == Pounds(5000)
    assert not tridem.passes


def test_every_run_over_the_formula_is_listed_and_nothing_else_is():
    over = failures(tractor_lowboy(), ARTERIAL)
    assert [(one.span.first, one.span.last) for one in over] == [
        (3, 5),
        (1, 4),
        (2, 5),
        (0, 4),
        (1, 5),
        (0, 5),
    ]


def test_the_worst_run_is_the_one_furthest_over_and_not_the_first_found():
    finding = worst(tractor_lowboy(), ARTERIAL)
    assert finding is not None
    assert (finding.span.first, finding.span.last) == (1, 5)
    assert finding.over == Pounds(16500)


def test_a_stream_that_clears_everything_has_no_worst_run():
    stream = make_stream([12000, 17000, 17000], [200, 54])
    assert worst(stream, ARTERIAL) is None
    assert failures(stream, ARTERIAL) == ()


def test_a_finding_prints_the_run_the_allowance_and_the_verdict():
    finding = worst(tractor_lowboy(), ARTERIAL)
    assert isinstance(finding, Finding)
    assert str(finding) == (
        "axles 2 to 6, 82,000 lb over 28 ft 2 in, allowed 65,500 lb, over by 16,500 lb"
    )
