"""What was written at the roadside, counted three ways for three people."""

from datetime import date

import pytest

from jeepdolly.enforcement.penalty import Offence
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.reports.enforcement import by_offence, money, report, watch_list, written
from jeepdolly.services import enforcement
from support import a_seeded_database

FIRST = date(2025, 1, 1)
LAST = date(2025, 12, 31)
WRITTEN = date(2025, 4, 8)
CLERK = "M Cyr"


@pytest.fixture
def work():
    with UnitOfWork(a_seeded_database()) as unit:
        yield unit


def file_one(work, number="E-25-0417", offence=Offence.OVERWEIGHT, on=WRITTEN, usdot=1204471):
    found = enforcement.file_citation(
        work,
        number=number,
        usdot=usdot,
        officer="Sgt A Thibodeau",
        offence=offence,
        place="US201-2 at Fairfield",
        written=on,
        filed=on,
        who=CLERK,
        over=Pounds(12000) if offence.by_weight else None,
    )
    work.db.flush()
    return found


def test_a_quiet_window_counts_nothing(work):
    assert written(work.db, FIRST, LAST) == ()
    assert by_offence(work.db, FIRST, LAST) == {}


def test_the_count_by_offence_is_what_the_legislature_quotes(work):
    file_one(work)
    file_one(work, number="E-25-0500", offence=Offence.NO_PERMIT, on=date(2025, 6, 2))
    file_one(work, number="E-25-0501", offence=Offence.NO_PERMIT, on=date(2025, 6, 3))
    found = by_offence(work.db, FIRST, LAST)
    assert found == {Offence.OVERWEIGHT: 1, Offence.NO_PERMIT: 2}


def test_a_dismissed_citation_is_still_an_enforcement_action(work):
    one = file_one(work)
    enforcement.contest(work, one.number, date(2025, 4, 20), "grounds", CLERK)
    enforcement.dismiss(work, one.number, date(2025, 5, 2), "scale", CLERK)
    work.db.flush()
    assert by_offence(work.db, FIRST, LAST) == {Offence.OVERWEIGHT: 1}


def test_a_citation_outside_the_window_is_not_counted(work):
    file_one(work, on=date(2024, 4, 8))
    assert by_offence(work.db, FIRST, LAST) == {}


def test_the_money_starts_as_all_assessed_and_all_owing(work):
    file_one(work)
    found = money(work.db, FIRST, LAST)
    assert found.assessed == Dollars.of(580)
    assert found.owing == Dollars.of(580)
    assert found.taken == Dollars.of(0)


def test_paying_moves_it_from_owing_to_taken(work):
    file_one(work)
    enforcement.pay(work, "E-25-0417", date(2025, 4, 22), CLERK)
    work.db.flush()
    found = money(work.db, FIRST, LAST)
    assert found.taken == Dollars.of(580)
    assert found.owing == Dollars.of(0)


def test_a_dismissed_citation_is_assessed_and_never_taken(work):
    file_one(work)
    enforcement.contest(work, "E-25-0417", date(2025, 4, 20), "grounds", CLERK)
    enforcement.dismiss(work, "E-25-0417", date(2025, 5, 2), "scale", CLERK)
    work.db.flush()
    found = money(work.db, FIRST, LAST)
    assert found.assessed == Dollars.of(580)
    assert found.taken == Dollars.of(0)
    assert found.owing == Dollars.of(0)
    assert found.written_off == Dollars.of(580)


def test_the_money_prints_the_four_amounts(work):
    file_one(work)
    assert money(work.db, FIRST, LAST).lines() == (
        "assessed  $580.00",
        "taken     $0.00",
        "owing     $580.00",
        "closed unpaid  $0.00",
    )


def test_the_money_says_the_short_form_too(work):
    file_one(work)
    assert str(money(work.db, FIRST, LAST)) == "$580.00 assessed, $0.00 taken, $580.00 out"


def test_one_citation_does_not_put_a_carrier_on_the_letter_list(work):
    file_one(work)
    assert watch_list(work.db, FIRST, LAST) == ()


def test_two_citations_do(work):
    file_one(work)
    file_one(work, number="E-25-0500", on=date(2025, 6, 2))
    found = watch_list(work.db, FIRST, LAST)
    assert [one.usdot for one in found] == [1204471]
    assert found[0].count == 2
    assert found[0].name == "Bickford Heavy Haul"


def test_the_letter_list_carries_what_is_still_out(work):
    file_one(work)
    file_one(work, number="E-25-0500", on=date(2025, 6, 2))
    enforcement.pay(work, "E-25-0417", date(2025, 4, 22), CLERK)
    work.db.flush()
    # The second one is a repeat, so it is priced at twice the flat rate.
    assert watch_list(work.db, FIRST, LAST)[0].owing == Dollars.of(1160)


def test_a_voided_citation_does_not_count_towards_the_letter(work):
    file_one(work)
    file_one(work, number="E-25-0500", on=date(2025, 6, 2))
    enforcement.void(work, "E-25-0500", date(2025, 6, 3), "wrong usdot", CLERK)
    work.db.flush()
    assert watch_list(work.db, FIRST, LAST) == ()


def test_a_dismissed_citation_still_counts_towards_the_letter(work):
    file_one(work)
    file_one(work, number="E-25-0500", on=date(2025, 6, 2))
    enforcement.contest(work, "E-25-0500", date(2025, 6, 10), "grounds", CLERK)
    enforcement.dismiss(work, "E-25-0500", date(2025, 6, 20), "scale", CLERK)
    work.db.flush()
    assert len(watch_list(work.db, FIRST, LAST)) == 1


def test_the_line_the_letter_list_prints(work):
    file_one(work)
    file_one(work, number="E-25-0500", on=date(2025, 6, 2))
    printed = str(watch_list(work.db, FIRST, LAST)[0])
    assert printed == "1204471  Bickford Heavy Haul  2 citations  $1,740.00 out"


def test_the_busiest_carrier_is_at_the_top(work):
    file_one(work)
    file_one(work, number="E-25-0500", on=date(2025, 6, 2))
    file_one(work, number="E-25-0501", on=date(2025, 7, 2))
    file_one(work, number="E-25-0600", on=date(2025, 6, 2), usdot=2299001)
    file_one(work, number="E-25-0601", on=date(2025, 7, 2), usdot=2299001)
    assert [one.usdot for one in watch_list(work.db, FIRST, LAST)] == [1204471, 2299001]


def test_the_line_can_be_drawn_somewhere_else(work):
    file_one(work)
    assert len(watch_list(work.db, FIRST, LAST, at=1)) == 1


def test_a_quiet_quarter_says_so_rather_than_printing_a_heading_alone(work):
    lines = report(work.db, FIRST, LAST)
    assert lines == (
        "enforcement 2025-01-01 to 2025-12-31",
        "nothing was written in that window",
    )


def test_the_whole_report_reads_top_to_bottom(work):
    file_one(work)
    file_one(work, number="E-25-0500", offence=Offence.NO_PERMIT, on=date(2025, 6, 2))
    lines = report(work.db, FIRST, LAST)
    assert lines[0] == "enforcement 2025-01-01 to 2025-12-31"
    assert "no permit  1" in lines
    assert "overweight  1" in lines
    assert "carriers to write to" in lines
    assert lines[-1].startswith("  1204471  Bickford Heavy Haul  2 citations")


def test_a_report_with_nobody_over_the_line_prints_no_letter_heading(work):
    file_one(work)
    assert "carriers to write to" not in report(work.db, FIRST, LAST)
