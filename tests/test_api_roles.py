"""Which rung each route needs, and what lands on the trail behind it."""

import pytest
from fastapi.testclient import TestClient

from jeepdolly.api.app import create_app
from jeepdolly.api.security import HEADER, Role
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit
from support import a_seeded_database, keys_for

FILED = "2025-04-07"
START = "2025-04-08"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def keys(factory):
    return keys_for(factory)


def client_for(factory, keys, role):
    client = TestClient(create_app(factory))
    if role is not None:
        client.headers[HEADER] = keys[role]
    return client


def body():
    return {
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


def issued_by(client):
    reference = client.post(f"/applications?filed={FILED}", json=body()).json()["reference"]
    client.post(f"/applications/{reference}/submit?on={FILED}")
    return client.post(f"/permits?reference={reference}&on={FILED}").json()["number"]


def test_reading_needs_a_key_at_all(factory, keys):
    nobody = client_for(factory, keys, None)
    assert nobody.get("/applications").status_code == 401
    assert nobody.get("/permits").status_code == 401


def test_the_health_route_is_open_because_a_load_balancer_has_no_key(factory, keys):
    assert client_for(factory, keys, None).get("/health").status_code == 200


def test_filing_an_application_needs_a_clerk(factory, keys):
    reader = client_for(factory, keys, Role.READER)
    clerk = client_for(factory, keys, Role.CLERK)
    assert reader.post(f"/applications?filed={FILED}", json=body()).status_code == 403
    assert clerk.post(f"/applications?filed={FILED}", json=body()).status_code == 201


def test_a_reader_may_still_read_what_a_clerk_filed(factory, keys):
    clerk = client_for(factory, keys, Role.CLERK)
    reference = clerk.post(f"/applications?filed={FILED}", json=body()).json()["reference"]
    reader = client_for(factory, keys, Role.READER)
    assert reader.get(f"/applications/{reference}").status_code == 200
    assert reader.get(f"/applications/{reference}/assessment").status_code == 200


def test_issuing_needs_a_clerk_and_voiding_needs_a_supervisor(factory, keys):
    clerk = client_for(factory, keys, Role.CLERK)
    number = issued_by(clerk)
    assert clerk.post(f"/permits/{number}/void?reason=no&on={START}").status_code == 403
    boss = client_for(factory, keys, Role.SUPERVISOR)
    assert boss.post(f"/permits/{number}/void?reason=cancelled&on={START}").status_code == 200


def test_an_engineer_may_issue_because_the_ladder_reaches_down(factory, keys):
    engineer = client_for(factory, keys, Role.ENGINEER)
    assert issued_by(engineer).startswith("25-P-")


def test_filing_lands_on_the_trail_with_the_name_of_who_did_it(factory, keys):
    clerk = client_for(factory, keys, Role.CLERK)
    reference = clerk.post(f"/applications?filed={FILED}", json=body()).json()["reference"]
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.session, "application", reference)
    assert [one.action for one in found] == ["application.filed"]
    assert found[0].actor == "Clerk"
    assert found[0].detail == "waterville to pittsfield"


def test_submitting_and_issuing_both_land_on_the_trail(factory, keys):
    clerk = client_for(factory, keys, Role.CLERK)
    number = issued_by(clerk)
    with UnitOfWork(factory) as work:
        permit_trail = audit.for_document(work.session, "permit", number)
        issued = audit.of_action(work.session, "application.submitted")
    assert [one.action for one in permit_trail] == ["permit.issued"]
    assert permit_trail[0].detail == "94,000 lb to Bickford Heavy Haul"
    assert len(issued) == 1


def test_voiding_lands_on_the_trail_with_the_reason(factory, keys):
    clerk = client_for(factory, keys, Role.CLERK)
    number = issued_by(clerk)
    boss = client_for(factory, keys, Role.SUPERVISOR)
    boss.post(f"/permits/{number}/void?reason=load cancelled&on={START}")
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.session, "permit", number)
    assert [one.action for one in found] == ["permit.issued", "permit.voided"]
    assert found[1].actor == "Supervisor"
    assert found[1].detail == "load cancelled"


def test_a_refused_issue_leaves_nothing_on_the_trail(factory, keys):
    clerk = client_for(factory, keys, Role.CLERK)
    written = body()
    written["commodity"] = "GRVL"
    written["pieces"] = [{"commodity": "GRVL", "description": "gravel"}]
    reference = clerk.post(f"/applications?filed={FILED}", json=written).json()["reference"]
    clerk.post(f"/applications/{reference}/submit?on={FILED}")
    clerk.post(f"/permits?reference={reference}&on={FILED}")
    with UnitOfWork(factory) as work:
        assert audit.of_action(work.session, "permit.issued") == ()


def test_reading_never_lands_on_the_trail(factory, keys):
    clerk = client_for(factory, keys, Role.CLERK)
    reference = clerk.post(f"/applications?filed={FILED}", json=body()).json()["reference"]
    clerk.get(f"/applications/{reference}")
    clerk.get(f"/applications/{reference}/assessment")
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.session, "application", reference)
    assert len(found) == 1
