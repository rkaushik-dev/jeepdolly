"""The fee schedule, which is a table and lives in the database, not in code.

Every number in this file is one the legislature or the commissioner can change
without anybody touching the system, which is why they are all on a record with
an effective date rather than written into the pricing.

Four charges make a permit.

The base charge is per kind of permit. An ordinary single trip is one number, a
study is a bigger one because somebody has to read it, and a superload is
bigger again.

The mileage charge is per chargeable mile, by class of road. The interstate is
the cheapest and the state aid roads are dearest.

The weight charge is per thousand pounds over the legal gross, in steps, so
that the first ten thousand pounds over costs less per thousand than the
fiftieth does. The steps are the part of the schedule the trucking association
argues about every session.

The extras are flat: a review fee where an engineer is involved, an amendment
fee, and a minimum below which no permit is written at all.

The schedule is dated. A permit issued in March is priced by the March
schedule for its whole life, including when it is amended in June, because a
hauler who amends a permit is not buying a new one at the new price.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date

from ..domain.highway import HighwayClass
from ..errors import Conflict, Impossible, NotOnFile
from ..limits.classify import PermitKind
from ..measure import Pounds
from ..money import Dollars

#: Weight is charged in blocks of this many pounds, part blocks rounded up.
WEIGHT_BLOCK = 1000


@dataclass(frozen=True)
class WeightStep:
    """A band of overage and what each block in it costs."""

    up_to: Pounds | None
    per_block: Dollars

    def __str__(self) -> str:
        ceiling = "and above" if self.up_to is None else f"to {self.up_to}"
        return f"{self.per_block} per {WEIGHT_BLOCK:,} lb {ceiling}"


@dataclass(frozen=True)
class FeeSchedule:
    """One schedule, effective from one day."""

    effective: date
    base: dict[PermitKind, Dollars]
    per_mile: dict[HighwayClass, Dollars]
    steps: tuple[WeightStep, ...]
    review: Dollars
    amendment: Dollars
    minimum: Dollars
    annual: Dollars = field(default_factory=lambda: Dollars.of(500))

    def __post_init__(self) -> None:
        if not self.steps:
            raise Impossible("a schedule has at least one weight step")
        if self.steps[-1].up_to is not None:
            raise Impossible("the last weight step has no ceiling")
        ceilings = [step.up_to for step in self.steps[:-1]]
        if any(one is None for one in ceilings):
            raise Impossible("only the last weight step may be open ended")
        values = [one.value for one in ceilings if one is not None]
        if values != sorted(values) or len(set(values)) != len(values):
            raise Impossible("the weight steps climb")

    def base_for(self, kind: PermitKind) -> Dollars:
        try:
            return self.base[kind]
        except KeyError:
            raise NotOnFile(f"no base fee for a {kind} permit", kind=str(kind)) from None

    def mile_rate(self, highway_class: HighwayClass) -> Dollars:
        try:
            return self.per_mile[highway_class]
        except KeyError:
            raise NotOnFile(
                f"no mileage rate for {highway_class}", highway_class=str(highway_class)
            ) from None

    def weight_charge(self, overage: Pounds) -> Dollars:
        """Walk the steps, charging whole blocks and rounding part blocks up."""
        if overage.value <= 0:
            return Dollars(0)
        charged = Dollars(0)
        floor = 0
        for step in self.steps:
            ceiling = step.up_to.value if step.up_to is not None else overage.value
            if overage.value <= floor:
                break
            inside = min(overage.value, ceiling) - floor
            blocks = -(-inside // WEIGHT_BLOCK)
            charged = charged + step.per_block * blocks
            floor = ceiling
        return charged

    def __str__(self) -> str:
        return f"schedule of {self.effective.isoformat()}"


class ScheduleBook:
    """Every schedule that has been in force, asked for by day."""

    def __init__(self) -> None:
        self._schedules: list[FeeSchedule] = []

    def file(self, schedule: FeeSchedule) -> FeeSchedule:
        for existing in self._schedules:
            if existing.effective == schedule.effective:
                raise Conflict(f"a schedule already takes effect on {schedule.effective}")
        self._schedules.append(schedule)
        return schedule

    def on(self, day: date) -> FeeSchedule:
        candidates = [one for one in self._schedules if one.effective <= day]
        if not candidates:
            raise NotOnFile(f"no fee schedule in force on {day.isoformat()}")
        return max(candidates, key=lambda one: one.effective)

    def __len__(self) -> int:
        return len(self._schedules)


def standard_schedule(effective: date = date(2024, 1, 1)) -> FeeSchedule:
    """The schedule as it stands."""
    return FeeSchedule(
        effective=effective,
        base={
            PermitKind.ORDINARY: Dollars.of(40),
            PermitKind.STUDY: Dollars.of(125),
            PermitKind.SUPERLOAD: Dollars.of(350),
        },
        per_mile={
            HighwayClass.INTERSTATE: Dollars.of(0, 12),
            HighwayClass.ARTERIAL: Dollars.of(0, 18),
            HighwayClass.STATE_AID: Dollars.of(0, 26),
            HighwayClass.TOWN_WAY: Dollars(0),
        },
        steps=(
            WeightStep(Pounds(20000), Dollars.of(5)),
            WeightStep(Pounds(60000), Dollars.of(8)),
            WeightStep(None, Dollars.of(12)),
        ),
        review=Dollars.of(200),
        amendment=Dollars.of(25),
        minimum=Dollars.of(40),
        annual=Dollars.of(500),
    )


def standard_book(effective: date = date(2024, 1, 1)) -> ScheduleBook:
    book = ScheduleBook()
    book.file(standard_schedule(effective))
    return book
