"""Sunrise, sunset, and the half hour either side the permit allows."""

from datetime import date, time

import pytest

from jeepdolly.errors import Impossible
from jeepdolly.travel.daylight import daylight, is_daylight, saving_time, shortest_day


def test_the_middle_of_a_month_comes_straight_off_the_table():
    winter = daylight(date(2025, 1, 15))
    assert winter.sunrise == time(7, 12)
    assert winter.sunset == time(16, 25)


def test_the_permit_day_reaches_half_an_hour_past_each_end():
    winter = daylight(date(2025, 1, 15))
    assert winter.opens == time(6, 42)
    assert winter.closes == time(16, 55)
    assert str(winter) == "2025-01-15 06:42 to 16:55"


def test_a_day_between_two_anchors_is_walked_between_them():
    first = daylight(date(2025, 1, 15)).sunrise
    last = daylight(date(2025, 2, 15)).sunrise
    middle = daylight(date(2025, 1, 31)).sunrise
    assert last < middle < first


def test_the_clock_goes_forward_in_march_and_the_table_follows():
    assert not saving_time(date(2025, 3, 8))
    assert saving_time(date(2025, 3, 9))
    assert saving_time(date(2025, 11, 1))
    assert not saving_time(date(2025, 11, 2))


def test_the_hour_shows_up_on_both_ends_of_the_day():
    before = daylight(date(2025, 3, 8))
    after = daylight(date(2025, 3, 9))
    assert after.sunrise.hour == before.sunrise.hour + 1
    assert after.sunset.hour == before.sunset.hour + 1


def test_a_summer_day_is_long_and_a_winter_day_is_not():
    assert daylight(date(2025, 6, 15)).hours == 16
    assert daylight(date(2025, 12, 15)).hours == 9


def test_a_moment_is_inside_the_window_or_it_is_not():
    assert is_daylight(date(2025, 6, 15), time(6, 0))
    assert not is_daylight(date(2025, 12, 15), time(6, 0))
    assert not is_daylight(date(2025, 6, 15), time(21, 0))


def test_the_window_is_closed_at_both_ends_and_the_edges_are_inside_it():
    day = daylight(date(2025, 6, 15))
    assert day.covers(day.opens)
    assert day.covers(day.closes)


def test_the_worst_day_of_the_year_is_around_the_solstice():
    worst = shortest_day(2025)
    assert worst.day.month == 12
    assert 10 <= worst.day.day <= 31
    assert worst.hours == 9


def test_a_day_before_the_table_starts_is_refused_rather_than_guessed():
    with pytest.raises(Impossible):
        daylight(date(1800, 6, 15))


def test_the_year_boundary_walks_between_december_and_january():
    december = daylight(date(2025, 12, 28)).sunrise
    january = daylight(date(2026, 1, 5)).sunrise
    assert december < january
