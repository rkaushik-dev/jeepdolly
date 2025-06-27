"""The pilot car register over the interface."""

from datetime import date

import pytest

from jeepdolly.api.security import Role
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit
from support import a_seeded_database, keyed_client

JUNE = date(2025, 6, 10)


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def client(factory):
    return keyed_client(factory=factory)


def a_company(ident="PC-2"):
    return {
        "ident": ident,
        "name": "Somerset Escort Service",
        "town": "Skowhegan",
        "telephone": "207 555 0188",
    }


def a_card(card="C-2001", company="PC-2", endorsements=()):
    return {
        "card": card,
        "name": "J Hebert",
        "company": company,
        "issued": "2025-01-06",
        "expires": "2028-01-05",
        "endorsements": list(endorsements),
    }


def test_the_seeded_company_is_on_the_register(client):
    found = client.get("/pilots/companies").json()
    assert [one["ident"] for one in found] == ["PC-1"]
    assert found[0]["cards"] == 3


def test_a_new_company_can_be_filed_and_read_back(client):
    answer = client.post("/pilots/companies", json=a_company())
    assert answer.status_code == 201
    assert answer.json()["cards"] == 0
    assert client.get("/pilots/companies/PC-2").json()["name"] == "Somerset Escort Service"


def test_the_same_company_is_not_filed_twice(client):
    client.post("/pilots/companies", json=a_company())
    assert client.post("/pilots/companies", json=a_company()).status_code == 409


def test_a_company_nobody_has_is_a_404(client):
    assert client.get("/pilots/companies/PC-9").status_code == 404


def test_a_card_is_issued_against_a_company_on_file(client):
    client.post("/pilots/companies", json=a_company())
    answer = client.post("/pilots/cards", json=a_card())
    assert answer.status_code == 201
    assert answer.json()["company"] == "PC-2"
    assert answer.json()["endorsements"] == []


def test_a_card_against_a_company_nobody_has_is_a_404(client):
    assert client.post("/pilots/cards", json=a_card(company="PC-9")).status_code == 404


def test_the_same_card_number_is_not_issued_twice(client):
    client.post("/pilots/companies", json=a_company())
    client.post("/pilots/cards", json=a_card())
    assert client.post("/pilots/cards", json=a_card()).status_code == 409


def test_the_endorsements_come_back_sorted(client):
    client.post("/pilots/companies", json=a_company())
    answer = client.post(
        "/pilots/cards", json=a_card(endorsements=["police liaison", "height pole"])
    )
    assert answer.json()["endorsements"] == ["height pole", "police liaison"]


def test_an_endorsement_this_office_does_not_issue_is_refused(client):
    client.post("/pilots/companies", json=a_company())
    answer = client.post("/pilots/cards", json=a_card(endorsements=["railroad flagging"]))
    assert answer.status_code == 422


def test_the_cards_of_one_company_can_be_asked_for_on_their_own(client):
    client.post("/pilots/companies", json=a_company())
    client.post("/pilots/cards", json=a_card())
    found = client.get("/pilots/cards?company=PC-2").json()
    assert [one["card"] for one in found] == ["C-2001"]
    assert len(client.get("/pilots/cards").json()) == 4


def test_suspending_a_company_shows_on_it_and_on_the_trail(client, factory):
    answer = client.post("/pilots/companies/PC-1/suspension?reason=insurance%20lapsed")
    assert answer.json()["suspended"] is True
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.db, "pilot company", "PC-1")
    assert found[0].action == "pilot.company_suspended"
    assert found[0].detail == "insurance lapsed"


def test_a_company_can_be_let_back_by_the_same_switch(client):
    client.post("/pilots/companies/PC-1/suspension")
    answer = client.post("/pilots/companies/PC-1/suspension?suspended=false")
    assert answer.json()["suspended"] is False


def test_suspending_an_operator_shows_on_the_card(client, factory):
    answer = client.post("/pilots/cards/C-1001/suspension?reason=incident%20on%20US%202")
    assert answer.json()["suspended"] is True
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.db, "pilot card", "C-1001")
    assert found[0].action == "pilot.card_suspended"


def test_an_operator_can_be_let_back_too(client):
    client.post("/pilots/cards/C-1001/suspension")
    assert client.post("/pilots/cards/C-1001/suspension?suspended=false").json()["suspended"] is (
        False
    )


def test_a_card_nobody_has_cannot_be_suspended(client):
    assert client.post("/pilots/cards/C-9999/suspension").status_code == 404


def test_the_cards_running_out_come_back_soonest_first(client):
    found = client.get("/pilots/expiring?start=2024-01-01&end=2027-01-01").json()
    assert [one["card"] for one in found] == ["C-1003", "C-1001", "C-1002"]


def test_a_suspended_card_is_off_the_renewal_list(client):
    client.post("/pilots/cards/C-1003/suspension")
    found = client.get("/pilots/expiring?start=2024-01-01&end=2027-01-01").json()
    assert "C-1003" not in [one["card"] for one in found]


def test_a_window_with_nothing_in_it_comes_back_empty(client):
    assert client.get("/pilots/expiring?start=2030-01-01&end=2030-12-31").json() == []


def test_a_clerk_cannot_suspend_a_company(factory):
    clerk = keyed_client(role=Role.CLERK, factory=factory)
    assert clerk.post("/pilots/companies/PC-1/suspension").status_code == 403


def test_a_reader_cannot_issue_a_card(factory):
    reader = keyed_client(role=Role.READER, factory=factory)
    assert reader.post("/pilots/cards", json=a_card(company="PC-1")).status_code == 403
