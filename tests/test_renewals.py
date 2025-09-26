"""The renewal run, and the batch that is all nine or none of them."""

from datetime import date, timedelta

import pytest

from jeepdolly.api.deps import office_from
from jeepdolly.domain.envelope import Envelope
from jeepdolly.enforcement.standing import Account, EnforcementBook, Owed, due_day
from jeepdolly.errors import NotAllowed
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars
from jeepdolly.permits.annual import issue_annual
from jeepdolly.permits.numbering import Numbering
from jeepdolly.permits.permit import PermitState
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit, renewals
from support import a_seeded_database, an_application

ISSUED = date(2025, 1, 6)
NEAR = date(2025, 12, 15)
FAR = date(2025, 6, 1)


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def work(factory):
    with UnitOfWork(factory) as unit:
        yield unit


def an_annual(work, reference="25-04471", usdot=1204471, on=ISSUED):
    """One annual permit on file, with the application behind it."""
    application = an_application(reference=reference, usdot=usdot)
    work.applications.add(application)
    work.db.flush()
    office = office_from(work, on)
    made = issue_annual(
        numbering=Numbering(work.counter),
        library=office.library,
        schedule=office.schedules.on(on),
        application_reference=reference,
        usdot=usdot,
        carrier_name=office.carriers.get(usdot).name,
        envelope=Envelope.read("11'", "13'6", "70'"),
        gross=Pounds(99000),
        commodity=office.commodities.get(application.commodity),
        on=on,
    )
    work.permits.add(made)
    work.db.flush()
    return made


def desk(work, on=NEAR, enforcement=None):
    """The office off the database, so the counter is the one on file."""
    office = office_from(work, on)
    if enforcement is not None:
        office.enforcement = enforcement
    return office


def test_nothing_is_due_on_a_quiet_day(work):
    assert renewals.due_by(work, FAR) == ()


def test_an_annual_running_out_inside_the_window_is_due(work):
    made = an_annual(work)
    found = renewals.due_by(work, NEAR)
    assert [one.number for one in found] == [str(made.number)]
    assert found[0].carrier == "Bickford Heavy Haul"


def test_an_annual_running_out_beyond_the_window_is_not(work):
    an_annual(work)
    assert renewals.due_by(work, FAR) == ()


def test_the_window_can_be_asked_for_wider(work):
    an_annual(work)
    assert len(renewals.due_by(work, FAR, within=365)) == 1


def test_a_due_permit_says_how_many_days_are_left(work):
    an_annual(work)
    found = renewals.due_by(work, NEAR)[0]
    assert found.days_left(NEAR) == (found.expires - NEAR).days
    assert str(found).startswith("25-A-")


def test_a_voided_annual_is_not_due(work):
    made = an_annual(work)
    made.void(NEAR, "carrier sold the crane")
    work.permits.save_state(made)
    work.db.flush()
    assert renewals.due_by(work, NEAR) == ()


def test_a_single_trip_permit_is_never_on_the_renewal_list(work):
    from support import a_permit

    application = an_application()
    work.applications.add(application)
    work.db.flush()
    work.permits.add(a_permit(application))
    work.db.flush()
    assert renewals.due_by(work, date(2025, 4, 8), within=365) == ()


def test_one_letter_per_carrier_however_many_permits(work):
    an_annual(work, reference="25-04471")
    an_annual(work, reference="25-04472")
    found = renewals.letters(work, NEAR)
    assert len(found) == 1
    assert found[0].count == 2
    assert found[0].usdot == 1204471


def test_two_carriers_get_two_letters(work):
    an_annual(work, reference="25-04471")
    an_annual(work, reference="25-04480", usdot=2299001)
    assert len(renewals.letters(work, NEAR)) == 2


def test_the_letter_reads_the_way_it_is_posted(work):
    an_annual(work)
    printed = renewals.letters(work, NEAR)[0].lines()
    assert printed[0] == "Bickford Heavy Haul (1204471)"
    assert printed[1].startswith("  25-A-")
    assert printed[-1].startswith("  1 permits, first on")


def test_the_letter_says_the_short_form_too(work):
    an_annual(work)
    assert str(renewals.letters(work, NEAR)[0]).startswith("Bickford Heavy Haul: 1 due by")


def test_a_quiet_window_writes_no_letters(work):
    assert renewals.letters(work, FAR) == ()


def test_renewing_one_permit_issues_a_new_one(work):
    made = an_annual(work)
    found = renewals.renew(work, desk(work), 1204471, (str(made.number),), NEAR)
    assert found.count == 1
    assert found.issued[0].number != made.number
    assert found.issued[0].start == NEAR


def test_the_old_permit_is_left_to_run_out_rather_than_superseded(work):
    made = an_annual(work)
    renewals.renew(work, desk(work), 1204471, (str(made.number),), NEAR)
    work.db.flush()
    assert work.permits.get(str(made.number)).state is PermitState.ISSUED


def test_the_new_permit_runs_a_year_from_the_day_it_was_renewed(work):
    made = an_annual(work)
    found = renewals.renew(work, desk(work), 1204471, (str(made.number),), NEAR)
    assert found.issued[0].end == NEAR + timedelta(days=364)


def test_renewing_two_gives_two_numbers_in_order(work):
    first = an_annual(work, reference="25-04471")
    second = an_annual(work, reference="25-04472")
    found = renewals.renew(work, desk(work), 1204471, (str(first.number), str(second.number)), NEAR)
    assert found.count == 2
    numbers = [str(one.number) for one in found.issued]
    assert numbers == sorted(numbers)


def test_renewing_nothing_is_refused(work):
    with pytest.raises(NotAllowed):
        renewals.renew(work, desk(work), 1204471, (), NEAR)


def test_a_permit_belonging_to_somebody_else_stops_the_batch(work):
    mine = an_annual(work, reference="25-04471")
    theirs = an_annual(work, reference="25-04480", usdot=2299001)
    with pytest.raises(NotAllowed) as trouble:
        renewals.renew(work, desk(work), 1204471, (str(mine.number), str(theirs.number)), NEAR)
    assert "does not belong" in str(trouble.value)


def test_a_permit_that_is_not_live_stops_the_batch(work):
    good = an_annual(work, reference="25-04471")
    dead = an_annual(work, reference="25-04472")
    dead.void(NEAR, "crane sold")
    work.permits.save_state(dead)
    work.db.flush()
    with pytest.raises(NotAllowed):
        renewals.renew(work, desk(work), 1204471, (str(good.number), str(dead.number)), NEAR)


def test_a_held_carrier_cannot_renew_anything(work):
    made = an_annual(work)
    book = EnforcementBook()
    written = date(2025, 1, 20)
    book.add(
        Account(
            1204471,
            (
                Owed(
                    number="E-25-0100",
                    written=written,
                    amount=Dollars.of(500),
                    due=due_day(written, 21),
                ),
            ),
        )
    )
    with pytest.raises(NotAllowed) as trouble:
        renewals.renew(work, desk(work, enforcement=book), 1204471, (str(made.number),), NEAR)
    assert "cannot renew" in str(trouble.value)


def test_a_carrier_with_no_paper_cannot_renew(work):
    made = an_annual(work, reference="25-04480", usdot=2299001)
    with pytest.raises(NotAllowed):
        renewals.renew(work, desk(work), 2299001, (str(made.number),), NEAR)


def test_a_renewal_lands_on_the_trail_against_the_carrier(work):
    made = an_annual(work)
    renewals.renew(work, desk(work), 1204471, (str(made.number),), NEAR, who="M Cyr")
    work.db.flush()
    found = audit.for_document(work.db, "carrier", "1204471")
    assert found[-1].action == "permit.renewed"
    assert found[-1].detail == "1 annuals"


def test_the_batch_says_what_it_replaced(work):
    made = an_annual(work)
    found = renewals.renew(work, desk(work), 1204471, (str(made.number),), NEAR)
    assert found.replaced == (str(made.number),)
    assert str(found) == "1204471: 1 renewed"
