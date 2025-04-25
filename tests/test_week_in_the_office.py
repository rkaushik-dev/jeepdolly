"""One week in the office, end to end, through the interface only.

Every other test in this suite holds one rule still and pushes on it. This one
does none of that. It runs the week the office actually has: applications in,
a road shut on the Tuesday, a thaw order signed on the Wednesday, a carrier
suspended on the Thursday, and the reports that get initialled on the Friday.

It is deliberately written against the http interface and nothing else. If a
refactor breaks a private helper this test will not notice, and if a refactor
breaks the office this test will fail on the day it is written.
"""

import pytest
from fastapi.testclient import TestClient

from jeepdolly.api.app import create_app
from jeepdolly.api.security import HEADER, Role
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit
from support import a_seeded_database, keys_for
from test_api_roles import body

MONDAY = "2025-04-07"
TUESDAY = "2025-04-08"
WEDNESDAY = "2025-04-09"
THURSDAY = "2025-04-10"
FRIDAY = "2025-04-11"
ACROSS = ["US201-1", "US201-2", "SA148-2", "SR43-1"]


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def office(factory):
    """Two people at two screens, over one database."""
    keys = keys_for(factory)
    made = []
    for role in (Role.CLERK, Role.SUPERVISOR):
        client = TestClient(create_app(factory))
        client.headers[HEADER] = keys[role]
        made.append(client)
    return tuple(made)


def application(route=None, **kw):
    written = body()
    written["start"] = TUESDAY
    written["end"] = FRIDAY
    if route is not None:
        written["route"] = route
    written.update(kw)
    return written


def test_a_week_in_the_office(office, factory):
    clerk, boss = office

    # Monday. Two haulers ring in. One is a walk in and gets a permit at the
    # window. The other wants a route the office has to find for him.
    walk_in = clerk.post(f"/permits/walk-in?on={MONDAY}", json=application())
    assert walk_in.status_code == 201
    first = walk_in.json()["number"]
    assert first == "25-P-000001"

    second = clerk.post(f"/applications?filed={MONDAY}", json=application(route=ACROSS))
    reference = second.json()["reference"]
    clerk.post(f"/applications/{reference}/submit?on={MONDAY}")
    quoted = clerk.get(f"/applications/{reference}/assessment?on={TUESDAY}").json()
    assert quoted["permittable"]
    assert quoted["route"] == ACROSS

    # Tuesday. The district rings to say the Carrabassett culvert is out. The
    # office shuts SA148-2 and finds out who is already crossing it.
    shut = boss.post(
        f"/restrictions?on={TUESDAY}",
        json={
            "ident": "R-culvert",
            "segment": "SA148-2",
            "kind": "closure",
            "note": "culvert washed out",
        },
    )
    assert shut.status_code == 201
    assert shut.json()["permits_crossing"] == []

    # The application that named that road cannot be issued now.
    refused = clerk.post(f"/permits?reference={reference}&on={TUESDAY}")
    assert refused.status_code == 409
    assert any("SA148-2" in one for one in refused.json()["facts"]["reasons"])

    # Wednesday. The district signs a thaw order for district four, and the
    # office files it. Nothing about it is retrospective.
    order = boss.post(
        "/seasons",
        json={
            "number": "25-04",
            "region": "district 4",
            "kind": "thaw",
            "start": WEDNESDAY,
            "end": "2025-05-02",
            "share": 65,
            "signed_by": "R Pelletier",
        },
    )
    assert order.status_code == 201
    assert boss.get(f"/seasons/in-force/district 4?day={WEDNESDAY}").json()["share"] == 65

    # The hauler rings back and takes the interstate instead, which is not in
    # district four and not shut.
    later = application(start=WEDNESDAY)
    replacement = clerk.post(f"/applications?filed={WEDNESDAY}", json=later)
    onward = replacement.json()["reference"]
    clerk.post(f"/applications/{onward}/submit?on={WEDNESDAY}")
    issued = clerk.post(f"/permits?reference={onward}&on={WEDNESDAY}")
    assert issued.status_code == 201
    assert issued.json()["route"] == ["I95-1", "I95-2"]

    # Thursday. The carrier is suspended for unpaid permits, which kills both
    # live permits and says so.
    suspended = boss.post(f"/carriers/1204471/suspend?reason=unpaid permits&on={THURSDAY}")
    assert suspended.status_code == 200
    assert "suspended" in suspended.json()["standing"]
    assert clerk.get(f"/permits/{first}").json()["state"] == "void"
    assert clerk.get(f"/permits?day={THURSDAY}").json() == []

    # And nothing more can be written for them.
    blocked = clerk.post(f"/permits/walk-in?on={THURSDAY}", json=application(start=THURSDAY))
    assert blocked.status_code == 409

    # Friday. The reports. Three permits were written this week, two of them
    # are void, and the money is still counted because it was taken.
    week = clerk.get(f"/reports/issuance?day={MONDAY}&to={FRIDAY}").json()
    assert week["count"] == 2
    assert week["by_series"] == {"P": 2}
    assert week["taken"] != "$0.00"

    revenue = clerk.get(f"/reports/revenue?start={MONDAY}&end={FRIDAY}").json()
    assert revenue["permits"] == 2
    assert revenue["by_kind"]["base fee"] == "$80.00"

    # The culvert closure carried no dates, so it is in force until somebody
    # lifts it and it never appears as a change.
    roads = clerk.get(f"/reports/roads?day={MONDAY}&days=7").json()
    assert roads["changes"][-1] == "nothing changes"
    assert [one["segment"] for one in roads["standing"]] == ["SA148-2"]

    numbers = clerk.get(f"/metrics.json?day={FRIDAY}").json()
    assert numbers["permits_void"] == 2
    assert numbers["permits_issued_week"] == 2
    assert numbers["restrictions"] == 1


def test_the_week_leaves_a_trail_that_reads_in_order(office, factory):
    clerk, boss = office
    clerk.post(f"/permits/walk-in?on={MONDAY}", json=application())
    boss.post(f"/carriers/1204471/suspend?reason=unpaid permits&on={THURSDAY}")

    with UnitOfWork(factory) as work:
        permit = audit.for_document(work.db, "permit", "25-P-000001")
        carrier = audit.for_document(work.db, "carrier", "1204471")
    assert [one.action for one in permit] == ["permit.issued", "permit.voided"]
    assert [one.action for one in carrier] == ["carrier.suspended"]
    assert permit[0].actor == "Clerk"
    assert permit[1].actor == "Supervisor"
