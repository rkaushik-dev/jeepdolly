"""The structure routes and the study queue over http."""

from datetime import date

import pytest
from fastapi.testclient import TestClient

from jeepdolly.api.app import create_app
from jeepdolly.api.security import HEADER, Role
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import studies
from support import a_seeded_database, keys_for
from test_api_roles import body

FILED = "2025-04-07"
START = "2025-04-08"
MONDAY = "2025-07-07"


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
    written = body()
    written["route"] = ["US201-1", "US201-2", "SA148-2", "SR43-1"]
    reference = client.post(f"/applications?filed={FILED}", json=written).json()["reference"]
    client.post(f"/applications/{reference}/submit?on={FILED}")
    return client.post(f"/permits?reference={reference}&on={FILED}").json()["number"]


def queued(factory, reference="25-F-000009"):
    with UnitOfWork(factory) as work:
        studies.open_study(work.db, reference, ("1247",), date(2025, 7, 7))
        work.commit()
    return reference


def test_the_structures_of_a_segment_come_back_with_their_ratings(client):
    found = client.get("/structures/on/US201-2").json()
    assert [one["ident"] for one in found] == ["1247"]
    assert found[0]["name"] == "Kennebec River"
    assert found[0]["longest_span"] == 120
    assert found[0]["operating_rating"] == "132,000 lb"
    assert found[0]["posted"] is None


def test_the_posted_list_is_the_one_the_router_reads(client):
    found = client.get("/structures/posted").json()
    assert [one["ident"] for one in found] == ["1533"]
    assert found[0]["posted"] == "48,000 lb"


def test_posting_needs_a_supervisor(factory, keys):
    clerk = client_for(factory, keys, Role.CLERK)
    assert clerk.post("/structures/1247/posting?weight=60000").status_code == 403


def test_posting_a_structure_names_the_permits_already_crossing_it(client):
    number = issue_one(client)
    answer = client.post(f"/structures/1247/posting?weight=60,000 lb&on={START}")
    assert answer.status_code == 200
    found = answer.json()
    assert found["structure"]["posted"] == "60,000 lb"
    assert found["permits_crossing"] == [number]


def test_a_posting_does_not_void_what_is_already_on_the_road(client):
    number = issue_one(client)
    client.post(f"/structures/1247/posting?weight=60000&on={START}")
    assert client.get(f"/permits/{number}").json()["state"] == "issued"


def test_a_structure_nobody_has_is_a_404(client):
    assert client.post("/structures/9999/posting?weight=60000").status_code == 404


def test_the_study_queue_comes_back_oldest_first(client, factory):
    queued(factory, "25-F-000002")
    queued(factory, "25-F-000001")
    found = client.get("/structures/studies/waiting").json()
    assert [one["reference"] for one in found] == ["25-F-000001", "25-F-000002"]
    assert found[0]["state"] == "waiting"


def test_an_engineer_picks_a_study_up_and_the_queue_loses_it(factory, keys):
    reference = queued(factory)
    engineer = client_for(factory, keys, Role.ENGINEER)
    answer = engineer.post(
        f"/structures/studies/{reference}/assign?engineer=R Pelletier&on={MONDAY}"
    )
    assert answer.status_code == 200
    assert answer.json()["engineer"] == "R Pelletier"
    assert engineer.get("/structures/studies/waiting").json() == []


def test_clearing_a_study_keeps_the_conditions_the_engineer_wrote(factory, keys):
    reference = queued(factory)
    engineer = client_for(factory, keys, Role.ENGINEER)
    engineer.post(f"/structures/studies/{reference}/assign?engineer=R Pelletier&on={MONDAY}")
    answer = engineer.post(
        f"/structures/studies/{reference}/clear?condition=cross alone&condition=walking pace"
    )
    assert answer.json()["state"] == "cleared"
    assert answer.json()["conditions"] == ["cross alone", "walking pace"]


def test_refusing_a_study_records_the_reason(factory, keys):
    reference = queued(factory)
    engineer = client_for(factory, keys, Role.ENGINEER)
    engineer.post(f"/structures/studies/{reference}/assign?engineer=R Pelletier&on={MONDAY}")
    answer = engineer.post(f"/structures/studies/{reference}/refuse?reason=the truss will not")
    assert answer.json()["state"] == "refused"


def test_a_clerk_may_not_sign_a_study(factory, keys):
    reference = queued(factory)
    clerk = client_for(factory, keys, Role.CLERK)
    assert clerk.post(f"/structures/studies/{reference}/clear").status_code == 403


def test_a_study_that_was_never_opened_is_a_404(factory, keys):
    engineer = client_for(factory, keys, Role.ENGINEER)
    answer = engineer.post("/structures/studies/25-F-999999/assign?engineer=R Pelletier")
    assert answer.status_code == 404
