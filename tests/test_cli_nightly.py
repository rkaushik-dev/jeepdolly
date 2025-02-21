"""The nightly command, and the difference one commit makes."""

import io
from datetime import date

import pytest
from click.testing import CliRunner

from jeepdolly import logs
from jeepdolly.cli.main import Console, main
from jeepdolly.config import Settings
from jeepdolly.permits.application import ApplicationState
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_seeded_database, an_application

STALE = "2025-04-01"
LATER = "2025-04-20"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def console(factory):
    made = Console(Settings(database_url="sqlite+pysqlite:///:memory:"))
    made._factory = factory
    return made


@pytest.fixture
def stale(factory):
    when = date(2025, 4, 1)
    application = an_application(filed=when, start=when, end=when)
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
    return application


def call(console, arguments):
    return CliRunner().invoke(main, ["--quiet", *arguments], obj=console)


def state_of(factory, reference="25-04471"):
    with UnitOfWork(factory) as work:
        return work.applications.row(reference).state


def test_a_quiet_night_prints_a_heading_and_a_footing(console):
    answer = call(console, ["nightly", "--day", STALE])
    assert answer.exit_code == 0
    assert answer.output.splitlines()[0] == "nightly 2025-04-01"
    assert answer.output.splitlines()[-1].endswith("0 lapsing")


def test_the_sweep_writes_when_it_is_not_a_dry_run(console, factory, stale):
    answer = call(console, ["nightly", "--day", LATER])
    assert "withdrawn  25-04471" in answer.output
    assert state_of(factory) is ApplicationState.WITHDRAWN


def test_a_dry_run_walks_the_same_passes_and_changes_nothing(console, factory, stale):
    answer = call(console, ["nightly", "--day", LATER, "--dry-run"])
    assert "withdrawn  25-04471" in answer.output
    assert state_of(factory) is ApplicationState.DRAFT


def test_a_dry_run_and_a_real_run_print_the_same_thing(console, factory, stale):
    dry = call(console, ["nightly", "--day", LATER, "--dry-run"]).output
    wet = call(console, ["nightly", "--day", LATER]).output
    assert dry == wet


def test_a_night_with_nothing_to_do_is_still_exit_code_zero(console):
    assert call(console, ["nightly", "--day", STALE]).exit_code == 0


def test_the_sweep_leaves_one_line_on_the_log(console, factory, stale):
    stream = io.StringIO()
    logs.configure(stream=stream)
    call(console, ["nightly", "--day", LATER])
    written = stream.getvalue()
    assert "nightly.done day=2025-04-20" in written
    assert "withdrawn=1" in written
    assert "dry_run=False" in written


def test_the_dry_run_says_so_on_the_log(console, factory, stale):
    stream = io.StringIO()
    logs.configure(stream=stream)
    call(console, ["nightly", "--day", LATER, "--dry-run"])
    assert "dry_run=True" in stream.getvalue()


def test_the_expiry_horizon_is_an_argument(console, factory, stale):
    answer = call(console, ["nightly", "--day", STALE, "--within", "30"])
    assert answer.exit_code == 0
    assert "0 expiring" in answer.output


def test_a_day_that_is_not_a_day_is_a_usage_error(console):
    assert call(console, ["nightly", "--day", "last tuesday"]).exit_code == 2
