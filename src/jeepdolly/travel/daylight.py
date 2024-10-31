"""Sunrise and sunset, close enough to write a permit against.

A great many permits say daylight hours only, and the office has to be able to
say what that means for a Tuesday in November without anybody looking it up.

This is not an ephemeris and it does not pretend to be. It holds sunrise and
sunset at the middle of each month for the middle of the state and walks a
straight line between them. Against the published tables it is within about
four minutes across the year, which is inside the half hour the rule allows
either side, so the answer is right for the purpose it is used for.

Two conventions matter more than the arithmetic.

The permit day runs from half an hour before sunrise to half an hour after
sunset. That is civil twilight in round numbers and it is what the enforcement
people work to.

Daylight saving is applied by the calendar and not by the sun. The table is
standard time. From the second Sunday in March to the first Sunday in November
an hour is added to both ends, because that is what the clock in the cab says.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, time, timedelta

from ..errors import Impossible
from ..travelcalendar import SUNDAY, nth_weekday

#: Sunrise and sunset in minutes after midnight, standard time, on the 15th.
MID_MONTH = {
    1: (432, 985),
    2: (400, 1027),
    3: (352, 1067),
    4: (298, 1107),
    5: (258, 1144),
    6: (241, 1166),
    7: (257, 1160),
    8: (290, 1121),
    9: (324, 1067),
    10: (359, 1015),
    11: (399, 975),
    12: (429, 965),
}

#: The permit day reaches this far past each end.
TWILIGHT_MINUTES = 30

MINUTES_PER_DAY = 24 * 60


def saving_time(day: date) -> bool:
    """Whether the clock is an hour ahead on this day."""
    starts = nth_weekday(day.year, 3, SUNDAY, 2)
    ends = nth_weekday(day.year, 11, SUNDAY, 1)
    return starts <= day < ends


def _anchor(year: int, month: int) -> date:
    return date(year, month, 15)


def _neighbours(day: date) -> tuple[date, date]:
    """The two mid month anchors this day falls between."""
    if day.day >= 15:
        after = _anchor(day.year + 1, 1) if day.month == 12 else _anchor(day.year, day.month + 1)
        return _anchor(day.year, day.month), after
    before = _anchor(day.year - 1, 12) if day.month == 1 else _anchor(day.year, day.month - 1)
    return before, _anchor(day.year, day.month)


def _between(first: int, second: int, part: int, whole: int) -> int:
    return first + (second - first) * part // whole


@dataclass(frozen=True)
class Daylight:
    """Sunrise, sunset, and the window a permit may travel in."""

    day: date
    sunrise: time
    sunset: time
    opens: time
    closes: time

    @property
    def hours(self) -> int:
        """Whole hours of travel window, for the planning report."""
        return (_minutes(self.closes) - _minutes(self.opens)) // 60

    def covers(self, clock: time) -> bool:
        return self.opens <= clock <= self.closes

    def __str__(self) -> str:
        return f"{self.day.isoformat()} {self.opens:%H:%M} to {self.closes:%H:%M}"


def _minutes(clock: time) -> int:
    return clock.hour * 60 + clock.minute


def _clock(minutes: int) -> time:
    minutes = max(0, min(MINUTES_PER_DAY - 1, minutes))
    return time(minutes // 60, minutes % 60)


def daylight(day: date) -> Daylight:
    """Sunrise, sunset and the travel window for one day."""
    if day.year < 1900:
        raise Impossible("the table does not reach back that far")
    before, after = _neighbours(day)
    whole = (after - before).days
    part = (day - before).days
    rise = _between(MID_MONTH[before.month][0], MID_MONTH[after.month][0], part, whole)
    set_ = _between(MID_MONTH[before.month][1], MID_MONTH[after.month][1], part, whole)
    shift = 60 if saving_time(day) else 0
    rise += shift
    set_ += shift
    return Daylight(
        day=day,
        sunrise=_clock(rise),
        sunset=_clock(set_),
        opens=_clock(rise - TWILIGHT_MINUTES),
        closes=_clock(set_ + TWILIGHT_MINUTES),
    )


def is_daylight(when: date, clock: time) -> bool:
    """Whether this moment is inside the travel window of its day."""
    return daylight(when).covers(clock)


def _span(found: Daylight) -> int:
    return _minutes(found.closes) - _minutes(found.opens)


def shortest_day(year: int) -> Daylight:
    """The worst day of the year for a daylight only permit."""
    day = date(year, 1, 1)
    worst = daylight(day)
    while day.year == year:
        found = daylight(day)
        if _span(found) < _span(worst):
            worst = found
        day = day + timedelta(days=1)
    return worst
