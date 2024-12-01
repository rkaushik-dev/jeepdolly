"""The permit register and the four questions the telephone asks it."""

from datetime import date

import pytest

from jeepdolly.conditions.library import standard_library
from jeepdolly.domain.envelope import Envelope
from jeepdolly.errors import Conflict, NotOnFile, Unreadable
from jeepdolly.fees.price import Quote
from jeepdolly.measure import Miles, Pounds
from jeepdolly.permits.numbering import PermitNumber, Series
from jeepdolly.permits.permit import Permit, PermitState
from jeepdolly.permits.register import PermitRegister

LIBRARY = standard_library()
ISSUED = date(2025, 4, 7)
START = date(2025, 4, 8)
END = date(2025, 4, 10)


def a_permit(sequence=4471, usdot=1204471, series=Series.SINGLE, **kw):
    fields = {
        "number": PermitNumber(2025, series, sequence),
        "application": f"25-{sequence:05d}",
        "usdot": usdot,
        "carrier_name": "Bickford Heavy Haul",
        "issued": ISSUED,
        "start": START,
        "end": END,
        "origin": "waterville",
        "destination": "pittsfield",
        "route": ("US201-1", "US201-2"),
        "narrative": "from Waterville, US 201 to Skowhegan",
        "gross": Pounds(94000),
        "envelope": Envelope.read("12'", "13'6", "82'"),
        "distance": Miles.of(17),
        "conditions": (LIBRARY.render("R-01"),),
        "quote": Quote((), "schedule of 2024-01-01"),
    }
    fields.update(kw)
    return Permit(**fields)


def filled():
    register = PermitRegister()
    register.file(a_permit())
    register.file(a_permit(sequence=4472, end=date(2025, 4, 20)))
    register.file(a_permit(sequence=4473, usdot=2299001, issued=date(2025, 4, 1)))
    register.file(a_permit(sequence=1, series=Series.SUPERLOAD))
    return register


def test_a_permit_is_found_by_its_number_however_it_was_written_down():
    register = filled()
    assert register.get("25-P-004471").application == "25-04471"
    assert register.get(PermitNumber(2025, Series.SINGLE, 4471)).usdot == 1204471
    assert register.has("25-P-004471")
    assert "25-P-004471" in register
    assert len(register) == 4


def test_a_number_that_cannot_be_read_is_refused_before_the_lookup():
    with pytest.raises(Unreadable):
        filled().get("permit four four seven one")


def test_a_number_nobody_issued_says_so():
    with pytest.raises(NotOnFile) as refusal:
        filled().get("25-P-009999")
    assert refusal.value.facts["number"] == "25-P-009999"
    assert not filled().has("25-P-009999")


def test_the_same_permit_is_not_filed_twice():
    register = filled()
    with pytest.raises(Conflict):
        register.file(a_permit())


def test_a_carrier_gets_its_own_permits_newest_first():
    found = filled().for_carrier(1204471)
    assert len(found) == 3
    assert [str(one.number) for one in found][0].startswith("25-")
    assert all(one.usdot == 1204471 for one in found)
    assert filled().for_carrier(9999) == ()


def test_what_is_on_the_road_today_is_asked_by_day():
    register = filled()
    assert len(register.on_the_road(START)) == 4
    assert len(register.on_the_road(date(2025, 4, 15))) == 1
    assert register.on_the_road(date(2025, 5, 1)) == ()


def test_a_void_permit_is_not_on_the_road_and_is_still_on_file():
    register = filled()
    register.get("25-P-004471").void(START, "load cancelled")
    assert len(register.on_the_road(START)) == 3
    assert len(register) == 4
    assert len(register.in_state(PermitState.VOID)) == 1


def test_the_permits_issued_on_a_day_are_the_days_work():
    register = filled()
    assert len(register.issued_on(ISSUED)) == 3
    assert len(register.issued_on(date(2025, 4, 1))) == 1


def test_the_expiring_list_is_what_the_office_rings_about_on_friday():
    register = filled()
    expiring = register.expiring(date(2025, 4, 9), within=2)
    assert [str(one.number) for one in expiring] == [
        "25-P-004471",
        "25-P-004473",
        "25-S-000001",
    ]
    assert register.expiring(date(2025, 4, 9), within=20)[-1].end == date(2025, 4, 20)


def test_an_expired_permit_is_not_expiring_any_more():
    register = filled()
    assert register.expiring(date(2025, 4, 15), within=2) == ()


def test_the_superload_series_can_be_listed_on_its_own():
    register = filled()
    assert [str(one.number) for one in register.of_series(Series.SUPERLOAD)] == ["25-S-000001"]


def test_the_chain_of_replacements_can_be_walked_from_either_end():
    register = PermitRegister()
    first = register.file(a_permit())
    second = register.file(a_permit(sequence=1, series=Series.REPLACEMENT, supersedes=first.number))
    first.supersede(START, second.number)
    chain = register.chain("25-P-004471")
    assert [str(one.number) for one in chain] == ["25-P-004471", "25-R-000001"]
    assert register.chain("25-R-000001") == (second,)


def test_suspending_a_carrier_voids_everything_it_holds():
    register = filled()
    voided = register.void_all_for(1204471, START, "carrier suspended")
    assert len(voided) == 3
    assert all(one.state is PermitState.VOID for one in voided)
    assert register.get("25-P-004473").state is PermitState.ISSUED
    assert register.void_all_for(1204471, START, "again") == ()
