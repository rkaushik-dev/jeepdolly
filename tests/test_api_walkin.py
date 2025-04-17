"""The window, where a hauler gets a permit or a reason in one request."""

from datetime import date

import pytest

from jeepdolly.api.security import Role
from jeepdolly.permits.application import ApplicationState
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


def test_a_walk_in_gets_a_permit_in_one_request(client):
    answer = client.post(f"/permits/walk-in?on={FILED}", json=body())
    assert answer.status_code == 201
    found = answer.json()
    assert found["number"] == "25-P-000001"
    assert found["conditions"]
    assert found["fees"]["total"].startswith("$")


def test_the_application_behind_it_is_on_file_and_issued(client, factory):
    client.post(f"/permits/walk-in?on={FILED}", json=body())
    found = client.get("/applications/25-F-000001").json()
    assert found["state"] == "issued"
    with UnitOfWork(factory) as work:
        trail = audit.for_document(work.db, "application", "25-F-000001")
    assert [one.action for one in trail] == ["application.filed", "application.submitted"]


def test_a_walk_in_that_cannot_be_permitted_says_every_reason(client):
    written = body()
    written["commodity"] = "GRVL"
    written["pieces"] = [{"commodity": "GRVL", "description": "gravel"}]
    answer = client.post(f"/permits/walk-in?on={FILED}", json=written)
    assert answer.status_code == 409
    assert answer.json()["facts"]["reasons"]
    assert answer.json()["facts"]["reference"] == "25-F-000001"


def test_a_refused_walk_in_leaves_the_application_refused_and_no_permit(client, factory):
    written = body()
    written["commodity"] = "GRVL"
    written["pieces"] = [{"commodity": "GRVL", "description": "gravel"}]
    client.post(f"/permits/walk-in?on={FILED}", json=written)
    assert client.get("/applications/25-F-000001").json()["state"] == "refused"
    with UnitOfWork(factory) as work:
        assert work.permits.on_the_road(date(2025, 4, 8)) == ()


def test_the_next_walk_in_still_gets_the_first_permit_number(client):
    written = body()
    written["commodity"] = "GRVL"
    written["pieces"] = [{"commodity": "GRVL", "description": "gravel"}]
    client.post(f"/permits/walk-in?on={FILED}", json=written)
    answer = client.post(f"/permits/walk-in?on={FILED}", json=body())
    assert answer.json()["number"] == "25-P-000001"


def test_a_walk_in_needs_a_clerk(factory):
    reader = keyed_client(Role.READER, factory)
    assert reader.post(f"/permits/walk-in?on={FILED}", json=body()).status_code == 403


def test_an_application_can_still_be_refused_on_its_own(client):
    reference = client.post(f"/applications?filed={FILED}", json=body()).json()["reference"]
    answer = client.post(f"/applications/{reference}/refuse?reason=the bridge is posted")
    assert answer.status_code == 200
    assert answer.json()["state"] == ApplicationState.REFUSED.value


def test_the_two_ways_in_produce_the_same_permit(client, factory):
    walk = client.post(f"/permits/walk-in?on={FILED}", json=body()).json()
    reference = client.post(f"/applications?filed={FILED}", json=body()).json()["reference"]
    client.post(f"/applications/{reference}/submit?on={FILED}")
    slow = client.post(f"/permits?reference={reference}&on={FILED}").json()
    assert walk["fees"]["total"] == slow["fees"]["total"]
    assert [one["code"] for one in walk["conditions"]] == [
        one["code"] for one in slow["conditions"]
    ]
