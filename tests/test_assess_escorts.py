"""What the assessment makes of the pilot cars a hauler names."""

from datetime import date

from jeepdolly.domain.envelope import Envelope
from jeepdolly.permits.assessment import assess
from support import a_combination, an_application, an_office

APRIL = date(2025, 4, 8)


def wide(width="15'"):
    return a_combination(envelope=Envelope.read(width, "13'6", "82'", "0", "6'"))


def tall():
    return a_combination(envelope=Envelope.read("15'", "15'", "82'", "0", "6'"))


def codes(found):
    return [one.code for one in found.conditions]


def test_a_load_needing_escorts_with_nobody_named_is_still_permittable():
    found = assess(an_office(), an_application(combination=wide()), APRIL)
    assert found.permittable
    assert found.naming is not None
    assert not found.naming.complete
    assert "E-06" in codes(found)
    assert "E-05" not in codes(found)


def test_the_named_pilot_cars_print_on_the_permit():
    application = an_application(combination=wide(), escort_cards=("C-1001",))
    found = assess(an_office(), application, APRIL)
    assert found.permittable
    printed = next(one for one in found.conditions if one.code == "E-05")
    assert printed.text == "Pilot cars for this move: R Dubois (C-1001)."


def test_naming_enough_cards_takes_the_reminder_off_the_permit():
    application = an_application(combination=wide(), escort_cards=("C-1001",))
    found = assess(an_office(), application, APRIL)
    assert found.naming is not None and found.naming.complete
    assert "E-06" not in codes(found)


def test_a_card_that_has_run_out_refuses_the_application():
    application = an_application(combination=wide(), escort_cards=("C-1003",))
    found = assess(an_office(), application, APRIL)
    assert not found.permittable
    assert any("C-1003 is expired" in one for one in found.refusals)


def test_a_card_nobody_has_refuses_the_application_and_quotes_it():
    application = an_application(combination=wide(), escort_cards=("C-9999",))
    found = assess(an_office(), application, APRIL)
    assert "pilot car card C-9999 is not on the register" in found.refusals


def test_a_tall_load_wants_the_height_pole_endorsement_on_somebody():
    plain = an_application(combination=tall(), escort_cards=("C-1001",))
    assert not assess(an_office(), plain, APRIL).permittable
    poled = an_application(combination=tall(), escort_cards=("C-1002",))
    assert assess(an_office(), poled, APRIL).permittable


def test_a_load_needing_no_escorts_prints_nothing_about_pilot_cars():
    found = assess(an_office(), an_application(), APRIL)
    assert found.escorts is not None and not found.escorts.any
    assert "E-06" not in codes(found)
    assert "E-05" not in codes(found)


def test_the_naming_survives_onto_the_assessment_for_the_letter():
    application = an_application(combination=wide(), escort_cards=("C-1001",))
    found = assess(an_office(), application, APRIL)
    assert found.naming is not None
    assert str(found.naming) == "1 of 1 named"


def test_an_assessment_that_never_found_a_route_carries_no_naming():
    nowhere = an_application(origin="madison", destination="madison")
    found = assess(an_office(), nowhere, APRIL)
    assert not found.permittable
    assert found.naming is None
