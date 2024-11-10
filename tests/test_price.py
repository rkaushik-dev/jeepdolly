"""Pricing a permit line by line, and what an amendment costs."""

from jeepdolly.fees.price import amendment, price
from jeepdolly.fees.schedule import FeeSchedule, standard_schedule
from jeepdolly.limits.classify import PermitKind
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars
from jeepdolly.routing.itinerary import Itinerary
from jeepdolly.routing.mileage import chargeable
from support import road_log

SCHEDULE = standard_schedule()
ACROSS = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")


def miles(idents=ACROSS, origin="waterville"):
    return chargeable(Itinerary(road_log(), origin, idents))


def quote(kind=PermitKind.ORDINARY, overage=0, review=False, idents=ACROSS, origin="waterville"):
    return price(SCHEDULE, kind, miles(idents, origin), Pounds(overage), review=review)


def test_a_plain_permit_is_the_base_fee_and_the_mileage():
    found = quote()
    assert len(found) == 3
    assert found.lines[0].description == "ordinary permit"
    assert found.lines[0].amount == Dollars.of(40)
    assert found.lines[1].description == "34 miles on arterial"
    assert found.lines[1].amount == Dollars.of(6, 12)
    assert found.lines[2].amount == Dollars.of(2, 60)
    assert found.total == Dollars.of(48, 72)


def test_each_class_of_road_gets_its_own_line_at_its_own_rate():
    found = quote()
    arterial = found.line("34 miles on arterial")
    state_aid = found.line("10 miles on state aid")
    assert arterial is not None and state_aid is not None
    assert state_aid.amount == Dollars.of(2, 60)


def test_a_class_with_no_rate_gets_no_line_at_all():
    found = quote(idents=("US201-3", "TR104-1", "SR8-1"), origin="skowhegan")
    assert found.line("3 miles on town way") is None


def test_the_weight_charge_is_a_line_when_there_is_an_overage():
    found = quote(overage=25000)
    assert found.line("25,000 lb over legal gross") is not None
    assert found.total == Dollars.of(188, 72)


def test_no_overage_means_no_weight_line():
    assert quote().line("0 lb over legal gross") is None


def test_a_study_costs_more_than_an_ordinary_permit_before_anything_else():
    ordinary = quote()
    study = quote(kind=PermitKind.STUDY)
    assert study.total - ordinary.total == Dollars.of(85)


def test_a_review_adds_its_own_line_and_says_what_it_is():
    found = quote(kind=PermitKind.SUPERLOAD, review=True)
    assert found.line("engineering review") is not None
    assert found.line("engineering review").amount == Dollars.of(200)


def test_a_short_trip_is_charged_up_to_the_minimum_mileage():
    found = price(SCHEDULE, PermitKind.ORDINARY, miles(("US2-1",), "skowhegan"), Pounds(0))
    assert found.line("7 miles on arterial") is not None
    short = found.line("3 miles to the minimum mileage")
    assert short is not None
    assert short.amount == Dollars.of(0, 54)
    assert found.total == Dollars.of(41, 80)


def test_a_cheap_permit_is_written_up_to_the_minimum_fee():
    cheap = FeeSchedule(
        effective=SCHEDULE.effective,
        base={PermitKind.ORDINARY: Dollars.of(10)},
        per_mile=SCHEDULE.per_mile,
        steps=SCHEDULE.steps,
        review=SCHEDULE.review,
        amendment=SCHEDULE.amendment,
        minimum=SCHEDULE.minimum,
    )
    found = price(cheap, PermitKind.ORDINARY, miles(("US2-1",), "skowhegan"), Pounds(0))
    assert found.minimum_applied
    assert found.total == Dollars.of(40)
    assert found.lines[-1].description == "minimum fee adjustment"


def test_the_quote_says_which_schedule_it_was_priced_off():
    assert quote().schedule == "schedule of 2024-01-01"
    assert str(quote()) == "3 lines, $48.72"


def test_an_amendment_is_the_flat_fee_when_nothing_got_dearer():
    before = quote(overage=25000)
    after = quote()
    found = amendment(SCHEDULE, before, after)
    assert len(found) == 1
    assert found.total == Dollars.of(25)


def test_an_amendment_that_costs_more_charges_the_difference_as_well():
    before = quote()
    after = quote(overage=25000)
    found = amendment(SCHEDULE, before, after)
    assert len(found) == 2
    assert found.line("difference in permit fee").amount == Dollars.of(140)
    assert found.total == Dollars.of(165)


def test_a_line_prints_the_way_the_receipt_prints_it():
    assert str(quote().lines[0]) == "ordinary permit  $40.00"
