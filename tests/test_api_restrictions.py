"""Closing a road and opening it again."""

import pytest

from jeepdolly.api.security import Role
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit
from support import a_seeded_database, keyed_client
from test_api_roles import body

FILED = "2025-04-07"
START = "2025-04-08"
ACROSS = ["US201-1", "US201-2", "SA148-2", "SR43-1"]


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def client(factory):
    return keyed_client(factory=factory)


def closure(ident="R-1", segment="SA148-2", **kw):
    fields = {"ident": ident, "segment": segment, "kind": "closure", "note": "culvert work"}
    fields.update(kw)
    return fields


def issue_across(client):
    written = body()
    written["route"] = ACROSS
    reference = client.post(f"/applications?filed={FILED}", json=written).json()["reference"]
    client.post(f"/applications/{reference}/submit?on={FILED}")
    return client.post(f"/permits?reference={reference}&on={FILED}").json()["number"]


def test_a_road_is_closed_in_one_request(client):
    answer = client.post(f"/restrictions?on={START}", json=closure())
    assert answer.status_code == 201
    assert answer.json()["segment"] == "SA148-2"
    assert answer.json()["permits_crossing"] == []


def test_the_answer_names_the_permits_already_crossing_it(client):
    number = issue_across(client)
    answer = client.post(f"/restrictions?on={START}", json=closure())
    assert answer.json()["permits_crossing"] == [number]


def test_a_closure_shows_up_on_the_segment_it_was_put_on(client):
    client.post(f"/restrictions?on={START}", json=closure())
    found = client.get(f"/restrictions/on/SA148-2?day={START}").json()
    assert [one["ident"] for one in found] == ["R-1"]
    assert client.get(f"/restrictions/on/US2-1?day={START}").json() == []


def test_a_closure_changes_what_the_router_will_do(client):
    load = {"width": "12'", "height": "13'6", "length": "82'", "gross": "94000"}
    before = client.post(
        f"/routes/find?origin=waterville&destination=pittsfield&day={START}", json=load
    ).json()
    client.post(f"/restrictions?on={START}", json=closure(segment="I95-2"))
    after = client.post(
        f"/routes/find?origin=waterville&destination=pittsfield&day={START}", json=load
    ).json()
    assert before["segments"] == ["I95-1", "I95-2"]
    assert "I95-2" not in after["segments"]


def test_the_same_ident_twice_is_a_conflict(client):
    client.post(f"/restrictions?on={START}", json=closure())
    assert client.post(f"/restrictions?on={START}", json=closure()).status_code == 409


def test_a_segment_that_is_not_on_the_log_is_a_404(client):
    answer = client.post(f"/restrictions?on={START}", json=closure(segment="NOPE-1"))
    assert answer.status_code == 404


def test_closing_a_road_needs_a_supervisor(factory):
    clerk = keyed_client(Role.CLERK, factory)
    assert clerk.post(f"/restrictions?on={START}", json=closure()).status_code == 403


def test_a_clearance_carries_its_height(client):
    written = closure(ident="R-2", segment="US2-1", kind="clearance", note="arch")
    written["height"] = "13'6"
    assert client.post(f"/restrictions?on={START}", json=written).status_code == 201
    found = client.get(f"/restrictions/on/US2-1?day={START}").json()
    assert found[0]["kind"] == "clearance"


def test_opening_the_road_again_takes_the_row_off(client):
    client.post(f"/restrictions?on={START}", json=closure())
    answer = client.delete(f"/restrictions/R-1?on={START}")
    assert answer.status_code == 200
    assert answer.json()["ident"] == "R-1"
    assert client.get(f"/restrictions/on/SA148-2?day={START}").json() == []


def test_opening_something_that_is_not_closed_is_a_404(client):
    assert client.delete("/restrictions/R-9").status_code == 404


def test_both_ends_of_the_closure_land_on_the_trail(client, factory):
    client.post(f"/restrictions?on={START}", json=closure())
    client.delete(f"/restrictions/R-1?on={START}")
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.db, "segment", "SA148-2")
    assert [one.action for one in found] == ["road.closed", "road.opened"]
    assert "culvert work" in found[0].detail
