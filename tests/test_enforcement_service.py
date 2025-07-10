"""Filing a citation, and the four steps that used to be done by hand."""

from datetime import date

import pytest

from jeepdolly.enforcement.citation import CitationState
from jeepdolly.enforcement.penalty import Offence
from jeepdolly.errors import Impossible, NotAllowed, NotOnFile
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars
from jeepdolly.permits.numbering import Series
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit, enforcement
from support import a_seeded_database

WRITTEN = date(2025, 4, 8)
FILED = date(2025, 4, 11)
CLERK = "M Cyr"


@pytest.fixture
def work():
    with UnitOfWork(a_seeded_database()) as unit:
        yield unit


def file_one(work, number="E-25-0417", offence=Offence.OVERWEIGHT, written=WRITTEN, **kw):
    fields = {
        "usdot": 1204471,
        "officer": "Sgt A Thibodeau",
        "offence": offence,
        "place": "US201-2 at Fairfield",
        "written": written,
        "filed": max(written, FILED),
        "who": CLERK,
        "over": Pounds(12000) if offence.by_weight else None,
    }
    fields.update(kw)
    found = enforcement.file_citation(work, number, **fields)
    work.db.flush()
    return found


def test_a_citation_is_priced_off_the_schedule_and_put_on_file(work):
    one = file_one(work)
    assert one.amount == Dollars.of(580)
    assert work.citations.get("E-25-0417").state is CitationState.WRITTEN


def test_the_carrier_name_is_read_off_the_register_rather_than_typed(work):
    assert file_one(work).carrier_name == "Bickford Heavy Haul"


def test_a_carrier_nobody_has_registered_is_a_refusal(work):
    with pytest.raises(NotOnFile):
        file_one(work, usdot=9999999)


def test_a_ticket_filed_before_it_was_written_is_refused(work):
    with pytest.raises(Impossible):
        file_one(work, written=date(2025, 5, 1), filed=date(2025, 4, 1))


def test_a_second_ticket_inside_the_year_is_priced_as_a_repeat(work):
    file_one(work, number="E-25-0100", written=date(2025, 1, 20))
    second = file_one(work, number="E-25-0417")
    assert second.assessed.multiplier == 2
    assert second.amount == Dollars.of(1160)


def test_a_dismissed_ticket_does_not_make_the_next_one_a_repeat(work):
    file_one(work, number="E-25-0100", written=date(2025, 1, 20))
    enforcement.contest(work, "E-25-0100", date(2025, 2, 1), "the scale", CLERK)
    enforcement.dismiss(work, "E-25-0100", date(2025, 2, 20), "scale had lapsed", CLERK)
    work.db.flush()
    assert file_one(work, number="E-25-0417").assessed.multiplier == 1


def test_another_carriers_history_does_not_price_this_ones_ticket(work):
    file_one(work, number="E-25-0100", written=date(2025, 1, 20), usdot=2299001)
    assert file_one(work, number="E-25-0417").assessed.multiplier == 1


def test_a_flat_offence_ignores_an_overage_the_form_carried(work):
    one = file_one(work, offence=Offence.OFF_ROUTE, over=Pounds(9000))
    assert one.over is None
    assert one.amount == Dollars.of(250)


def test_filing_writes_the_offence_and_the_money_on_the_trail(work):
    file_one(work)
    found = audit.for_document(work.db, "citation", "E-25-0417")
    assert [one.action for one in found] == ["citation.filed"]
    assert found[0].detail == "overweight $580.00"


def test_contesting_moves_it_and_writes_the_grounds_on_the_trail(work):
    file_one(work)
    one = enforcement.contest(work, "E-25-0417", date(2025, 4, 20), "scale not certified", CLERK)
    work.db.flush()
    assert one.state is CitationState.CONTESTED
    assert audit.for_document(work.db, "citation", "E-25-0417")[-1].detail == (
        "scale not certified"
    )


def test_upholding_leaves_it_owing(work):
    file_one(work)
    enforcement.contest(work, "E-25-0417", date(2025, 4, 20), "grounds", CLERK)
    one = enforcement.uphold(work, "E-25-0417", date(2025, 5, 2), CLERK)
    work.db.flush()
    assert one.state is CitationState.UPHELD
    assert [one.number for one in work.citations.owing()] == ["E-25-0417"]


def test_dismissing_writes_the_reason_onto_the_row_for_the_letter(work):
    file_one(work)
    enforcement.contest(work, "E-25-0417", date(2025, 4, 20), "grounds", CLERK)
    enforcement.dismiss(work, "E-25-0417", date(2025, 5, 2), "scale had lapsed", CLERK)
    work.db.flush()
    assert work.citations.row("E-25-0417").closing_reason == "scale had lapsed"


def test_paying_it_takes_it_off_the_chase_list(work):
    file_one(work)
    enforcement.pay(work, "E-25-0417", date(2025, 4, 22), CLERK, "cheque 4471")
    work.db.flush()
    assert work.citations.owing() == ()


def test_voiding_it_writes_the_reason_and_takes_it_off_the_list(work):
    file_one(work)
    enforcement.void(work, "E-25-0417", date(2025, 4, 14), "wrong usdot", CLERK)
    work.db.flush()
    assert work.citations.row("E-25-0417").state is CitationState.VOID
    assert work.citations.owing() == ()


def test_a_paid_citation_cannot_be_voided_through_the_service_either(work):
    file_one(work)
    enforcement.pay(work, "E-25-0417", date(2025, 4, 22), CLERK)
    work.db.flush()
    with pytest.raises(NotAllowed):
        enforcement.void(work, "E-25-0417", date(2025, 4, 23), "wrong usdot", CLERK)


def test_a_citation_nobody_has_is_a_refusal_at_every_step(work):
    with pytest.raises(NotOnFile):
        enforcement.pay(work, "E-25-9999", FILED, CLERK)


def test_the_numbers_come_off_the_same_counter_in_their_own_series(work):
    first = enforcement.next_number(work, WRITTEN)
    second = enforcement.next_number(work, WRITTEN)
    assert first == "25-E-000001"
    assert second == "25-E-000002"
    assert work.counter.issued(2025, Series.CITATION) == 2


def test_the_citation_series_does_not_eat_the_permit_series(work):
    enforcement.next_number(work, WRITTEN)
    assert str(work.counter.number(WRITTEN, Series.SINGLE)) == "25-P-000001"


def test_what_a_carrier_owes_adds_up_what_is_still_open(work):
    file_one(work, number="E-25-0100", written=date(2025, 1, 20))
    file_one(work, number="E-25-0417")
    assert enforcement.owed_by(work, 1204471) == Dollars.of(1740).cents


def test_a_paid_citation_is_not_owed_any_more(work):
    file_one(work)
    enforcement.pay(work, "E-25-0417", date(2025, 4, 22), CLERK)
    work.db.flush()
    assert enforcement.owed_by(work, 1204471) == 0


def test_a_carrier_with_nothing_against_it_owes_nothing(work):
    assert enforcement.owed_by(work, 2299001) == 0


def test_the_schedule_is_asked_for_by_day_rather_than_reached_for(work):
    assert enforcement.schedule_on(WRITTEN).effective == date(2024, 1, 1)
