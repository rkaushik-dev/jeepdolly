"""Seasonal order routes, and the day the routing starts reading them."""

import pytest

from jeepdolly.api.security import HEADER, Role
from jeepdolly.domain.restriction import RestrictionKind
from jeepdolly.persistence.models import RestrictionRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_seeded_database, keyed_client
from test_api_roles import body

FILED = "2025-04-07"
APRIL = "2025-04-08"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def client(factory):
    return keyed_client(factory=factory)


def order(number="25-04", region="district 4", kind="thaw", share=65):
    return {
        "number": number,
        "region": region,
        "kind": kind,
        "start": "2025-03-20",
        "end": "2025-05-02",
        "share": share,
        "signed_by": "R Pelletier",
    }


def posted_road(factory, segment="SA148-2", region="district 4"):
    with UnitOfWork(factory) as work:
        work.db.add(
            RestrictionRow(
                ident=f"R-{segment}",
                segment_ident=segment,
                kind=RestrictionKind.SEASONAL,
                note="spring posting",
                region=region,
            )
        )
        work.commit()


def test_an_order_is_filed_and_read_back(client):
    answer = client.post("/seasons", json=order())
    assert answer.status_code == 201
    assert answer.json()["share"] == 65
    assert client.get("/seasons/25-04").json()["signed_by"] == "R Pelletier"


def test_an_overlapping_order_in_one_region_is_a_conflict(client):
    client.post("/seasons", json=order())
    answer = client.post("/seasons", json=order(number="25-05"))
    assert answer.status_code == 409


def test_an_order_that_does_not_reduce_anything_is_refused(client):
    answer = client.post("/seasons", json=order(share=100))
    assert answer.status_code == 422
    assert answer.json()["code"] == "impossible"


def test_the_orders_of_a_region_come_back_in_the_order_they_ran(client):
    client.post("/seasons", json=order())
    client.post(
        "/seasons",
        json={**order(number="25-08"), "start": "2025-11-03", "end": "2025-12-20", "share": 80},
    )
    found = client.get("/seasons?region=district 4").json()
    assert [one["number"] for one in found] == ["25-04", "25-08"]


def test_what_is_in_force_is_asked_by_region_and_day(client):
    client.post("/seasons", json=order())
    assert client.get(f"/seasons/in-force/district 4?day={APRIL}").json()["number"] == "25-04"
    assert client.get("/seasons/in-force/district 4?day=2025-06-01").json() is None


def test_filing_an_order_needs_a_supervisor(factory):
    clerk = keyed_client(Role.CLERK, factory)
    assert clerk.post("/seasons", json=order()).status_code == 403


def test_lifting_an_order_ends_it_on_the_day_and_keeps_the_row(client):
    client.post("/seasons", json=order())
    answer = client.post(f"/seasons/25-04/lift?on={APRIL}")
    assert answer.status_code == 200
    assert answer.json()["end"] == APRIL
    assert client.get("/seasons/in-force/district 4?day=2025-04-20").json() is None
    assert len(client.get("/seasons?region=district 4").json()) == 1


def test_an_order_nobody_signed_is_a_404(client):
    assert client.get("/seasons/25-99").status_code == 404


def test_the_office_now_reads_the_orders_when_it_assesses(client, factory):
    posted_road(factory)
    client.post("/seasons", json=order())
    written = body()
    written["route"] = ["US201-1", "US201-2", "SA148-2", "SR43-1"]
    reference = client.post(f"/applications?filed={FILED}", json=written).json()["reference"]
    found = client.get(f"/applications/{reference}/assessment?on={APRIL}").json()
    assert found["permittable"]
    assert any(one["code"] == "R-02" for one in found["conditions"])


def test_a_route_with_no_order_running_carries_no_posting_condition(client, factory):
    posted_road(factory)
    written = body()
    written["route"] = ["US201-1", "US201-2", "SA148-2", "SR43-1"]
    reference = client.post(f"/applications?filed={FILED}", json=written).json()["reference"]
    found = client.get(f"/applications/{reference}/assessment?on={APRIL}").json()
    assert not any(one["code"] == "R-02" for one in found["conditions"])


def test_reading_an_order_needs_a_key(factory):
    nobody = keyed_client(factory=factory)
    nobody.headers.pop(HEADER)
    assert nobody.get("/seasons/25-04").status_code == 401
