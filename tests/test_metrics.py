"""The numbers about the office, counted off the database every time."""

from datetime import date

import pytest

from jeepdolly.permits.application import ApplicationState
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import metrics, studies
from support import a_permit, a_seeded_database, an_application

APRIL = date(2025, 4, 7)
START = date(2025, 4, 8)


@pytest.fixture
def factory():
    return a_seeded_database()


def issued(factory):
    application = an_application()
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
        work.permits.add(a_permit(application))
        work.commit()
    return application


def numbers(factory, day=START):
    with UnitOfWork(factory) as work:
        return metrics.by_name(work.db, day)


def test_an_empty_office_still_counts_the_road_log(factory):
    found = numbers(factory)
    assert found["permits_on_the_road"] == 0
    assert found["segments"] == 12
    assert found["structures_posted"] == 1
    assert found["carriers"] == 2


def test_a_permit_shows_up_on_the_road_and_in_the_week(factory):
    issued(factory)
    found = numbers(factory)
    assert found["permits_on_the_road"] == 1
    assert found["permits_issued_week"] == 1
    assert found["permits_void"] == 0


def test_a_permit_that_has_run_out_is_off_the_road_but_still_in_the_week(factory):
    issued(factory)
    found = numbers(factory, date(2025, 4, 12))
    assert found["permits_on_the_road"] == 0
    assert found["permits_issued_week"] == 1


def test_a_voided_permit_moves_from_one_number_to_another(factory):
    issued(factory)
    with UnitOfWork(factory) as work:
        permit = work.permits.get("25-P-004471")
        permit.void(START, "load cancelled")
        work.permits.save_state(permit)
        work.commit()
    found = numbers(factory)
    assert found["permits_on_the_road"] == 0
    assert found["permits_void"] == 1


def test_the_queues_are_counted_by_state(factory):
    application = an_application()
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
    assert numbers(factory)["applications_draft"] == 1
    assert numbers(factory)["applications_waiting"] == 0
    with UnitOfWork(factory) as work:
        row = work.applications.row("25-04471")
        row.state = ApplicationState.SUBMITTED
        work.commit()
    assert numbers(factory)["applications_waiting"] == 1


def test_an_open_study_is_counted_and_a_decided_one_is_not(factory):
    with UnitOfWork(factory) as work:
        studies.open_study(work.db, "25-F-000002", ("1247",), APRIL)
        work.commit()
    assert numbers(factory)["studies_open"] == 1
    with UnitOfWork(factory) as work:
        studies.assign(work.db, "25-F-000002", "R Pelletier", APRIL)
        studies.clear(work.db, "25-F-000002", START)
        work.commit()
    assert numbers(factory)["studies_open"] == 0


def test_the_exposition_carries_a_help_and_a_type_for_every_number(factory):
    with UnitOfWork(factory) as work:
        written = metrics.exposition(work.db, START)
    assert "# HELP jeepdolly_segments segments on the road log" in written
    assert "# TYPE jeepdolly_segments gauge" in written
    assert "jeepdolly_segments 12" in written
    assert written.endswith("\n")


def test_every_name_is_flat_and_prefixed(factory):
    with UnitOfWork(factory) as work:
        found = metrics.snapshot(work.db, START)
    for one in found:
        assert str(one).startswith("jeepdolly_")
        assert " " not in one.name
        assert one.about
