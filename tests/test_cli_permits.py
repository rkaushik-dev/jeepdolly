"""The permit commands, over a database with a permit in it."""

import pytest

from jeepdolly.cli import permits as commands
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit
from support import a_permit, an_application, call

FILED = "2025-04-07"
START = "2025-04-08"


@pytest.fixture
def issued(factory):
    application = an_application()
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
        work.permits.add(a_permit(application))
        work.commit()
    return "25-P-004471"


def test_the_group_is_registered_under_its_own_name(console):
    assert commands.permits.name == "permits"
    assert "permits" in call(console, ["--help"]).output


def test_what_is_on_the_road_is_one_permit_to_a_line(console, issued):
    answer = call(console, ["permits", "on-the-road", "--day", START])
    assert answer.exit_code == 0
    assert answer.output.splitlines() == [
        "25-P-004471  Bickford Heavy Haul  94,000 lb  waterville  pittsfield"
    ]


def test_a_day_with_nothing_out_prints_nothing_at_all(console, issued):
    answer = call(console, ["permits", "on-the-road", "--day", "2025-05-01"])
    assert answer.output == ""
    assert answer.exit_code == 0


def test_the_expiring_list_looks_the_number_of_days_it_was_asked_to(console, issued):
    soon = call(console, ["permits", "expiring", "--day", START, "--within", "3"])
    assert "25-P-004471" in soon.output
    later = call(console, ["permits", "expiring", "--day", "2025-04-20", "--within", "1"])
    assert later.output == ""


def test_showing_a_permit_prints_it_in_the_order_it_is_read_out(console, issued):
    answer = call(console, ["permits", "show", "25-P-004471"])
    lines = answer.output.splitlines()
    assert lines[0].startswith("25-P-004471  Bickford Heavy Haul")
    assert "94,000 lb gross" in lines[3]
    assert any(one.startswith("R-01") for one in lines)
    assert lines[-1] == "total  $46.12"


def test_a_number_nobody_issued_is_a_refusal_and_not_a_traceback(console):
    answer = call(console, ["permits", "show", "25-P-999999"])
    assert answer.exit_code == 1
    assert answer.exception is not None


def test_voiding_from_the_console_takes_it_off_the_road(console, issued, factory):
    answer = call(
        console,
        ["permits", "void", "25-P-004471", "--reason", "load cancelled", "--by", "D Bickford"],
    )
    assert answer.exit_code == 0
    assert answer.output.strip() == "25-P-004471  void  load cancelled"
    assert call(console, ["permits", "on-the-road", "--day", START]).output == ""


def test_voiding_from_the_console_lands_on_the_trail(console, issued, factory):
    call(
        console,
        ["permits", "void", "25-P-004471", "--reason", "load cancelled", "--by", "D Bickford"],
    )
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.db, "permit", "25-P-004471")
    assert [one.actor for one in found] == ["D Bickford"]
    assert found[0].detail == "load cancelled"


def test_voiding_without_saying_who_is_a_usage_error(console, issued):
    answer = call(console, ["permits", "void", "25-P-004471", "--reason", "no"])
    assert answer.exit_code == 2


def test_what_a_carrier_holds_is_asked_by_number(console, issued):
    answer = call(console, ["permits", "carrier", "1204471"])
    assert "25-P-004471" in answer.output
    assert call(console, ["permits", "carrier", "2299001"]).output == ""
