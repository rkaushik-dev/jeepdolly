"""The road log commands, including the dry run everybody runs first."""

from pathlib import Path

import pytest
from click.testing import CliRunner

from jeepdolly.cli.main import Console, main
from jeepdolly.config import Settings
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_seeded_database

NEW_LINE = "US201-4|US 201|anson|solon|910|Somerset|Anson|4|0"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def console(factory):
    made = Console(Settings(database_url="sqlite+pysqlite:///:memory:"))
    made._factory = factory
    return made


def call(console, arguments):
    return CliRunner().invoke(main, ["--quiet", *arguments], obj=console)


def written(tmp_path, lines):
    path = tmp_path / "roadlog.txt"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return str(path)


def test_the_log_prints_one_segment_to_a_line(console):
    answer = call(console, ["roads", "export"])
    assert answer.exit_code == 0
    assert len(answer.output.splitlines()) == 12
    assert answer.output.splitlines()[0].startswith("I95-1|")


def test_the_log_can_be_written_to_a_file(console, tmp_path):
    path = str(tmp_path / "out.txt")
    answer = call(console, ["roads", "export", "--to", path])
    assert answer.output.strip() == f"wrote  12  {path}"
    assert len(Path(path).read_text(encoding="utf-8").splitlines()) == 12


def test_a_dry_run_reads_the_file_and_changes_nothing(console, factory, tmp_path):
    path = written(tmp_path, [NEW_LINE])
    answer = call(console, ["roads", "load", path, "--dry-run"])
    assert "1 added" in answer.output
    with UnitOfWork(factory) as work:
        assert len(work.network.segments()) == 12


def test_a_real_load_puts_the_segment_on(console, factory, tmp_path):
    path = written(tmp_path, [NEW_LINE])
    answer = call(console, ["roads", "load", path])
    assert "1 added" in answer.output
    with UnitOfWork(factory) as work:
        assert len(work.network.segments()) == 13


def test_a_bad_line_is_reported_by_number_and_the_rest_goes_in(console, factory, tmp_path):
    path = written(tmp_path, ["nonsense", NEW_LINE])
    answer = call(console, ["roads", "load", path])
    assert "1 added" in answer.output
    assert "line 1" in answer.output
    with UnitOfWork(factory) as work:
        assert len(work.network.segments()) == 13


def test_a_file_that_is_not_there_is_a_usage_error(console):
    assert call(console, ["roads", "load", "nowhere.txt"]).exit_code == 2


def test_the_log_round_trips_through_a_file(console, factory, tmp_path):
    path = str(tmp_path / "out.txt")
    call(console, ["roads", "export", "--to", path])
    answer = call(console, ["roads", "load", path])
    assert "12 updated" in answer.output
    assert "0 refused" in answer.output


def test_a_segment_prints_with_what_is_on_it(console):
    answer = call(console, ["roads", "segment", "US201-2"])
    lines = answer.output.splitlines()
    assert lines[0].startswith("US201-2  US 201  fairfield  skowhegan  12.40 mi")
    assert lines[1].strip().startswith("structure  1247  Kennebec River")


def test_a_segment_nobody_has_is_a_refusal(console):
    answer = call(console, ["roads", "segment", "NOPE-1"])
    assert answer.exit_code == 1


def test_a_district_prints_what_a_thaw_order_would_reach(console):
    answer = call(console, ["roads", "district", "4"])
    assert len(answer.output.splitlines()) == 7
    assert call(console, ["roads", "district", "9"]).output == ""
