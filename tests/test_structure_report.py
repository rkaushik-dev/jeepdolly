"""What actually crossed each structure, which is the record nobody else has."""

from datetime import date

import pytest

from jeepdolly.measure import Pounds
from jeepdolly.permits.numbering import PermitNumber, Series
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.reports.structures import (
    anything_over_a_posting,
    busiest,
    crossings,
    report,
)
from support import a_permit, a_seeded_database, an_application

MONDAY = date(2025, 4, 7)
FRIDAY = date(2025, 4, 11)
ACROSS = ("US201-1", "US201-2", "SA148-2", "SR43-1")


@pytest.fixture
def factory():
    return a_seeded_database()


def written(factory, permits):
    application = an_application()
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
        for sequence, route, gross in permits:
            work.permits.add(
                a_permit(
                    application,
                    number=PermitNumber(2025, Series.SINGLE, sequence),
                    route=route,
                    gross=Pounds(gross),
                )
            )
        work.commit()


def test_nothing_crossed_anything_on_a_quiet_week(factory):
    with UnitOfWork(factory) as work:
        lines = report(work.db, MONDAY, FRIDAY)
    assert lines[-1] == "nothing crossed anything"


def test_a_permit_is_counted_onto_every_structure_on_its_route(factory):
    written(factory, [(1, ACROSS, 94000)])
    with UnitOfWork(factory) as work:
        found = {one.ident: one for one in crossings(work.db, MONDAY, FRIDAY)}
    assert found["1247"].count == 1
    assert found["3310"].count == 1
    assert found["5006"].count == 0


def test_the_heaviest_crossing_is_the_number_the_bridge_people_want(factory):
    written(factory, [(1, ACROSS, 94000), (2, ACROSS, 132000)])
    with UnitOfWork(factory) as work:
        found = {one.ident: one for one in crossings(work.db, MONDAY, FRIDAY)}
    heaviest = found["1247"].heaviest
    assert heaviest is not None
    assert heaviest.gross == Pounds(132000)
    assert heaviest.permit == "25-P-000002"


def test_a_voided_permit_is_not_counted_because_it_did_not_go(factory):
    written(factory, [(1, ACROSS, 94000)])
    with UnitOfWork(factory) as work:
        permit = work.permits.get("25-P-000001")
        permit.void(MONDAY, "load cancelled")
        work.permits.save_state(permit)
        work.commit()
        found = {one.ident: one for one in crossings(work.db, MONDAY, FRIDAY)}
    assert found["1247"].count == 0


def test_a_permit_outside_the_span_is_not_counted(factory):
    written(factory, [(1, ACROSS, 94000)])
    with UnitOfWork(factory) as work:
        found = {one.ident: one for one in crossings(work.db, date(2025, 5, 1), date(2025, 5, 9))}
    assert found["1247"].count == 0


def test_the_busiest_structures_are_where_the_inspection_goes(factory):
    written(factory, [(1, ACROSS, 94000), (2, ("US201-1", "US201-2"), 80000)])
    with UnitOfWork(factory) as work:
        found = busiest(work.db, MONDAY, FRIDAY)
    assert [one.ident for one in found][0] == "1247"
    assert found[0].count == 2
    assert all(one.count for one in found)


def test_anything_permitted_over_a_posting_is_reported_loudly(factory):
    over = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")
    written(factory, [(1, over, 94000)])
    with UnitOfWork(factory) as work:
        found = anything_over_a_posting(work.db, MONDAY, FRIDAY)
    assert [one.ident for one in found] == ["1533"]
    assert found[0].over_posting[0].gross == Pounds(94000)


def test_a_load_inside_the_posting_is_not_reported(factory):
    over = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")
    written(factory, [(1, over, 40000)])
    with UnitOfWork(factory) as work:
        assert anything_over_a_posting(work.db, MONDAY, FRIDAY) == ()


def test_the_over_the_posting_line_shouts(factory):
    over = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")
    written(factory, [(1, over, 94000)])
    with UnitOfWork(factory) as work:
        lines = report(work.db, MONDAY, FRIDAY)
    assert any("OVER THE POSTING" in one for one in lines)
    assert lines[0].startswith("structure crossings 2025-04-07")
