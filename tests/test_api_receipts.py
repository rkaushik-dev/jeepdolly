"""Taking money over the interface, and the drawer at half past four."""

import pytest

from jeepdolly.api.security import Role
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit
from support import a_seeded_database, keyed_client
from test_api_roles import body

FILED = "2025-04-07"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def client(factory):
    return keyed_client(factory=factory)


@pytest.fixture
def number(client):
    return client.post(f"/permits/walk-in?on={FILED}", json=body()).json()["number"]


def test_a_permit_starts_owed_in_full(client, number):
    found = client.get(f"/receipts/owing/{number}").json()
    assert found["paid"] == "$0.00"
    assert found["owed"] == found["charged"]
    assert not found["settled"]


def test_a_payment_settles_it_and_says_so(client, number):
    owed = client.get(f"/receipts/owing/{number}").json()["owed"]
    answer = client.post(
        f"/receipts?on={FILED}",
        json={"permit": number, "amount": owed, "method": "cash"},
    )
    assert answer.status_code == 201
    assert answer.json()["settled"]
    assert answer.json()["paid"] == owed


def test_a_part_payment_leaves_the_rest(client, number):
    answer = client.post(
        f"/receipts?on={FILED}",
        json={"permit": number, "amount": "$10.00", "method": "cheque"},
    )
    assert not answer.json()["settled"]
    assert answer.json()["paid"] == "$10.00"


def test_a_way_of_paying_the_office_does_not_take_is_refused(client, number):
    answer = client.post(
        f"/receipts?on={FILED}",
        json={"permit": number, "amount": "$10.00", "method": "goats"},
    )
    assert answer.status_code == 422
    assert answer.json()["facts"]["method"] == "goats"


def test_a_payment_against_a_permit_nobody_issued_is_a_404(client):
    answer = client.post(
        f"/receipts?on={FILED}",
        json={"permit": "25-P-999999", "amount": "$10.00", "method": "cash"},
    )
    assert answer.status_code == 404


def test_taking_money_needs_a_clerk(factory, number):
    reader = keyed_client(Role.READER, factory)
    answer = reader.post(
        f"/receipts?on={FILED}", json={"permit": number, "amount": "$10.00", "method": "cash"}
    )
    assert answer.status_code == 403


def test_a_payment_lands_on_the_trail(client, number, factory):
    client.post(
        f"/receipts?on={FILED}",
        json={"permit": number, "amount": "$10.00", "method": "cash"},
    )
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.db, "permit", number)
    assert [one.action for one in found] == ["permit.issued", "payment.taken"]
    assert found[1].detail == "cash $10.00"


def test_the_outstanding_list_loses_what_is_paid(client, number):
    assert len(client.get(f"/receipts/outstanding?day={FILED}").json()) == 1
    owed = client.get(f"/receipts/owing/{number}").json()["owed"]
    client.post(f"/receipts?on={FILED}", json={"permit": number, "amount": owed, "method": "cash"})
    assert client.get(f"/receipts/outstanding?day={FILED}").json() == []


def test_a_carrier_is_told_one_number(client, number):
    found = client.get(f"/receipts/carrier/1204471?day={FILED}").json()
    assert found["usdot"] == 1204471
    assert found["owed"].startswith("$")


def test_the_drawer_splits_the_cash_from_the_account_work(client, number):
    client.post(
        f"/receipts?on={FILED}", json={"permit": number, "amount": "$20.00", "method": "cash"}
    )
    client.post(
        f"/receipts?on={FILED}",
        json={"permit": number, "amount": "$26.12", "method": "account"},
    )
    found = client.get(f"/receipts/drawer?day={FILED}").json()
    assert found["in_the_drawer"] == "$20.00"
    assert found["on_account"] == "$26.12"
    assert found["receipts"] == 2
    assert found["lines"][0] == "till 2025-04-07"
