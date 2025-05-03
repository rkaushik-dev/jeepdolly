"""Building the office out of what is on file."""

from datetime import date, time

import pytest

from jeepdolly.api.deps import (
    carriers_from,
    network_from,
    office_from,
    restrictions_from,
    structures_from,
)
from jeepdolly.domain.restriction import RestrictionKind
from jeepdolly.measure import Inches, Pounds
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


def test_the_road_log_is_read_back_into_a_network(work):
    network = network_from(work.session)
    assert len(network) == 12
    assert network.segment("US201-2").start == "fairfield"
    assert [one.ident for one in network.leaving("skowhegan")][0] == "SA148-2"


def test_the_structures_are_read_back_into_a_book(work):
    book = structures_from(work.session)
    assert len(book) == 6
    assert book.get("1533").posted == Pounds(48000)


def test_the_carriers_are_read_back_with_the_paper_in_force_that_day(work):
    register = carriers_from(work.session, MARCH)
    assert register.get(1204471).in_good_standing(MARCH, MARCH)
    assert not register.get(2299001).in_good_standing(MARCH, MARCH)


def test_an_empty_restriction_book_is_what_a_clean_road_looks_like(work):
    assert len(restrictions_from(work.session)) == 0


def test_a_restriction_row_comes_back_as_the_restriction_it_was(work):
    work.session.add(
        RestrictionRow(
            ident="R-1",
            segment_ident="US2-1",
            kind=RestrictionKind.CLEARANCE,
            note="railroad bridge",
            height=Inches.of(13, 9),
        )
    )
    work.commit()
    book = restrictions_from(work.session)
    found = book.on_segment("US2-1", MARCH)
    assert len(found) == 1
    assert found[0].height == Inches(165)
    assert found[0].note == "railroad bridge"


def test_a_curfew_row_comes_back_as_a_curfew(work):
    work.session.add(
        RestrictionRow(
            ident="R-2",
            segment_ident="US201-1",
            kind=RestrictionKind.CURFEW,
            note="rush hour",
            curfew_days="01234",
            curfew_from=time(6, 30),
            curfew_to=time(8, 30),
        )
    )
    work.commit()
    found = restrictions_from(work.session).on_segment("US201-1", MARCH)[0]
    assert found.curfew is not None
    assert found.curfew.covers(date(2025, 3, 3), time(7, 0))
    assert not found.curfew.covers(date(2025, 3, 8), time(7, 0))


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
    assert len(office.library) == 29
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
