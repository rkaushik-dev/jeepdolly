"""The routes about the service, which need no key at all."""

import pytest
from fastapi.testclient import TestClient

from jeepdolly import __version__
from jeepdolly.api.app import ROUTERS, create_app
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_permit, a_seeded_database, an_application

START = "2025-04-08"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def client(factory):
    return TestClient(create_app(factory))


def issued(factory):
    application = an_application()
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
        work.permits.add(a_permit(application))
        work.commit()


def test_health_touches_the_database_and_says_which_service(client):
    found = client.get("/health").json()
    assert found["service"] == "jeepdolly"
    assert found["database"] == "up"
    assert found["version"] == __version__


def test_version_is_answered_without_touching_anything(client):
    answer = client.get("/version")
    assert answer.status_code == 200
    assert answer.json() == {"service": "jeepdolly", "version": __version__}


def test_the_scrape_is_plain_text_with_a_help_line(client):
    answer = client.get(f"/metrics?day={START}")
    assert answer.status_code == 200
    assert answer.headers["content-type"].startswith("text/plain")
    assert "# HELP jeepdolly_segments" in answer.text
    assert "jeepdolly_segments 12" in answer.text


def test_the_scrape_moves_when_the_office_does(client, factory):
    before = client.get(f"/metrics.json?day={START}").json()
    issued(factory)
    after = client.get(f"/metrics.json?day={START}").json()
    assert before["permits_on_the_road"] == 0
    assert after["permits_on_the_road"] == 1


def test_the_json_and_the_text_carry_the_same_numbers(client):
    found = client.get(f"/metrics.json?day={START}").json()
    written = client.get(f"/metrics?day={START}").text
    for name, value in found.items():
        assert f"jeepdolly_{name} {value}" in written


def test_none_of_the_service_routes_needs_a_key(client):
    for path in ("/health", "/version", "/metrics", "/metrics.json"):
        assert client.get(path).status_code == 200


def test_every_router_is_registered_once(client):
    assert len(ROUTERS) == 12
    paths = {one.path for one in client.app.routes}
    assert "/health" in paths
    assert "/permits/walk-in" in paths
    assert "/applications/{reference}/assessment" in paths


def test_the_service_still_describes_itself(client):
    schema = client.get("/openapi.json").json()
    assert schema["info"]["version"] == __version__
    assert "/metrics" in schema["paths"]
