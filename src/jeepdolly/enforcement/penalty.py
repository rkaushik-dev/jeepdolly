"""What a violation costs, which is a table and an argument about the table.

The office does not write the penalties. They are in statute and they change
when the legislature says so, which is roughly every four years and never on a
schedule anybody can plan around. So the schedule is data with a date on it,
the same shape the fee schedules use, and an old citation is always priced
against the schedule that was in force on the day it was written.

Weight is priced by the pound over, in bands, and the bands are cumulative.
That word does the work. A load twelve thousand pounds over is not priced at
the twelve thousand pound rate for all twelve thousand. It is priced at the
first band rate for the first five thousand, the second band rate for the next
five, and the third for the last two. People get this wrong in both directions
and the office has had to send two letters explaining it.

Everything that is not weight is a flat amount. There is no attempt to scale a
route deviation by how far off the route the load went, because the statute
does not scale it and an office that invented a scale would be inventing law.

A second offence inside a year doubles. A third trebles, and there it stops:
the statute caps the multiplier at three and the office has never had to
explain why, because a carrier with four citations in a year has lost its
registration long before the fourth is priced.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from ..errors import Impossible, NotOnFile
from ..measure import Pounds
from ..money import Dollars

#: The most the repeat multiplier ever reaches, whatever the count.
MOST_REPEATS = 3

#: How far back the office looks when counting a repeat.
REPEAT_DAYS = 365


class Offence(str, Enum):
    """What was wrong, in the words the citation prints."""

    OVERWEIGHT = "overweight"
    NO_PERMIT = "no permit"
    OFF_ROUTE = "off route"
    IN_CURFEW = "moved in a curfew"
    NO_ESCORT = "escorts not present"
    NO_MARKING = "banners or flags missing"
    AXLES_NOT_AS_DECLARED = "axle spacing not as declared"
    EXPIRED_PERMIT = "permit expired"

    @property
    def by_weight(self) -> bool:
        """Whether the amount is read off a scale or is a flat sum."""
        return self is Offence.OVERWEIGHT

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Band:
    """One step of the overweight scale, priced in cents per pound."""

    upto: Pounds | None
    cents_per_pound: int

    def __post_init__(self) -> None:
        if self.cents_per_pound <= 0:
            raise Impossible("a penalty band charges something")

    def covers(self, over: Pounds) -> Pounds:
        """How much of this overage falls in this band."""
        if self.upto is None:
            return over
        return over if over < self.upto else self.upto

    def __str__(self) -> str:
        ceiling = "and over" if self.upto is None else f"to {self.upto}"
        return f"{ceiling} at {self.cents_per_pound}c a pound"


@dataclass(frozen=True)
class PenaltySchedule:
    """The statute as it stood from one day, with the day on it."""

    effective: date
    minimum: Dollars
    bands: tuple[Band, ...]
    flat: dict[Offence, Dollars]

    def __post_init__(self) -> None:
        if not self.bands:
            raise Impossible("a penalty schedule has at least one weight band")
        if any(one.upto is None for one in self.bands[:-1]):
            raise Impossible("only the last penalty band runs to the top")
        if self.bands[-1].upto is not None:
            raise Impossible("the last penalty band runs to the top")

    def flat_for(self, offence: Offence) -> Dollars:
        try:
            return self.flat[offence]
        except KeyError:
            raise NotOnFile(
                f"the schedule of {self.effective.isoformat()} does not price {offence}",
                offence=offence.value,
            ) from None

    def __str__(self) -> str:
        return f"penalties of {self.effective.isoformat()}"


def by_weight(schedule: PenaltySchedule, over: Pounds) -> Dollars:
    """The overweight penalty, walking the bands cumulatively."""
    if over.value <= 0:
        return Dollars.of(0)
    standing = over
    amount = Dollars.of(0)
    for band in schedule.bands:
        if standing.value <= 0:
            break
        inside = band.covers(standing)
        amount = amount + Dollars(inside.value * band.cents_per_pound)
        standing = Pounds(standing.value - inside.value)
    return amount


def repeats_multiplier(prior: int) -> int:
    """One for a first offence, two for a second, three for anything after."""
    if prior < 0:
        raise Impossible("a count of prior offences is not negative")
    return min(prior + 1, MOST_REPEATS)


@dataclass(frozen=True)
class Assessed:
    """What one citation comes to, with the working shown."""

    offence: Offence
    base: Dollars
    multiplier: int
    total: Dollars
    over: Pounds | None = None

    def lines(self) -> tuple[str, ...]:
        found = [f"{self.offence}  {self.base}"]
        if self.over is not None:
            found.append(f"  over by  {self.over}")
        if self.multiplier > 1:
            found.append(f"  repeat offence, times {self.multiplier}")
        found.append(f"  due  {self.total}")
        return tuple(found)

    def __str__(self) -> str:
        return f"{self.offence}: {self.total}"


def assess(
    schedule: PenaltySchedule,
    offence: Offence,
    over: Pounds | None = None,
    prior: int = 0,
) -> Assessed:
    """Price one offence against one schedule, repeats included.

    An overweight citation with no overage on it is a mistake at the roadside
    rather than a free one, so it is refused. Everything else ignores the
    overage if one is passed, which happens because the enforcement officer's
    form has one box and fills it in whatever the offence.
    """
    if offence.by_weight:
        if over is None:
            raise Impossible("an overweight citation says by how much")
        base = by_weight(schedule, over)
    else:
        base = schedule.flat_for(offence)
        over = None
    if base < schedule.minimum:
        base = schedule.minimum
    multiplier = repeats_multiplier(prior)
    return Assessed(offence, base, multiplier, base * multiplier, over)


def standard_schedule(effective: date | None = None) -> PenaltySchedule:
    """The schedule as it stands in the statute, for an office with no rows."""
    return PenaltySchedule(
        effective=effective or date(2024, 1, 1),
        minimum=Dollars.of(50),
        bands=(
            Band(Pounds(5000), 3),
            Band(Pounds(5000), 5),
            Band(Pounds(10000), 9),
            Band(None, 15),
        ),
        flat={
            Offence.NO_PERMIT: Dollars.of(500),
            Offence.OFF_ROUTE: Dollars.of(250),
            Offence.IN_CURFEW: Dollars.of(250),
            Offence.NO_ESCORT: Dollars.of(350),
            Offence.NO_MARKING: Dollars.of(100),
            Offence.AXLES_NOT_AS_DECLARED: Dollars.of(300),
            Offence.EXPIRED_PERMIT: Dollars.of(400),
        },
    )
