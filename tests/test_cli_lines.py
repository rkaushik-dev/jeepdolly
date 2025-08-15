"""The line commands: the van's list and the counter's question."""

import pytest
from click.testing import CliRunner

from jeepdolly.cli import lines as commands
from jeepdolly.cli.main import Console, main
from jeepdolly.config import Settings
from support import a_seeded_database

# The import above registers the group. Without it this file passes in a whole
# run, where some other test module imported it first, and fails on its own.
assert commands.wires.name == "wires"


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


def owner(console, ident="CMP", notice=5):
    return call(
        console,
        [
            "wires",
            "owner",
            ident,
            "Central Maine Power",
            "--telephone",
            "207 555 0100",
            "--notice",
            str(notice),
        ],
    )


def wire(
    console,
    ident="L-1",
    segment="US201-2",
    height="19'",
    lift_to="26'",
    measured="2024-09-12",
):
    arguments = [
        "wires",
        "add",
        ident,
        segment,
        "CMP",
        "--place",
        "Fairfield village",
        "--height",
        height,
    ]
    if lift_to is not None:
        arguments += ["--lift-to", lift_to]
    if measured is not None:
        arguments += ["--measured", measured]
    return call(console, arguments)


def seeded(console):
    owner(console)
    wire(console)
    return console


def test_an_owner_can_be_filed_from_the_command_line(console):
    answer = owner(console)
    assert answer.exit_code == 0
    assert answer.output.strip() == "filed  CMP  Central Maine Power"


def test_the_owners_print_with_their_notice_and_their_wire_count(console):
    seeded(console)
    printed = lines(call(console, ["wires", "owners"]))
    assert printed == ["CMP  Central Maine Power  207 555 0100  5 days  1"]


def test_a_wire_can_be_filed_and_listed(console):
    seeded(console)
    printed = lines(call(console, ["wires", "list"]))
    assert printed == ["L-1  US201-2  Fairfield village  19 ft  26 ft  2024-09-12"]


def test_a_wire_nothing_can_lift_says_fixed(console):
    owner(console)
    wire(console, lift_to=None)
    assert "fixed" in call(console, ["wires", "list"]).output


def test_a_wire_nobody_measured_says_never(console):
    owner(console)
    wire(console, measured=None)
    assert "never" in call(console, ["wires", "list"]).output


def test_a_wire_over_a_road_nobody_has_is_a_refusal(console):
    owner(console)
    assert wire(console, segment="XX-9").exit_code != 0


def test_a_wire_against_an_owner_nobody_has_is_a_refusal(console):
    assert wire(console).exit_code != 0


def test_the_wires_of_one_road_can_be_asked_for(console):
    seeded(console)
    wire(console, ident="L-2", segment="US2-1")
    assert len(lines(call(console, ["wires", "list", "--segment", "US2-1"]))) == 1
    assert len(lines(call(console, ["wires", "list"]))) == 2


def test_the_low_wires_can_be_asked_for_on_their_own(console):
    seeded(console)
    wire(console, ident="L-2", segment="US2-1", height="30'")
    assert len(lines(call(console, ["wires", "list", "--under", "20'"]))) == 1


def test_remeasuring_replaces_what_the_card_said(console):
    seeded(console)
    answer = call(console, ["wires", "measure", "L-1", "21'", "--on", "2025-10-02"])
    assert answer.output.strip() == "measured  L-1  21 ft  2025-10-02"
    assert "21 ft" in call(console, ["wires", "list"]).output


def test_remeasuring_a_wire_nobody_has_is_a_refusal(console):
    assert call(console, ["wires", "measure", "L-9", "21'"]).exit_code != 0


def test_the_survey_list_names_the_low_wires_out_of_cycle(console):
    owner(console)
    wire(console, measured="2015-06-01")
    printed = lines(call(console, ["wires", "survey", "--day", "2025-05-01"]))
    assert printed[0] == "line survey 2025-05-01, wires under 20 ft"
    assert printed[1].startswith("L-1  US201-2 at Fairfield village  19 ft  2015-06-01")


def test_a_wire_walked_inside_the_cycle_is_not_on_the_list(console):
    seeded(console)
    printed = lines(call(console, ["wires", "survey", "--day", "2025-05-01"]))
    assert printed[1] == "nothing low is out of cycle"


def test_a_wire_nobody_ever_measured_is_always_on_the_list(console):
    owner(console)
    wire(console, measured=None)
    assert "never" in call(console, ["wires", "survey", "--day", "2025-05-01"]).output


def test_the_fixed_wires_are_listed_under_their_own_heading(console):
    owner(console)
    wire(console, lift_to=None)
    printed = call(console, ["wires", "survey", "--day", "2025-05-01"]).output
    assert "wires that cannot be lifted" in printed


def test_a_tall_wire_out_of_cycle_is_left_off_the_low_list(console):
    owner(console)
    wire(console, height="30'", lift_to=None, measured="2015-06-01")
    printed = lines(call(console, ["wires", "survey", "--day", "2025-05-01"]))
    assert printed[1] == "nothing low is out of cycle"


def test_the_clearance_question_answers_clear_for_a_short_load(console):
    seeded(console)
    answer = call(
        console,
        [
            "wires",
            "clearance",
            "waterville",
            "US201-1",
            "US201-2",
            "--height",
            "13'6",
            "--travel",
            "2025-04-09",
            "--filed",
            "2025-03-01",
        ],
    )
    assert answer.output.strip() == "clear  13 ft 6 in"


def test_the_clearance_question_names_the_lift_and_the_number(console):
    seeded(console)
    answer = call(
        console,
        [
            "wires",
            "clearance",
            "waterville",
            "US201-1",
            "US201-2",
            "--height",
            "20'",
            "--travel",
            "2025-04-09",
            "--filed",
            "2025-03-01",
        ],
    )
    assert answer.output.strip() == "lift  L-1  Central Maine Power  207 555 0100  2025-04-02"


def test_the_clearance_question_says_what_is_blocked(console):
    owner(console)
    wire(console, lift_to=None)
    answer = call(
        console,
        [
            "wires",
            "clearance",
            "waterville",
            "US201-1",
            "US201-2",
            "--height",
            "20'",
            "--travel",
            "2025-04-09",
            "--filed",
            "2025-03-01",
        ],
    )
    assert answer.output.startswith("blocked  L-1")
