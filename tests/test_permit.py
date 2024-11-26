"""The permit document, its states, and the copies it keeps of everything."""

from datetime import date

import pytest

from jeepdolly.conditions.library import standard_library
from jeepdolly.domain.envelope import Envelope
from jeepdolly.errors import Impossible, NotAllowed
from jeepdolly.fees.price import Quote
from jeepdolly.measure import Miles, Pounds
from jeepdolly.permits.numbering import PermitNumber, Series
from jeepdolly.permits.permit import Permit, PermitState

LIBRARY = standard_library()
ISSUED = date(2025, 4, 7)
START = date(2025, 4, 8)
END = date(2025, 4, 10)


def a_permit(**kw):
    fields = {
        "number": PermitNumber(2025, Series.SINGLE, 4471),
        "application": "25-04471",
        "usdot": 1204471,
        "carrier_name": "Bickford Heavy Haul",
        "issued": ISSUED,
        "start": START,
        "end": END,
        "origin": "waterville",
        "destination": "pittsfield",
        "route": ("US201-1", "US201-2", "SA148-2", "SR43-1"),
        "narrative": "from Waterville, US 201 to Skowhegan, SA 148 to Canaan",
        "gross": Pounds(94000),
        "envelope": Envelope.read("12'", "13'6", "82'"),
        "distance": Miles.of(35, 20),
        "conditions": (LIBRARY.render("R-01"), LIBRARY.render("G-01")),
        "quote": Quote((), "schedule of 2024-01-01"),
    }
    fields.update(kw)
    return Permit(**fields)


def test_a_new_permit_is_issued_and_carries_one_line_of_history():
    permit = a_permit()
    assert permit.state is PermitState.ISSUED
    assert permit.history == [(ISSUED, "issued to Bickford Heavy Haul")]
    assert str(permit) == "25-P-004471 Bickford Heavy Haul issued"


def test_a_permit_that_ends_before_it_starts_is_refused():
    with pytest.raises(Impossible):
        a_permit(start=END, end=START)


def test_a_permit_valid_before_it_was_issued_is_refused():
    with pytest.raises(Impossible):
        a_permit(start=date(2025, 4, 1))


def test_a_permit_with_no_route_on_it_is_refused():
    with pytest.raises(Impossible):
        a_permit(route=())


def test_the_days_are_counted_inclusive_of_both_ends():
    assert a_permit().days == 3
    assert a_permit(end=START).days == 1


def test_a_permit_is_good_only_on_the_days_it_names():
    permit = a_permit()
    assert not permit.valid_on(date(2025, 4, 7))
    assert permit.valid_on(START)
    assert permit.valid_on(END)
    assert not permit.valid_on(date(2025, 4, 11))


def test_expiry_is_a_fact_about_the_calendar_and_not_a_state():
    permit = a_permit()
    assert permit.expired_on(date(2025, 4, 11))
    assert permit.state is PermitState.ISSUED
    assert permit.status_on(date(2025, 4, 11)) == "expired"
    assert permit.status_on(ISSUED) == "issued, not yet in force"
    assert permit.status_on(START) == "in force"


def test_voiding_records_the_reason_and_kills_the_permit():
    permit = a_permit()
    permit.void(START, "load cancelled by the customer")
    assert permit.state is PermitState.VOID
    assert not permit.valid_on(START)
    assert permit.status_on(START) == "void: load cancelled by the customer"
    assert permit.history[-1][1].startswith("void:")


def test_voiding_without_a_reason_is_refused():
    with pytest.raises(Impossible):
        a_permit().void(START, "   ")


def test_a_void_permit_cannot_be_voided_or_superseded_again():
    permit = a_permit()
    permit.void(START, "load cancelled")
    with pytest.raises(NotAllowed):
        permit.void(START, "again")
    with pytest.raises(NotAllowed):
        permit.supersede(START, PermitNumber(2025, Series.REPLACEMENT, 1))


def test_an_amendment_supersedes_and_the_chain_can_be_walked_both_ways():
    first = a_permit()
    second = a_permit(
        number=PermitNumber(2025, Series.REPLACEMENT, 1),
        supersedes=first.number,
    )
    first.supersede(START, second.number)
    assert first.state is PermitState.SUPERSEDED
    assert first.superseded_by == second.number
    assert second.supersedes == first.number
    assert first.status_on(START) == "superseded by 25-R-000001"


def test_the_conditions_are_copied_and_can_be_listed_by_code():
    permit = a_permit()
    assert permit.condition_codes() == ("R-01", "G-01")
    assert permit.conditions[0].text.startswith("Route as written")


def test_the_printed_face_is_what_the_officer_reads_first():
    face = a_permit().face().splitlines()
    assert face[0] == "25-P-004471  Bickford Heavy Haul (1204471)"
    assert face[1] == "Waterville to Pittsfield, 35.20 mi"
    assert face[2] == "12 ft wide, 13 ft 6 in high, 82 ft long"
    assert face[3] == "94,000 lb gross, 2025-04-08 to 2025-04-10"
