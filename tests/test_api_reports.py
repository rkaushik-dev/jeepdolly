"""The report routes, which hand back both the lines and the figures."""

from datetime import date

import pytest

from jeepdolly.domain.restriction import RestrictionKind
from jeepdolly.persistence.models import RestrictionRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_permit, a_seeded_database, an_application, keyed_client

APRIL = "2025-04-07"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def client(factory):
    return keyed_client(factory=factory)


@pytest.fixture
def issued(factory):
    application = an_application()
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
        work.permits.add(a_permit(application))
        work.commit()
    return application


def test_the_evening_report_comes_back_as_lines_and_as_figures(client, issued):
    found = client.get(f"/reports/issuance?day={APRIL}").json()
    assert found["count"] == 1
    assert found["taken"] == "$46.12"
    assert found["by_series"] == {"P": 1}
    assert found["lines"][0] == "permits issued 2025-04-07"


def test_a_span_is_asked_for_with_a_second_day(client, issued):
    found = client.get(f"/reports/issuance?day={APRIL}&to=2025-04-30").json()
    assert found["end"] == "2025-04-30"
    assert found["lines"][0].endswith("to 2025-04-30")


def test_a_day_with_nothing_on_it_is_a_report_with_nothing_on_it(client):
    found = client.get(f"/reports/issuance?day={APRIL}").json()
    assert found["count"] == 0
    assert found["taken"] == "$0.00"


def test_a_report_that_ends_before_it_starts_is_refused(client):
    answer = client.get(f"/reports/issuance?day=2025-04-30&to={APRIL}")
    assert answer.status_code == 422
    assert answer.json()["code"] == "impossible"


def test_the_revenue_report_carries_the_kinds_as_money(client, issued):
    found = client.get(f"/reports/revenue?start={APRIL}&end=2025-04-30").json()
    assert found["permits"] == 1
    assert found["by_kind"]["base fee"] == "$40.00"
    assert found["by_month"] == {"2025-04": "$46.12"}


def test_the_revenue_report_needs_both_ends(client):
    assert client.get(f"/reports/revenue?start={APRIL}").status_code == 422


def test_the_road_report_carries_the_changes_and_what_is_standing(client, factory):
    with UnitOfWork(factory) as work:
        work.db.add(
            RestrictionRow(
                ident="R-1",
                segment_ident="US2-1",
                kind=RestrictionKind.CLOSURE,
                note="culvert work",
                effective=date(2025, 4, 9),
                expires=date(2025, 4, 11),
            )
        )
        work.commit()
    found = client.get(f"/reports/roads?day={APRIL}&days=7").json()
    assert any("closes" in one for one in found["changes"])
    assert found["standing"] == []


def test_what_is_standing_today_is_beside_what_changes(client, factory):
    with UnitOfWork(factory) as work:
        work.db.add(
            RestrictionRow(
                ident="R-2",
                segment_ident="SA148-1",
                kind=RestrictionKind.CLOSURE,
                note="washout",
            )
        )
        work.commit()
    found = client.get(f"/reports/roads?day={APRIL}").json()
    assert found["standing"][0]["segment"] == "SA148-1"
    assert found["standing"][0]["permanent"]
    assert found["changes"][-1] == "nothing changes"


def test_a_fortnight_is_the_default_and_the_span_is_capped(client):
    assert client.get(f"/reports/roads?day={APRIL}").json()["days"] == 14
    assert client.get(f"/reports/roads?day={APRIL}&days=0").status_code == 422
    assert client.get(f"/reports/roads?day={APRIL}&days=400").status_code == 422


def test_reading_a_report_needs_a_key(factory):
    nobody = keyed_client(factory=factory)
    nobody.headers.pop("X-Permit-Key")
    assert nobody.get(f"/reports/issuance?day={APRIL}").status_code == 401
