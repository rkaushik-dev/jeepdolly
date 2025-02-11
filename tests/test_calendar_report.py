"""The road calendar, which says what changed rather than what is."""

from datetime import date, time

import pytest

from jeepdolly.domain.restriction import RestrictionKind
from jeepdolly.errors import Impossible
from jeepdolly.measure import Inches, Pounds
from jeepdolly.persistence.models import RestrictionRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.reports.calendar import calendar, changes, standing_on
from support import a_seeded_database

JUNE = date(2025, 6, 1)


@pytest.fixture
def factory():
    return a_seeded_database()


def add(factory, **kw):
    fields = {
        "ident": "R-1",
        "segment_ident": "US2-1",
        "kind": RestrictionKind.CLOSURE,
        "note": "culvert work",
    }
    fields.update(kw)
    with UnitOfWork(factory) as work:
        work.db.add(RestrictionRow(**fields))
        work.commit()


def test_a_permanent_restriction_is_standing_on_any_day(factory):
    add(factory, kind=RestrictionKind.CLEARANCE, note="railroad bridge", height=Inches.of(13, 9))
    with UnitOfWork(factory) as work:
        found = standing_on(work.db, JUNE)
    assert len(found) == 1
    assert found[0].permanent
    assert found[0].limit == "13 ft 9 in"
    assert "permanent" in str(found[0])


def test_a_dated_restriction_is_only_standing_inside_its_dates(factory):
    add(factory, effective=date(2025, 6, 10), expires=date(2025, 6, 20))
    with UnitOfWork(factory) as work:
        assert standing_on(work.db, JUNE) == ()
        assert len(standing_on(work.db, date(2025, 6, 15))) == 1
        assert standing_on(work.db, date(2025, 7, 1)) == ()


def test_the_limit_printed_is_the_one_that_kind_carries(factory):
    add(factory, ident="R-2", kind=RestrictionKind.WEIGHT, note="bridge", weight=Pounds(48000))
    add(
        factory,
        ident="R-3",
        segment_ident="US201-1",
        kind=RestrictionKind.CURFEW,
        note="rush hour",
        curfew_days="01234",
        curfew_from=time(6, 30),
        curfew_to=time(8, 30),
    )
    with UnitOfWork(factory) as work:
        found = {one.ident: one.limit for one in standing_on(work.db, JUNE)}
    assert found["R-2"] == "48,000 lb"
    assert found["R-3"] == "06:30 to 08:30"


def test_the_standing_list_is_in_road_log_order(factory):
    add(factory, ident="R-2", segment_ident="US201-1")
    add(factory, ident="R-1", segment_ident="SA148-1")
    with UnitOfWork(factory) as work:
        found = standing_on(work.db, JUNE)
    assert [one.segment for one in found] == ["SA148-1", "US201-1"]


def test_the_calendar_says_the_day_a_closure_starts(factory):
    add(factory, effective=date(2025, 6, 3), expires=date(2025, 6, 5))
    with UnitOfWork(factory) as work:
        days = calendar(work.db, JUNE, days=7)
    started = [one.day for one in days if one.started]
    lifted = [one.day for one in days if one.lifted]
    assert started == [date(2025, 6, 3)]
    assert lifted == [date(2025, 6, 6)]


def test_a_day_with_nothing_happening_is_quiet(factory):
    add(factory, effective=date(2025, 6, 3), expires=date(2025, 6, 5))
    with UnitOfWork(factory) as work:
        days = calendar(work.db, JUNE, days=7)
    assert days[0].quiet
    assert not days[2].quiet
    assert days[2].lines()[0].startswith("2025-06-03  closes  US2-1")


def test_a_permanent_restriction_never_shows_up_as_a_change(factory):
    add(factory, kind=RestrictionKind.CLEARANCE, height=Inches.of(13), note="arch")
    with UnitOfWork(factory) as work:
        days = calendar(work.db, JUNE, days=7)
    assert all(one.quiet for one in days)


def test_the_change_list_says_so_when_nothing_changes(factory):
    with UnitOfWork(factory) as work:
        lines = changes(work.db, JUNE, days=7)
    assert lines[0].startswith("road changes 2025-06-01")
    assert lines[-1] == "nothing changes"


def test_the_change_list_prints_both_ends_of_a_closure(factory):
    add(factory, effective=date(2025, 6, 3), expires=date(2025, 6, 5))
    with UnitOfWork(factory) as work:
        lines = changes(work.db, JUNE, days=7)
    assert any("closes" in one for one in lines)
    assert any("opens" in one for one in lines)


def test_a_calendar_of_no_days_is_refused(factory):
    with UnitOfWork(factory) as work, pytest.raises(Impossible):
        calendar(work.db, JUNE, days=0)
