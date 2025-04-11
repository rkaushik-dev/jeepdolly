"""The console, its three exit codes, and the plain lines it prints."""

import click
import pytest
from click.testing import CliRunner

from jeepdolly.cli.main import Console, main, run, say
from jeepdolly.config import Settings
from jeepdolly.errors import OverLimit


@pytest.fixture
def runner():
    return CliRunner()


@pytest.fixture
def scratch(tmp_path):
    return f"sqlite+pysqlite:///{tmp_path / 'office.sqlite3'}"


def test_the_console_says_which_version_it_is(runner):
    answer = runner.invoke(main, ["--version"])
    assert answer.exit_code == 0
    assert "jeepdolly" in answer.output


def test_the_help_names_what_the_office_does(runner):
    answer = runner.invoke(main, ["--help"])
    assert "Permits for the motor carrier office" in answer.output


def test_building_a_scratch_database_and_reaching_it(runner, scratch):
    built = runner.invoke(main, ["--database", scratch, "--quiet", "build"])
    assert built.exit_code == 0
    assert built.output.splitlines() == [
        "tariff  seeded",
        f"schema  built  {scratch}",
    ]
    checked = runner.invoke(main, ["--database", scratch, "--quiet", "health"])
    assert checked.exit_code == 0
    assert checked.output.startswith("database  up")


def test_a_database_that_is_not_there_is_a_failure_and_not_a_zero(runner, tmp_path):
    missing = f"sqlite+pysqlite:///{tmp_path / 'nowhere' / 'office.sqlite3'}"
    answer = runner.invoke(main, ["--database", missing, "--quiet", "health"])
    assert answer.exit_code != 0


def test_a_command_that_does_not_exist_is_a_usage_error(runner):
    answer = runner.invoke(main, ["--quiet", "nonsense"])
    assert answer.exit_code == 2


def test_a_record_is_one_line_with_two_spaces_between_the_fields(runner):
    @click.command()
    def sample() -> None:
        say("25-P-004471", "issued", "94,000 lb", None)

    answer = runner.invoke(sample, [])
    assert answer.output == "25-P-004471  issued  94,000 lb  \n"


def test_the_console_holds_the_settings_it_was_built_with():
    console = Console(Settings(database_url="sqlite+pysqlite:///:memory:"))
    assert console.settings.in_memory
    assert console.sessions is console.sessions


def test_a_refusal_out_of_a_command_is_exit_code_one(monkeypatch, capsys):
    @main.command()
    def refuser() -> None:
        raise OverLimit("the tandem is 4,000 lb over")

    monkeypatch.setattr("sys.argv", ["jeepdolly", "--quiet", "refuser"])
    assert run() == 1
    assert "over_limit: the tandem is 4,000 lb over" in capsys.readouterr().err


def test_a_usage_mistake_out_of_run_is_exit_code_two(monkeypatch, capsys):
    monkeypatch.setattr("sys.argv", ["jeepdolly", "--quiet", "nonsense"])
    assert run() == 2


def test_a_command_that_works_is_exit_code_zero(monkeypatch, tmp_path):
    where = f"sqlite+pysqlite:///{tmp_path / 'office.sqlite3'}"
    monkeypatch.setattr("sys.argv", ["jeepdolly", "--database", where, "--quiet", "build"])
    assert run() == 0
