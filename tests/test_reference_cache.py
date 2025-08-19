"""Holding the published books between requests, and letting go of them."""

from datetime import date

import pytest

from jeepdolly.domain.network import Node
from jeepdolly.measure import Inches, Pounds
from jeepdolly.persistence.models import (
    ApplicationRow,
    CarrierRow,
    NodeRow,
    RestrictionRow,
    StructureRow,
)
from jeepdolly.persistence.models.utilities import LineCrossingRow, LineOwnerRow
from jeepdolly.persistence.reference import (
    WATCHED,
    Reference,
    ReferenceCache,
    read,
    touched,
    watch,
)
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.structures.inventory import Structure, StructureKind
from jeepdolly.utilities.lines import Crossing, Owner
from support import a_seeded_database, an_application

APRIL = date(2025, 4, 8)
JUNE = date(2025, 6, 10)


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def work(factory):
    with UnitOfWork(factory) as unit:
        yield unit


def a_structure(ident="9001"):
    return Structure(
        ident=ident,
        name="Test culvert",
        segment="US2-1",
        kind=StructureKind.CULVERT,
        spans_feet=(12,),
        built=1970,
        operating_rating=Pounds(90000),
        inventory_rating=Pounds(70000),
    )


def a_wire(ident="L-1"):
    return Crossing("L-1", "US201-2", "Fairfield", Inches.of(19), "CMP", Inches.of(26))


def test_the_slow_read_builds_every_published_book(work):
    found = read(work.db, APRIL)
    assert isinstance(found, Reference)
    assert len(found.network.segments()) == 12
    assert len(found.commodities) == 17
    assert found.day == APRIL


def test_the_first_ask_is_a_miss_and_the_second_is_a_hit(work):
    cache = ReferenceCache()
    first = cache.books(work.db, APRIL)
    second = cache.books(work.db, APRIL)
    assert first is second
    assert (cache.hits, cache.misses) == (1, 1)


def test_a_different_day_is_a_different_entry(work):
    cache = ReferenceCache()
    cache.books(work.db, APRIL)
    cache.books(work.db, JUNE)
    assert cache.days() == (APRIL, JUNE)
    assert len(cache) == 2
    assert cache.misses == 2


def test_clearing_an_empty_cache_is_not_counted(work):
    cache = ReferenceCache()
    cache.clear()
    assert cache.emptied == 0


def test_clearing_a_full_one_is(work):
    cache = ReferenceCache()
    cache.books(work.db, APRIL)
    cache.clear()
    assert cache.emptied == 1
    assert len(cache) == 0


def test_a_new_published_row_is_a_touch(work):
    work.db.add(NodeRow.of(Node("solon", "Solon")))
    assert touched(work.db)


def test_a_new_application_is_not_a_touch(work):
    work.applications.add(an_application())
    assert not touched(work.db)


def test_a_carrier_is_not_a_touch_because_carriers_are_read_fresh(work):
    work.db.add(CarrierRow(usdot=3300111, name="New Haulage", town="Bangor", state="ME"))
    assert not touched(work.db)


def test_a_restriction_is_not_a_touch_because_closures_change_all_day(work):
    from jeepdolly.domain.restriction import RestrictionKind

    work.db.add(
        RestrictionRow(
            ident="R-9", segment_ident="US2-1", kind=RestrictionKind.CLOSURE, note="paving"
        )
    )
    assert not touched(work.db)


def test_a_changed_structure_is_a_touch(work):
    row = work.db.get(StructureRow, "1533")
    row.name = "Carrabassett Stream bridge"
    assert touched(work.db)


def test_a_deleted_published_row_is_a_touch(work):
    work.db.delete(work.db.get(StructureRow, "1533"))
    assert touched(work.db)


def test_the_watched_list_covers_every_book_the_cache_holds():
    names = {one.__name__ for one in WATCHED}
    assert {"NodeRow", "SegmentRow", "StructureRow", "CommodityRow"} <= names
    assert "LineCrossingRow" in names
    assert "PermitRow" not in names


def test_a_watched_write_empties_the_cache_on_flush(factory):
    cache = ReferenceCache()
    watch(factory, cache)
    with UnitOfWork(factory) as work:
        cache.books(work.db, APRIL)
        work.db.add(StructureRow.of(a_structure()))
        work.db.flush()
        assert len(cache) == 0
        assert cache.emptied == 1


def test_an_unwatched_write_leaves_the_cache_alone(factory):
    cache = ReferenceCache()
    watch(factory, cache)
    with UnitOfWork(factory) as work:
        cache.books(work.db, APRIL)
        work.applications.add(an_application())
        work.db.flush()
        assert len(cache) == 1
        assert cache.emptied == 0


def test_the_office_sees_its_own_edit_inside_the_same_transaction(factory):
    cache = ReferenceCache()
    watch(factory, cache)
    with UnitOfWork(factory) as work:
        before = cache.books(work.db, APRIL)
        assert len(before.structures.on_segment("US2-1")) == 1
        work.db.add(StructureRow.of(a_structure()))
        work.db.flush()
        after = cache.books(work.db, APRIL)
        assert len(after.structures.on_segment("US2-1")) == 2


def test_a_wire_written_empties_the_cache_too(factory):
    cache = ReferenceCache()
    watch(factory, cache)
    with UnitOfWork(factory) as work:
        work.db.add(LineOwnerRow.of(Owner("CMP", "Central Maine Power", "207 555 0100", 5)))
        work.db.flush()
        cache.books(work.db, APRIL)
        work.db.add(LineCrossingRow.of(a_wire()))
        work.db.flush()
        assert len(cache) == 0


def test_a_flush_that_fails_leaves_the_cache_alone(factory):
    from sqlalchemy.exc import IntegrityError

    cache = ReferenceCache()
    watch(factory, cache)
    with UnitOfWork(factory) as work:
        cache.books(work.db, APRIL)
        work.db.add(LineCrossingRow.of(a_wire()))
        with pytest.raises(IntegrityError):
            work.db.flush()
        assert len(cache) == 1


def test_two_caches_over_one_factory_are_both_emptied(factory):
    first = ReferenceCache()
    second = ReferenceCache()
    watch(factory, first)
    watch(factory, second)
    with UnitOfWork(factory) as work:
        first.books(work.db, APRIL)
        second.books(work.db, JUNE)
        work.db.add(StructureRow.of(a_structure()))
        work.db.flush()
    assert len(first) == 0
    assert len(second) == 0


def test_an_application_row_reaching_the_session_is_still_not_a_touch(work):
    work.db.add(ApplicationRow(reference="25-99999"))
    assert not touched(work.db)
