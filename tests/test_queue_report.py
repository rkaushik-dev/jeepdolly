"""How old the work is, which is the number the supervisor watches."""

from datetime import date

import pytest

from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.reports.queue import ageing
from jeepdolly.services import studies
from support import a_seeded_database, an_application

FILED = date(2025, 4, 7)
LATER = date(2025, 4, 21)


@pytest.fixture
def factory():
    return a_seeded_database()


def submitted(factory, reference="25-F-000001", when=FILED):
    application = an_application(reference=reference, filed=when, start=when, end=when)
    application.submit(when)
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()


def queued(factory, reference="25-F-000002", when=FILED):
    with UnitOfWork(factory) as work:
        studies.open_study(work.db, reference, ("1247",), when)
        work.commit()


def test_a_quiet_office_has_nothing_waiting(factory):
    with UnitOfWork(factory) as work:
        found = ageing(work.db, FILED)
    assert found.quiet
    assert found.oldest_study == 0
    assert found.lines()[-1].endswith("oldest study 0 days")


def test_an_application_in_the_queue_is_aged_from_the_day_it_was_filed(factory):
    submitted(factory)
    with UnitOfWork(factory) as work:
        found = ageing(work.db, LATER)
    assert len(found.applications) == 1
    assert found.applications[0].days == 14
    assert found.oldest_application == 14


def test_a_draft_is_not_waiting_on_anybody(factory):
    application = an_application()
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
        found = ageing(work.db, LATER)
    assert found.applications == ()


def test_a_study_waiting_and_one_with_an_engineer_are_both_studies(factory):
    queued(factory, "25-F-000002")
    queued(factory, "25-F-000003")
    with UnitOfWork(factory) as work:
        studies.assign(work.db, "25-F-000003", "R Pelletier", FILED)
        work.commit()
        found = ageing(work.db, LATER)
    assert [one.reference for one in found.studies] == ["25-F-000002", "25-F-000003"]
    assert found.studies[1].note == "R Pelletier"


def test_a_held_study_is_counted_apart_from_the_engineers_queue(factory):
    queued(factory)
    with UnitOfWork(factory) as work:
        studies.assign(work.db, "25-F-000002", "R Pelletier", FILED)
        studies.hold(work.db, "25-F-000002", FILED, "send the spacings")
        work.commit()
        found = ageing(work.db, LATER)
    assert found.studies == ()
    assert len(found.held) == 1
    assert found.held[0].days == 14


def test_a_decided_study_is_off_every_queue(factory):
    queued(factory)
    with UnitOfWork(factory) as work:
        studies.assign(work.db, "25-F-000002", "R Pelletier", FILED)
        studies.clear(work.db, "25-F-000002", FILED)
        work.commit()
        found = ageing(work.db, LATER)
    assert found.quiet


def test_what_is_over_a_promise_comes_back_oldest_first(factory):
    submitted(factory, "25-F-000001", date(2025, 4, 1))
    submitted(factory, "25-F-000004", date(2025, 4, 20))
    queued(factory, "25-F-000002", date(2025, 4, 5))
    with UnitOfWork(factory) as work:
        found = ageing(work.db, LATER)
    late = found.over(14)
    assert [one.reference for one in late] == ["25-F-000001", "25-F-000002"]
    assert found.over(60) == ()


def test_the_report_prints_a_line_for_everything_waiting(factory):
    submitted(factory)
    queued(factory)
    with UnitOfWork(factory) as work:
        lines = ageing(work.db, LATER).lines()
    assert lines[0] == "waiting on 2025-04-21"
    assert lines[1].startswith("application  25-F-000001")
    assert lines[2].startswith("study  25-F-000002")
    assert lines[-1].startswith("1 applications  1 studies")
