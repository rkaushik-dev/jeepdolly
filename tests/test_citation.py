"""A citation, and what becomes of it."""

from datetime import date

import pytest

from jeepdolly.enforcement.citation import CONTEST_DAYS, Citation, CitationState
from jeepdolly.enforcement.penalty import Offence, assess, standard_schedule
from jeepdolly.errors import Impossible, NotAllowed
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars

SCHEDULE = standard_schedule()
WRITTEN = date(2025, 4, 8)
FILED = date(2025, 4, 11)
LATE = date(2025, 5, 15)


def a_citation(offence=Offence.OVERWEIGHT, over=Pounds(12000), prior=0, **kw):
    fields = {
        "number": "E-25-0417",
        "written": WRITTEN,
        "filed": FILED,
        "officer": "Sgt A Thibodeau",
        "usdot": 1204471,
        "carrier_name": "Bickford Heavy Haul",
        "offence": offence,
        "place": "US201-2 at Fairfield",
        "assessed": assess(SCHEDULE, offence, over, prior),
        "over": over if offence.by_weight else None,
    }
    fields.update(kw)
    return Citation(**fields)


def test_a_citation_starts_written_and_says_when_it_was_filed():
    one = a_citation()
    assert one.state is CitationState.WRITTEN
    assert one.history == [(FILED, CitationState.WRITTEN, "filed")]


def test_a_citation_with_no_number_is_refused():
    with pytest.raises(Impossible):
        a_citation(number="  ")


def test_a_citation_filed_before_it_was_written_is_refused():
    with pytest.raises(Impossible):
        a_citation(filed=date(2025, 4, 1))


def test_an_overweight_citation_that_does_not_say_by_how_much_is_refused():
    with pytest.raises(Impossible):
        Citation(
            number="E-25-0418",
            written=WRITTEN,
            filed=FILED,
            officer="Sgt A Thibodeau",
            usdot=1204471,
            carrier_name="Bickford Heavy Haul",
            offence=Offence.OVERWEIGHT,
            place="US201-2",
            assessed=assess(SCHEDULE, Offence.OVERWEIGHT, Pounds(1000)),
        )


def test_the_amount_comes_off_the_assessment():
    assert a_citation().amount == Dollars.of(580)


def test_the_contest_window_runs_from_the_day_it_was_written():
    one = a_citation()
    assert one.contest_closes() == date(2025, 4, 29)
    assert (one.contest_closes() - WRITTEN).days == CONTEST_DAYS


def test_it_is_contestable_inside_the_window_and_not_after():
    one = a_citation()
    assert one.contestable_on(date(2025, 4, 29))
    assert not one.contestable_on(date(2025, 4, 30))


def test_contesting_it_inside_the_window_moves_it():
    one = a_citation()
    one.contest(date(2025, 4, 20), "the scale was not certified")
    assert one.state is CitationState.CONTESTED
    assert one.history[-1][2] == "the scale was not certified"


def test_contesting_it_after_the_window_is_refused_and_names_the_day():
    one = a_citation()
    with pytest.raises(NotAllowed) as trouble:
        one.contest(LATE, "too late")
    assert "2025-04-29" in str(trouble.value)


def test_contesting_it_without_grounds_is_refused():
    one = a_citation()
    with pytest.raises(Impossible):
        one.contest(date(2025, 4, 20), "   ")


def test_a_contested_citation_can_be_upheld_and_still_wants_paying():
    one = a_citation()
    one.contest(date(2025, 4, 20), "the scale was not certified")
    one.uphold(date(2025, 5, 2))
    assert one.state is CitationState.UPHELD
    assert one.state.owing


def test_a_contested_citation_can_be_dismissed_and_that_is_the_end():
    one = a_citation()
    one.contest(date(2025, 4, 20), "the scale was not certified")
    one.dismiss(date(2025, 5, 2), "scale certificate had lapsed")
    assert one.state.closed
    assert not one.state.owing


def test_a_dismissal_says_why():
    one = a_citation()
    one.contest(date(2025, 4, 20), "grounds")
    with pytest.raises(Impossible):
        one.dismiss(date(2025, 5, 2), "")


def test_nothing_is_upheld_or_dismissed_before_it_is_contested():
    one = a_citation()
    with pytest.raises(NotAllowed):
        one.uphold(date(2025, 5, 2))
    with pytest.raises(NotAllowed):
        one.dismiss(date(2025, 5, 2), "no")


def test_a_written_citation_can_simply_be_paid():
    one = a_citation()
    one.pay(date(2025, 4, 22), "cheque 4471")
    assert one.state is CitationState.PAID
    assert one.history[-1][2] == "cheque 4471"


def test_an_upheld_citation_can_be_paid_after_the_hearing():
    one = a_citation()
    one.contest(date(2025, 4, 20), "grounds")
    one.uphold(date(2025, 5, 2))
    one.pay(date(2025, 5, 9))
    assert one.state is CitationState.PAID


def test_a_dismissed_citation_is_not_owing_and_cannot_be_paid():
    one = a_citation()
    one.contest(date(2025, 4, 20), "grounds")
    one.dismiss(date(2025, 5, 2), "scale")
    with pytest.raises(NotAllowed):
        one.pay(date(2025, 5, 9))


def test_a_citation_written_against_the_wrong_carrier_is_voided():
    one = a_citation()
    one.void(date(2025, 4, 14), "written against the wrong usdot")
    assert one.state is CitationState.VOID
    assert one.state.closed


def test_a_contested_citation_can_still_be_voided_by_the_office():
    one = a_citation()
    one.contest(date(2025, 4, 20), "grounds")
    one.void(date(2025, 4, 21), "wrong day on the ticket")
    assert one.state is CitationState.VOID


def test_a_paid_citation_is_refunded_rather_than_voided():
    one = a_citation()
    one.pay(date(2025, 4, 22))
    with pytest.raises(NotAllowed) as trouble:
        one.void(date(2025, 4, 23), "wrong carrier")
    assert "refund" in str(trouble.value)


def test_a_void_citation_is_not_voided_twice():
    one = a_citation()
    one.void(date(2025, 4, 14), "wrong usdot")
    with pytest.raises(NotAllowed):
        one.void(date(2025, 4, 15), "wrong usdot again")


def test_voiding_says_why():
    one = a_citation()
    with pytest.raises(Impossible):
        one.void(date(2025, 4, 14), " ")


def test_the_citation_reads_out_the_way_it_is_read_over_the_telephone():
    one = a_citation(permit="25-P-004471")
    assert one.lines() == (
        "E-25-0417  2025-04-08  Bickford Heavy Haul (1204471)",
        "  overweight at US201-2 at Fairfield, written by Sgt A Thibodeau",
        "  under permit 25-P-004471",
        "  overweight  $580.00",
        "    over by  12,000 lb",
        "    due  $580.00",
        "  written",
    )


def test_a_citation_with_no_permit_says_nothing_about_one():
    assert not any("permit" in one for one in a_citation().lines())


def test_a_flat_offence_carries_no_overage():
    one = a_citation(offence=Offence.NO_PERMIT, over=None)
    assert one.over is None
    assert one.amount == Dollars.of(500)


def test_a_repeat_offender_pays_the_multiplier_on_the_citation():
    one = a_citation(prior=1)
    assert one.amount == Dollars.of(1160)


def test_the_one_line_form_says_everything_a_list_needs():
    assert str(a_citation()) == "E-25-0417 Bickford Heavy Haul overweight $580.00 written"


def test_two_citations_saying_the_same_thing_are_the_same_citation():
    first = a_citation()
    second = a_citation()
    first.pay(date(2025, 4, 22))
    second.pay(date(2025, 4, 25))
    assert first == second
