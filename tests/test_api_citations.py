"""Citations over the interface, which is the counter typing in the post."""

import pytest

from jeepdolly.api.security import Role
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit
from support import a_seeded_database, keyed_client

WRITTEN = "2025-04-08"
FILED = "2025-04-11"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def client(factory):
    return keyed_client(factory=factory)


def ticket(number="E-25-0417", offence="overweight", over="12000", written=WRITTEN, **kw):
    body = {
        "number": number,
        "usdot": 1204471,
        "officer": "Sgt A Thibodeau",
        "offence": offence,
        "place": "US201-2 at Fairfield",
        "written": written,
        "filed": FILED,
        "over": over,
    }
    body.update(kw)
    return body


def file_one(client, **kw):
    return client.post("/citations", json=ticket(**kw))


def test_a_ticket_is_filed_and_priced_off_the_schedule(client):
    answer = file_one(client)
    assert answer.status_code == 201
    found = answer.json()
    assert found["amount"] == "$580.00"
    assert found["multiplier"] == 1
    assert found["state"] == "written"


def test_the_carrier_name_comes_off_the_register(client):
    assert file_one(client).json()["carrier_name"] == "Bickford Heavy Haul"


def test_the_contest_window_is_on_the_answer(client):
    assert file_one(client).json()["contest_closes"] == "2025-04-29"


def test_an_offence_this_office_does_not_price_is_refused(client):
    answer = file_one(client, offence="parked badly")
    assert answer.status_code == 422
    assert "parked badly" in answer.json()["message"]


def test_a_carrier_nobody_has_registered_is_a_404(client):
    assert file_one(client, usdot=9999999).status_code == 404


def test_the_same_number_is_not_filed_twice(client):
    file_one(client)
    assert file_one(client).status_code == 409


def test_a_ticket_filed_before_it_was_written_is_refused(client):
    assert file_one(client, written="2025-05-01").status_code == 422


def test_a_second_ticket_in_the_year_is_priced_as_a_repeat(client):
    file_one(client, number="E-25-0100", written="2025-01-20")
    found = file_one(client).json()
    assert found["multiplier"] == 2
    assert found["amount"] == "$1,160.00"


def test_a_flat_offence_carries_no_overage_on_the_answer(client):
    found = file_one(client, offence="off route", over=None).json()
    assert found["over"] is None
    assert found["amount"] == "$250.00"


def test_one_citation_can_be_read_back(client):
    file_one(client)
    assert client.get("/citations/E-25-0417").json()["place"] == "US201-2 at Fairfield"


def test_a_citation_nobody_has_is_a_404(client):
    assert client.get("/citations/E-25-9999").status_code == 404


def test_the_chase_list_is_what_is_still_owing(client):
    file_one(client, number="E-25-0100", written="2025-01-20")
    file_one(client)
    assert len(client.get("/citations/owing").json()) == 2
    client.post("/citations/E-25-0417/payment?on=2025-04-22&reference=cheque%204471")
    assert [one["number"] for one in client.get("/citations/owing").json()] == ["E-25-0100"]


def test_everything_against_one_carrier_comes_back_newest_first(client):
    file_one(client, number="E-25-0100", written="2025-01-20")
    file_one(client)
    found = client.get("/citations/carrier/1204471").json()
    assert [one["number"] for one in found] == ["E-25-0417", "E-25-0100"]


def test_a_carrier_with_nothing_against_it_comes_back_empty(client):
    assert client.get("/citations/carrier/2299001").json() == []


def test_contesting_it_moves_it_and_lands_on_the_trail(client, factory):
    file_one(client)
    answer = client.post(
        "/citations/E-25-0417/contest?reason=scale%20not%20certified&on=2025-04-20"
    )
    assert answer.json()["state"] == "contested"
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.db, "citation", "E-25-0417")
    assert [one.action for one in found] == ["citation.filed", "citation.contested"]


def test_contesting_it_after_the_window_is_a_409(client):
    file_one(client)
    answer = client.post("/citations/E-25-0417/contest?reason=too%20late&on=2025-05-15")
    assert answer.status_code == 409


def test_contesting_it_without_grounds_is_refused_by_the_query(client):
    file_one(client)
    assert client.post("/citations/E-25-0417/contest?reason=no").status_code == 422


def test_upholding_it_leaves_it_owing(client):
    file_one(client)
    client.post("/citations/E-25-0417/contest?reason=grounds&on=2025-04-20")
    answer = client.post("/citations/E-25-0417/uphold?on=2025-05-02")
    assert answer.json()["state"] == "upheld"
    assert len(client.get("/citations/owing").json()) == 1


def test_dismissing_it_closes_it(client):
    file_one(client)
    client.post("/citations/E-25-0417/contest?reason=grounds&on=2025-04-20")
    answer = client.post("/citations/E-25-0417/dismiss?reason=scale%20had%20lapsed&on=2025-05-02")
    assert answer.json()["state"] == "dismissed"
    assert client.get("/citations/owing").json() == []


def test_nothing_is_upheld_before_it_is_contested(client):
    file_one(client)
    assert client.post("/citations/E-25-0417/uphold").status_code == 409


def test_paying_it_closes_it(client):
    file_one(client)
    answer = client.post("/citations/E-25-0417/payment?on=2025-04-22")
    assert answer.json()["state"] == "paid"


def test_voiding_it_needs_a_reason_and_closes_it(client):
    file_one(client)
    answer = client.post("/citations/E-25-0417/void?reason=wrong%20usdot&on=2025-04-14")
    assert answer.json()["state"] == "void"


def test_a_paid_citation_cannot_be_voided(client):
    file_one(client)
    client.post("/citations/E-25-0417/payment?on=2025-04-22")
    assert client.post("/citations/E-25-0417/void?reason=wrong%20usdot").status_code == 409


def test_a_number_can_be_had_off_the_counter_for_the_few_the_office_writes(client):
    first = client.get("/citations/next-number?on=2025-04-08").json()["number"]
    second = client.get("/citations/next-number?on=2025-04-08").json()["number"]
    assert first == "25-E-000001"
    assert second == "25-E-000002"


def test_a_clerk_cannot_dismiss_a_ticket(factory):
    clerk = keyed_client(role=Role.CLERK, factory=factory)
    clerk.post("/citations", json=ticket())
    clerk.post("/citations/E-25-0417/contest?reason=grounds&on=2025-04-20")
    answer = clerk.post("/citations/E-25-0417/dismiss?reason=scale&on=2025-05-02")
    assert answer.status_code == 403


def test_a_clerk_can_take_the_money(factory):
    clerk = keyed_client(role=Role.CLERK, factory=factory)
    clerk.post("/citations", json=ticket())
    assert clerk.post("/citations/E-25-0417/payment?on=2025-04-22").status_code == 200


def test_a_reader_cannot_file_a_ticket(factory):
    reader = keyed_client(role=Role.READER, factory=factory)
    assert reader.post("/citations", json=ticket()).status_code == 403
