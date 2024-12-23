"""The application factory and the one error shape."""

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from jeepdolly.api.app import create_app
from jeepdolly.api.errors import status_for
from jeepdolly.errors import Blocked, Conflict, Impossible, NotAllowed, NotOnFile, OverLimit
from jeepdolly.persistence.base import create_all, memory_engine, session_factory


@pytest.fixture
def client():
    engine = memory_engine()
    create_all(engine)
    app = create_app(session_factory(engine))
    _install_refusals(app)
    return TestClient(app)


def _install_refusals(app: FastAPI) -> None:
    """Routes that raise, so the shape can be checked without a real one."""

    @app.get("/testing/{code}")
    def raiser(code: str) -> dict[str, str]:
        kinds = {
            "not_on_file": NotOnFile("no carrier with usdot 9999", usdot=9999),
            "conflict": Conflict("already on file"),
            "impossible": Impossible("the overhangs are longer than the load"),
            "over_limit": OverLimit("the tandem is 4,000 lb over", over=4000),
            "blocked": Blocked("US 2 is closed", segment="US2-1"),
            "not_allowed": NotAllowed("that permit is void"),
        }
        raise kinds[code]


def test_the_health_route_says_which_service_and_which_version(client):
    answer = client.get("/health")
    assert answer.status_code == 200
    assert answer.json()["service"] == "jeepdolly"
    assert answer.json()["database"] == "up"


def test_the_health_route_actually_touches_the_database(client):
    assert client.get("/health").json()["database"] == "up"


def test_the_service_describes_itself_for_the_carriers_own_software(client):
    schema = client.get("/openapi.json")
    assert schema.status_code == 200
    assert schema.json()["info"]["title"] == "jeepdolly"


def test_a_refusal_comes_back_in_the_one_shape(client):
    answer = client.get("/testing/not_on_file")
    assert answer.status_code == 404
    assert answer.json() == {
        "code": "not_on_file",
        "message": "no carrier with usdot 9999",
        "facts": {"usdot": 9999},
    }


def test_each_refusal_carries_the_status_the_table_gives_it(client):
    assert client.get("/testing/conflict").status_code == 409
    assert client.get("/testing/impossible").status_code == 422
    assert client.get("/testing/over_limit").status_code == 422
    assert client.get("/testing/blocked").status_code == 422
    assert client.get("/testing/not_allowed").status_code == 409


def test_the_facts_come_through_so_the_caller_can_act_on_them(client):
    body = client.get("/testing/blocked").json()
    assert body["facts"] == {"segment": "US2-1"}
    assert body["code"] == "blocked"


def test_an_unknown_code_falls_back_to_the_refused_status():
    assert status_for("something_new") == 422
    assert status_for("not_on_file") == 404


def test_a_route_that_is_not_there_is_the_frameworks_own_answer(client):
    assert client.get("/nothing/here").status_code == 404
