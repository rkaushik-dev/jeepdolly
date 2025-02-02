"""The evening report, which somebody initials."""

from datetime import date

import pytest

from jeepdolly.errors import Impossible
from jeepdolly.money import Dollars
from jeepdolly.permits.numbering import PermitNumber, Series
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.reports.issuance import issuance
from support import a_permit, a_seeded_database, an_application

MONDAY = date(2025, 4, 7)
TUESDAY = date(2025, 4, 8)


@pytest.fixture
def factory():
    return a_seeded_database()


def written(factory, numbers):
    application = an_application()
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
        for sequence, series, issued in numbers:
            work.permits.add(
                a_permit(
                    application,
                    number=PermitNumber(2025, series, sequence),
                    issued=issued,
                    start=max(issued, application.start),
                )
            )
        work.commit()
    return application


def test_a_day_with_nothing_on_it_still_prints_a_heading(factory):
    with UnitOfWork(factory) as work:
        found = issuance(work.db, MONDAY)
    assert found.count == 0
    assert found.lines()[0] == "permits issued 2025-04-07"
    assert found.taken == Dollars(0)


def test_the_day_counts_what_was_written_and_what_it_took(factory):
    written(factory, [(1, Series.SINGLE, MONDAY), (2, Series.SINGLE, MONDAY)])
    with UnitOfWork(factory) as work:
        found = issuance(work.db, MONDAY)
    assert found.count == 2
    assert found.taken == Dollars.of(92, 24)
    assert str(found) == "2 permits, $92.24"


def test_the_money_is_the_lines_that_were_printed_and_not_a_recomputation(factory):
    written(factory, [(1, Series.SINGLE, MONDAY)])
    with UnitOfWork(factory) as work:
        found = issuance(work.db, MONDAY)
    assert found.permits[0].fee == Dollars.of(46, 12)


def test_the_count_by_series_says_what_kind_of_day_it_was(factory):
    written(
        factory,
        [(1, Series.SINGLE, MONDAY), (2, Series.SINGLE, MONDAY), (1, Series.SUPERLOAD, MONDAY)],
    )
    with UnitOfWork(factory) as work:
        found = issuance(work.db, MONDAY)
    assert found.by_series == {"P": 2, "S": 1}
    assert [one.number for one in found.of_series("S")] == ["25-S-000001"]


def test_a_span_of_days_reads_as_a_span(factory):
    written(factory, [(1, Series.SINGLE, MONDAY), (2, Series.SINGLE, TUESDAY)])
    with UnitOfWork(factory) as work:
        one_day = issuance(work.db, MONDAY)
        both = issuance(work.db, MONDAY, TUESDAY)
    assert one_day.count == 1
    assert both.count == 2
    assert both.lines()[0] == "permits issued 2025-04-07 to 2025-04-08"
    assert not both.one_day


def test_the_report_lines_are_what_gets_initialled(factory):
    written(factory, [(1, Series.SINGLE, MONDAY)])
    with UnitOfWork(factory) as work:
        lines = issuance(work.db, MONDAY).lines()
    assert lines[1] == "25-P-000001  Bickford Heavy Haul  94,000 lb  $46.12"
    assert lines[-1] == "1 permits  $46.12"


def test_the_refused_and_withdrawn_are_counted_where_there_are_any(factory):
    application = an_application()
    application.submit(MONDAY)
    application.refuse(MONDAY, "the bridge is posted")
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
        found = issuance(work.db, MONDAY)
    assert found.refused == 1
    assert found.withdrawn == 0
    assert "1 refused  0 withdrawn" in found.lines()


def test_a_report_that_ends_before_it_starts_is_refused(factory):
    with UnitOfWork(factory) as work, pytest.raises(Impossible):
        issuance(work.db, TUESDAY, MONDAY)
