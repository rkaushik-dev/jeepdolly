"""The application routes, over a real database."""

import pytest

from support import keyed_client

FILED = "2025-04-07"


@pytest.fixture
def client():
    return keyed_client()


def body(**kw):
    fields = {
        "usdot": 1204471,
        "commodity": "EXCV",
        "origin": "waterville",
        "destination": "pittsfield",
        "start": "2025-04-08",
        "end": "2025-04-10",
        "applicant": {"name": "Dana Bickford", "phone": "207 555 0143"},
        "envelope": {"width": "12'", "height": "13'6", "length": "82 ft", "rear_overhang": "6'"},
        "axles": [
            {"kind": "steer", "weight": "12000", "tires": 2, "tire_width": "16"},
            {"kind": "drive", "weight": "17000", "spacing_before": "200"},
            {"kind": "drive", "weight": "17000", "spacing_before": "54"},
            {"kind": "trailer", "weight": "16000", "spacing_before": "180"},
            {"kind": "trailer", "weight": "16000", "spacing_before": "52"},
            {"kind": "trailer", "weight": "16000", "spacing_before": "52"},
        ],
        "units": [
            {"kind": "tractor", "axles": 3, "plate": "ME 4471", "tare": "19000"},
            {"kind": "lowboy", "axles": 3, "plate": "ME 7712", "tare": "24000"},
        ],
        "pieces": [{"commodity": "EXCV", "description": "Cat 336 excavator"}],
    }
    fields.update(kw)
    return fields


def filed(client, **kw):
    return client.post(f"/applications?filed={FILED}", json=body(**kw))


def test_an_application_is_filed_and_given_a_reference(client):
    answer = filed(client)
    assert answer.status_code == 201
    found = answer.json()
    assert found["reference"] == "25-F-000001"
    assert found["state"] == "draft"
    assert found["gross"] == "94,000 lb"


def test_the_references_run_on_without_gaps(client):
    assert filed(client).json()["reference"] == "25-F-000001"
    assert filed(client).json()["reference"] == "25-F-000002"


def test_a_filed_application_can_be_read_back(client):
    reference = filed(client).json()["reference"]
    found = client.get(f"/applications/{reference}").json()
    assert found["origin"] == "waterville"
    assert found["width"] == "12 ft"
    assert found["height"] == "13 ft 6 in"


def test_an_application_nobody_filed_is_a_404_in_the_one_shape(client):
    answer = client.get("/applications/25-F-999999")
    assert answer.status_code == 404
    assert answer.json()["code"] == "not_on_file"


def test_a_body_the_office_cannot_read_is_refused_with_a_reason(client):
    written = body()
    written["envelope"]["height"] = "about fourteen feet"
    answer = client.post(f"/applications?filed={FILED}", json=written)
    assert answer.status_code == 422
    assert answer.json()["code"] == "unreadable"
    assert answer.json()["facts"]["written"] == "about fourteen feet"


def test_a_combination_that_cannot_exist_is_refused_with_a_reason(client):
    written = body()
    written["units"][1]["axles"] = 4
    answer = client.post(f"/applications?filed={FILED}", json=written)
    assert answer.status_code == 422
    assert answer.json()["code"] == "impossible"
    assert answer.json()["facts"] == {"declared": 7, "stream": 6}


def test_a_body_the_framework_cannot_parse_is_the_frameworks_own_answer(client):
    assert client.post("/applications", json={"usdot": 1}).status_code == 422


def test_submitting_moves_it_into_the_queue(client):
    reference = filed(client).json()["reference"]
    answer = client.post(f"/applications/{reference}/submit?on={FILED}")
    assert answer.status_code == 200
    assert answer.json()["state"] == "submitted"
    assert client.get(f"/applications/{reference}").json()["state"] == "submitted"


def test_submitting_twice_is_refused_and_says_why(client):
    reference = filed(client).json()["reference"]
    client.post(f"/applications/{reference}/submit?on={FILED}")
    answer = client.post(f"/applications/{reference}/submit?on={FILED}")
    assert answer.status_code == 409
    assert answer.json()["code"] == "not_allowed"


def test_the_queue_is_read_by_state(client):
    first = filed(client).json()["reference"]
    filed(client)
    client.post(f"/applications/{first}/submit?on={FILED}")
    submitted = client.get("/applications?state=submitted").json()
    drafts = client.get("/applications?state=draft").json()
    assert [one["reference"] for one in submitted] == [first]
    assert len(drafts) == 1


def test_a_state_that_is_not_a_state_is_refused_rather_than_crashing(client):
    answer = client.get("/applications?state=nonsense")
    assert answer.status_code == 422
    assert answer.json()["code"] == "unreadable"


def test_withdrawing_closes_the_file(client):
    reference = filed(client).json()["reference"]
    answer = client.post(f"/applications/{reference}/withdraw?on={FILED}")
    assert answer.json()["state"] == "withdrawn"


def test_the_assessment_prices_the_trip_without_changing_anything(client):
    reference = filed(client).json()["reference"]
    found = client.get(f"/applications/{reference}/assessment").json()
    assert found["permittable"]
    assert found["kind"] == "ordinary"
    assert found["route"]
    assert found["fees"]["total"].startswith("$")
    assert client.get(f"/applications/{reference}").json()["state"] == "draft"


def test_an_assessment_that_refuses_says_every_reason(client):
    gravel = [{"commodity": "GRVL", "description": "gravel"}]
    reference = filed(client, commodity="GRVL", pieces=gravel).json()["reference"]
    found = client.get(f"/applications/{reference}/assessment").json()
    assert not found["permittable"]
    assert found["refusals"]
    assert found["fees"] is None


def test_the_conditions_come_back_with_the_assessment(client):
    reference = filed(client).json()["reference"]
    found = client.get(f"/applications/{reference}/assessment").json()
    codes = [one["code"] for one in found["conditions"]]
    assert "T-01" in codes
    assert "G-01" in codes
