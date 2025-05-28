"""The register of who is allowed to pilot a load."""

from datetime import date

import pytest

from jeepdolly.errors import Conflict, NotOnFile
from jeepdolly.escorts.register import (
    Company,
    Endorsement,
    EscortRegister,
    Operator,
    Standing,
)

JUNE = date(2025, 6, 10)
MARCH = date(2025, 3, 3)


def a_company(ident="PC-1", name="Kennebec Pilot Car", suspended=False):
    return Company(ident, name, "Waterville", "207 555 0142", suspended=suspended)


def an_operator(
    card="C-1001",
    name="R Dubois",
    company="PC-1",
    expires=date(2026, 4, 30),
    endorsements=(),
    suspended=False,
):
    return Operator(
        card=card,
        name=name,
        company=company,
        issued=date(2023, 5, 1),
        expires=expires,
        endorsements=frozenset(endorsements),
        suspended=suspended,
    )


def a_register(company=None, operators=()):
    register = EscortRegister()
    register.add_company(company or a_company())
    for one in operators:
        register.certify(one)
    return register


def test_a_company_prints_the_way_the_counter_reads_it():
    assert str(a_company()) == "PC-1  Kennebec Pilot Car, Waterville"
    assert str(a_company(suspended=True)).endswith("(suspended)")


def test_a_company_with_no_ident_is_not_a_company():
    with pytest.raises(Conflict):
        Company("  ", "Nobody", "Nowhere", "")


def test_a_card_that_expires_before_it_was_issued_is_refused():
    with pytest.raises(Conflict):
        an_operator(expires=date(2022, 1, 1))


def test_a_card_prints_its_company_and_its_run_out_date():
    assert str(an_operator()) == "C-1001  R Dubois  PC-1  to 2026-04-30"


def test_an_endorsement_shows_on_the_card_line():
    printed = str(an_operator(endorsements=(Endorsement.HEIGHT_POLE,)))
    assert printed.endswith("[height pole]")


def test_a_company_is_only_filed_once():
    register = a_register()
    with pytest.raises(Conflict):
        register.add_company(a_company())


def test_a_company_nobody_has_is_a_refusal_that_names_it():
    with pytest.raises(NotOnFile):
        EscortRegister().company("PC-9")


def test_a_card_cannot_be_certified_without_a_company_behind_it():
    register = EscortRegister()
    register.add_company(a_company())
    with pytest.raises(NotOnFile):
        register.certify(an_operator(company="PC-2"))


def test_the_same_card_is_not_certified_twice():
    register = a_register(operators=[an_operator()])
    with pytest.raises(Conflict):
        register.certify(an_operator())


def test_a_card_nobody_has_is_a_refusal_that_names_it():
    with pytest.raises(NotOnFile):
        a_register().operator("C-9999")


def test_a_good_card_on_the_day_of_travel_is_available():
    register = a_register(operators=[an_operator()])
    assert register.standing("C-1001", JUNE) is Standing.GOOD
    assert register.standing("C-1001", JUNE).available


def test_a_card_that_runs_out_before_the_move_is_expired_on_that_day():
    register = a_register(operators=[an_operator(expires=date(2025, 5, 31))])
    assert register.standing("C-1001", JUNE) is Standing.EXPIRED
    assert register.standing("C-1001", MARCH) is Standing.GOOD


def test_a_card_is_good_on_the_day_it_expires_and_not_the_day_after():
    register = a_register(operators=[an_operator(expires=JUNE)])
    assert register.standing("C-1001", JUNE) is Standing.GOOD
    assert register.standing("C-1001", date(2025, 6, 11)) is Standing.EXPIRED


def test_a_suspended_operator_is_suspended_whatever_the_date():
    register = a_register(operators=[an_operator(suspended=True)])
    assert register.standing("C-1001", MARCH) is Standing.SUSPENDED


def test_a_suspended_company_takes_every_good_card_with_it():
    register = a_register(a_company(suspended=True), [an_operator()])
    assert register.standing("C-1001", JUNE) is Standing.COMPANY_SUSPENDED
    assert not register.standing("C-1001", JUNE).available


def test_the_operators_own_suspension_is_read_before_the_companys():
    register = a_register(a_company(suspended=True), [an_operator(suspended=True)])
    assert register.standing("C-1001", JUNE) is Standing.SUSPENDED


def test_everybody_available_that_day_comes_back_in_card_order():
    register = a_register(
        operators=[
            an_operator(card="C-1003", name="A Poulin"),
            an_operator(card="C-1001"),
            an_operator(card="C-1002", name="M Levesque", expires=date(2025, 1, 1)),
        ]
    )
    assert [one.card for one in register.available(JUNE)] == ["C-1001", "C-1003"]


def test_an_endorsement_narrows_who_is_available():
    register = a_register(
        operators=[
            an_operator(card="C-1001"),
            an_operator(card="C-1002", endorsements=(Endorsement.HEIGHT_POLE,)),
        ]
    )
    found = register.available(JUNE, Endorsement.HEIGHT_POLE)
    assert [one.card for one in found] == ["C-1002"]
    assert found[0].carries(Endorsement.HEIGHT_POLE)
    assert not found[0].carries(Endorsement.POLICE_LIAISON)


def test_a_company_with_nobody_available_comes_back_empty():
    register = a_register(a_company(suspended=True), [an_operator()])
    assert register.available(JUNE) == ()


def test_the_operators_of_one_company_can_be_asked_for_on_their_own():
    register = a_register(operators=[an_operator()])
    register.add_company(a_company(ident="PC-2", name="Somerset Escort"))
    register.certify(an_operator(card="C-2001", name="J Hebert", company="PC-2"))
    assert [one.card for one in register.operators("PC-2")] == ["C-2001"]
    assert len(register.operators()) == 2


def test_the_cards_running_out_in_a_window_come_back_soonest_first():
    register = a_register(
        operators=[
            an_operator(card="C-1001", expires=date(2025, 9, 30)),
            an_operator(card="C-1002", expires=date(2025, 7, 15)),
            an_operator(card="C-1003", expires=date(2027, 1, 1)),
        ]
    )
    found = register.expiring(JUNE, date(2025, 12, 31))
    assert [one.card for one in found] == ["C-1002", "C-1001"]


def test_a_suspended_card_is_left_off_the_renewal_list():
    register = a_register(
        operators=[an_operator(card="C-1001", expires=date(2025, 7, 15), suspended=True)]
    )
    assert register.expiring(JUNE, date(2025, 12, 31)) == ()


def test_the_register_counts_cards_and_says_whether_it_holds_one():
    register = a_register(operators=[an_operator()])
    assert len(register) == 1
    assert "C-1001" in register
    assert "C-9999" not in register


def test_the_companies_come_back_in_ident_order():
    register = a_register()
    register.add_company(a_company(ident="PC-0", name="Androscoggin Pilot"))
    assert [one.ident for one in register.companies()] == ["PC-0", "PC-1"]
