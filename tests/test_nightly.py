"""The nightly sweep, and the one thing it is allowed to change."""

from datetime import date, timedelta

import pytest

from jeepdolly.enforcement.penalty import Offence
from jeepdolly.errors import Impossible
from jeepdolly.permits.application import ApplicationState
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit, enforcement, nightly, studies
from support import a_permit, a_seeded_database, an_application

APRIL = date(2025, 4, 7)
LATER = date(2025, 4, 30)


@pytest.fixture
def factory():
    return a_seeded_database()


def filed(factory, reference="25-F-000001", when=APRIL, submitted=False):
    application = an_application(reference=reference, filed=when, start=when, end=when)
    if submitted:
        application.submit(when)
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
    return application


def issued(factory, application):
    with UnitOfWork(factory) as work:
        work.permits.add(a_permit(application))
        work.commit()


def test_a_quiet_night_changes_nothing_and_says_so(factory):
    with UnitOfWork(factory) as work:
        found = nightly.sweep(work.db, APRIL)
    assert found.changed == 0
    assert found.lines()[-1] == "0 expiring  0 withdrawn  0 slow  0 lapsing  0 cards  0 held"


def test_a_permit_about_to_run_out_is_noticed_and_not_touched(factory):
    application = filed(factory)
    issued(factory, application)
    with UnitOfWork(factory) as work:
        found = nightly.sweep(work.db, APRIL, expiring_within=2)
        assert work.permits.row("25-P-004471").state.value == "issued"
    assert found.expiring == ["25-P-004471"]
    assert "expiring  25-P-004471" in found.lines()


def test_a_permit_that_ran_out_yesterday_is_not_expiring_any_more(factory):
    application = filed(factory)
    issued(factory, application)
    with UnitOfWork(factory) as work:
        found = nightly.sweep(work.db, LATER)
    assert found.expiring == []


def test_a_draft_nobody_submitted_is_withdrawn_after_a_fortnight(factory):
    filed(factory, when=date(2025, 4, 1))
    with UnitOfWork(factory) as work:
        found = nightly.sweep(work.db, date(2025, 4, 20))
        work.commit()
        assert work.applications.row("25-F-000001").state is ApplicationState.WITHDRAWN
    assert found.withdrawn == ["25-F-000001"]
    assert found.changed == 1


def test_a_draft_that_is_still_fresh_is_left_alone(factory):
    filed(factory, when=date(2025, 4, 1))
    with UnitOfWork(factory) as work:
        found = nightly.sweep(work.db, date(2025, 4, 10))
        assert work.applications.row("25-F-000001").state is ApplicationState.DRAFT
    assert found.withdrawn == []


def test_a_submitted_application_is_never_withdrawn_by_the_night(factory):
    filed(factory, when=date(2025, 4, 1), submitted=True)
    with UnitOfWork(factory) as work:
        found = nightly.sweep(work.db, date(2025, 5, 1))
    assert found.withdrawn == []


def test_what_the_night_withdrew_lands_on_the_trail_as_the_night(factory):
    filed(factory, when=date(2025, 4, 1))
    with UnitOfWork(factory) as work:
        nightly.sweep(work.db, date(2025, 4, 20))
        work.commit()
        trail = audit.for_document(work.db, "application", "25-F-000001")
    assert [one.actor for one in trail] == ["nightly"]
    assert "untouched since 2025-04-01" in trail[0].detail


def test_a_study_that_has_waited_too_long_is_counted_and_not_moved(factory):
    with UnitOfWork(factory) as work:
        studies.open_study(work.db, "25-F-000002", ("1247",), date(2025, 4, 1))
        work.commit()
        found = nightly.sweep(work.db, date(2025, 4, 30))
        assert studies.row_for(work.db, "25-F-000002").state.value == "waiting"
    assert found.slow_studies == ["25-F-000002"]


def test_a_study_inside_the_fortnight_is_not_on_the_list(factory):
    with UnitOfWork(factory) as work:
        studies.open_study(work.db, "25-F-000002", ("1247",), date(2025, 4, 1))
        work.commit()
        found = nightly.sweep(work.db, date(2025, 4, 10))
    assert found.slow_studies == []


def test_insurance_lapsing_inside_the_week_is_worth_a_telephone_call(factory):
    with UnitOfWork(factory) as work:
        cover = work.carriers.row(1204471).certificates[0]
        cover.expires = date(2025, 4, 10)
        work.commit()
        found = nightly.sweep(work.db, APRIL)
    assert found.lapsing == ["Bickford Heavy Haul"]
    assert "insurance lapsing  Bickford Heavy Haul" in found.lines()


def test_insurance_with_months_left_is_not_on_the_list(factory):
    with UnitOfWork(factory) as work:
        found = nightly.sweep(work.db, APRIL)
    assert found.lapsing == []


def test_a_sweep_that_looks_backwards_is_refused(factory):
    with UnitOfWork(factory) as work, pytest.raises(Impossible):
        nightly.sweep(work.db, APRIL, expiring_within=-1)


def test_the_sweep_prints_a_heading_and_a_footing(factory):
    with UnitOfWork(factory) as work:
        found = nightly.sweep(work.db, APRIL)
    assert found.lines()[0] == "nightly 2025-04-07"
    assert str(found) == "nightly 2025-04-07, 0 changed"
    assert APRIL + timedelta(days=1) == date(2025, 4, 8)


def test_a_pilot_card_running_out_inside_the_month_is_listed(factory):
    with UnitOfWork(factory) as work:
        found = nightly.sweep(work.db, date(2026, 4, 10))
    assert found.cards == ["C-1001 R Dubois", "C-1002 M Levesque"]


def test_a_card_that_ran_out_long_ago_is_not_listed_again(factory):
    with UnitOfWork(factory) as work:
        found = nightly.sweep(work.db, APRIL)
    assert found.cards == []


def test_nobody_is_held_on_a_night_with_no_citations(factory):
    with UnitOfWork(factory) as work:
        assert nightly.sweep(work.db, APRIL).held == []


def test_a_carrier_that_crossed_the_line_today_is_reported_once(factory):
    with UnitOfWork(factory) as work:
        enforcement.file_citation(
            work,
            number="E-25-0417",
            usdot=1204471,
            officer="Sgt A Thibodeau",
            offence=Offence.NO_PERMIT,
            place="US201-2",
            written=date(2025, 4, 8),
            filed=date(2025, 4, 8),
            who="M Cyr",
        )
        work.commit()
        crossed = nightly.sweep(work.db, date(2025, 5, 30))
        after = nightly.sweep(work.db, date(2025, 5, 31))
    assert crossed.held == ["1204471 Bickford Heavy Haul $500.00"]
    assert after.held == []


def test_a_held_carrier_shows_on_the_printed_lines(factory):
    with UnitOfWork(factory) as work:
        enforcement.file_citation(
            work,
            number="E-25-0417",
            usdot=1204471,
            officer="Sgt A Thibodeau",
            offence=Offence.NO_PERMIT,
            place="US201-2",
            written=date(2025, 4, 8),
            filed=date(2025, 4, 8),
            who="M Cyr",
        )
        work.commit()
        found = nightly.sweep(work.db, date(2025, 5, 30))
    assert "held  1204471 Bickford Heavy Haul $500.00" in found.lines()
