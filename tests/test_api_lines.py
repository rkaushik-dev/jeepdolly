"""Overhead lines over the interface, and the clearance question."""

import pytest

from jeepdolly.api.security import Role
from support import a_seeded_database, keyed_client

TRAVEL = "2025-04-09"
EARLY = "2025-03-01"
LATE = "2025-04-07"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def client(factory):
    return keyed_client(factory=factory)


def an_owner(ident="CMP", notice=5):
    return {
        "ident": ident,
        "name": "Central Maine Power",
        "telephone": "207 555 0100",
        "notice_days": notice,
    }


def a_wire(ident="L-1", segment="US201-2", height="19'", lift_to="26'", owner="CMP"):
    return {
        "ident": ident,
        "segment": segment,
        "place": "Fairfield village",
        "height": height,
        "owner": owner,
        "lift_to": lift_to,
        "measured": "2024-09-12",
    }


def seeded(client, wires=(a_wire(),), owners=(an_owner(),)):
    for one in owners:
        client.post("/lines/owners", json=one)
    for one in wires:
        client.post("/lines", json=one)
    return client


def clearance(client, height, filed=EARLY, segments=("US201-1", "US201-2")):
    query = "&".join(f"segments={one}" for one in segments)
    return client.get(
        f"/lines/clearance?origin=waterville&{query}&height={height}&travel={TRAVEL}&filed={filed}"
    )


def test_an_owner_is_filed_and_read_back_with_no_wires(client):
    answer = client.post("/lines/owners", json=an_owner())
    assert answer.status_code == 201
    assert answer.json()["wires"] == 0


def test_the_same_owner_is_not_filed_twice(client):
    client.post("/lines/owners", json=an_owner())
    assert client.post("/lines/owners", json=an_owner()).status_code == 409


def test_an_owner_wanting_absurd_notice_is_refused_by_the_schema(client):
    assert client.post("/lines/owners", json=an_owner(notice=400)).status_code == 422


def test_a_wire_is_filed_against_an_owner_on_file(client):
    client.post("/lines/owners", json=an_owner())
    answer = client.post("/lines", json=a_wire())
    assert answer.status_code == 201
    assert answer.json()["height"] == "19 ft"
    assert answer.json()["lift_to"] == "26 ft"


def test_a_wire_against_an_owner_nobody_has_is_a_404(client):
    assert client.post("/lines", json=a_wire()).status_code == 404


def test_a_wire_over_a_road_nobody_has_is_a_404_rather_than_a_five_hundred(client):
    client.post("/lines/owners", json=an_owner())
    answer = client.post("/lines", json=a_wire(segment="XX-9"))
    assert answer.status_code == 404
    assert "XX-9" in answer.json()["message"]


def test_the_same_wire_is_not_filed_twice(client):
    seeded(client)
    assert client.post("/lines", json=a_wire()).status_code == 409


def test_a_wire_that_lifts_to_below_where_it_hangs_is_refused(client):
    client.post("/lines/owners", json=an_owner())
    assert client.post("/lines", json=a_wire(lift_to="16'")).status_code == 409


def test_the_owner_shows_how_many_wires_it_has(client):
    seeded(client)
    assert client.get("/lines/owners").json()[0]["wires"] == 1


def test_the_wires_of_one_segment_can_be_asked_for(client):
    seeded(client, wires=(a_wire(), a_wire(ident="L-2", segment="US2-1")))
    assert len(client.get("/lines?segment=US201-2").json()) == 1
    assert len(client.get("/lines").json()) == 2


def test_one_wire_can_be_read_back(client):
    seeded(client)
    assert client.get("/lines/L-1").json()["place"] == "Fairfield village"


def test_a_wire_nobody_has_is_a_404(client):
    assert client.get("/lines/L-9").status_code == 404


def test_the_survey_list_is_everything_under_a_height_lowest_first(client):
    seeded(
        client,
        wires=(a_wire(), a_wire(ident="L-2", segment="US2-1", height="17'")),
    )
    found = client.get("/lines/low?under=20'").json()
    assert [one["ident"] for one in found] == ["L-2", "L-1"]
    assert client.get("/lines/low?under=16'").json() == []


def test_remeasuring_replaces_what_the_card_said(client):
    seeded(client)
    answer = client.put(
        "/lines/L-1/measurement",
        json={"height": "21'", "lift_to": "27'", "measured": "2025-10-02"},
    )
    assert answer.status_code == 200
    assert answer.json()["height"] == "21 ft"
    assert answer.json()["measured"] == "2025-10-02"


def test_remeasuring_can_take_the_lift_away_when_the_span_is_rebuilt(client):
    seeded(client)
    answer = client.put("/lines/L-1/measurement", json={"height": "28'", "measured": "2025-10-02"})
    assert answer.json()["lift_to"] is None


def test_remeasuring_to_a_lift_below_the_wire_is_refused(client):
    seeded(client)
    answer = client.put(
        "/lines/L-1/measurement",
        json={"height": "21'", "lift_to": "20'", "measured": "2025-10-02"},
    )
    assert answer.status_code == 409


def test_a_wire_nobody_has_cannot_be_remeasured(client):
    assert (
        client.put(
            "/lines/L-9/measurement", json={"height": "21'", "measured": "2025-10-02"}
        ).status_code
        == 404
    )


def test_a_short_load_clears_everything_and_rings_nobody(client):
    seeded(client)
    found = clearance(client, "13'6").json()
    assert found["clear"]
    assert found["lifts"] == []
    assert found["wanted"] == "13 ft 6 in"


def test_a_tall_load_gets_a_lift_with_a_telephone_number(client):
    seeded(client)
    found = clearance(client, "20'").json()
    assert found["clear"]
    assert found["lifts"][0]["telephone"] == "207 555 0100"
    assert found["lifts"][0]["notice_by"] == "2025-04-02"


def test_a_load_asking_too_late_is_not_clear(client):
    seeded(client)
    found = clearance(client, "20'", filed=LATE).json()
    assert not found["clear"]
    assert "working days notice" in found["blocked"][0]["reason"]


def test_a_load_nothing_can_be_done_about_says_both_heights(client):
    seeded(client, wires=(a_wire(lift_to=None),))
    found = clearance(client, "20'").json()
    assert "hangs at 19 ft" in found["blocked"][0]["reason"]


def test_a_route_that_misses_the_wire_is_clear(client):
    seeded(client)
    found = clearance(client, "20'", segments=("I95-1", "I95-2")).json()
    assert found["clear"]
    assert found["lifts"] == []


def test_a_route_that_cannot_be_driven_is_refused(client):
    seeded(client)
    assert clearance(client, "20'", segments=("SR8-1",)).status_code == 422


def test_a_clerk_cannot_file_a_wire(factory):
    clerk = keyed_client(role=Role.CLERK, factory=factory)
    assert clerk.post("/lines/owners", json=an_owner()).status_code == 403


def test_a_reader_can_ask_the_clearance_question(factory):
    supervisor = keyed_client(factory=factory)
    seeded(supervisor)
    reader = keyed_client(role=Role.READER, factory=factory)
    assert clearance(reader, "20'").status_code == 200
