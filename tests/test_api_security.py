"""Keys, the four roles, and the ladder between them."""

from datetime import date

import pytest
from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from jeepdolly.api.app import create_app
from jeepdolly.api.security import (
    HEADER,
    Caller,
    Role,
    caller,
    digest_of,
    issue_key,
    needs,
    new_key,
)
from jeepdolly.persistence.models import ApiKeyRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_seeded_database

TODAY = date.today()


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def keys(factory):
    made = {}
    with UnitOfWork(factory) as work:
        for role in Role:
            key, _ = issue_key(work, f"k-{role.value}", role.value.title(), role, TODAY)
            made[role] = key
        revoked, row = issue_key(work, "k-old", "Gone Away", Role.CLERK, TODAY)
        row.revoked = TODAY
        made["revoked"] = revoked
        work.commit()
    return made


@pytest.fixture
def client(factory):
    app = create_app(factory)
    _guarded(app)
    return TestClient(app)


def _guarded(app: FastAPI) -> None:
    @app.get("/guarded/reader")
    def reader(who: Caller = Depends(needs(Role.READER))) -> dict[str, str]:
        return {"person": who.person, "role": who.role.value}

    @app.get("/guarded/clerk")
    def clerk(who: Caller = Depends(needs(Role.CLERK))) -> dict[str, str]:
        return {"person": who.person}

    @app.get("/guarded/supervisor")
    def supervisor(who: Caller = Depends(needs(Role.SUPERVISOR))) -> dict[str, str]:
        return {"person": who.person}

    @app.get("/guarded/whoami")
    def whoami(who: Caller = Depends(caller)) -> dict[str, object]:
        return {"person": who.person, "usdot": who.usdot, "carrier": who.is_carrier}


def test_a_key_is_never_stored_only_its_hash(factory):
    with UnitOfWork(factory) as work:
        key, row = issue_key(work, "k-1", "Dana Bickford", Role.CLERK, TODAY)
        work.commit()
        assert row.digest == digest_of(key)
        assert key not in row.digest
        assert len(row.digest) == 64


def test_two_keys_are_never_the_same():
    assert new_key() != new_key()
    assert len(new_key()) > 20


def test_a_request_with_no_key_at_all_is_refused(client):
    answer = client.get("/guarded/reader")
    assert answer.status_code == 401


def test_a_key_nobody_issued_is_refused_without_saying_why(client):
    answer = client.get("/guarded/reader", headers={HEADER: "not-a-key"})
    assert answer.status_code == 401
    assert answer.json()["detail"] == "that key is not in use"


def test_a_revoked_key_is_refused_the_same_way(client, keys):
    answer = client.get("/guarded/reader", headers={HEADER: keys["revoked"]})
    assert answer.status_code == 401


def test_a_reader_may_read_and_nothing_else(client, keys):
    headers = {HEADER: keys[Role.READER]}
    assert client.get("/guarded/reader", headers=headers).status_code == 200
    assert client.get("/guarded/clerk", headers=headers).status_code == 403
    assert client.get("/guarded/supervisor", headers=headers).status_code == 403


def test_a_clerk_reaches_everything_a_reader_does(client, keys):
    headers = {HEADER: keys[Role.CLERK]}
    assert client.get("/guarded/reader", headers=headers).status_code == 200
    assert client.get("/guarded/clerk", headers=headers).status_code == 200
    assert client.get("/guarded/supervisor", headers=headers).status_code == 403


def test_a_supervisor_reaches_everything(client, keys):
    headers = {HEADER: keys[Role.SUPERVISOR]}
    for route in ("reader", "clerk", "supervisor"):
        assert client.get(f"/guarded/{route}", headers=headers).status_code == 200


def test_the_refusal_says_what_was_needed_and_what_was_held(client, keys):
    answer = client.get("/guarded/supervisor", headers={HEADER: keys[Role.CLERK]})
    assert answer.json()["detail"] == "this needs a supervisor, and you are a clerk"


def test_the_caller_carries_the_name_that_goes_on_the_permit(client, keys):
    found = client.get("/guarded/reader", headers={HEADER: keys[Role.ENGINEER]}).json()
    assert found["person"] == "Engineer"
    assert found["role"] == "engineer"


def test_a_carrier_key_carries_the_number_it_belongs_to(factory, client):
    with UnitOfWork(factory) as work:
        key, _ = issue_key(work, "k-carrier", "D Bickford", Role.READER, TODAY, usdot=1204471)
        work.commit()
    found = client.get("/guarded/whoami", headers={HEADER: key}).json()
    assert found["usdot"] == 1204471
    assert found["carrier"]


def test_an_office_key_belongs_to_nobody_in_particular(client, keys):
    found = client.get("/guarded/whoami", headers={HEADER: keys[Role.CLERK]}).json()
    assert found["usdot"] is None
    assert not found["carrier"]


def test_a_key_issued_for_tomorrow_is_not_live_today(factory):
    row = ApiKeyRow(
        ident="k-future",
        digest=digest_of("whatever"),
        person="Later",
        role="clerk",
        issued=date(2030, 1, 1),
    )
    assert not row.live_on(TODAY)
    assert row.live_on(date(2030, 6, 1))
    assert repr(row) == "<ApiKeyRow k-future clerk>"


def test_the_ladder_is_a_ladder():
    assert Role.SUPERVISOR.reaches(Role.READER)
    assert not Role.READER.reaches(Role.CLERK)
    assert Role.ENGINEER.rank > Role.CLERK.rank
    assert str(Role.ENGINEER) == "engineer"
