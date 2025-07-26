"""The three steps of the procedure somebody reads on their own."""

from datetime import date

import pytest

from jeepdolly.domain.envelope import Envelope
from jeepdolly.domain.highway import HighwayClass
from jeepdolly.enforcement.standing import Account, EnforcementBook, Owed, due_day
from jeepdolly.errors import Impossible
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars
from jeepdolly.permits.assessment import (
    Assessment,
    entitled,
    governing_class,
    walk_route,
    weigh,
)
from jeepdolly.routing.itinerary import Itinerary
from support import an_application, an_office, road_log, tractor_lowboy

APRIL = date(2025, 4, 8)
ACROSS = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")
GROSS = Pounds(94000)


def trip(idents=ACROSS, origin="waterville"):
    return Itinerary(road_log(), origin, idents)


def held_book(usdot=1204471, written=date(2025, 1, 20)):
    book = EnforcementBook()
    book.add(
        Account(
            usdot,
            (
                Owed(
                    number="E-25-0100",
                    written=written,
                    amount=Dollars.of(500),
                    due=due_day(written, 21),
                ),
            ),
        )
    )
    return book


def test_a_carrier_in_good_standing_is_entitled_to_the_afternoon():
    assert entitled(an_office(), an_application(), APRIL, GROSS) == ()


def test_a_held_carrier_is_refused_before_anything_is_measured():
    office = an_office(enforcement=held_book())
    found = entitled(office, an_application(), APRIL, GROSS)
    assert len(found) == 1
    assert "E-25-0100" in found[0]


def test_a_carrier_with_no_paper_is_refused_on_its_own_standing():
    application = an_application(usdot=2299001)
    found = entitled(an_office(), application, APRIL, GROSS)
    assert found


def test_both_refusals_come_back_together_rather_than_the_first_one():
    application = an_application(usdot=2299001)
    office = an_office(enforcement=held_book(usdot=2299001))
    assert len(entitled(office, application, APRIL, GROSS)) == 2


def test_the_governing_class_is_the_least_generous_road_on_the_route():
    office = an_office()
    assert governing_class(trip(), office.statutes, APRIL) is HighwayClass.STATE_AID


def test_a_route_over_nothing_permittable_has_no_governing_class():
    office = an_office()
    with pytest.raises(Impossible):
        governing_class(trip(idents=("TR104-1",), origin="madison"), office.statutes, APRIL)


def test_the_load_is_weighed_against_the_tightest_table_the_route_touches():
    found = weigh(an_office(), trip(), tractor_lowboy(), APRIL)
    assert found.gross.value == 94000
    assert found.overweight


def test_the_interstate_is_the_tightest_road_on_gross_because_of_the_federal_cap():
    state = weigh(an_office(), trip(), tractor_lowboy(), APRIL)
    federal = weigh(an_office(), trip(idents=("I95-1", "I95-2")), tractor_lowboy(), APRIL)
    assert federal.gross_limit < state.gross_limit


def test_one_mile_of_interstate_holds_the_whole_trip_to_the_federal_cap():
    mixed = trip(idents=("I95-1", "US201-2", "SA148-2", "SR43-1"), origin="waterville")
    assert weigh(an_office(), mixed, tractor_lowboy(), APRIL).gross_limit == Pounds(80000)


def test_walking_a_clear_route_finds_nothing_in_the_way():
    reading = walk_route(an_office(), trip(), Envelope.read("12'", "13'6", "82'"), GROSS, APRIL)
    assert reading.clear


def test_walking_it_with_a_load_that_will_not_turn_finds_the_corner():
    long_one = Envelope.read("12'", "13'6", "150'")
    reading = walk_route(an_office(), trip(), long_one, GROSS, APRIL)
    assert not reading.clear
    assert any(one.kind == "corner" for one in reading.fatal)


def test_an_assessment_that_stopped_early_carries_only_what_it_had():
    found = Assessment(an_application(), ("no route",))
    assert not found.permittable
    assert found.itinerary is None
    assert found.conditions == ()
    assert found.quote is None
