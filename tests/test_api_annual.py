"""The annual routes, including the one that answers before anybody pays."""

import pytest

from support import keyed_client
from test_api_roles import body

FILED = "2025-11-03"


@pytest.fixture
def client():
    return keyed_client()


def question(width="11'", gross="110000", commodity="CRNE"):
    return {
        "envelope": {"width": width, "height": "13'6", "length": "90'", "rear_overhang": "6'"},
        "gross": gross,
        "commodity": commodity,
    }


def filed(client, **kw):
    written = body()
    written["start"] = "2025-11-04"
    written["end"] = "2025-11-06"
    written.update(kw)
    reference = client.post(f"/applications?filed={FILED}", json=written).json()["reference"]
    client.post(f"/applications/{reference}/submit?on={FILED}")
    return reference


def test_a_combination_inside_the_envelope_is_eligible(client):
    answer = client.post("/annual/eligibility", json=question())
    assert answer.status_code == 200
    found = answer.json()
    assert found["allowed"]
    assert found["reasons"] == []


def test_the_answer_carries_the_envelope_it_was_read_against(client):
    found = client.post("/annual/eligibility", json=question()).json()
    assert found["envelope_width"] == "12 ft"
    assert found["envelope_height"] == "14 ft"
    assert found["gross_cap"] == "120,000 lb"


def test_a_load_past_the_envelope_gets_the_reasons_back(client):
    found = client.post("/annual/eligibility", json=question(width="14'")).json()
    assert not found["allowed"]
    assert found["reasons"] == ["width is past the annual envelope"]


def test_a_load_over_the_cap_gets_that_reason_too(client):
    found = client.post("/annual/eligibility", json=question(gross="130000")).json()
    assert "over the annual cap" in found["reasons"][0]


def test_a_commodity_nobody_filed_is_a_404(client):
    answer = client.post("/annual/eligibility", json=question(commodity="ZZZZ"))
    assert answer.status_code == 404


def test_asking_the_question_writes_nothing_down(client):
    client.post("/annual/eligibility", json=question())
    assert client.get("/permits?day=2025-11-03").json() == []


def test_an_annual_is_issued_against_an_application_on_file(client):
    reference = filed(client)
    answer = client.post(f"/annual?reference={reference}&on={FILED}")
    assert answer.status_code == 201
    found = answer.json()
    assert found["number"].startswith("25-A-")
    assert found["route"] == ["all state maintained highways"]
    assert found["fees"]["total"] == "$500.00"


def test_the_annual_runs_a_year_and_says_so_on_the_face(client):
    reference = filed(client)
    found = client.post(f"/annual?reference={reference}&on={FILED}").json()
    assert found["start"] == "2025-11-03"
    assert found["end"] == "2026-11-02"
    assert found["conditions"][0]["code"] == "T-01"


def test_a_combination_too_big_for_an_annual_is_refused(client):
    written = body()
    written["envelope"]["width"] = "16'"
    reference = filed(client, envelope=written["envelope"])
    answer = client.post(f"/annual?reference={reference}&on={FILED}")
    assert answer.status_code == 409
    assert answer.json()["facts"]["reasons"]


def test_a_carrier_that_is_not_in_good_standing_is_refused(client):
    reference = filed(client, usdot=2299001)
    answer = client.post(f"/annual?reference={reference}&on={FILED}")
    assert answer.status_code == 409
    assert answer.json()["facts"]["usdot"] == 2299001


def test_the_application_is_marked_issued_like_any_other(client):
    reference = filed(client)
    client.post(f"/annual?reference={reference}&on={FILED}")
    assert client.get(f"/applications/{reference}").json()["state"] == "issued"
