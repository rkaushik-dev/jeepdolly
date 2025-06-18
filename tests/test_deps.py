"""Building the office out of what is on file, and choosing what goes in it."""

from datetime import date

import pytest

from jeepdolly.api.deps import office_from
from jeepdolly.domain.restriction import RestrictionKind
from jeepdolly.permits.assessment import assess
from jeepdolly.persistence.models import RestrictionRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_seeded_database, an_application

MARCH = date(2025, 3, 3)
APRIL = date(2025, 4, 8)


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def work(factory):
    with UnitOfWork(factory) as opened:
        yield opened


def test_the_whole_office_can_be_built_and_can_decide_something(work):
    office = office_from(work, APRIL)
    found = assess(office, an_application(), APRIL)
    assert found.permittable
    assert found.itinerary is not None
    assert found.quote is not None


def test_the_office_carries_the_tables_that_are_not_in_the_database_yet(work):
    office = office_from(work, APRIL)
    assert len(office.commodities) == 17
    assert len(office.schedules) == 1
    assert len(office.library) == 33
    assert office.numbering.issued_in(2025) == 0


def test_a_closure_written_today_is_in_the_office_built_next(work):
    work.session.add(
        RestrictionRow(
            ident="R-3", segment_ident="I95-2", kind=RestrictionKind.CLOSURE, note="bridge out"
        )
    )
    work.commit()
    office = office_from(work, APRIL)
    found = assess(office, an_application(), APRIL)
    assert found.itinerary is not None
    assert "I95-2" not in found.itinerary.idents
