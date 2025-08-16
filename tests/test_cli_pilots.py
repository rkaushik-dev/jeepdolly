"""The pilot car commands, and the renewal run that made them necessary."""

from jeepdolly.cli import pilots as commands
from support import call, shown

# The import above registers the group. Without it this file passes in a whole
# run and fails on its own.
assert commands.pilots.name == "pilots"


def test_the_companies_print_with_how_many_cards_each_holds(console):
    answer = call(console, ["pilots", "companies"])
    assert answer.exit_code == 0
    assert shown(answer)[0].startswith("PC-1  Kennebec Pilot Car  Waterville  207 555 0142  3")


def test_a_suspended_company_is_marked_and_can_be_asked_for_alone(console):
    call(console, ["pilots", "companies"])
    assert call(console, ["pilots", "companies", "--suspended"]).output == ""


def test_every_card_prints_with_its_standing_on_the_day(console):
    answer = call(console, ["pilots", "cards", "--day", "2025-06-10"])
    assert answer.exit_code == 0
    printed = shown(answer)
    assert printed[0].endswith("2026-04-30  good  ")
    assert "expired" in printed[2]


def test_the_endorsement_shows_on_the_card_line(console):
    printed = shown(call(console, ["pilots", "cards", "--day", "2025-06-10"]))
    assert printed[1].endswith("height pole")


def test_the_cards_of_one_company_can_be_asked_for(console):
    answer = call(console, ["pilots", "cards", "--company", "PC-1"])
    assert len(shown(answer)) == 3


def test_the_renewal_run_prints_who_to_ring_soonest_first(console):
    answer = call(console, ["pilots", "expiring", "--day", "2026-02-01", "--within", "120"])
    assert answer.exit_code == 0
    printed = shown(answer)
    assert len(printed) == 2
    assert printed[0].startswith("C-1001  R Dubois  Kennebec Pilot Car  207 555 0142  2026-04-30")
    assert printed[0].endswith("  88")


def test_a_window_with_nothing_in_it_says_so(console):
    answer = call(console, ["pilots", "expiring", "--day", "2030-01-01", "--within", "10"])
    assert answer.output.strip() == "nothing runs out in that window"


def test_a_company_can_be_filed_from_the_command_line(console):
    answer = call(console, ["pilots", "company", "PC-2", "Somerset Escort", "--town", "Skowhegan"])
    assert answer.exit_code == 0
    assert answer.output.strip() == "filed  PC-2  Somerset Escort"
    assert len(shown(call(console, ["pilots", "companies"]))) == 2


def test_filing_the_same_company_twice_is_a_refusal_and_not_a_traceback(console):
    call(console, ["pilots", "company", "PC-2", "Somerset Escort"])
    assert call(console, ["pilots", "company", "PC-2", "Somerset Escort"]).exit_code != 0


def test_a_card_can_be_issued_from_the_command_line(console):
    answer = call(
        console,
        [
            "pilots",
            "certify",
            "C-1004",
            "P Ouellette",
            "PC-1",
            "--issued",
            "2025-06-02",
            "--expires",
            "2028-06-01",
        ],
    )
    assert answer.exit_code == 0
    assert answer.output.strip() == "certified  C-1004  P Ouellette  PC-1  2028-06-01"


def test_an_endorsement_can_be_given_more_than_once(console):
    answer = call(
        console,
        [
            "pilots",
            "certify",
            "C-1004",
            "P Ouellette",
            "PC-1",
            "--issued",
            "2025-06-02",
            "--expires",
            "2028-06-01",
            "--endorsement",
            "height pole",
            "--endorsement",
            "police liaison",
        ],
    )
    assert answer.exit_code == 0
    printed = shown(call(console, ["pilots", "cards", "--day", "2025-06-10"]))
    assert printed[-1].endswith("height pole,police liaison")


def test_an_endorsement_nobody_issues_is_a_refusal(console):
    answer = call(
        console,
        [
            "pilots",
            "certify",
            "C-1004",
            "P Ouellette",
            "PC-1",
            "--issued",
            "2025-06-02",
            "--expires",
            "2028-06-01",
            "--endorsement",
            "railroad flagging",
        ],
    )
    assert answer.exit_code != 0


def test_an_operator_can_be_suspended_and_let_back(console):
    stopped = call(console, ["pilots", "suspend", "C-1001"])
    assert stopped.output.strip() == "suspended  C-1001  R Dubois"
    assert "suspended" in call(console, ["pilots", "cards", "--day", "2025-06-10"]).output
    lifted = call(console, ["pilots", "suspend", "C-1001", "--lift"])
    assert lifted.output.strip() == "lifted  C-1001  R Dubois"


def test_who_is_available_on_a_day_prints_with_the_telephone_number(console):
    answer = call(console, ["pilots", "available", "--day", "2025-06-10"])
    assert answer.exit_code == 0
    assert len(shown(answer)) == 2
    assert shown(answer)[0] == "C-1001  R Dubois  Kennebec Pilot Car  207 555 0142"


def test_the_endorsement_narrows_who_is_available(console):
    answer = call(
        console, ["pilots", "available", "--day", "2025-06-10", "--endorsement", "height pole"]
    )
    assert shown(answer) == ["C-1002  M Levesque  Kennebec Pilot Car  207 555 0142"]


def test_a_day_when_nobody_is_available_says_so_rather_than_printing_nothing(console):
    answer = call(console, ["pilots", "available", "--day", "2030-01-01"])
    assert answer.exit_code == 0
    assert answer.output.strip() == "nobody is available that day"
