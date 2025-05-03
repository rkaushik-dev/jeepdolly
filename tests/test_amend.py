"""Amending a permit, which replaces it rather than editing it."""

from datetime import date

import pytest

from jeepdolly.domain.envelope import Envelope
from jeepdolly.domain.restriction import Restriction, RestrictionKind
from jeepdolly.errors import Impossible, NotAllowed
from jeepdolly.measure import Pounds
from jeepdolly.money import Dollars
from jeepdolly.permits.amend import amend, changes_between
from jeepdolly.permits.assessment import assess
from jeepdolly.permits.issue import issue
from jeepdolly.permits.permit import PermitState
from support import a_combination, an_application, an_office, make_stream

FILED = date(2025, 4, 7)
ACROSS = ("US201-1", "US201-2", "SA148-2", "SR43-1")


def issued(office, **kw):
    application = an_application(route=ACROSS, **kw)
    application.submit(FILED)
    return issue(office, assess(office, application), FILED), application


def revised(reference="25-04471-A", route=ACROSS, **kw):
    return an_application(reference=reference, route=route, **kw)


def test_nothing_changed_is_not_an_amendment():
    office = an_office()
    permit, _ = issued(office)
    with pytest.raises(Impossible):
        amend(office, permit, revised(), FILED)


def test_the_changes_are_worked_out_and_named():
    office = an_office()
    permit, _ = issued(office)
    changes = changes_between(permit, revised(end=date(2025, 4, 14)))
    assert len(changes) == 1
    assert changes[0].what == "dates"
    assert str(changes[0]) == "dates: 2025-04-08 to 2025-04-10 to 2025-04-08 to 2025-04-14"


def test_a_change_of_weight_is_noticed():
    office = an_office()
    permit, _ = issued(office)
    stream = make_stream([12000, 17000, 17000, 18000, 18000, 18000], [200, 54, 180, 52, 52])
    heavier = a_combination(stream)
    changes = changes_between(permit, revised(combination=heavier))
    assert [one.what for one in changes] == ["gross"]
    assert changes[0].after == "100,000 lb"


def test_an_amendment_issues_a_replacement_and_supersedes_the_original():
    office = an_office()
    permit, _ = issued(office)
    found = amend(office, permit, revised(end=date(2025, 4, 14)), FILED)
    assert permit.state is PermitState.SUPERSEDED
    assert permit.superseded_by == found.replacement.number
    assert found.replacement.supersedes == permit.number
    assert str(found.replacement.number).startswith("25-R-")


def test_the_replacement_carries_the_new_dates_and_its_own_conditions():
    office = an_office()
    permit, _ = issued(office)
    found = amend(office, permit, revised(end=date(2025, 4, 14)), FILED)
    assert found.replacement.end == date(2025, 4, 14)
    assert found.replacement.days == 7
    assert found.replacement.conditions


def test_the_amendment_costs_the_flat_fee_when_nothing_got_dearer():
    office = an_office()
    permit, _ = issued(office)
    found = amend(office, permit, revised(end=date(2025, 4, 14)), FILED)
    assert found.quote.total == Dollars.of(25)


def test_an_amendment_that_adds_weight_charges_the_difference_too():
    office = an_office()
    permit, _ = issued(office)
    stream = make_stream([12000, 20000, 20000, 20000, 20000, 20000], [200, 54, 240, 54, 54])
    heavier = a_combination(stream)
    found = amend(office, permit, revised(combination=heavier), FILED)
    assert found.quote.total > Dollars.of(25)
    assert found.quote.line("difference in permit fee") is not None


def test_an_amendment_is_assessed_against_todays_road_and_can_be_refused():
    office = an_office()
    permit, _ = issued(office)
    office.restrictions.add(Restriction("R-1", "SA148-2", RestrictionKind.CLOSURE, "bridge out"))
    with pytest.raises(NotAllowed) as refusal:
        amend(office, permit, revised(end=date(2025, 4, 14)), FILED)
    assert refusal.value.facts["reasons"]
    assert permit.state is PermitState.ISSUED


def test_a_superseded_permit_cannot_be_amended_again():
    office = an_office()
    permit, _ = issued(office)
    amend(office, permit, revised(end=date(2025, 4, 14)), FILED)
    with pytest.raises(NotAllowed):
        amend(office, permit, revised(end=date(2025, 4, 15)), FILED)


def test_an_expired_permit_cannot_be_amended():
    office = an_office()
    permit, _ = issued(office)
    with pytest.raises(NotAllowed) as refusal:
        amend(office, permit, revised(end=date(2025, 4, 14)), date(2025, 5, 1))
    assert "expired" in str(refusal.value)


def test_the_amendment_prints_what_moved():
    office = an_office()
    permit, _ = issued(office)
    found = amend(office, permit, revised(end=date(2025, 4, 14)), FILED)
    assert str(found).endswith("dates changed")
    assert found.assessment.permittable


def test_a_change_of_destination_is_an_amendment_like_any_other():
    office = an_office()
    permit, _ = issued(office)
    onward = ACROSS + ("US2-2",)
    changes = changes_between(permit, revised(destination="newport", route=onward))
    assert [one.what for one in changes] == ["trip", "route"]


def test_a_wider_load_on_the_amendment_picks_up_the_escort_conditions():
    office = an_office()
    permit, _ = issued(office)
    wide = a_combination(envelope=Envelope.read("15'", "13'6", "82'", "0", "6'"))
    found = amend(office, permit, revised(combination=wide), FILED)
    assert "E-01" in [one.code for one in found.replacement.conditions]
    assert found.replacement.gross == Pounds(94000)
