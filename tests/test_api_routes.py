"""The route routes, which answer questions and change nothing."""

from datetime import date

import pytest
from fastapi.testclient import TestClient

from jeepdolly.api.app import create_app
from jeepdolly.api.security import HEADER, Role
from jeepdolly.domain.restriction import RestrictionKind
from jeepdolly.measure import Inches
from jeepdolly.permits.application import ApplicationState
from jeepdolly.persistence.models import RestrictionRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_seeded_database, keyed_client, keys_for

DAY = "2025-04-08"
ACROSS = ["US201-1", "US201-2", "SA148-2", "SR43-1"]


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def client(factory):
    keys = keys_for(factory)
    made = TestClient(create_app(factory))
    made.headers[HEADER] = keys[Role.READER]
    return made


def load(**kw):
    fields = {"width": "12'", "height": "13'6", "length": "82'", "gross": "94000"}
    fields.update(kw)
    return fields


def close(factory, segment):
    with UnitOfWork(factory) as work:
        work.db.add(
            RestrictionRow(
                ident=f"R-{segment}",
                segment_ident=segment,
                kind=RestrictionKind.CLOSURE,
                note="bridge out",
            )
        )
        work.commit()


def query(segments=None, origin="waterville"):
    parts = [f"segments={one}" for one in (segments or ACROSS)]
    return "&".join([*parts, f"origin={origin}", f"day={DAY}"])


def test_a_written_route_is_walked_and_comes_back_clear(client):
    answer = client.post(f"/routes/check?{query()}", json=load())
    assert answer.status_code == 200
    found = answer.json()
    assert found["clear"]
    assert found["distance"] == "35.20 mi"
    assert found["chargeable_miles"] == 36
    assert found["towns"][0] == "Waterville"
    assert found["obstructions"] == []


def test_the_narrative_is_what_would_print_on_the_permit(client):
    found = client.post(f"/routes/check?{query()}", json=load()).json()
    assert found["narrative"].startswith("from Waterville, US 201 to Fairfield")


def test_a_closure_on_the_written_route_comes_back_as_an_obstruction(client, factory):
    close(factory, "SA148-2")
    found = client.post(f"/routes/check?{query()}", json=load()).json()
    assert not found["clear"]
    trouble = found["obstructions"][0]
    assert trouble["segment"] == "SA148-2"
    assert trouble["kind"] == "closure"
    assert trouble["fatal"]


def test_a_route_that_does_not_join_is_refused(client):
    answer = client.post(f"/routes/check?{query(segments=['US201-1', 'SR43-1'])}", json=load())
    assert answer.status_code == 422
    assert answer.json()["code"] == "impossible"


def test_the_router_finds_the_cheapest_route_for_a_load(client):
    answer = client.post(
        f"/routes/find?origin=waterville&destination=pittsfield&day={DAY}", json=load()
    )
    assert answer.status_code == 200
    assert answer.json()["segments"] == ["I95-1", "I95-2"]
    assert answer.json()["clear"]


def test_a_closure_pushes_the_found_route_onto_the_state_roads(client, factory):
    close(factory, "I95-2")
    found = client.post(
        f"/routes/find?origin=waterville&destination=pittsfield&day={DAY}", json=load()
    ).json()
    assert "I95-2" not in found["segments"]
    assert found["clear"]


def test_a_load_with_nowhere_to_go_is_a_404_that_names_both_ends(client, factory):
    close(factory, "I95-2")
    close(factory, "SR43-1")
    answer = client.post(
        f"/routes/find?origin=waterville&destination=pittsfield&day={DAY}", json=load()
    )
    assert answer.status_code == 404
    assert answer.json()["facts"] == {"origin": "waterville", "destination": "pittsfield"}


def test_everywhere_a_load_can_get_to_comes_back_as_a_list(client):
    answer = client.post(f"/routes/reachable?origin=skowhegan&day={DAY}", json=load())
    assert answer.status_code == 200
    places = answer.json()
    assert "newport" in places
    assert "anson" in places


def test_a_closure_cuts_off_what_is_behind_it(client, factory):
    close(factory, "US2-2")
    places = client.post(f"/routes/reachable?origin=newport&day={DAY}", json=load()).json()
    assert places == ["newport"]


def test_a_clearance_is_only_in_the_way_of_a_load_that_is_too_tall(client, factory):
    with UnitOfWork(factory) as work:
        work.db.add(
            RestrictionRow(
                ident="R-1",
                segment_ident="US201-2",
                kind=RestrictionKind.CLEARANCE,
                note="stone arch",
                height=Inches.of(13),
            )
        )
        work.commit()
    tall = client.post(f"/routes/check?{query()}", json=load()).json()
    short = client.post(f"/routes/check?{query()}", json=load(height="12'")).json()
    assert not tall["clear"]
    assert short["clear"]
    assert tall["obstructions"][0]["needed"] == "13 ft 6 in"


def test_asking_about_a_route_writes_nothing_down(client, factory):
    client.post(f"/routes/check?{query()}", json=load())
    client.post(f"/routes/find?origin=waterville&destination=pittsfield&day={DAY}", json=load())
    with UnitOfWork(factory) as work:
        assert work.permits.on_the_road(date(2025, 4, 8)) == ()
        assert work.applications.in_state(ApplicationState.DRAFT) == ()


def test_a_request_with_no_key_is_refused(factory):
    nobody = keyed_client(Role.READER, factory)
    nobody.headers.pop(HEADER)
    assert nobody.post(f"/routes/check?{query()}", json=load()).status_code == 401


def test_the_default_load_is_a_legal_one_that_fits_everywhere(client):
    found = client.post(f"/routes/check?{query()}", json={}).json()
    assert found["clear"]
    assert found["distance"] == "35.20 mi"
