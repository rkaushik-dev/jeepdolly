"""What is left of a week once every rule about when has been applied.

A permit is valid for a span of days and the hauler wants to know which of
those days he can actually move on, and between what hours. Four rules answer
that and they are read in a fixed order because they knock each other out.

A holiday ban closes the whole day. Nothing else is asked about that day.

A weekend restriction closes Saturday and Sunday for the loads that carry it,
which is the wide ones and the superloads.

A daylight restriction cuts the day down to the window in the daylight file.
A load that is not daylight restricted may run through the night, and in
February that is most of the working day.

A curfew cuts a bite out of what is left. A curfew that swallows the whole
remaining window closes the day, which happens to a daylight only load with an
afternoon curfew in December and surprises everybody the first time.

The limits themselves come off the load. Width and height put it in daylight.
Superload adds the weekend. A high centre of gravity adds a wind hold, which is
not a time rule at all but rides with them because it belongs on the same part
of the permit.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, time, timedelta

from ..domain.envelope import Envelope
from ..domain.restriction import Curfew
from ..errors import Impossible
from ..measure import Inches
from ..travelcalendar import PermitCalendar
from .daylight import daylight

#: Past this width a load moves in daylight only.
DAYLIGHT_WIDTH = Inches.of(12)

#: Past this height a load moves in daylight only.
DAYLIGHT_HEIGHT = Inches.of(14, 6)

#: A load with a high centre stops when the wind reaches this.
WIND_HOLD_MPH = 30


@dataclass(frozen=True)
class TravelLimits:
    """The when rules this load carries, whatever route it takes."""

    daylight_only: bool = False
    no_weekend: bool = False
    wind_hold_mph: int | None = None
    reasons: tuple[str, ...] = ()

    @property
    def any(self) -> bool:
        return bool(self.daylight_only or self.no_weekend or self.wind_hold_mph)

    def __str__(self) -> str:
        if not self.any:
            return "no travel restriction"
        parts = []
        if self.daylight_only:
            parts.append("daylight only")
        if self.no_weekend:
            parts.append("no weekend travel")
        if self.wind_hold_mph:
            parts.append(f"hold above {self.wind_hold_mph} mph wind")
        return ", ".join(parts)


def limits_for(
    envelope: Envelope,
    superload: bool = False,
    high_centre: bool = False,
) -> TravelLimits:
    """Read the when rules off the load."""
    reasons: list[str] = []
    daylight_only = False
    if envelope.width > DAYLIGHT_WIDTH:
        daylight_only = True
        reasons.append(f"width over {DAYLIGHT_WIDTH}")
    if envelope.height > DAYLIGHT_HEIGHT:
        daylight_only = True
        reasons.append(f"height over {DAYLIGHT_HEIGHT}")
    if superload:
        daylight_only = True
        reasons.append("superload")
    return TravelLimits(
        daylight_only=daylight_only,
        no_weekend=superload,
        wind_hold_mph=WIND_HOLD_MPH if high_centre else None,
        reasons=tuple(reasons),
    )


@dataclass(frozen=True)
class TravelDay:
    """One day of the permit, and what is left of it."""

    day: date
    opens: time | None
    closes: time | None
    reason: str = ""

    @property
    def open(self) -> bool:
        return self.opens is not None and self.closes is not None

    @property
    def minutes(self) -> int:
        if self.opens is None or self.closes is None:
            return 0
        return (self.closes.hour * 60 + self.closes.minute) - (
            self.opens.hour * 60 + self.opens.minute
        )

    def __str__(self) -> str:
        if not self.open:
            return f"{self.day.isoformat()} closed: {self.reason}"
        return f"{self.day.isoformat()} {self.opens:%H:%M} to {self.closes:%H:%M}"


def travel_day(
    day: date,
    limits: TravelLimits,
    calendar: PermitCalendar,
    curfew: Curfew | None = None,
) -> TravelDay:
    """What is left of one day once every when rule has been applied."""
    if limits.no_weekend and day.weekday() >= 5:
        return TravelDay(day, None, None, "no weekend travel")
    opens, closes = time(0, 0), time(23, 59)
    if limits.daylight_only:
        window = daylight(day)
        opens, closes = window.opens, window.closes
    after_ban = _ban_trim(day, calendar, opens, closes)
    if after_ban[0] is None or after_ban[1] is None:
        return TravelDay(day, None, None, after_ban[2])
    opens, closes = after_ban[0], after_ban[1]
    if curfew is not None and day.weekday() in curfew.days:
        after_curfew = _outside_curfew(opens, closes, curfew)
        if after_curfew[0] is None or after_curfew[1] is None:
            return TravelDay(day, None, None, after_curfew[2])
        opens, closes = after_curfew[0], after_curfew[1]
    return TravelDay(day, opens, closes)


def _outside_curfew(
    opens: time, closes: time, curfew: Curfew
) -> tuple[time | None, time | None, str]:
    """Take the curfew out of the window, keeping the longer remaining piece."""
    if curfew.end < opens or curfew.start > closes:
        return opens, closes, ""
    before = _span(opens, curfew.start)
    after = _span(curfew.end, closes)
    if before <= 0 and after <= 0:
        return None, None, "the curfew covers the whole window"
    if before >= after:
        return opens, curfew.start, ""
    return curfew.end, closes, ""


def _span(first: time, second: time) -> int:
    return (second.hour * 60 + second.minute) - (first.hour * 60 + first.minute)


def _ban_trim(
    day: date, calendar: PermitCalendar, opens: time, closes: time
) -> tuple[time | None, time | None, str]:
    """Cut a holiday ban out of the day, which may leave nothing at all.

    A ban that starts at noon on the Friday leaves the Friday morning, and
    haulers do use it. A ban that covers the whole day closes it.
    """
    first = datetime.combine(day, opens)
    last = datetime.combine(day, closes)
    for window in calendar.bans:
        if window.start <= first and last <= window.end:
            return None, None, window.reason
        if window.covers(last):
            return opens, window.start.time(), ""
        if window.covers(first):
            return window.end.time(), closes, ""
    return opens, closes, ""


def travel_days(
    start: date,
    end: date,
    limits: TravelLimits,
    calendar: PermitCalendar,
    curfew: Curfew | None = None,
) -> tuple[TravelDay, ...]:
    """Every day of a permit span, open or closed, in order."""
    if end < start:
        raise Impossible("a permit span ends after it starts")
    found = []
    day = start
    while day <= end:
        found.append(travel_day(day, limits, calendar, curfew))
        day = day + timedelta(days=1)
    return tuple(found)


def open_days(days: tuple[TravelDay, ...]) -> int:
    return sum(1 for one in days if one.open)
