"""Taking money, and proving at the end of the day that it adds up."""

from datetime import date

import pytest

from jeepdolly.errors import Impossible, NotOnFile
from jeepdolly.money import Dollars
from jeepdolly.permits.numbering import PermitNumber, Series
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import till
from support import a_permit, a_seeded_database, an_application

MONDAY = date(2025, 4, 7)
TUESDAY = date(2025, 4, 8)
CLERK = "D Bickford"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def issued(factory):
    application = an_application()
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
        work.permits.add(a_permit(application))
        work.permits.add(a_permit(application, number=PermitNumber(2025, Series.SINGLE, 4472)))
        work.commit()
    return factory


def test_a_permit_with_nothing_against_it_is_owed_in_full(issued):
    with UnitOfWork(issued) as work:
        found = till.owing_on(work.db, "25-P-004471")
    assert found.charged == Dollars.of(46, 12)
    assert found.paid == Dollars(0)
    assert found.owed == Dollars.of(46, 12)
    assert not found.settled


def test_a_receipt_settles_it(issued):
    with UnitOfWork(issued) as work:
        till.take(work.db, "25-P-004471", Dollars.of(46, 12), "cash", CLERK, MONDAY)
        work.commit()
        found = till.owing_on(work.db, "25-P-004471")
    assert found.settled
    assert str(found).startswith("25-P-004471  Bickford Heavy Haul")


def test_a_part_payment_leaves_the_rest_owing(issued):
    with UnitOfWork(issued) as work:
        till.take(work.db, "25-P-004471", Dollars.of(20), "cash", CLERK, MONDAY)
        work.commit()
        found = till.owing_on(work.db, "25-P-004471")
    assert found.owed == Dollars.of(26, 12)


def test_a_payment_taken_twice_is_put_right_with_a_refund(issued):
    with UnitOfWork(issued) as work:
        till.take(work.db, "25-P-004471", Dollars.of(46, 12), "cash", CLERK, MONDAY)
        till.take(work.db, "25-P-004471", Dollars.of(46, 12), "cash", CLERK, MONDAY)
        work.commit()
        assert till.owing_on(work.db, "25-P-004471").owed == Dollars.of(-46, -12)
        till.take(work.db, "25-P-004471", Dollars.of(-46, -12), "refund", CLERK, TUESDAY)
        work.commit()
        assert till.owing_on(work.db, "25-P-004471").settled


def test_a_way_of_paying_the_office_does_not_take_is_refused(issued):
    with UnitOfWork(issued) as work, pytest.raises(Impossible) as refusal:
        till.take(work.db, "25-P-004471", Dollars.of(40), "goats", CLERK, MONDAY)
    assert refusal.value.facts["method"] == "goats"


def test_a_receipt_for_nothing_is_refused(issued):
    with UnitOfWork(issued) as work, pytest.raises(Impossible):
        till.take(work.db, "25-P-004471", Dollars(0), "cash", CLERK, MONDAY)


def test_a_receipt_against_a_permit_nobody_issued_is_refused(issued):
    with UnitOfWork(issued) as work, pytest.raises(NotOnFile):
        till.take(work.db, "25-P-999999", Dollars.of(40), "cash", CLERK, MONDAY)


def test_the_drawer_holds_cash_and_cheques_and_not_the_account_work(issued):
    with UnitOfWork(issued) as work:
        till.take(work.db, "25-P-004471", Dollars.of(46, 12), "cash", CLERK, MONDAY)
        till.take(work.db, "25-P-004472", Dollars.of(46, 12), "account", CLERK, MONDAY)
        work.commit()
        found = till.drawer(work.db, MONDAY)
    assert found.in_the_drawer == Dollars.of(46, 12)
    assert found.on_account == Dollars.of(46, 12)
    assert found.taken == Dollars.of(92, 24)
    assert found.receipts == 2


def test_the_drawer_is_counted_by_day(issued):
    with UnitOfWork(issued) as work:
        till.take(work.db, "25-P-004471", Dollars.of(46, 12), "cash", CLERK, MONDAY)
        work.commit()
        assert till.drawer(work.db, TUESDAY).receipts == 0
        assert till.drawer(work.db, MONDAY).lines()[1] == "in the drawer  $46.12"


def test_what_is_outstanding_is_oldest_first_and_loses_what_is_paid(issued):
    with UnitOfWork(issued) as work:
        assert len(till.outstanding(work.db, TUESDAY)) == 2
        till.take(work.db, "25-P-004471", Dollars.of(46, 12), "cheque", CLERK, MONDAY)
        work.commit()
        left = till.outstanding(work.db, TUESDAY)
    assert [one.permit for one in left] == ["25-P-004472"]


def test_a_carrier_is_told_one_number(issued):
    with UnitOfWork(issued) as work:
        assert till.owed_by_carrier(work.db, 1204471, TUESDAY) == Dollars.of(92, 24)
        till.take(work.db, "25-P-004471", Dollars.of(46, 12), "cash", CLERK, MONDAY)
        work.commit()
        assert till.owed_by_carrier(work.db, 1204471, TUESDAY) == Dollars.of(46, 12)
        assert till.owed_by_carrier(work.db, 2299001, TUESDAY) == Dollars(0)
