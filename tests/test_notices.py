"""The letters, and the rule that a refusal letter says everything."""

from datetime import date

import pytest

from jeepdolly.errors import Impossible
from jeepdolly.services.notices import (
    LINE_LENGTH,
    expiry_notice,
    issue_letter,
    refusal_letter,
    wrap,
)
from support import a_permit, an_application

APRIL = date(2025, 4, 7)


def test_a_sentence_is_broken_at_spaces_and_never_mid_word():
    lines = wrap("the load is nine inches too wide for the Kennebec River bridge", 20)
    assert all(len(one) <= 20 for one in lines)
    assert " ".join(lines) == "the load is nine inches too wide for the Kennebec River bridge"


def test_a_word_longer_than_the_line_is_left_alone_rather_than_cut():
    long_word = "supercalifragilisticexpialidocious"
    assert wrap(long_word, 20) == (long_word,)


def test_a_letter_narrower_than_anybody_writes_is_refused():
    with pytest.raises(Impossible):
        wrap("something", 19)


def test_a_refusal_letter_says_every_reason_in_the_order_they_were_found():
    letter = refusal_letter(
        an_application(),
        ("the Carrabassett bridge is posted at 48,000 lb", "US 2 is closed for culvert work"),
        APRIL,
    )
    text = letter.text()
    assert " 1. the Carrabassett bridge is posted" in text
    assert " 2. US 2 is closed" in text
    assert text.index("Carrabassett") < text.index("culvert")


def test_a_refusal_letter_with_no_reasons_is_a_mistake():
    with pytest.raises(Impossible):
        refusal_letter(an_application(), (), APRIL)


def test_a_refusal_letter_names_the_trip_it_is_about():
    letter = refusal_letter(an_application(), ("the load is too wide",), APRIL)
    assert "Waterville to Pittsfield" in letter.text()
    assert letter.about == "application 25-04471"
    assert letter.to == "Dana Bickford"


def test_every_letter_carries_the_office_the_day_and_a_number_to_ring():
    letter = refusal_letter(an_application(), ("the load is too wide",), APRIL)
    lines = letter.lines()
    assert lines[0] == "Motor Carrier Permit Office"
    assert lines[1] == "2025-04-07"
    assert lines[-1].endswith("207 555 0100.")


def test_a_letter_is_narrow_enough_to_survive_a_fax():
    letter = refusal_letter(
        an_application(),
        ("the Kennebec River bridge will not take the declared tridem at that spacing",),
        APRIL,
    )
    assert letter.widest <= LINE_LENGTH + 4


def test_the_letter_that_goes_with_a_permit_carries_the_face_and_the_conditions():
    permit = a_permit(an_application())
    letter = issue_letter(permit, APRIL)
    text = letter.text()
    assert "25-P-004471" in text
    assert "94,000 lb gross" in text
    assert "Conditions:" in text
    assert "- Route as written." in text


def test_an_expiry_notice_lists_the_permits_and_says_they_cannot_be_extended():
    permit = a_permit(an_application())
    letter = expiry_notice((permit,), "Bickford Heavy Haul", APRIL)
    text = letter.text()
    assert "25-P-004471  expires 2025-04-10" in text
    assert "cannot be extended" in text


def test_an_expiry_notice_about_nothing_is_a_mistake():
    with pytest.raises(Impossible):
        expiry_notice((), "Bickford Heavy Haul", APRIL)


def test_a_notice_says_what_it_is_when_it_is_printed():
    permit = a_permit(an_application())
    assert str(issue_letter(permit, APRIL)) == "permit 25-P-004471 to Bickford Heavy Haul"
