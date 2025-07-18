"""The citation commands: the chase list and the quarterly letters."""

import pytest
from click.testing import CliRunner

from jeepdolly.cli import citations as commands
from jeepdolly.cli.main import Console, main
from jeepdolly.config import Settings
from support import a_seeded_database

# The import above registers the group. Without it this file passes in a whole
# run and fails on its own.
assert commands.citations.name == "citations"


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


def lines(answer):
    return answer.output.splitlines()


def file_one(console, number="E-25-0417", offence="overweight", written="2025-04-08", over="12000"):
    arguments = [
        "citations",
        "file",
        number,
        "1204471",
        offence,
        "--officer",
        "Sgt A Thibodeau",
        "--place",
        "US201-2 at Fairfield",
        "--written",
        written,
    ]
    if over is not None:
        arguments += ["--over", over]
    return call(console, arguments)


def test_a_ticket_can_be_filed_from_the_command_line(console):
    answer = file_one(console)
    assert answer.exit_code == 0
    assert answer.output.strip() == ("filed  E-25-0417  Bickford Heavy Haul  overweight  $580.00")


def test_an_offence_nobody_prices_is_a_refusal_rather_than_a_traceback(console):
    answer = file_one(console, offence="parked badly", over=None)
    assert answer.exit_code != 0


def test_a_carrier_nobody_has_is_a_refusal(console):
    answer = call(
        console,
        [
            "citations",
            "file",
            "E-25-0417",
            "9999999",
            "no permit",
            "--officer",
            "Sgt A Thibodeau",
            "--place",
            "US201-2",
            "--written",
            "2025-04-08",
        ],
    )
    assert answer.exit_code != 0


def test_the_chase_list_prints_one_citation_to_a_line(console):
    file_one(console)
    answer = call(console, ["citations", "owing"])
    assert answer.exit_code == 0
    assert lines(answer) == ["E-25-0417  2025-04-08  Bickford Heavy Haul  $580.00  written"]


def test_an_empty_chase_list_says_so(console):
    assert call(console, ["citations", "owing"]).output.strip() == "nothing is owed"


def test_paying_it_takes_it_off_the_chase_list(console):
    file_one(console)
    answer = call(console, ["citations", "pay", "E-25-0417", "--on", "2025-04-22"])
    assert answer.output.strip() == "paid  E-25-0417  $580.00"
    assert call(console, ["citations", "owing"]).output.strip() == "nothing is owed"


def test_everything_against_one_carrier_prints_newest_first(console):
    file_one(console, number="E-25-0100", written="2025-01-20")
    file_one(console)
    printed = lines(call(console, ["citations", "carrier", "1204471"]))
    assert printed[0].startswith("E-25-0417")
    assert printed[1].startswith("E-25-0100")


def test_one_citation_reads_out_in_full(console):
    file_one(console)
    printed = lines(call(console, ["citations", "show", "E-25-0417"]))
    assert printed[0] == "E-25-0417  2025-04-08  Bickford Heavy Haul (1204471)"
    assert printed[-1] == "  written"


def test_a_citation_nobody_has_is_a_refusal(console):
    assert call(console, ["citations", "show", "E-25-9999"]).exit_code != 0


def test_the_quarter_report_prints_the_counts_and_the_money(console):
    file_one(console)
    answer = call(console, ["citations", "report", "--from", "2025-01-01", "--to", "2025-12-31"])
    assert answer.exit_code == 0
    printed = lines(answer)
    assert printed[0] == "enforcement 2025-01-01 to 2025-12-31"
    assert "overweight  1" in printed
    assert "assessed  $580.00" in printed


def test_a_quiet_quarter_says_so(console):
    answer = call(console, ["citations", "report", "--from", "2025-01-01", "--to", "2025-12-31"])
    assert lines(answer)[-1] == "nothing was written in that window"


def test_the_letter_list_needs_two_before_it_names_anybody(console):
    file_one(console)
    answer = call(console, ["citations", "watch", "--from", "2025-01-01", "--to", "2025-12-31"])
    assert answer.output.strip() == "nobody is over the line"


def test_two_citations_put_a_carrier_on_the_letter_list(console):
    file_one(console, number="E-25-0100", written="2025-01-20")
    file_one(console)
    answer = call(console, ["citations", "watch", "--from", "2025-01-01", "--to", "2025-12-31"])
    assert lines(answer) == ["1204471  Bickford Heavy Haul  2  $1,740.00"]


def test_the_line_can_be_drawn_at_one(console):
    file_one(console)
    answer = call(
        console,
        ["citations", "watch", "--from", "2025-01-01", "--to", "2025-12-31", "--at", "1"],
    )
    assert lines(answer) == ["1204471  Bickford Heavy Haul  1  $580.00"]


def test_the_second_ticket_from_the_command_line_is_priced_as_a_repeat(console):
    file_one(console, number="E-25-0100", written="2025-01-20")
    answer = file_one(console)
    assert answer.output.strip().endswith("$1,160.00")
