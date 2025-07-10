"""Citations into the rows and back out, and the count that drives a repeat."""

from datetime import date

import pytest

from jeepdolly.enforcement.citation import Citation, CitationState
from jeepdolly.enforcement.penalty import Offence, assess, standard_schedule
from jeepdolly.errors import Conflict, NotOnFile
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars
from jeepdolly.persistence.models.enforcement import CitationRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_permit, a_seeded_database, an_application

SCHEDULE = standard_schedule()
WRITTEN = date(2025, 4, 8)
FILED = date(2025, 4, 11)


@pytest.fixture
def work():
    with UnitOfWork(a_seeded_database()) as unit:
        yield unit


def a_citation(number="E-25-0417", written=WRITTEN, offence=Offence.OVERWEIGHT, prior=0, **kw):
    over = Pounds(12000) if offence.by_weight else None
    fields = {
        "number": number,
        "written": written,
        "filed": written,
        "officer": "Sgt A Thibodeau",
        "usdot": 1204471,
        "carrier_name": "Bickford Heavy Haul",
        "offence": offence,
        "place": "US201-2 at Fairfield",
        "assessed": assess(SCHEDULE, offence, over, prior),
        "over": over,
    }
    fields.update(kw)
    return Citation(**fields)


def filed(work, **kw):
    one = a_citation(**kw)
    work.citations.add(one, SCHEDULE.effective)
    work.db.flush()
    return one


def test_a_citation_goes_in_and_comes_back_the_same_citation(work):
    one = filed(work)
    assert work.citations.get(one.number) == one


def test_the_money_survives_the_round_trip_exactly(work):
    filed(work)
    found = work.citations.get("E-25-0417")
    assert found.amount == Dollars.of(580)
    assert found.assessed.base == Dollars.of(580)
    assert found.assessed.multiplier == 1


def test_the_overage_survives_the_round_trip(work):
    filed(work)
    assert work.citations.get("E-25-0417").over == Pounds(12000)


def test_a_flat_offence_comes_back_with_no_overage(work):
    filed(work, number="E-25-0418", offence=Offence.NO_PERMIT)
    assert work.citations.get("E-25-0418").over is None


def test_the_same_number_is_not_filed_twice(work):
    filed(work)
    with pytest.raises(Conflict):
        work.citations.add(a_citation(), SCHEDULE.effective)


def test_a_citation_nobody_has_is_a_refusal_that_names_it(work):
    with pytest.raises(NotOnFile):
        work.citations.get("E-25-9999")


def test_a_citation_can_name_the_permit_it_was_written_under(work):
    application = an_application()
    work.applications.add(application)
    work.db.flush()
    permit = a_permit(application)
    work.permits.add(permit)
    work.db.flush()
    filed(work, permit=str(permit.number))
    assert work.citations.get("E-25-0417").permit == "25-P-004471"


def test_the_state_is_written_back_with_the_reason_when_it_closes(work):
    one = filed(work)
    one.contest(date(2025, 4, 20), "the scale was not certified")
    one.dismiss(date(2025, 5, 2), "scale certificate had lapsed")
    work.citations.save_state(one, "scale certificate had lapsed")
    work.db.flush()
    row = work.citations.row("E-25-0417")
    assert row.state is CitationState.DISMISSED
    assert row.closing_reason == "scale certificate had lapsed"


def test_a_state_written_back_without_a_reason_leaves_the_old_one(work):
    one = filed(work)
    work.citations.save_state(one, "first reason")
    one.pay(date(2025, 4, 22))
    work.citations.save_state(one)
    assert work.citations.row("E-25-0417").closing_reason == "first reason"


def test_everything_against_one_carrier_comes_back_newest_first(work):
    filed(work, number="E-25-0417", written=date(2025, 4, 8))
    filed(work, number="E-25-0500", written=date(2025, 6, 2))
    found = work.citations.for_carrier(1204471)
    assert [one.number for one in found] == ["E-25-0500", "E-25-0417"]


def test_a_carrier_with_nothing_against_it_comes_back_empty(work):
    assert work.citations.for_carrier(2299001) == ()


def test_the_chase_list_is_everything_still_owing_oldest_first(work):
    filed(work, number="E-25-0500", written=date(2025, 6, 2))
    one = filed(work, number="E-25-0417")
    one.pay(date(2025, 4, 22))
    work.citations.save_state(one)
    work.db.flush()
    assert [one.number for one in work.citations.owing()] == ["E-25-0500"]


def test_a_contested_citation_is_still_on_the_chase_list(work):
    one = filed(work)
    one.contest(date(2025, 4, 20), "grounds")
    work.citations.save_state(one)
    work.db.flush()
    assert [one.number for one in work.citations.owing()] == ["E-25-0417"]


def test_the_citations_of_a_window_come_back_in_order(work):
    filed(work, number="E-25-0500", written=date(2025, 6, 2))
    filed(work, number="E-25-0417", written=date(2025, 4, 8))
    found = work.citations.written_between(date(2025, 1, 1), date(2025, 5, 1))
    assert [one.number for one in found] == ["E-25-0417"]


def test_a_carrier_with_no_history_has_no_priors(work):
    assert work.citations.priors(1204471, WRITTEN) == 0


def test_a_citation_written_earlier_in_the_year_is_a_prior(work):
    filed(work, number="E-25-0100", written=date(2025, 1, 20))
    assert work.citations.priors(1204471, WRITTEN) == 1


def test_a_citation_written_the_same_day_is_not_a_prior_against_itself(work):
    filed(work)
    assert work.citations.priors(1204471, WRITTEN) == 0


def test_a_citation_older_than_the_window_is_not_a_prior(work):
    filed(work, number="E-24-0100", written=date(2024, 1, 20))
    assert work.citations.priors(1204471, WRITTEN) == 0


def test_a_dismissed_citation_is_not_a_prior_offence(work):
    one = filed(work, number="E-25-0100", written=date(2025, 1, 20))
    one.contest(date(2025, 2, 1), "grounds")
    one.dismiss(date(2025, 2, 20), "scale")
    work.citations.save_state(one)
    work.db.flush()
    assert work.citations.priors(1204471, WRITTEN) == 0


def test_a_voided_citation_is_not_a_prior_offence(work):
    one = filed(work, number="E-25-0100", written=date(2025, 1, 20))
    one.void(date(2025, 1, 25), "wrong usdot")
    work.citations.save_state(one)
    work.db.flush()
    assert work.citations.priors(1204471, WRITTEN) == 0


def test_a_contested_citation_that_has_not_been_heard_still_counts(work):
    one = filed(work, number="E-25-0100", written=date(2025, 1, 20))
    one.contest(date(2025, 2, 1), "grounds")
    work.citations.save_state(one)
    work.db.flush()
    assert work.citations.priors(1204471, WRITTEN) == 1


def test_another_carriers_history_is_not_this_carriers(work):
    filed(
        work,
        number="E-25-0100",
        written=date(2025, 1, 20),
        usdot=2299001,
        carrier_name="Aroostook Crane Service",
    )
    assert work.citations.priors(1204471, WRITTEN) == 0
    assert work.citations.priors(2299001, WRITTEN) == 1


def test_the_window_can_be_asked_for_shorter(work):
    filed(work, number="E-25-0100", written=date(2025, 1, 20))
    assert work.citations.priors(1204471, WRITTEN, within_days=30) == 0


def test_the_repository_counts_what_it_holds(work):
    filed(work)
    assert work.citations.count() == 1


def test_the_row_says_what_it_is_when_something_prints_it(work):
    filed(work)
    assert repr(work.db.get(CitationRow, "E-25-0417")) == "<CitationRow E-25-0417>"
