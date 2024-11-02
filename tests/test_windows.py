"""What is left of a week once every rule about when has been read."""

from datetime import date, time

import pytest

from jeepdolly.domain.envelope import Envelope
from jeepdolly.domain.restriction import Curfew
from jeepdolly.errors import Impossible
from jeepdolly.travel.windows import limits_for, open_days, travel_day, travel_days
from jeepdolly.travelcalendar import PermitCalendar

YEAR = PermitCalendar(2025)
WEEKDAYS = frozenset({0, 1, 2, 3, 4})
JUNE = date(2025, 6, 16)


def load(width="10'", height="13'6"):
    return Envelope.read(width, height, "80'")


def test_an_ordinary_load_carries_no_when_rules_at_all():
    limits = limits_for(load())
    assert not limits.any
    assert str(limits) == "no travel restriction"


def test_a_wide_load_moves_in_daylight_and_says_why():
    limits = limits_for(load(width="13'"))
    assert limits.daylight_only
    assert not limits.no_weekend
    assert limits.reasons == ("width over 12 ft",)


def test_a_tall_load_moves_in_daylight_too():
    assert limits_for(load(height="15'")).daylight_only


def test_a_superload_loses_the_weekend_as_well_as_the_night():
    limits = limits_for(load(), superload=True)
    assert limits.daylight_only
    assert limits.no_weekend
    assert str(limits) == "daylight only, no weekend travel"


def test_a_high_load_carries_a_wind_hold_that_is_not_a_time_rule():
    limits = limits_for(load(), high_centre=True)
    assert limits.wind_hold_mph == 30
    assert not limits.daylight_only
    assert "hold above 30 mph wind" in str(limits)


def test_a_load_with_no_rules_may_run_through_the_night():
    day = travel_day(JUNE, limits_for(load()), YEAR)
    assert day.open
    assert day.opens == time(0, 0)
    assert day.closes == time(23, 59)


def test_a_daylight_load_gets_the_window_out_of_the_daylight_table():
    day = travel_day(JUNE, limits_for(load(width="13'")), YEAR)
    assert day.opens == time(4, 31)
    assert day.closes == time(20, 55)
    assert str(day) == "2025-06-16 04:31 to 20:55"


def test_a_superload_is_shut_out_of_saturday_and_sunday():
    limits = limits_for(load(), superload=True)
    assert not travel_day(date(2025, 6, 21), limits, YEAR).open
    assert travel_day(date(2025, 6, 20), limits, YEAR).open
    assert travel_day(date(2025, 6, 21), limits, YEAR).reason == "no weekend travel"


def test_a_holiday_ban_closes_the_day_and_names_the_holiday():
    day = travel_day(date(2025, 7, 4), limits_for(load()), YEAR)
    assert not day.open
    assert day.reason == "independence day travel ban"
    assert day.minutes == 0


def test_the_morning_before_a_ban_starts_is_still_a_working_morning():
    day = travel_day(date(2025, 7, 3), limits_for(load()), YEAR)
    assert day.open
    assert day.opens == time(0, 0)
    assert day.closes == time(12, 0)


def test_the_morning_after_a_ban_lifts_opens_late():
    day = travel_day(date(2025, 9, 1), limits_for(load()), YEAR)
    assert not day.open
    day = travel_day(date(2025, 8, 29), limits_for(load()), YEAR)
    assert day.closes == time(12, 0)


def test_a_curfew_takes_a_bite_and_the_longer_piece_is_kept():
    curfew = Curfew(WEEKDAYS, time(6, 30), time(8, 30))
    day = travel_day(JUNE, limits_for(load()), YEAR, curfew)
    assert day.opens == time(8, 30)
    assert day.closes == time(23, 59)


def test_a_curfew_late_in_the_day_leaves_the_morning():
    curfew = Curfew(WEEKDAYS, time(14, 0), time(23, 59))
    day = travel_day(JUNE, limits_for(load()), YEAR, curfew)
    assert day.opens == time(0, 0)
    assert day.closes == time(14, 0)


def test_a_curfew_over_the_whole_window_closes_the_day():
    curfew = Curfew(WEEKDAYS, time(0, 0), time(23, 59))
    day = travel_day(JUNE, limits_for(load()), YEAR, curfew)
    assert not day.open
    assert day.reason == "the curfew covers the whole window"


def test_a_curfew_on_days_this_load_does_not_travel_is_ignored():
    curfew = Curfew(frozenset({5, 6}), time(0, 0), time(23, 59))
    assert travel_day(JUNE, limits_for(load()), YEAR, curfew).open


def test_a_permit_span_comes_back_day_by_day_open_or_shut():
    days = travel_days(date(2025, 6, 30), date(2025, 7, 6), limits_for(load()), YEAR)
    assert len(days) == 7
    assert open_days(days) == 6
    assert [one.day.day for one in days if not one.open] == [4]


def test_a_span_that_ends_before_it_starts_is_refused():
    with pytest.raises(Impossible):
        travel_days(date(2025, 7, 6), date(2025, 6, 30), limits_for(load()), YEAR)


def test_a_daylight_superload_over_a_holiday_week_loses_most_of_it():
    limits = limits_for(load(width="16'"), superload=True)
    days = travel_days(date(2025, 8, 28), date(2025, 9, 3), limits, YEAR)
    assert open_days(days) == 4
    assert [one.day.weekday() for one in days if one.open] == [3, 4, 1, 2]
