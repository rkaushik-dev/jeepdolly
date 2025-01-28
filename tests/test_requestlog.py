"""One line per request, and what is deliberately not in it."""

import io

import pytest

from jeepdolly import logs
from jeepdolly.api.requestlog import SLOW_MILLISECONDS, duration_since
from support import keyed_client
from test_api_roles import body

FILED = "2025-04-07"
START = "2025-04-08"


@pytest.fixture
def written():
    stream = io.StringIO()
    logs.configure(stream=stream)
    return stream


@pytest.fixture
def client():
    return keyed_client()


def test_every_request_leaves_one_line_with_what_was_asked(client, written):
    client.get("/health")
    found = written.getvalue()
    assert "request.done" in found
    assert "method=GET" in found
    assert "path=/health" in found
    assert "status=200" in found


def test_the_line_carries_how_long_it_took(client, written):
    client.get("/health")
    assert "ms=" in written.getvalue()


def test_a_refusal_is_logged_at_info_because_it_is_the_service_working(client, written):
    client.get("/permits/25-P-999999")
    found = written.getvalue()
    assert "status=404" in found
    assert "ERROR" not in found


def test_the_permit_number_is_in_the_path_and_so_it_is_in_the_line(client, written):
    client.get("/permits/25-P-999999")
    assert "path=/permits/25-P-999999" in written.getvalue()


def test_the_query_string_never_reaches_the_log(client, written):
    client.post(f"/applications?filed={FILED}", json=body())
    found = written.getvalue()
    assert "path=/applications" in found
    assert "filed=2025-04-07" not in found


def test_issuing_writes_its_own_line_with_the_things_worth_grepping(client, written):
    reference = client.post(f"/applications?filed={FILED}", json=body()).json()["reference"]
    client.post(f"/applications/{reference}/submit?on={FILED}")
    client.post(f"/permits?reference={reference}&on={FILED}")
    found = written.getvalue()
    assert "permit.issued number=25-P-000001" in found
    assert "usdot=1204471" in found
    assert "gross=94000" in found
    assert "by=Supervisor" in found


def test_voiding_writes_its_own_line_too(client, written):
    reference = client.post(f"/applications?filed={FILED}", json=body()).json()["reference"]
    client.post(f"/applications/{reference}/submit?on={FILED}")
    number = client.post(f"/permits?reference={reference}&on={FILED}").json()["number"]
    client.post(f"/permits/{number}/void?reason=load cancelled&on={START}")
    assert f"permit.voided number={number}" in written.getvalue()


def test_the_key_that_was_sent_is_never_in_the_log(client, written):
    client.get("/health")
    key = client.headers["X-Permit-Key"]
    assert key not in written.getvalue()


def test_a_duration_is_whole_milliseconds_from_a_start(monkeypatch):
    assert duration_since(0.0) >= 0
    assert isinstance(duration_since(0.0), int)
    assert SLOW_MILLISECONDS == 500
