"""What the assessment makes of the wires over the route."""

from datetime import date

from jeepdolly.domain.envelope import Envelope
from jeepdolly.measure import Inches
from jeepdolly.permits.assessment import assess
from jeepdolly.utilities.lines import Crossing, LineBook, Owner
from support import a_combination, an_application, an_office

APRIL = date(2025, 4, 8)
FILED = date(2025, 4, 7)
# The load runs waterville to pittsfield, which the router sends up the
# interstate, so the wire that matters is the one on I95-2.
LATE_FILED = date(2025, 4, 7)


def load(height="13'6"):
    return a_combination(envelope=Envelope.read("12'", height, "82'", "0", "6'"))


def codes(found):
    return [one.code for one in found.conditions]


def book_with(height="19'", lift_to="26'", segment="I95-2", owner_notice=5):
    made = LineBook()
    made.add_owner(Owner("CMP", "Central Maine Power", "207 555 0100", owner_notice))
    made.add(
        Crossing(
            "L-9",
            segment,
            "Burnham line",
            Inches.parse(height),
            "CMP",
            None if lift_to is None else Inches.parse(lift_to),
        )
    )
    return made


def test_an_ordinary_load_goes_under_everything_and_says_nothing():
    found = assess(an_office(), an_application(), APRIL)
    assert found.permittable
    assert found.lines is not None
    assert found.lines.lifts == ()
    assert "U-01" not in codes(found)


def test_a_load_that_needs_a_lift_is_permitted_with_a_telephone_number():
    application = an_application(combination=load(height="20'"), filed=date(2025, 3, 1))
    found = assess(an_office(lines=book_with()), application, APRIL)
    assert found.permittable
    assert found.lines is not None
    assert len(found.lines.lifts) == 1
    printed = next(one for one in found.conditions if one.code == "U-01")
    assert (
        printed.text
        == "Line lift at I95-2 at Burnham line. Ring Central Maine Power on 207 555 0100."
    )
    assert "U-02" in codes(found)


def test_a_load_that_needs_no_lift_prints_no_reminder():
    found = assess(an_office(lines=book_with()), an_application(), APRIL)
    assert "U-02" not in codes(found)


def test_a_wire_nothing_can_be_done_about_refuses_the_application():
    application = an_application(combination=load(height="26'"), filed=date(2025, 3, 1))
    found = assess(an_office(lines=book_with(lift_to=None)), application, APRIL)
    assert not found.permittable
    assert any("hangs at" in one for one in found.refusals)


def test_a_lift_asked_for_too_late_refuses_the_application():
    application = an_application(combination=load(height="20'"), filed=LATE_FILED)
    found = assess(an_office(lines=book_with()), application, APRIL)
    assert not found.permittable
    assert any("working days notice" in one for one in found.refusals)


def test_the_same_load_filed_in_time_is_permitted():
    early = an_application(combination=load(height="20'"), filed=date(2025, 3, 1))
    assert assess(an_office(lines=book_with()), early, APRIL).permittable


def test_an_owner_wanting_no_notice_can_be_rung_the_day_before():
    application = an_application(combination=load(height="20'"), filed=LATE_FILED)
    office = an_office(lines=book_with(owner_notice=0))
    assert assess(office, application, APRIL).permittable


def test_a_wire_on_a_road_the_route_misses_is_nobodys_business():
    application = an_application(combination=load(height="26'"), filed=date(2025, 3, 1))
    office = an_office(lines=book_with(segment="SR8-1", lift_to=None))
    found = assess(office, application, APRIL)
    assert not any("hangs at" in one for one in found.refusals)


def test_two_lifts_on_one_route_both_print_with_their_own_place():
    made = book_with()
    made.add(Crossing("L-8", "I95-1", "Waterville flats", Inches.of(20), "CMP", Inches.of(26)))
    application = an_application(combination=load(height="21'"), filed=date(2025, 3, 1))
    found = assess(an_office(lines=made), application, APRIL)
    printed = [one for one in found.conditions if one.code == "U-01"]
    assert len(printed) == 2
    assert {one.text for one in printed} != {printed[0].text}


def test_the_reminder_is_printed_once_however_many_lifts_there_are():
    made = book_with()
    made.add(Crossing("L-8", "I95-1", "Waterville flats", Inches.of(20), "CMP", Inches.of(26)))
    application = an_application(combination=load(height="21'"), filed=date(2025, 3, 1))
    found = assess(an_office(lines=made), application, APRIL)
    assert codes(found).count("U-02") == 1
