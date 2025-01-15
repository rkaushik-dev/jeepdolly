"""The carrier routes, and what a suspension does to the permits it holds."""

import pytest
from fastapi.testclient import TestClient

from jeepdolly.api.app import create_app
from jeepdolly.api.security import HEADER, Role
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit
from support import a_seeded_database, keys_for
from test_api_roles import body

FILED = "2025-04-07"
START = "2025-04-08"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def keys(factory):
    return keys_for(factory)


def client_for(factory, keys, role=Role.SUPERVISOR):
    made = TestClient(create_app(factory))
    made.headers[HEADER] = keys[role]
    return made


@pytest.fixture
def client(factory, keys):
    return client_for(factory, keys)


def issue_one(client):
    reference = client.post(f"/applications?filed={FILED}", json=body()).json()["reference"]
    client.post(f"/applications/{reference}/submit?on={FILED}")
    return client.post(f"/permits?reference={reference}&on={FILED}").json()["number"]


def test_a_carrier_is_registered_and_read_back(client):
    answer = client.post(
        f"/carriers?on={START}",
        json={"usdot": 3311002, "name": "Sons Rigging", "town": "Bangor", "state": "ME"},
    )
    assert answer.status_code == 201
    assert answer.json()["standing"] == "no certificate of insurance on file"
    assert client.get(f"/carriers/3311002?on={START}").json()["name"] == "Sons Rigging"


def test_the_same_carrier_twice_is_a_conflict(client):
    written = {"usdot": 1204471, "name": "Bickford", "town": "Newport", "state": "ME"}
    answer = client.post(f"/carriers?on={START}", json=written)
    assert answer.status_code == 409
    assert answer.json()["code"] == "conflict"


def test_a_carrier_with_paper_is_in_good_standing(client):
    assert client.get(f"/carriers/1204471?on={START}").json()["standing"] == "in good standing"


def test_a_certificate_filed_by_the_counter_puts_a_carrier_right(client):
    assert "no certificate" in client.get(f"/carriers/2299001?on={START}").json()["standing"]
    answer = client.post(
        f"/carriers/2299001/insurance?on={START}",
        json={
            "policy": "CA-99001",
            "insurer": "Acadia Mutual",
            "liability": "$1,000,000.00",
            "effective": "2025-01-01",
            "expires": "2025-12-31",
        },
    )
    assert answer.status_code == 201
    assert answer.json()["standing"] == "in good standing"


def test_a_bond_is_filed_the_same_way(client):
    answer = client.post(
        f"/carriers/1204471/bond?on={START}",
        json={
            "number": "SB-9001",
            "surety": "Penobscot Surety",
            "amount": "$50,000.00",
            "effective": "2025-01-01",
            "expires": "2026-12-31",
        },
    )
    assert answer.status_code == 201


def test_the_counter_searches_by_part_of_a_name(client):
    found = client.get(f"/carriers?name=crane&on={START}").json()
    assert [one["usdot"] for one in found] == [2299001]
    assert client.get(f"/carriers?name=nobody&on={START}").json() == []


def test_a_carrier_nobody_registered_is_a_404(client):
    assert client.get("/carriers/9999999").status_code == 404


def test_suspending_needs_a_supervisor(factory, keys):
    clerk = client_for(factory, keys, Role.CLERK)
    answer = clerk.post(f"/carriers/1204471/suspend?reason=unpaid&on={START}")
    assert answer.status_code == 403


def test_a_suspension_shows_up_in_the_standing(client):
    client.post(f"/carriers/1204471/suspend?reason=unpaid permits&on={START}")
    assert "suspended" in client.get(f"/carriers/1204471?on={START}").json()["standing"]


def test_a_suspension_voids_every_permit_the_carrier_holds(client):
    number = issue_one(client)
    client.post(f"/carriers/1204471/suspend?reason=unpaid permits&on={START}")
    assert client.get(f"/permits/{number}").json()["state"] == "void"
    assert client.get(f"/permits?day={START}").json() == []


def test_every_voided_permit_lands_on_the_trail_with_the_reason(client, factory):
    number = issue_one(client)
    client.post(f"/carriers/1204471/suspend?reason=unpaid permits&on={START}")
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.db, "permit", number)
        carrier = audit.for_document(work.db, "carrier", "1204471")
    assert [one.action for one in found] == ["permit.issued", "permit.voided"]
    assert found[1].detail == "unpaid permits"
    assert [one.action for one in carrier] == ["carrier.suspended"]


def test_restoring_lets_the_carrier_back_and_leaves_the_permits_dead(client):
    number = issue_one(client)
    client.post(f"/carriers/1204471/suspend?reason=unpaid permits&on={START}")
    answer = client.post("/carriers/1204471/restore?on=2025-04-09")
    assert answer.status_code == 200
    assert client.get("/carriers/1204471?on=2025-04-10").json()["standing"] == "in good standing"
    assert client.get(f"/permits/{number}").json()["state"] == "void"
