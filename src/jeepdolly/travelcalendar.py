"""The calendar a permit is written against.

Two different calendars live in this file and they are not the same thing.

The first is the office calendar: which days the counter is open, which is what
an application date and a processing clock run on.

The second is the road calendar: when an oversize load is not allowed to move
at all. The long weekends are the ones that matter. The order the office works
to bans movement from noon the day before a holiday weekend until the end of
the holiday itself, and a hauler who is caught out by that on a Friday
afternoon sits in a gravel pit until Tuesday.

Holidays are computed, not listed. A table of dates is a table somebody has to
remember to extend, and the year it is not extended is the year a permit gets
written for Thanksgiving Day.

Nothing here carries a timezone. The state is one timezone wide and a permit
that says noon means noon where the load is.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, time, timedelta

MONDAY = 0
THURSDAY = 3
FRIDAY = 4
SATURDAY = 5
SUNDAY = 6

#: Holidays whose ban window covers the whole weekend around them.
LONG_WEEKENDS = frozenset({"memorial day", "independence day", "labor day", "thanksgiving"})


def nth_weekday(year: int, month: int, weekday: int, n: int) -> date:
    """The nth given weekday of a month, one based."""
    first = date(year, month, 1)
    ahead = (weekday - first.weekday()) % 7
    day = first + timedelta(days=ahead + 7 * (n - 1))
    if day.month != month:
        raise ValueError(f"no {n} of weekday {weekday} in {year}-{month:02d}")
    return day


def last_weekday(year: int, month: int, weekday: int) -> date:
    """The last given weekday of a month."""
    following = date(year + 1, 1, 1) if month == 12 else date(year, month + 1, 1)
    last = following - timedelta(days=1)
    back = (last.weekday() - weekday) % 7
    return last - timedelta(days=back)


def observed(day: date) -> date:
    """A fixed date holiday falling on a weekend is kept on the near weekday."""
    if day.weekday() == SATURDAY:
        return day - timedelta(days=1)
    if day.weekday() == SUNDAY:
        return day + timedelta(days=1)
    return day


@dataclass(frozen=True, order=True)
class Holiday:
    """One holiday in one year, on the day the office actually keeps it."""

    day: date
    name: str
    fixed: bool = False

    def __str__(self) -> str:
        return f"{self.name} {self.day.isoformat()}"


@dataclass(frozen=True)
class Window:
    """A closed span of clock time with a reason attached."""

    start: datetime
    end: datetime
    reason: str

    def __post_init__(self) -> None:
        if self.end <= self.start:
            raise ValueError("a window ends after it starts")

    def covers(self, when: datetime) -> bool:
        return self.start <= when <= self.end

    def __str__(self) -> str:
        return f"{self.reason} {self.start:%Y-%m-%d %H:%M} to {self.end:%Y-%m-%d %H:%M}"


def holidays(year: int) -> tuple[Holiday, ...]:
    """Every holiday the office keeps, in the order they fall."""
    found = [
        Holiday(observed(date(year, 1, 1)), "new year", fixed=True),
        Holiday(nth_weekday(year, 1, MONDAY, 3), "martin luther king day"),
        Holiday(nth_weekday(year, 2, MONDAY, 3), "presidents day"),
        Holiday(nth_weekday(year, 4, MONDAY, 3), "patriots day"),
        Holiday(last_weekday(year, 5, MONDAY), "memorial day"),
        Holiday(observed(date(year, 7, 4)), "independence day", fixed=True),
        Holiday(nth_weekday(year, 9, MONDAY, 1), "labor day"),
        Holiday(nth_weekday(year, 10, MONDAY, 2), "indigenous peoples day"),
        Holiday(observed(date(year, 11, 11)), "veterans day", fixed=True),
        Holiday(nth_weekday(year, 11, THURSDAY, 4), "thanksgiving"),
        Holiday(observed(date(year, 12, 25)), "christmas", fixed=True),
    ]
    return tuple(sorted(found))


def _ban_for(holiday: Holiday) -> Window:
    """Noon the day before through the end of the holiday, weekend included.

    A holiday on a Monday pulls the whole weekend in, because the ban starts
    at noon on the Friday. Thanksgiving runs through the Friday after it, which
    is the busiest travel day of the year on every road in the state.
    """
    start_day = holiday.day - timedelta(days=1)
    if holiday.day.weekday() == MONDAY:
        start_day = holiday.day - timedelta(days=3)
    end_day = holiday.day
    if holiday.name == "thanksgiving":
        end_day = holiday.day + timedelta(days=1)
    return Window(
        datetime.combine(start_day, time(12, 0)),
        datetime.combine(end_day, time(23, 59)),
        f"{holiday.name} travel ban",
    )


class PermitCalendar:
    """One year of the office calendar and one year of the road calendar."""

    def __init__(self, year: int) -> None:
        self.year = year
        self.holidays = holidays(year)
        self._by_day = {holiday.day: holiday for holiday in self.holidays}
        self.bans = tuple(
            _ban_for(holiday) for holiday in self.holidays if holiday.name in LONG_WEEKENDS
        )

    def holiday_on(self, day: date) -> Holiday | None:
        return self._by_day.get(day)

    def is_holiday(self, day: date) -> bool:
        return day in self._by_day

    def is_open(self, day: date) -> bool:
        """The counter is open on weekdays that are not holidays."""
        return day.weekday() < SATURDAY and not self.is_holiday(day)

    def next_open(self, day: date) -> date:
        """The next day the counter is open, not counting the day given."""
        following = day + timedelta(days=1)
        while not self.is_open(following):
            following += timedelta(days=1)
        return following

    def open_days_between(self, start: date, end: date) -> int:
        """Open days from ``start`` up to and including ``end``."""
        if end < start:
            raise ValueError("the span ends before it starts")
        day, count = start, 0
        while day <= end:
            if self.is_open(day):
                count += 1
            day += timedelta(days=1)
        return count

    def ban_at(self, when: datetime) -> Window | None:
        """The travel ban covering this moment, if there is one."""
        for window in self.bans:
            if window.covers(when):
                return window
        return None

    def banned(self, when: datetime) -> bool:
        return self.ban_at(when) is not None

    def __repr__(self) -> str:
        return f"PermitCalendar({self.year})"
