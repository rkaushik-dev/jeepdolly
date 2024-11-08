"""The fee schedule, its weight steps, and which one is in force."""

from datetime import date

import pytest

from jeepdolly.domain.highway import HighwayClass
from jeepdolly.errors import Conflict, Impossible, NotOnFile
from jeepdolly.fees.schedule import FeeSchedule, ScheduleBook, WeightStep, standard_schedule
from jeepdolly.limits.classify import PermitKind
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars

SCHEDULE = standard_schedule()


def test_the_base_fee_is_read_by_the_kind_of_permit():
    assert SCHEDULE.base_for(PermitKind.ORDINARY) == Dollars.of(40)
    assert SCHEDULE.base_for(PermitKind.STUDY) == Dollars.of(125)
    assert SCHEDULE.base_for(PermitKind.SUPERLOAD) == Dollars.of(350)


def test_a_permit_kind_with_no_fee_is_refused_rather_than_charged_nothing():
    with pytest.raises(NotOnFile) as refusal:
        SCHEDULE.base_for(PermitKind.NONE)
    assert refusal.value.facts["kind"] == "none"


def test_the_mile_rate_is_dearest_on_the_roads_that_break_first():
    assert SCHEDULE.mile_rate(HighwayClass.INTERSTATE) == Dollars(12)
    assert SCHEDULE.mile_rate(HighwayClass.STATE_AID) == Dollars(26)
    assert SCHEDULE.mile_rate(HighwayClass.STATE_AID) > SCHEDULE.mile_rate(HighwayClass.ARTERIAL)


def test_no_overage_is_no_weight_charge():
    assert SCHEDULE.weight_charge(Pounds(0)) == Dollars(0)
    assert SCHEDULE.weight_charge(Pounds(-500)) == Dollars(0)


def test_a_part_block_is_charged_as_a_whole_one():
    assert SCHEDULE.weight_charge(Pounds(1)) == Dollars.of(5)
    assert SCHEDULE.weight_charge(Pounds(1000)) == Dollars.of(5)
    assert SCHEDULE.weight_charge(Pounds(1001)) == Dollars.of(10)


def test_the_charge_climbs_through_the_steps():
    assert SCHEDULE.weight_charge(Pounds(20000)) == Dollars.of(100)
    assert SCHEDULE.weight_charge(Pounds(25000)) == Dollars.of(140)
    assert SCHEDULE.weight_charge(Pounds(60000)) == Dollars.of(420)
    assert SCHEDULE.weight_charge(Pounds(100000)) == Dollars.of(900)


def test_the_step_boundary_is_charged_at_the_lower_rate():
    at_edge = SCHEDULE.weight_charge(Pounds(20000))
    past_edge = SCHEDULE.weight_charge(Pounds(21000))
    assert past_edge - at_edge == Dollars.of(8)


def test_a_step_prints_the_way_the_published_table_reads():
    assert str(SCHEDULE.steps[0]) == "$5.00 per 1,000 lb to 20,000 lb"
    assert str(SCHEDULE.steps[-1]) == "$12.00 per 1,000 lb and above"


def test_a_schedule_whose_last_step_has_a_ceiling_is_refused():
    with pytest.raises(Impossible) as refusal:
        FeeSchedule(
            effective=date(2025, 1, 1),
            base={},
            per_mile={},
            steps=(WeightStep(Pounds(20000), Dollars.of(5)),),
            review=Dollars.of(200),
            amendment=Dollars.of(25),
            minimum=Dollars.of(40),
        )
    assert "no ceiling" in str(refusal.value)


def test_a_schedule_with_steps_out_of_order_is_refused():
    with pytest.raises(Impossible):
        FeeSchedule(
            effective=date(2025, 1, 1),
            base={},
            per_mile={},
            steps=(
                WeightStep(Pounds(60000), Dollars.of(5)),
                WeightStep(Pounds(20000), Dollars.of(8)),
                WeightStep(None, Dollars.of(12)),
            ),
            review=Dollars.of(200),
            amendment=Dollars.of(25),
            minimum=Dollars.of(40),
        )


def test_a_schedule_with_no_steps_at_all_is_refused():
    with pytest.raises(Impossible):
        FeeSchedule(
            effective=date(2025, 1, 1),
            base={},
            per_mile={},
            steps=(),
            review=Dollars.of(200),
            amendment=Dollars.of(25),
            minimum=Dollars.of(40),
        )


def test_the_schedule_in_force_is_the_latest_one_that_had_started():
    book = ScheduleBook()
    book.file(standard_schedule(date(2024, 1, 1)))
    book.file(standard_schedule(date(2025, 7, 1)))
    assert book.on(date(2025, 3, 3)).effective == date(2024, 1, 1)
    assert book.on(date(2025, 8, 3)).effective == date(2025, 7, 1)
    assert str(book.on(date(2025, 8, 3))) == "schedule of 2025-07-01"


def test_two_schedules_starting_on_one_day_is_refused():
    book = ScheduleBook()
    book.file(standard_schedule(date(2024, 1, 1)))
    with pytest.raises(Conflict):
        book.file(standard_schedule(date(2024, 1, 1)))


def test_asking_before_any_schedule_existed_says_so():
    book = ScheduleBook()
    book.file(standard_schedule(date(2024, 1, 1)))
    with pytest.raises(NotOnFile):
        book.on(date(2023, 6, 1))
    assert len(book) == 1
