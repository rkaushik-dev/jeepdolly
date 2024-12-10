"""The carrier tables, and the paper that is appended rather than edited."""

from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from jeepdolly.domain.carrier import Billing, Bond, Insurance
from jeepdolly.money import Dollars
from jeepdolly.persistence.base import create_all, memory_engine, session_factory
from jeepdolly.persistence.models import BondRow, CarrierRow, InsuranceRow, SuspensionRow
from support import a_carrier_register

MARCH = date(2025, 3, 3)
JULY = date(2025, 7, 1)


@pytest.fixture
def session():
    engine = memory_engine()
    create_all(engine)
    with session_factory(engine)() as opened:
        yield opened


def cover(policy="CA-88214", effective=date(2025, 1, 1), expires=date(2025, 6, 30)):
    return Insurance(policy, "Acadia Mutual", Dollars.of(1000000), effective, expires)


def loaded(session):
    register = a_carrier_register()
    for usdot in (1204471, 2299001):
        carrier = register.get(usdot)
        row = CarrierRow.of(carrier)
        if carrier.insurance:
            row.certificates.append(InsuranceRow.of(usdot, carrier.insurance, filed=MARCH))
        if carrier.bond:
            row.bonds.append(BondRow.of(usdot, carrier.bond))
        session.add(row)
    session.commit()
    return register


def test_a_carrier_and_its_paper_go_in_together(session):
    loaded(session)
    session.expunge_all()
    row = session.get(CarrierRow, 1204471)
    assert row.name == "Bickford Heavy Haul"
    assert len(row.certificates) == 1
    assert len(row.bonds) == 1
    assert repr(row) == "<CarrierRow 1204471>"


def test_the_carrier_comes_back_as_a_domain_object_for_a_given_day(session):
    loaded(session)
    carrier = session.get(CarrierRow, 1204471).to_domain(MARCH)
    assert carrier.in_good_standing(MARCH, MARCH)
    assert carrier.insurance.policy == "CA-88214"
    assert carrier.billing is Billing.COUNTER


def test_a_renewal_is_a_new_row_and_the_old_one_stays(session):
    loaded(session)
    row = session.get(CarrierRow, 1204471)
    row.certificates.append(
        InsuranceRow.of(1204471, cover("CA-99001", date(2025, 6, 1), date(2026, 5, 31)))
    )
    session.commit()
    assert len(session.get(CarrierRow, 1204471).certificates) == 2
    assert session.get(CarrierRow, 1204471).current_insurance(MARCH).policy == "CA-88214"


def test_the_certificate_in_force_is_the_one_covering_the_day_asked_about(session):
    loaded(session)
    row = session.get(CarrierRow, 1204471)
    row.certificates.append(
        InsuranceRow.of(1204471, cover("CA-99001", date(2026, 1, 1), date(2026, 12, 31)))
    )
    session.commit()
    assert session.get(CarrierRow, 1204471).current_insurance(date(2026, 3, 3)).policy == "CA-99001"
    assert session.get(CarrierRow, 1204471).current_insurance(date(2027, 1, 1)) is None


def test_a_carrier_with_no_paper_reports_it_the_same_way_the_domain_does(session):
    loaded(session)
    carrier = session.get(CarrierRow, 2299001).to_domain(MARCH)
    assert carrier.standing(MARCH, MARCH) == "no certificate of insurance on file"


def test_a_certificate_that_expires_before_it_starts_is_refused(session):
    loaded(session)
    session.add(
        InsuranceRow(
            policy="CA-BAD",
            usdot=1204471,
            insurer="Acadia Mutual",
            liability=Dollars.of(1000000),
            effective=date(2025, 6, 1),
            expires=date(2025, 1, 1),
        )
    )
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_certificate_for_a_carrier_nobody_registered_is_refused(session):
    session.add(InsuranceRow.of(9999999, cover()))
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_usdot_number_that_is_not_one_is_refused_by_the_database(session):
    session.add(CarrierRow(usdot=0, name="Nobody", town="Nowhere", state="ME"))
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_state_that_is_not_two_letters_is_refused_by_the_database(session):
    session.add(CarrierRow(usdot=5, name="Nobody", town="Nowhere", state="Maine"))
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_suspension_is_ended_rather_than_deleted(session):
    loaded(session)
    row = session.get(CarrierRow, 2299001)
    row.suspensions.append(
        SuspensionRow(
            usdot=2299001, reason="no certificate", from_day=MARCH, ordered_by="supervisor"
        )
    )
    session.commit()
    carrier = session.get(CarrierRow, 2299001).to_domain(MARCH)
    assert carrier.suspension_on(MARCH) is not None
    session.get(CarrierRow, 2299001).suspensions[0].until = date(2025, 3, 20)
    session.commit()
    assert session.get(CarrierRow, 2299001).to_domain(JULY).suspension_on(JULY) is None
    assert len(session.scalars(select(SuspensionRow)).all()) == 1


def test_a_suspension_that_lifts_before_it_starts_is_refused(session):
    loaded(session)
    session.add(
        SuspensionRow(usdot=1204471, reason="nothing", from_day=JULY, until=date(2025, 1, 1))
    )
    with pytest.raises(IntegrityError):
        session.commit()


def test_deleting_a_carrier_takes_its_paper_with_it(session):
    loaded(session)
    session.delete(session.get(CarrierRow, 1204471))
    session.commit()
    assert session.scalars(select(InsuranceRow)).all() == []
    assert session.scalars(select(BondRow)).all() == []


def test_a_bond_round_trips_through_the_row(session):
    loaded(session)
    bond = session.get(CarrierRow, 1204471).bonds[0].to_domain()
    assert isinstance(bond, Bond)
    assert bond.amount == Dollars.of(25000)
    assert bond.surety == "Penobscot Surety"
