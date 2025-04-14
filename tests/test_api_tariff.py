"""Filing a published table, and the day it starts working."""

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


def table(effective="2025-06-01", gross="104,000 lb"):
    return {
        "highway_class": "arterial",
        "effective": effective,
        "single": "23000",
        "tandem": "42000",
        "tridem": "49000",
        "quad": "57000",
        "beyond": "7000",
        "gross": gross,
        "tire_rate": 600,
    }


def test_what_is_in_force_today_is_one_table_per_class(client):
    found = client.get(f"/tariff/statutory?day={FILED}").json()
    assert len(found) == 4
    arterial = next(one for one in found if one["highway_class"] == "arterial")
    assert arterial["gross"] == "100,000 lb"
    assert arterial["effective"] == "2024-01-01"


def test_a_new_table_is_filed_and_takes_effect_on_its_own_day(client):
    answer = client.post("/tariff/statutory", json=table())
    assert answer.status_code == 201
    assert answer.json()["gross"] == "104,000 lb"
    before = client.get(f"/tariff/statutory?day={FILED}").json()
    after = client.get("/tariff/statutory?day=2025-07-01").json()
    assert next(one for one in before if one["highway_class"] == "arterial")["gross"] == (
        "100,000 lb"
    )
    assert next(one for one in after if one["highway_class"] == "arterial")["gross"] == (
        "104,000 lb"
    )


def test_two_tables_for_one_class_on_one_day_is_a_conflict(client):
    client.post("/tariff/statutory", json=table())
    answer = client.post("/tariff/statutory", json=table())
    assert answer.status_code == 409
    assert answer.json()["facts"]["highway_class"] == "arterial"


def test_filing_a_table_needs_a_supervisor(factory):
    clerk = keyed_client(Role.CLERK, factory)
    assert clerk.post("/tariff/statutory", json=table()).status_code == 403


def test_filing_a_table_lands_on_the_trail(client, factory):
    client.post("/tariff/statutory", json=table())
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.db, "tariff", "arterial 2025-06-01")
    assert [one.action for one in found] == ["tariff.filed"]
    assert found[0].detail == "gross 104,000 lb"


def test_the_history_of_one_class_comes_back_oldest_first(client):
    client.post("/tariff/statutory", json=table())
    found = client.get("/tariff/statutory/arterial").json()
    assert [one["effective"] for one in found] == ["2024-01-01", "2025-06-01"]


def test_a_new_table_changes_what_an_assessment_says(client):
    written = body()
    written["route"] = ["US201-1", "US201-2", "SA148-2", "SR43-1"]
    reference = client.post(f"/applications?filed={FILED}", json=written).json()["reference"]
    before = client.get(f"/applications/{reference}/assessment?on={FILED}").json()
    tightened = {
        **table(effective="2025-04-01"),
        "highway_class": "state aid",
        "gross": "70000",
    }
    client.post("/tariff/statutory", json=tightened)
    after = client.get(f"/applications/{reference}/assessment?on={FILED}").json()
    assert before["permittable"]
    assert before["fees"]["total"] != after["fees"]["total"]


def test_a_weight_that_cannot_be_read_is_refused(client):
    answer = client.post("/tariff/statutory", json={**table(), "gross": "heavy"})
    assert answer.status_code == 422
    assert answer.json()["code"] == "unreadable"


def test_a_class_of_road_that_does_not_exist_is_refused(client):
    answer = client.post("/tariff/statutory", json={**table(), "highway_class": "goat track"})
    assert answer.status_code == 422
    assert answer.json()["facts"]["written"] == "goat track"
