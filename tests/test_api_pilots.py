"""Naming the pilot cars, which happens after the application is typed."""

from datetime import date

import pytest

from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit
from support import a_seeded_database, an_application, keyed_client
from test_api_roles import body

APRIL = date(2025, 4, 8)
FILED = date(2025, 4, 7)


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def client(factory):
    return keyed_client(factory=factory)


@pytest.fixture
def filed(factory):
    with UnitOfWork(factory) as work:
        work.applications.add(an_application())
        work.commit()
    return "25-04471"


def name(client, reference, cards, on=FILED):
    return client.post(f"/applications/{reference}/pilots?on={on}", json=cards)


def test_naming_two_cards_puts_them_on_the_application(client, filed):
    answer = name(client, filed, ["C-1001", "C-1002"])
    assert answer.status_code == 200
    assert answer.json()["escort_cards"] == ["C-1001", "C-1002"]


def test_the_cards_are_still_there_when_the_application_is_read_back(client, filed):
    name(client, filed, ["C-1001"])
    found = client.get(f"/applications/{filed}").json()
    assert found["escort_cards"] == ["C-1001"]


def test_naming_them_again_replaces_rather_than_adds(client, filed):
    name(client, filed, ["C-1001", "C-1002"])
    answer = name(client, filed, ["C-1002"])
    assert answer.json()["escort_cards"] == ["C-1002"]


def test_naming_nobody_takes_the_cards_off_again(client, filed):
    name(client, filed, ["C-1001"])
    assert name(client, filed, []).json()["escort_cards"] == []


def test_a_card_nobody_has_is_a_404_while_the_carrier_is_still_on_the_phone(client, filed):
    answer = name(client, filed, ["C-9999"])
    assert answer.status_code == 404
    assert answer.json()["code"] == "not_on_file"
    assert "C-9999" in answer.json()["message"]


def test_a_bad_card_leaves_the_good_ones_off_too(client, filed, factory):
    name(client, filed, ["C-1001", "C-9999"])
    found = client.get(f"/applications/{filed}").json()
    assert found["escort_cards"] == []


def test_a_card_that_has_run_out_can_still_be_named_because_it_may_be_renewed(client, filed):
    assert name(client, filed, ["C-1003"]).status_code == 200


def test_naming_the_pilots_writes_a_line_on_the_trail(client, filed, factory):
    name(client, filed, ["C-1001"])
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.db, "application", filed)
    assert [one.action for one in found] == ["application.pilots_named"]
    assert found[0].detail == "C-1001"


def test_naming_nobody_says_so_on_the_trail(client, filed, factory):
    name(client, filed, [])
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.db, "application", filed)
    assert found[0].detail == "nobody"


def test_an_application_nobody_has_is_a_404(client):
    assert name(client, "25-99999", ["C-1001"]).status_code == 404


def test_the_pilots_cannot_be_changed_once_the_application_is_closed(client, filed, factory):
    client.post(f"/applications/{filed}/submit?on={FILED}")
    client.post(f"/applications/{filed}/refuse?reason=load%20cancelled&on={FILED}")
    answer = name(client, filed, ["C-1001"])
    assert answer.status_code == 409


def test_a_reader_cannot_name_a_pilot_car(factory):
    reader = keyed_client(factory=factory, role="reader")
    with UnitOfWork(factory) as work:
        work.applications.add(an_application())
        work.commit()
    assert name(reader, "25-04471", ["C-1001"]).status_code == 403


def test_the_cards_can_be_named_when_the_application_is_first_filed(client):
    sent = body()
    sent["escort_cards"] = ["C-1001"]
    answer = client.post(f"/applications?filed={FILED}", json=sent)
    assert answer.status_code == 201
    assert answer.json()["escort_cards"] == ["C-1001"]
