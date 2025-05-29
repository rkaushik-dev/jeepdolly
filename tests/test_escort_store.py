"""Pilot car companies and cards, into the rows and back out again."""

from datetime import date

import pytest

from jeepdolly.errors import Conflict
from jeepdolly.escorts.register import Company, Endorsement, Operator, Standing
from jeepdolly.persistence.books import escorts_from
from jeepdolly.persistence.models.escorts import PilotCardRow, PilotCompanyRow, pack, unpack
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_seeded_database

JUNE = date(2025, 6, 10)


@pytest.fixture
def work():
    with UnitOfWork(a_seeded_database()) as unit:
        yield unit


def a_company(ident="PC-1", suspended=False):
    return Company(ident, "Kennebec Pilot Car", "Waterville", "207 555 0142", suspended=suspended)


def an_operator(card="C-1001", company="PC-1", expires=date(2026, 4, 30), endorsements=()):
    return Operator(
        card=card,
        name="R Dubois",
        company=company,
        issued=date(2023, 5, 1),
        expires=expires,
        endorsements=frozenset(endorsements),
    )


def seed(work, company=None, operators=()):
    work.db.add(PilotCompanyRow.of(company or a_company()))
    work.db.flush()
    for one in operators:
        work.db.add(PilotCardRow.of(one))
    work.db.flush()


def test_the_endorsements_pack_into_one_sorted_string():
    assert pack(frozenset()) == ""
    assert pack(frozenset({Endorsement.POLICE_LIAISON, Endorsement.HEIGHT_POLE})) == (
        "height pole,police liaison"
    )


def test_the_endorsements_unpack_back_into_the_set_they_were():
    assert unpack("height pole,police liaison") == frozenset(
        {Endorsement.HEIGHT_POLE, Endorsement.POLICE_LIAISON}
    )
    assert unpack("") == frozenset()
    assert unpack("  height pole  ") == frozenset({Endorsement.HEIGHT_POLE})


def test_an_endorsement_this_code_does_not_know_is_dropped_rather_than_raised():
    assert unpack("height pole,railroad flagging") == frozenset({Endorsement.HEIGHT_POLE})


def test_a_company_goes_in_and_comes_back_the_same_company(work):
    seed(work)
    assert escorts_from(work.db).company("PC-1") == a_company()


def test_a_card_goes_in_and_comes_back_the_same_card(work):
    one = an_operator(endorsements=(Endorsement.HEIGHT_POLE,))
    seed(work, operators=[one])
    assert escorts_from(work.db).operator("C-1001") == one


def test_the_register_reads_the_standing_off_the_rows(work):
    seed(work, operators=[an_operator()])
    assert escorts_from(work.db).standing("C-1001", JUNE) is Standing.GOOD


def test_a_suspended_company_row_suspends_its_cards_on_the_way_back(work):
    seed(work, company=a_company(suspended=True), operators=[an_operator()])
    assert escorts_from(work.db).standing("C-1001", JUNE) is Standing.COMPANY_SUSPENDED


def test_a_card_that_ran_out_is_still_on_the_register(work):
    seed(work, operators=[an_operator(expires=date(2024, 12, 31))])
    register = escorts_from(work.db)
    assert "C-1001" in register
    assert register.standing("C-1001", JUNE) is Standing.EXPIRED


def test_every_card_of_a_company_comes_back_under_it(work):
    seed(work, operators=[an_operator(), an_operator(card="C-1002")])
    assert len(escorts_from(work.db).operators("PC-1")) == 2


def test_two_companies_keep_their_own_cards(work):
    seed(work, operators=[an_operator()])
    work.db.add(PilotCompanyRow.of(a_company(ident="PC-2")))
    work.db.flush()
    work.db.add(PilotCardRow.of(an_operator(card="C-2001", company="PC-2")))
    work.db.flush()
    register = escorts_from(work.db)
    assert [one.card for one in register.operators("PC-2")] == ["C-2001"]
    assert len(register) == 2


def test_a_register_built_from_an_empty_database_holds_nothing(work):
    register = escorts_from(work.db)
    assert len(register) == 0
    assert register.companies() == ()


def test_a_card_with_a_bad_run_of_dates_is_refused_before_it_reaches_a_row():
    with pytest.raises(Conflict):
        an_operator(expires=date(2022, 1, 1))


def test_the_company_row_carries_its_cards_for_a_cascade(work):
    seed(work, operators=[an_operator()])
    row = work.db.get(PilotCompanyRow, "PC-1")
    assert [one.card for one in row.cards] == ["C-1001"]
    work.db.delete(row)
    work.db.flush()
    assert work.db.get(PilotCardRow, "C-1001") is None


def test_the_rows_say_what_they_are_when_something_prints_them(work):
    seed(work, operators=[an_operator()])
    assert repr(work.db.get(PilotCompanyRow, "PC-1")) == "<PilotCompanyRow PC-1>"
    assert repr(work.db.get(PilotCardRow, "C-1001")) == "<PilotCardRow C-1001>"
