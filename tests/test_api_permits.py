"""The permit routes, over a real database."""

import pytest

from support import keyed_client

FILED = "2025-04-07"
START = "2025-04-08"


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


def submitted(client, **kw):
    reference = client.post(f"/applications?filed={FILED}", json=body(**kw)).json()["reference"]
    client.post(f"/applications/{reference}/submit?on={FILED}")
    return reference


def issued(client, **kw):
    reference = submitted(client, **kw)
    return client.post(f"/permits?reference={reference}&on={FILED}")


def test_a_permit_is_issued_against_an_application(client):
    answer = issued(client)
    assert answer.status_code == 201
    found = answer.json()
    assert found["number"] == "25-P-000001"
    assert found["carrier"] == "Bickford Heavy Haul"
    assert found["state"] == "issued"
    assert found["route"]
    assert found["fees"]["total"].startswith("$")


def test_the_permit_carries_the_conditions_that_were_worked_out(client):
    codes = [one["code"] for one in issued(client).json()["conditions"]]
    assert "T-01" in codes
    assert "R-01" in codes
    assert "G-02" in codes


def test_the_application_is_marked_issued_when_the_permit_is_written(client):
    reference = submitted(client)
    client.post(f"/permits?reference={reference}&on={FILED}")
    assert client.get(f"/applications/{reference}").json()["state"] == "issued"


def test_the_numbers_run_on_without_gaps_across_requests(client):
    assert issued(client).json()["number"] == "25-P-000001"
    assert issued(client).json()["number"] == "25-P-000002"


def test_an_application_that_cannot_be_permitted_is_refused_with_its_reasons(client):
    gravel = [{"commodity": "GRVL", "description": "gravel"}]
    reference = submitted(client, commodity="GRVL", pieces=gravel)
    answer = client.post(f"/permits?reference={reference}&on={FILED}")
    assert answer.status_code == 409
    assert answer.json()["code"] == "not_allowed"
    assert answer.json()["facts"]["reasons"]


def test_a_refused_issue_takes_no_number_with_it(client):
    gravel = [{"commodity": "GRVL", "description": "gravel"}]
    reference = submitted(client, commodity="GRVL", pieces=gravel)
    client.post(f"/permits?reference={reference}&on={FILED}")
    assert issued(client).json()["number"] == "25-P-000001"


def test_a_permit_can_be_read_back_by_its_number(client):
    number = issued(client).json()["number"]
    found = client.get(f"/permits/{number}").json()
    assert found["gross"] == "94,000 lb"
    assert found["narrative"].startswith("from Waterville")


def test_a_number_nobody_issued_is_a_404_in_the_one_shape(client):
    answer = client.get("/permits/25-P-999999")
    assert answer.status_code == 404
    assert answer.json()["code"] == "not_on_file"


def test_what_is_on_the_road_is_asked_by_day(client):
    issued(client)
    assert len(client.get(f"/permits?day={START}").json()) == 1
    assert client.get("/permits?day=2025-05-01").json() == []


def test_voiding_takes_it_off_the_road_and_keeps_the_reason(client):
    number = issued(client).json()["number"]
    answer = client.post(f"/permits/{number}/void?reason=load cancelled&on={START}")
    assert answer.status_code == 200
    assert answer.json()["state"] == "void"
    assert client.get(f"/permits?day={START}").json() == []


def test_voiding_twice_is_refused(client):
    number = issued(client).json()["number"]
    client.post(f"/permits/{number}/void?reason=load cancelled&on={START}")
    answer = client.post(f"/permits/{number}/void?reason=again&on={START}")
    assert answer.status_code == 409


def test_voiding_with_no_reason_is_refused_by_the_framework(client):
    number = issued(client).json()["number"]
    assert client.post(f"/permits/{number}/void?reason=x&on={START}").status_code == 422


def test_the_expiring_list_is_what_the_office_rings_about(client):
    issued(client)
    soon = client.get(f"/permits/expiring/soon?day={START}&within=3").json()
    assert len(soon) == 1
    assert client.get("/permits/expiring/soon?day=2025-05-01&within=3").json() == []


def test_a_carrier_can_be_asked_what_it_has_out(client):
    issued(client)
    issued(client)
    assert len(client.get("/permits/carrier/1204471").json()) == 2
    assert client.get("/permits/carrier/2299001").json() == []
