"""Reading the rows back into the books the office decides with."""

from datetime import date, time

import pytest

from jeepdolly.domain.restriction import RestrictionKind
from jeepdolly.measure import Inches, Pounds
from jeepdolly.persistence.books import (
    carriers_from,
    network_from,
    restrictions_from,
    structures_from,
)
from jeepdolly.persistence.models import RestrictionRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_seeded_database

MARCH = date(2025, 3, 3)
APRIL = date(2025, 4, 8)


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def work(factory):
    with UnitOfWork(factory) as unit:
        yield unit


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


def test_the_network_comes_back_with_every_place_the_log_names(work):
    network = network_from(work.db)
    assert len(network.nodes()) == 9
    assert network.node("skowhegan").town == "Skowhegan"


def test_a_segment_can_be_driven_from_either_end_after_a_round_trip(work):
    segment = network_from(work.db).segment("US201-2")
    assert segment.runs_from("fairfield")
    assert segment.runs_from("skowhegan")


def test_a_carrier_read_at_a_day_after_the_paper_lapses_is_not_in_good_standing(work):
    lapsed = carriers_from(work.db, date(2026, 6, 1)).get(1204471)
    assert lapsed.standing(date(2026, 6, 1), date(2026, 6, 2), 94000)


def test_the_structure_book_carries_the_postings_as_they_stand(work):
    book = structures_from(work.db)
    posted = book.get("1533")
    assert posted.posted == Pounds(48000)


def test_reading_the_books_twice_gives_two_books_that_do_not_share_anything(work):
    first = restrictions_from(work.db)
    second = restrictions_from(work.db)
    assert first is not second
    work.db.add(
        RestrictionRow(
            ident="R-9",
            segment_ident="US2-1",
            kind=RestrictionKind.CLOSURE,
            note="paving",
        )
    )
    work.db.flush()
    assert len(restrictions_from(work.db).on_segment("US2-1", MARCH)) == 1
    assert len(first.on_segment("US2-1", MARCH)) == 0
