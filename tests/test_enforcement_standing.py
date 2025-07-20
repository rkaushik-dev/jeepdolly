"""Where a carrier stands on what it owes, and when that holds a permit."""

from datetime import date

import pytest

from jeepdolly.enforcement.citation import CONTEST_DAYS
from jeepdolly.enforcement.penalty import Offence
from jeepdolly.enforcement.standing import (
    GRACE_DAYS,
    Account,
    EnforcementBook,
    Owed,
    due_day,
)
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars
from jeepdolly.permits.assessment import assess
from jeepdolly.persistence.books import enforcement_from
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import enforcement as service
from support import a_seeded_database, an_application, an_office

WRITTEN = date(2025, 4, 8)
DUE = date(2025, 5, 29)
APRIL = date(2025, 4, 8)
CLERK = "M Cyr"


def owed(number="E-25-0417", written=WRITTEN, amount=580, contested=False):
    return Owed(
        number=number,
        written=written,
        amount=Dollars.of(amount),
        due=due_day(written, CONTEST_DAYS),
        contested=contested,
    )


def account(usdot=1204471, unpaid=()):
    return Account(usdot, tuple(unpaid))


def test_the_due_day_is_the_contest_window_and_the_grace_after_it():
    assert due_day(WRITTEN, CONTEST_DAYS) == DUE
    assert (DUE - WRITTEN).days == CONTEST_DAYS + GRACE_DAYS


def test_a_citation_is_not_past_due_on_the_day_it_falls_due():
    one = owed()
    assert not one.past_due_on(DUE)
    assert one.past_due_on(date(2025, 5, 30))


def test_a_contested_citation_is_never_past_due_however_old():
    assert not owed(contested=True).past_due_on(date(2026, 1, 1))


def test_an_unpaid_citation_prints_its_due_day():
    assert str(owed()) == "E-25-0417  $580.00  due 2025-05-29"
    assert str(owed(contested=True)).endswith("(contested)")


def test_a_carrier_with_nothing_owes_nothing_and_is_not_held():
    one = account()
    assert one.owing == Dollars.of(0)
    assert one.hold(date(2026, 1, 1)) is None
    assert str(one) == "1204471 owes nothing"


def test_what_a_carrier_owes_adds_up():
    one = account(unpaid=[owed(), owed(number="E-25-0500", amount=250)])
    assert one.owing == Dollars.of(830)
    assert str(one) == "1204471 owes $830.00 on 2 citations"


def test_a_late_citation_does_not_hold_until_the_grace_has_run():
    one = account(unpaid=[owed()])
    assert one.hold(date(2025, 5, 1)) is None
    assert one.hold(date(2025, 6, 1)) is not None


def test_the_hold_names_the_oldest_one_and_counts_them_all():
    one = account(unpaid=[owed(), owed(number="E-25-0100", written=date(2025, 1, 20))])
    line = one.hold(date(2025, 6, 1))
    assert "E-25-0100" in line
    assert "2 unpaid citations" in line


def test_a_contested_citation_does_not_hold_a_carrier_at_all():
    one = account(unpaid=[owed(contested=True)])
    assert one.hold(date(2026, 1, 1)) is None
    assert one.owing == Dollars.of(580)


def test_the_book_answers_for_a_carrier_it_has_never_heard_of():
    book = EnforcementBook()
    assert book.account(9999999).owing == Dollars.of(0)
    assert book.hold(9999999, date(2026, 1, 1)) is None
    assert 9999999 not in book


def test_the_book_holds_what_it_was_given():
    book = EnforcementBook()
    book.add(account(unpaid=[owed()]))
    assert len(book) == 1
    assert 1204471 in book
    assert book.hold(1204471, date(2025, 6, 1)) is not None


def test_everybody_held_comes_back_worst_first():
    book = EnforcementBook()
    book.add(account(unpaid=[owed()]))
    book.add(
        account(
            usdot=2299001,
            unpaid=[owed(number="E-25-0600"), owed(number="E-25-0601")],
        )
    )
    assert [one.usdot for one in book.held(date(2025, 6, 1))] == [2299001, 1204471]


def test_nobody_is_held_before_anything_falls_due():
    book = EnforcementBook()
    book.add(account(unpaid=[owed()]))
    assert book.held(date(2025, 5, 1)) == ()


class TestAgainstTheDatabase:
    """The book built off the rows, and what the assessment makes of it."""

    @pytest.fixture
    def work(self):
        with UnitOfWork(a_seeded_database()) as unit:
            yield unit

    def file_one(self, work, number="E-25-0417", on=WRITTEN):
        found = service.file_citation(
            work,
            number=number,
            usdot=1204471,
            officer="Sgt A Thibodeau",
            offence=Offence.OVERWEIGHT,
            place="US201-2 at Fairfield",
            written=on,
            filed=on,
            who=CLERK,
            over=Pounds(12000),
        )
        work.db.flush()
        return found

    def test_an_empty_database_holds_nobody(self, work):
        assert len(enforcement_from(work.db)) == 0

    def test_an_unpaid_citation_shows_up_in_the_account(self, work):
        self.file_one(work)
        book = enforcement_from(work.db)
        assert book.account(1204471).owing == Dollars.of(580)

    def test_a_paid_citation_is_not_in_the_account_at_all(self, work):
        self.file_one(work)
        service.pay(work, "E-25-0417", date(2025, 4, 22), CLERK)
        work.db.flush()
        assert len(enforcement_from(work.db)) == 0

    def test_a_dismissed_citation_is_not_in_the_account(self, work):
        self.file_one(work)
        service.contest(work, "E-25-0417", date(2025, 4, 20), "grounds", CLERK)
        service.dismiss(work, "E-25-0417", date(2025, 5, 2), "scale", CLERK)
        work.db.flush()
        assert len(enforcement_from(work.db)) == 0

    def test_a_contested_citation_is_in_the_account_and_holds_nobody(self, work):
        self.file_one(work)
        service.contest(work, "E-25-0417", date(2025, 4, 20), "grounds", CLERK)
        work.db.flush()
        book = enforcement_from(work.db)
        assert book.account(1204471).owing == Dollars.of(580)
        assert book.hold(1204471, date(2026, 1, 1)) is None

    def test_a_permit_is_written_while_a_citation_is_merely_late(self, work):
        self.file_one(work)
        office = an_office(enforcement=enforcement_from(work.db))
        assert assess(office, an_application(), APRIL).permittable

    def test_a_permit_is_held_once_the_citation_is_past_due(self, work):
        self.file_one(work, on=date(2025, 1, 20))
        office = an_office(enforcement=enforcement_from(work.db))
        found = assess(office, an_application(), APRIL)
        assert not found.permittable
        assert any("E-25-0417" in one for one in found.refusals)

    def test_paying_it_lifts_the_hold_the_same_day(self, work):
        self.file_one(work, on=date(2025, 1, 20))
        service.pay(work, "E-25-0417", APRIL, CLERK)
        work.db.flush()
        office = an_office(enforcement=enforcement_from(work.db))
        assert assess(office, an_application(), APRIL).permittable

    def test_another_carriers_debt_does_not_hold_this_one(self, work):
        service.file_citation(
            work,
            number="E-25-0600",
            usdot=2299001,
            officer="Sgt A Thibodeau",
            offence=Offence.NO_PERMIT,
            place="US2-1",
            written=date(2025, 1, 20),
            filed=date(2025, 1, 20),
            who=CLERK,
        )
        work.db.flush()
        office = an_office(enforcement=enforcement_from(work.db))
        assert assess(office, an_application(), APRIL).permittable
