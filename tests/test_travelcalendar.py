"""The office calendar and the road calendar, worked against 2025 and 2026."""

from datetime import date, datetime

import pytest

from jeepdolly.errors import Impossible
from jeepdolly.travelcalendar import (
    MONDAY,
    PermitCalendar,
    Window,
    holidays,
    last_weekday,
    nth_weekday,
    observed,
)


def test_the_nth_weekday_lands_where_a_wall_calendar_says():
    assert nth_weekday(2025, 1, MONDAY, 3) == date(2025, 1, 20)
    assert nth_weekday(2025, 9, MONDAY, 1) == date(2025, 9, 1)
    assert nth_weekday(2025, 11, 3, 4) == date(2025, 11, 27)


def test_asking_for_a_weekday_a_month_does_not_have_is_an_error():
    with pytest.raises(Impossible):
        nth_weekday(2025, 2, MONDAY, 5)


def test_the_last_weekday_of_a_month_walks_back_from_the_end():
    assert last_weekday(2025, 5, MONDAY) == date(2025, 5, 26)
    assert last_weekday(2025, 12, MONDAY) == date(2025, 12, 29)
    assert last_weekday(2024, 2, MONDAY) == date(2024, 2, 26)


def test_a_fixed_holiday_on_a_weekend_moves_to_the_near_weekday():
    assert observed(date(2026, 7, 4)) == date(2026, 7, 3)
    assert observed(date(2027, 7, 4)) == date(2027, 7, 5)
    assert observed(date(2025, 7, 4)) == date(2025, 7, 4)


def test_the_year_holds_eleven_holidays_in_the_order_they_fall():
    year = holidays(2025)
    assert len(year) == 11
    assert [holiday.day for holiday in year] == sorted(holiday.day for holiday in year)
    assert year[0].name == "new year"
    assert year[-1].name == "christmas"


def test_the_holidays_of_2025_are_the_ones_on_the_wall():
    named = {holiday.name: holiday.day for holiday in holidays(2025)}
    assert named["new year"] == date(2025, 1, 1)
    assert named["martin luther king day"] == date(2025, 1, 20)
    assert named["presidents day"] == date(2025, 2, 17)
    assert named["patriots day"] == date(2025, 4, 21)
    assert named["memorial day"] == date(2025, 5, 26)
    assert named["independence day"] == date(2025, 7, 4)
    assert named["labor day"] == date(2025, 9, 1)
    assert named["indigenous peoples day"] == date(2025, 10, 13)
    assert named["veterans day"] == date(2025, 11, 11)
    assert named["thanksgiving"] == date(2025, 11, 27)
    assert named["christmas"] == date(2025, 12, 25)


def test_a_holiday_prints_as_the_office_writes_it_on_the_board():
    memorial = next(one for one in holidays(2025) if one.name == "memorial day")
    assert str(memorial) == "memorial day 2025-05-26"


def test_the_counter_is_shut_on_weekends_and_holidays():
    year = PermitCalendar(2025)
    assert year.is_open(date(2025, 8, 6))
    assert not year.is_open(date(2025, 8, 9))
    assert not year.is_open(date(2025, 8, 10))
    assert not year.is_open(date(2025, 5, 26))
    assert repr(year) == "PermitCalendar(2025)"


def test_the_next_open_day_steps_over_a_holiday_weekend():
    year = PermitCalendar(2025)
    assert year.next_open(date(2025, 5, 23)) == date(2025, 5, 27)
    assert year.next_open(date(2025, 8, 6)) == date(2025, 8, 7)
    assert year.next_open(date(2025, 11, 26)) == date(2025, 11, 28)


def test_counting_open_days_includes_both_ends_of_the_span():
    year = PermitCalendar(2025)
    assert year.open_days_between(date(2025, 8, 4), date(2025, 8, 8)) == 5
    assert year.open_days_between(date(2025, 8, 4), date(2025, 8, 10)) == 5
    assert year.open_days_between(date(2025, 5, 23), date(2025, 5, 27)) == 2
    assert year.open_days_between(date(2025, 8, 4), date(2025, 8, 4)) == 1


def test_counting_backwards_is_a_mistake_and_says_so():
    with pytest.raises(Impossible):
        PermitCalendar(2025).open_days_between(date(2025, 8, 8), date(2025, 8, 4))


def test_only_the_long_weekends_close_the_road():
    year = PermitCalendar(2025)
    reasons = [window.reason for window in year.bans]
    assert reasons == [
        "memorial day travel ban",
        "independence day travel ban",
        "labor day travel ban",
        "thanksgiving travel ban",
    ]


def test_a_monday_holiday_pulls_the_whole_weekend_into_the_ban():
    year = PermitCalendar(2025)
    labor = year.bans[2]
    assert labor.start == datetime(2025, 8, 29, 12, 0)
    assert labor.end == datetime(2025, 9, 1, 23, 59)
    assert not year.banned(datetime(2025, 8, 29, 11, 59))
    assert year.banned(datetime(2025, 8, 29, 12, 0))
    assert year.banned(datetime(2025, 8, 30, 8, 0))
    assert not year.banned(datetime(2025, 9, 2, 0, 1))


def test_thanksgiving_runs_through_the_friday_after_it():
    year = PermitCalendar(2025)
    assert year.banned(datetime(2025, 11, 26, 12, 0))
    assert year.banned(datetime(2025, 11, 28, 15, 0))
    assert not year.banned(datetime(2025, 11, 29, 6, 0))


def test_a_holiday_in_the_middle_of_a_week_bans_only_its_own_afternoon_before():
    year = PermitCalendar(2026)
    christmas = year.holiday_on(date(2026, 12, 25))
    assert christmas is not None
    assert year.ban_at(datetime(2026, 12, 25, 9, 0)) is None


def test_the_ban_covering_a_moment_names_itself():
    year = PermitCalendar(2025)
    window = year.ban_at(datetime(2025, 7, 4, 9, 0))
    assert window is not None
    assert window.reason == "independence day travel ban"
    assert str(window).startswith("independence day travel ban 2025-07-03 12:00")


def test_a_window_that_ends_before_it_starts_is_not_a_window():
    with pytest.raises(Impossible):
        Window(datetime(2025, 7, 4, 12, 0), datetime(2025, 7, 3, 12, 0), "backwards")
