"""The report commands, which print what the paper report prints."""

from datetime import date

import pytest
from click.testing import CliRunner

from jeepdolly.cli import reports as commands
from jeepdolly.cli.main import Console, main
from jeepdolly.config import Settings
from jeepdolly.domain.restriction import RestrictionKind
from jeepdolly.persistence.models import RestrictionRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.reports.issuance import issuance
from support import a_permit, a_seeded_database, an_application

# The import above registers the group. Without it this file passes in a
# whole run, where some other test module imported it first, and fails on
# its own.
assert commands.report.name == "report"

APRIL = "2025-04-07"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def console(factory):
    made = Console(Settings(database_url="sqlite+pysqlite:///:memory:"))
    made._factory = factory
    return made


@pytest.fixture
def issued(factory):
    application = an_application()
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
        work.permits.add(a_permit(application))
        work.commit()
    return application


def call(console, arguments):
    return CliRunner().invoke(main, ["--quiet", *arguments], obj=console)


def test_the_evening_report_prints_the_lines_the_report_produced(console, issued, factory):
    answer = call(console, ["report", "issuance", "--day", APRIL])
    with UnitOfWork(factory) as work:
        expected = issuance(work.db, date(2025, 4, 7)).lines()
    assert answer.exit_code == 0
    assert answer.output.splitlines() == list(expected)


def test_the_evening_report_writes_nothing_on_the_error_stream(console, issued):
    answer = CliRunner(mix_stderr=False).invoke(
        main, ["--quiet", "report", "issuance", "--day", APRIL], obj=console
    )
    assert answer.stderr == ""
    assert "permits issued" in answer.stdout


def test_a_span_is_asked_for_with_a_second_day(console, issued):
    answer = call(console, ["report", "issuance", "--day", APRIL, "--to", "2025-04-30"])
    assert answer.output.splitlines()[0] == "permits issued 2025-04-07 to 2025-04-30"


def test_the_revenue_report_needs_both_ends_of_the_span(console, issued):
    assert call(console, ["report", "revenue", "--from", APRIL]).exit_code == 2
    answer = call(console, ["report", "revenue", "--from", APRIL, "--to", "2025-04-30"])
    assert answer.exit_code == 0
    assert "base fee" in answer.output


def test_a_carrier_can_be_handed_one_line(console, issued):
    answer = call(
        console,
        ["report", "carrier", "Bickford Heavy Haul", "--from", APRIL, "--to", "2025-04-30"],
    )
    assert answer.output.strip() == "Bickford Heavy Haul  $46.12"


def test_a_carrier_that_was_charged_nothing_still_gets_a_line(console, issued):
    answer = call(console, ["report", "carrier", "Nobody", "--from", APRIL, "--to", "2025-04-30"])
    assert answer.output.strip() == "Nobody  $0.00"


def test_the_road_report_says_what_changes(console, factory):
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
    answer = call(console, ["report", "roads", "--day", APRIL, "--days", "7"])
    assert "closes" in answer.output
    assert "opens" in answer.output


def test_a_quiet_fortnight_says_so(console):
    answer = call(console, ["report", "roads", "--day", APRIL])
    assert answer.output.splitlines()[-1] == "nothing changes"


def test_the_standing_list_prints_one_restriction_to_a_line(console, factory):
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
    answer = call(console, ["report", "standing", "--day", APRIL])
    assert answer.output.strip().startswith("SA148-1  closure")
