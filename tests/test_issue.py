"""The whole procedure on one application, and the permit at the end of it."""

from datetime import date

import pytest

from jeepdolly.domain.envelope import Envelope
from jeepdolly.domain.restriction import Restriction, RestrictionBook, RestrictionKind
from jeepdolly.errors import NotAllowed
from jeepdolly.limits.classify import PermitKind
from jeepdolly.measure import Inches, Pounds
from jeepdolly.permits.assessment import assess, governing_class
from jeepdolly.permits.issue import issue
from jeepdolly.seasons import SeasonBook, SeasonKind, SeasonOrder
from support import a_combination, an_application, an_office, legal_semi, make_stream

APRIL = date(2025, 4, 8)
FILED = date(2025, 4, 7)


def application(**kw):
    return an_application(**kw)


def test_an_ordinary_move_comes_back_permittable_with_a_price_on_it():
    found = assess(an_office(), application())
    assert found.permittable
    assert found.kind is PermitKind.ORDINARY
    assert found.itinerary is not None
    assert found.quote is not None
    assert found.quote.total.cents > 0
    assert str(found) == "25-04471: ordinary permit"


def test_the_route_is_the_one_the_hauler_wrote_when_he_wrote_one():
    found = assess(an_office(), application(route=("US201-1", "US201-2", "SA148-2", "SR43-1")))
    assert found.itinerary is not None
    assert found.itinerary.idents == ("US201-1", "US201-2", "SA148-2", "SR43-1")


def test_the_office_finds_a_route_when_the_hauler_left_it_blank():
    found = assess(an_office(), application())
    assert found.itinerary is not None
    assert found.itinerary.origin == "waterville"
    assert found.itinerary.destination == "pittsfield"


def test_a_suspended_carrier_is_refused_before_anything_is_measured():
    office = an_office()
    office.carriers.get(1204471).suspend("unpaid permits", date(2025, 1, 5))
    found = assess(office, application())
    assert not found.permittable
    assert "suspended" in found.refusals[0]


def test_a_carrier_with_no_paper_is_refused_and_the_rest_still_runs():
    found = assess(an_office(), application(usdot=2299001))
    assert not found.permittable
    assert found.refusals[0] == "no certificate of insurance on file"
    assert found.itinerary is not None


def test_a_divisible_load_is_refused_by_the_classification():
    found = assess(an_office(), application(commodity="GRVL"))
    assert not found.permittable
    assert "could have been loaded to legal weight" in found.refusals[0]


def test_a_legal_load_is_told_it_needs_no_permit_at_all():
    combination = a_combination(legal_semi(), Envelope.read("8'", "13'", "70'"))
    found = assess(an_office(), application(combination=combination))
    assert not found.permittable
    assert found.refusals == ("this load is legal and needs no permit",)
    assert found.kind is PermitKind.NONE


def test_the_governing_table_is_the_tightest_class_the_route_touches():
    found = assess(an_office(), application(route=("US201-1", "US201-2", "SA148-2", "SR43-1")))
    assert found.itinerary is not None
    office = an_office()
    governing = governing_class(found.itinerary, office.statutes, APRIL)
    assert governing.value == "state aid"


def test_a_closed_road_on_the_written_route_refuses_and_names_it():
    restrictions = RestrictionBook()
    restrictions.add(Restriction("R-1", "SA148-2", RestrictionKind.CLOSURE, "bridge out"))
    office = an_office(restrictions=restrictions)
    found = assess(office, application(route=("US201-1", "US201-2", "SA148-2", "SR43-1")))
    assert not found.permittable
    assert "SA148-2 closure" in found.refusals[0]


def test_a_posted_structure_on_the_route_refuses_the_whole_trip():
    across = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")
    found = assess(an_office(), application(route=across))
    assert not found.permittable
    assert "Carrabassett Stream will not take this load" in found.refusals


def test_a_thaw_order_shows_up_as_a_condition_rather_than_a_refusal():
    seasons = SeasonBook()
    seasons.file(SeasonOrder("25-04", "district 4", SeasonKind.THAW, APRIL, date(2025, 5, 2), 65))
    restrictions = RestrictionBook()
    restrictions.add(
        Restriction("R-2", "SA148-2", RestrictionKind.SEASONAL, "spring", region="district 4")
    )
    office = an_office(restrictions=restrictions, seasons=seasons)
    found = assess(office, application(route=("US201-1", "US201-2", "SA148-2", "SR43-1")))
    assert found.permittable
    assert any(one.code == "R-02" for one in found.conditions)


def test_a_wide_load_picks_up_escorts_marking_and_daylight_together():
    combination = a_combination(envelope=Envelope.read("15'", "13'6", "82'", "0", "6'"))
    across = ("US201-1", "US201-2", "SA148-2", "SR43-1")
    found = assess(an_office(), application(combination=combination, route=across))
    assert found.escorts is not None and found.escorts.front == 1
    assert found.marking is not None and found.marking.banners == 2
    assert found.limits is not None and found.limits.daylight_only
    assert "T-02" in [one.code for one in found.conditions]


def test_a_superload_is_classified_as_one_and_priced_as_one():
    stream = make_stream([12000, 30000, 30000, 30000, 30000, 30000], [200, 54, 240, 54, 54])
    combination = a_combination(stream, Envelope.read("12'", "13'6", "82'", "0", "6'"))
    found = assess(an_office(), application(combination=combination, commodity="XFMR"))
    assert found.kind is PermitKind.SUPERLOAD
    assert found.quote is not None
    assert found.quote.line("engineering review") is not None


def test_issuing_gives_a_number_and_moves_the_application_on():
    office = an_office()
    subject = application()
    subject.submit(FILED)
    found = assess(office, subject)
    permit = issue(office, found, FILED)
    assert str(permit.number) == "25-P-000001"
    assert permit.carrier_name == "Bickford Heavy Haul"
    assert permit.gross == Pounds(94000)
    assert subject.state.value == "issued"
    assert permit.conditions


def test_a_refused_assessment_will_not_issue():
    office = an_office()
    subject = application(commodity="GRVL")
    found = assess(office, subject)
    with pytest.raises(NotAllowed) as refusal:
        issue(office, found, FILED)
    assert refusal.value.facts["reasons"] == found.refusals


def test_a_superload_takes_a_number_out_of_its_own_series():
    office = an_office()
    combination = a_combination(envelope=Envelope.read("18'", "13'6", "82'", "0", "6'"))
    subject = application(combination=combination)
    subject.submit(FILED)
    found = assess(office, subject)
    assert found.kind is PermitKind.SUPERLOAD
    permit = issue(office, found, FILED)
    assert str(permit.number).startswith("25-S-")


def test_a_load_with_nowhere_to_go_reports_that_and_nothing_else():
    restrictions = RestrictionBook()
    for ident in ("I95-2", "SR43-1"):
        restrictions.add(Restriction(f"R-{ident}", ident, RestrictionKind.CLOSURE, "bridge out"))
    found = assess(an_office(restrictions=restrictions), application())
    assert found.refusals == ("no route this load can take on the days asked for",)
    assert found.itinerary is None
    assert found.conditions == ()


def test_a_load_too_tall_for_the_only_route_is_refused_on_the_clearance():
    restrictions = RestrictionBook()
    restrictions.add(
        Restriction("R-9", "US201-2", RestrictionKind.CLEARANCE, "arch", height=Inches.of(12))
    )
    office = an_office(restrictions=restrictions)
    found = assess(office, application(route=("US201-1", "US201-2", "SA148-2", "SR43-1")))
    assert not found.permittable
    assert "clearance" in found.refusals[0]
