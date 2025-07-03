"""What a violation costs, band by band."""

from datetime import date

import pytest

from jeepdolly.enforcement.penalty import (
    MOST_REPEATS,
    Assessed,
    Band,
    Offence,
    PenaltySchedule,
    assess,
    by_weight,
    repeats_multiplier,
    standard_schedule,
)
from jeepdolly.errors import Impossible, NotOnFile
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars

SCHEDULE = standard_schedule()


def over(pounds):
    return Pounds(pounds)


def test_the_offences_say_which_are_priced_by_weight():
    assert Offence.OVERWEIGHT.by_weight
    assert not Offence.NO_PERMIT.by_weight
    assert str(Offence.OFF_ROUTE) == "off route"


def test_a_band_that_charges_nothing_is_not_a_band():
    with pytest.raises(Impossible):
        Band(Pounds(5000), 0)


def test_a_band_prints_the_way_the_statute_reads():
    assert str(Band(Pounds(5000), 3)) == "to 5,000 lb at 3c a pound"
    assert str(Band(None, 15)) == "and over at 15c a pound"


def test_a_schedule_with_no_bands_is_refused():
    with pytest.raises(Impossible):
        PenaltySchedule(date(2024, 1, 1), Dollars.of(50), (), {})


def test_a_schedule_whose_last_band_has_a_ceiling_is_refused():
    with pytest.raises(Impossible):
        PenaltySchedule(date(2024, 1, 1), Dollars.of(50), (Band(Pounds(5000), 3),), {})


def test_a_schedule_with_a_ceiling_missing_in_the_middle_is_refused():
    bands = (Band(None, 3), Band(None, 5))
    with pytest.raises(Impossible):
        PenaltySchedule(date(2024, 1, 1), Dollars.of(50), bands, {})


def test_nothing_over_costs_nothing_before_the_minimum_is_read():
    assert by_weight(SCHEDULE, over(0)) == Dollars.of(0)


def test_the_first_band_is_charged_at_its_own_rate():
    assert by_weight(SCHEDULE, over(4000)) == Dollars.of(120)


def test_the_bands_are_cumulative_and_not_a_lookup():
    # Five thousand at three cents, then two thousand at five.
    assert by_weight(SCHEDULE, over(7000)) == Dollars.of(250)


def test_a_load_in_the_third_band_pays_all_three():
    # 5000 at 3c, 5000 at 5c, 2000 at 9c.
    assert by_weight(SCHEDULE, over(12000)) == Dollars.of(580)


def test_a_load_past_the_last_ceiling_pays_the_open_band_on_the_rest():
    # 150 + 250 + 900 + 5000 at 15c.
    assert by_weight(SCHEDULE, over(25000)) == Dollars.of(2050)


def test_exactly_on_a_band_edge_does_not_spill_into_the_next_one():
    assert by_weight(SCHEDULE, over(5000)) == Dollars.of(150)
    assert by_weight(SCHEDULE, over(10000)) == Dollars.of(400)


def test_a_small_overage_is_lifted_to_the_minimum():
    found = assess(SCHEDULE, Offence.OVERWEIGHT, over(200))
    assert found.total == Dollars.of(50)


def test_an_overweight_citation_with_no_overage_is_a_mistake():
    with pytest.raises(Impossible):
        assess(SCHEDULE, Offence.OVERWEIGHT)


def test_a_flat_offence_is_priced_off_the_table():
    assert assess(SCHEDULE, Offence.NO_PERMIT).total == Dollars.of(500)
    assert assess(SCHEDULE, Offence.NO_MARKING).total == Dollars.of(100)


def test_an_overage_passed_with_a_flat_offence_is_ignored():
    found = assess(SCHEDULE, Offence.OFF_ROUTE, over(9000))
    assert found.total == Dollars.of(250)
    assert found.over is None


def test_an_offence_the_schedule_does_not_price_is_a_refusal():
    thin = PenaltySchedule(date(2024, 1, 1), Dollars.of(50), (Band(None, 3),), {})
    with pytest.raises(NotOnFile):
        assess(thin, Offence.NO_PERMIT)


def test_a_first_offence_is_charged_once():
    assert repeats_multiplier(0) == 1


def test_a_second_offence_inside_the_year_doubles():
    assert repeats_multiplier(1) == 2
    assert assess(SCHEDULE, Offence.NO_PERMIT, prior=1).total == Dollars.of(1000)


def test_a_third_offence_trebles_and_it_stops_there():
    assert repeats_multiplier(2) == MOST_REPEATS
    assert repeats_multiplier(9) == MOST_REPEATS


def test_a_negative_count_of_priors_is_refused():
    with pytest.raises(Impossible):
        repeats_multiplier(-1)


def test_the_assessment_shows_its_working():
    found = assess(SCHEDULE, Offence.OVERWEIGHT, over(12000), prior=1)
    assert found.lines() == (
        "overweight  $580.00",
        "  over by  12,000 lb",
        "  repeat offence, times 2",
        "  due  $1,160.00",
    )
    assert str(found) == "overweight: $1,160.00"


def test_a_first_offence_does_not_print_a_multiplier_line():
    found = assess(SCHEDULE, Offence.NO_ESCORT)
    assert found.lines() == ("escorts not present  $350.00", "  due  $350.00")


def test_the_assessment_keeps_the_offence_it_was_asked_about():
    assert isinstance(assess(SCHEDULE, Offence.IN_CURFEW), Assessed)
    assert assess(SCHEDULE, Offence.IN_CURFEW).offence is Offence.IN_CURFEW


def test_the_schedule_says_when_it_came_into_force():
    assert str(SCHEDULE) == "penalties of 2024-01-01"
    assert standard_schedule(date(2026, 1, 1)).effective == date(2026, 1, 1)
