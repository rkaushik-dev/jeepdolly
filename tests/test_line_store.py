"""Line owners and wires, into the rows and back out again."""

from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from jeepdolly.measure import Inches
from jeepdolly.persistence.books import lines_from
from jeepdolly.persistence.models.utilities import LineCrossingRow, LineOwnerRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.utilities.lines import Crossing, Owner
from support import a_seeded_database

SURVEYED = date(2024, 9, 12)


@pytest.fixture
def work():
    with UnitOfWork(a_seeded_database()) as unit:
        yield unit


def an_owner(ident="CMP", notice=5):
    return Owner(ident, "Central Maine Power", "207 555 0100", notice)


def a_crossing(ident="L-1", segment="US201-2", height="18'", lift_to="24'", owner="CMP"):
    return Crossing(
        ident=ident,
        segment=segment,
        place="Fairfield village",
        height=Inches.parse(height),
        owner=owner,
        lift_to=None if lift_to is None else Inches.parse(lift_to),
    )


def seed(work, owners=None, crossings=()):
    for one in owners or [an_owner()]:
        work.db.add(LineOwnerRow.of(one))
    work.db.flush()
    for one in crossings:
        work.db.add(LineCrossingRow.of(one, SURVEYED))
    work.db.flush()


def test_an_owner_goes_in_and_comes_back_the_same_owner(work):
    seed(work)
    assert lines_from(work.db).owner("CMP") == an_owner()


def test_a_crossing_goes_in_and_comes_back_the_same_crossing(work):
    one = a_crossing()
    seed(work, crossings=[one])
    assert lines_from(work.db).get("L-1") == one


def test_a_wire_that_cannot_be_lifted_keeps_its_null(work):
    seed(work, crossings=[a_crossing(lift_to=None)])
    found = lines_from(work.db).get("L-1")
    assert found.lift_to is None
    assert not found.liftable


def test_the_measured_day_is_kept_even_though_nothing_reads_it_yet(work):
    seed(work, crossings=[a_crossing()])
    assert work.db.get(LineCrossingRow, "L-1").measured == SURVEYED


def test_a_wire_over_a_segment_nobody_has_is_refused_by_the_database(work):
    seed(work)
    work.db.add(LineCrossingRow.of(a_crossing(segment="XX-9"), SURVEYED))
    with pytest.raises(IntegrityError):
        work.db.flush()


def test_a_wire_hanging_at_nothing_is_refused_before_it_reaches_a_row(work):
    from jeepdolly.errors import Conflict

    with pytest.raises(Conflict):
        a_crossing(height="0")


def test_the_crossings_of_a_segment_come_back_together(work):
    seed(
        work,
        crossings=[
            a_crossing(ident="L-1"),
            a_crossing(ident="L-2", segment="US2-1", height="17'"),
        ],
    )
    book = lines_from(work.db)
    assert [one.ident for one in book.on_segment("US201-2")] == ["L-1"]
    assert len(book) == 2


def test_two_owners_keep_their_own_wires(work):
    seed(
        work,
        owners=[an_owner(), an_owner(ident="TEL", notice=3)],
        crossings=[a_crossing(), a_crossing(ident="L-2", owner="TEL")],
    )
    book = lines_from(work.db)
    assert book.get("L-2").owner == "TEL"
    assert book.owner("TEL").notice_days == 3


def test_the_owners_come_back_in_ident_order(work):
    seed(work, owners=[an_owner(ident="TEL"), an_owner()])
    assert [one.ident for one in lines_from(work.db).owners()] == ["CMP", "TEL"]


def test_an_empty_database_has_no_wires_and_no_lowest_one(work):
    book = lines_from(work.db)
    assert len(book) == 0
    assert book.lowest() is None


def test_the_owner_row_carries_its_wires_for_a_cascade(work):
    seed(work, crossings=[a_crossing()])
    row = work.db.get(LineOwnerRow, "CMP")
    assert [one.ident for one in row.wires] == ["L-1"]
    work.db.delete(row)
    work.db.flush()
    assert work.db.get(LineCrossingRow, "L-1") is None


def test_the_row_answers_what_it_hangs_at(work):
    seed(work, crossings=[a_crossing()])
    assert work.db.get(LineCrossingRow, "L-1").clearance == Inches.of(18)


def test_the_rows_say_what_they_are_when_something_prints_them(work):
    seed(work, crossings=[a_crossing()])
    assert repr(work.db.get(LineOwnerRow, "CMP")) == "<LineOwnerRow CMP>"
    assert repr(work.db.get(LineCrossingRow, "L-1")) == "<LineCrossingRow L-1>"
