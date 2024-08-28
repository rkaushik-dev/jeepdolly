"""Carriers, their paper, and the order the office reports trouble in."""

from datetime import date

import pytest

from jeepdolly.domain.carrier import Billing, Bond, Carrier, CarrierRegister, Insurance
from jeepdolly.errors import Conflict, Impossible, NotOnFile
from jeepdolly.money import Dollars

JAN = date(2025, 1, 1)
MARCH = date(2025, 3, 3)
DEC = date(2025, 12, 31)


def cover(effective=JAN, expires=DEC, policy="CA-88214"):
    return Insurance(policy, "Acadia Mutual", Dollars.of(1000000), effective, expires)


def bond(effective=JAN, expires=DEC):
    return Bond("SB-4471", "Penobscot Surety", Dollars.of(25000), effective, expires)


def hauler(**kw):
    fields = {"usdot": 1204471, "name": "Bickford Heavy Haul", "town": "Newport", "state": "ME"}
    fields.update(kw)
    return Carrier(**fields)


def test_a_carrier_prints_with_its_number_because_names_repeat():
    assert str(hauler()) == "Bickford Heavy Haul (1204471)"


def test_a_number_that_is_not_a_usdot_number_is_refused():
    with pytest.raises(Impossible):
        hauler(usdot=0)
    with pytest.raises(Impossible):
        hauler(usdot=123456789)


def test_a_carrier_needs_a_name_and_a_state_that_looks_like_one():
    with pytest.raises(Impossible):
        hauler(name="  ")
    with pytest.raises(Impossible):
        hauler(state="Maine")
    with pytest.raises(Impossible):
        hauler(state="me")


def test_a_certificate_covers_a_span_and_not_just_a_day():
    paper = cover(expires=date(2025, 6, 30))
    assert paper.in_force(MARCH)
    assert paper.covers(MARCH, date(2025, 6, 1))
    assert not paper.covers(MARCH, date(2025, 7, 1))
    assert str(paper) == "CA-88214 Acadia Mutual to 2025-06-30"


def test_a_certificate_that_expires_before_it_starts_is_refused():
    with pytest.raises(Impossible):
        cover(effective=DEC, expires=JAN)


def test_a_certificate_with_no_limit_on_it_is_not_a_certificate():
    with pytest.raises(Impossible):
        Insurance("CA-1", "Acadia Mutual", Dollars(0), JAN, DEC)


def test_a_carrier_with_current_paper_is_in_good_standing():
    carrier = hauler(insurance=cover())
    assert carrier.in_good_standing(MARCH, MARCH)
    assert carrier.standing(MARCH, MARCH) is None


def test_a_carrier_with_no_certificate_at_all_is_stopped():
    assert hauler().standing(MARCH, MARCH) == "no certificate of insurance on file"


def test_a_permit_running_past_the_certificate_is_stopped_by_the_span():
    carrier = hauler(insurance=cover(expires=date(2025, 3, 10)))
    assert carrier.in_good_standing(MARCH, date(2025, 3, 9))
    assert not carrier.in_good_standing(MARCH, date(2025, 3, 20))
    assert "does not cover the whole trip" in carrier.standing(MARCH, date(2025, 3, 20))


def test_a_bond_is_only_wanted_once_the_load_is_heavy():
    carrier = hauler(insurance=cover())
    assert not carrier.needs_bond(119000)
    assert carrier.needs_bond(130000)
    assert carrier.in_good_standing(MARCH, MARCH, gross_pounds=119000)
    assert carrier.standing(MARCH, MARCH, gross_pounds=130000).startswith("no bond on file")


def test_a_bonded_carrier_clears_the_heavy_check():
    carrier = hauler(insurance=cover(), bond=bond())
    assert carrier.in_good_standing(MARCH, MARCH, gross_pounds=180000)


def test_a_bond_that_lapses_inside_the_trip_stops_it_too():
    carrier = hauler(insurance=cover(), bond=bond(expires=date(2025, 3, 5)))
    assert "bond SB-4471 does not cover" in carrier.standing(MARCH, date(2025, 3, 20), 180000)


def test_a_bond_that_expires_before_it_takes_effect_is_refused():
    with pytest.raises(Impossible):
        bond(effective=DEC, expires=JAN)


def test_a_suspension_beats_every_other_finding():
    carrier = hauler()
    carrier.suspend("failed to pay four permits", date(2025, 2, 1))
    assert carrier.insurance is None
    assert carrier.standing(MARCH, MARCH).startswith("suspended 2025-02-01 to further notice")


def test_a_suspension_with_an_end_lifts_on_its_own():
    carrier = hauler(insurance=cover())
    carrier.suspend("late affidavit", date(2025, 2, 1), until=date(2025, 2, 28))
    assert carrier.suspension_on(date(2025, 2, 15)) is not None
    assert carrier.suspension_on(MARCH) is None
    assert carrier.in_good_standing(MARCH, MARCH)


def test_a_suspension_that_lifts_before_it_starts_is_refused():
    with pytest.raises(Impossible):
        hauler().suspend("nothing", date(2025, 3, 1), until=date(2025, 2, 1))


def test_a_suspension_prints_the_reason_because_the_letter_needs_it():
    order = hauler().suspend("failed to pay four permits", date(2025, 2, 1), date(2025, 4, 1))
    assert str(order) == "suspended 2025-02-01 to 2025-04-01: failed to pay four permits"


def test_the_register_refuses_the_same_usdot_twice():
    register = CarrierRegister()
    register.add(hauler())
    with pytest.raises(Conflict) as refusal:
        register.add(hauler(name="Bickford Trucking"))
    assert refusal.value.facts["usdot"] == 1204471
    assert len(register) == 1


def test_asking_for_a_carrier_nobody_registered_says_the_number_back():
    with pytest.raises(NotOnFile) as refusal:
        CarrierRegister().get(9999)
    assert refusal.value.facts["usdot"] == 9999


def test_the_counter_searches_by_any_part_of_the_name():
    register = CarrierRegister()
    register.add(hauler())
    register.add(hauler(usdot=2299001, name="Aroostook Crane Service"))
    register.add(hauler(usdot=3311002, name="Bickford Sons Rigging"))
    assert [one.usdot for one in register.search("bickford")] == [1204471, 3311002]
    assert [one.name for one in register.search("CRANE")] == ["Aroostook Crane Service"]
    assert register.search("   ") == ()
    assert 2299001 in register


def test_the_suspended_list_is_what_the_supervisor_reads_on_monday():
    register = CarrierRegister()
    good = register.add(hauler(insurance=cover(), billing=Billing.ACCOUNT))
    bad = register.add(hauler(usdot=2299001, name="Aroostook Crane Service"))
    bad.suspend("no certificate", date(2025, 2, 1))
    assert [one.name for one in register.suspended_on(MARCH)] == ["Aroostook Crane Service"]
    assert good.billing is Billing.ACCOUNT
    assert str(good.billing) == "account"
