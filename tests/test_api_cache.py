"""The cache as the application uses it, which is the part that can go wrong."""

import pytest

from jeepdolly.api.app import create_app
from jeepdolly.api.deps import office_from
from jeepdolly.persistence.reference import ReferenceCache
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_seeded_database, keyed_client

APRIL = "2025-04-08"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def client(factory):
    return keyed_client(factory=factory)


def cache_of(client):
    return client.app.state.reference


def a_wire(ident="L-1"):
    return {
        "ident": ident,
        "segment": "US201-2",
        "place": "Fairfield village",
        "height": "19'",
        "owner": "CMP",
        "lift_to": "26'",
    }


def an_owner():
    return {"ident": "CMP", "name": "Central Maine Power", "telephone": "207 555 0100"}


def test_the_application_builds_a_cache_of_its_own(client):
    assert isinstance(cache_of(client), ReferenceCache)
    assert len(cache_of(client)) == 0


def test_two_applications_over_one_database_do_not_share_books(factory):
    first = keyed_client(factory=factory)
    second = keyed_client(factory=factory)
    assert cache_of(first) is not cache_of(second)


def find(client):
    query = "origin=waterville&destination=pittsfield&day=2025-04-08"
    return client.post(f"/routes/find?{query}", json={})


def test_the_first_request_fills_it_and_the_second_hits_it(client):
    assert find(client).status_code == 200
    find(client)
    assert cache_of(client).misses == 1
    assert cache_of(client).hits == 1


def test_a_route_that_writes_a_published_row_empties_it(client):
    find(client)
    assert len(cache_of(client)) == 1
    client.post("/lines/owners", json=an_owner())
    assert len(cache_of(client)) == 0


def test_a_route_that_writes_an_application_leaves_it_alone(client):
    find(client)
    filled = len(cache_of(client))
    client.get("/citations/next-number?on=2025-04-08")
    assert len(cache_of(client)) == filled


def test_a_new_wire_is_seen_by_the_very_next_request(client):
    query = "origin=waterville&segments=US201-1&segments=US201-2&height=20'&travel=2025-04-09"
    before = client.get(f"/lines/clearance?{query}&filed=2025-03-01").json()
    assert before["lifts"] == []
    client.post("/lines/owners", json=an_owner())
    client.post("/lines", json=a_wire())
    after = client.get(f"/lines/clearance?{query}&filed=2025-03-01").json()
    assert len(after["lifts"]) == 1


def test_an_office_built_with_no_cache_still_reads_everything(factory):
    with UnitOfWork(factory) as work:
        office = office_from(work, None)
    assert len(office.network.segments()) == 12
    assert len(office.commodities) == 17


def test_an_office_built_with_a_cache_reads_the_same_books(factory):
    cache = ReferenceCache()
    with UnitOfWork(factory) as work:
        plain = office_from(work, None)
        cached = office_from(work, None, cache=cache)
    assert len(cached.network.segments()) == len(plain.network.segments())
    assert len(cached.library) == len(plain.library)
    assert cache.misses == 1


def test_an_application_with_no_cache_on_its_state_still_works(factory):
    app = create_app(factory)
    del app.state.reference
    from starlette.testclient import TestClient

    from jeepdolly.api.security import HEADER, Role
    from support import keys_for

    client = TestClient(app)
    client.headers[HEADER] = keys_for(factory)[Role.SUPERVISOR]
    assert client.get(f"/applications/25-04471/assessment?on={APRIL}").status_code in (200, 404)
