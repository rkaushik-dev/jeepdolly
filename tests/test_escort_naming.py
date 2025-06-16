"""Checking the pilot cars a hauler names against the register."""

from datetime import date

from jeepdolly.escorts.naming import NOT_NAMED, check, shortfall
from jeepdolly.escorts.register import Company, Endorsement, EscortRegister, Operator
from jeepdolly.escorts.rules import Escorts

JUNE = date(2025, 6, 10)


def register(cards=(), suspended=False):
    made = EscortRegister()
    made.add_company(Company("PC-1", "Kennebec Pilot Car", "Waterville", "207 555 0142", suspended))
    for card, expires, endorsements, off in cards:
        made.certify(
            Operator(
                card=card,
                name=f"Operator {card[-1]}",
                company="PC-1",
                issued=date(2023, 5, 1),
                expires=expires,
                endorsements=frozenset(endorsements),
                suspended=off,
            )
        )
    return made


def two_good():
    return register(
        [
            ("C-1001", date(2026, 4, 30), (), False),
            ("C-1002", date(2026, 4, 30), (Endorsement.HEIGHT_POLE,), False),
        ]
    )


def escorts(front=1, rear=1, police=False, pole=False):
    return Escorts(front, rear, police, pole)


def test_naming_nothing_is_allowed_and_says_so_on_the_permit():
    found = check(two_good(), (), escorts(), JUNE)
    assert found.acceptable
    assert not found.complete
    assert found.line() == NOT_NAMED
    assert str(found) == "no pilot cars named"


def test_two_good_cards_cover_a_load_that_wants_two():
    found = check(two_good(), ("C-1001", "C-1002"), escorts(), JUNE)
    assert found.acceptable
    assert found.complete
    assert found.line() == "Operator 1 (C-1001), Operator 2 (C-1002)"
    assert str(found) == "2 of 2 named"


def test_one_good_card_on_a_load_that_wants_two_is_short_but_not_refused():
    found = check(two_good(), ("C-1001",), escorts(), JUNE)
    assert found.acceptable
    assert not found.complete
    assert shortfall(found) == 1


def test_naming_more_than_the_load_needs_is_nobodys_business_but_the_carriers():
    found = check(two_good(), ("C-1001", "C-1002"), escorts(front=1, rear=0), JUNE)
    assert found.complete
    assert shortfall(found) == 0


def test_a_card_nobody_has_is_refused_and_the_refusal_quotes_it():
    found = check(two_good(), ("C-9999",), escorts(), JUNE)
    assert not found.acceptable
    assert found.refusals == ("pilot car card C-9999 is not on the register",)
    assert str(found) == "1 of 1 named cards are no good"


def test_a_card_that_has_run_out_by_the_day_of_travel_is_refused():
    book = register([("C-1001", date(2025, 5, 31), (), False)])
    found = check(book, ("C-1001",), escorts(), JUNE)
    assert found.refusals == ("pilot car card C-1001 is expired on 2025-06-10",)


def test_a_suspended_card_is_refused_and_the_refusal_says_which_kind():
    book = register([("C-1001", date(2026, 4, 30), (), True)])
    assert "suspended" in check(book, ("C-1001",), escorts(), JUNE).refusals[0]


def test_a_card_from_a_suspended_company_is_refused_too():
    book = register([("C-1001", date(2026, 4, 30), (), False)], suspended=True)
    assert "company suspended" in check(book, ("C-1001",), escorts(), JUNE).refusals[0]


def test_the_same_card_named_twice_is_refused_once():
    found = check(two_good(), ("C-1001", "C-1001"), escorts(), JUNE)
    assert found.refusals == ("pilot car card C-1001 is named twice",)
    assert len(found.good) == 1


def test_a_load_wanting_a_height_pole_needs_somebody_examined_on_one():
    found = check(two_good(), ("C-1001",), escorts(pole=True), JUNE)
    assert found.refusals == (
        "this load needs a height pole and no card named carries the endorsement",
    )


def test_the_endorsement_on_any_named_card_satisfies_the_pole():
    found = check(two_good(), ("C-1001", "C-1002"), escorts(pole=True), JUNE)
    assert found.acceptable


def test_a_load_wanting_a_pole_with_nobody_named_is_not_refused_yet():
    found = check(two_good(), (), escorts(pole=True), JUNE)
    assert found.acceptable
    assert not found.complete


def test_the_bad_card_does_not_take_the_good_one_down_with_it():
    found = check(two_good(), ("C-1001", "C-9999"), escorts(), JUNE)
    assert [one.card for one in found.good] == ["C-1001"]
    assert len(found.refusals) == 1


def test_a_police_escort_wants_no_card_because_the_trooper_holds_none():
    found = check(two_good(), ("C-1001", "C-1002"), escorts(police=True), JUNE)
    assert found.acceptable


def test_the_shortfall_never_goes_below_nothing():
    found = check(two_good(), ("C-1001", "C-1002"), escorts(front=0, rear=0), JUNE)
    assert shortfall(found) == 0
