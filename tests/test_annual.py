"""The annual permit, its envelope, and what it does not excuse."""

from datetime import date

import pytest

from jeepdolly.conditions.library import standard_library
from jeepdolly.domain.commodity import standard_book as commodities
from jeepdolly.domain.envelope import Envelope
from jeepdolly.errors import NotAllowed
from jeepdolly.fees.schedule import standard_schedule
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars
from jeepdolly.permits.annual import (
    ANNUAL_GROSS,
    EVERYWHERE,
    eligible,
    issue_annual,
    price_annual,
)
from jeepdolly.permits.numbering import Numbering

BOOK = commodities()
LIBRARY = standard_library()
SCHEDULE = standard_schedule()
NOVEMBER = date(2025, 11, 3)


def load(width="11'", height="13'6", length="90'", rear="6'"):
    return Envelope.read(width, height, length, "0", rear)


def write(envelope=None, gross=110000, commodity="CRNE", on=NOVEMBER):
    return issue_annual(
        Numbering(),
        LIBRARY,
        SCHEDULE,
        "25-F-000001",
        1204471,
        "Bickford Heavy Haul",
        envelope or load(),
        Pounds(gross),
        BOOK.get(commodity),
        on,
    )


def test_a_crane_inside_the_envelope_is_eligible():
    found = eligible(load(), Pounds(110000), BOOK.get("CRNE"))
    assert found.allowed
    assert str(found) == "eligible for an annual permit"


def test_a_load_past_the_envelope_is_not_and_says_which_measure():
    found = eligible(load(width="14'"), Pounds(110000), BOOK.get("CRNE"))
    assert not found.allowed
    assert found.reasons == ("width is past the annual envelope",)


def test_a_load_over_the_cap_is_not_eligible():
    found = eligible(load(), Pounds(130000), BOOK.get("CRNE"))
    assert "over the annual cap of 120,000 lb" in found.reasons[0]
    assert ANNUAL_GROSS.value == 120000


def test_a_divisible_load_is_not_eligible_and_neither_is_an_allowance_load():
    assert not eligible(load(), Pounds(90000), BOOK.get("GRVL")).allowed
    wood = eligible(load(), Pounds(90000), BOOK.get("WOOD"))
    assert not wood.allowed
    assert "moves under an allowance" in wood.reasons[0]


def test_an_annual_runs_a_year_from_the_day_it_was_issued():
    permit = write()
    assert permit.start == NOVEMBER
    assert permit.end == date(2026, 11, 2)
    assert permit.days == 365


def test_an_annual_has_no_route_and_says_what_it_covers():
    permit = write()
    assert permit.route == (EVERYWHERE,)
    assert permit.origin == EVERYWHERE
    assert permit.narrative.endswith("except as posted or closed")


def test_an_annual_takes_a_number_out_of_its_own_series():
    assert str(write().number).startswith("25-A-")


def test_an_annual_is_one_line_and_one_number():
    permit = write()
    assert len(permit.quote.lines) == 1
    assert permit.quote.total == Dollars.of(500)
    assert price_annual(SCHEDULE).lines[0].description == "annual permit"


def test_an_annual_carries_the_conditions_that_say_what_it_does_not_excuse():
    codes = write().condition_codes()
    assert "R-01" in codes
    assert "T-02" in codes
    assert "G-03" in codes
    assert "E-01" not in codes


def test_the_dates_are_printed_on_the_first_condition():
    permit = write()
    assert permit.conditions[0].code == "T-01"
    assert "2025-11-03 to 2026-11-02" in permit.conditions[0].text


def test_writing_an_annual_for_something_too_big_is_refused_with_the_reasons():
    with pytest.raises(NotAllowed) as refusal:
        write(envelope=load(width="16'"), gross=130000)
    assert len(refusal.value.facts["reasons"]) == 2


def test_the_envelope_on_the_permit_is_the_one_that_was_asked_for():
    permit = write(envelope=load(width="12'"))
    assert permit.envelope.width.value == 144
    assert permit.gross == Pounds(110000)
