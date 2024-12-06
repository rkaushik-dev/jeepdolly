"""The structure table, which is edited, and the review tables, which are not."""

from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from jeepdolly.errors import Impossible
from jeepdolly.measure import Pounds
from jeepdolly.persistence.base import create_all, memory_engine, session_factory
from jeepdolly.persistence.models import NodeRow, ReviewMoveRow, ReviewRow, SegmentRow, StructureRow
from jeepdolly.persistence.models.structures import spans_from_text, spans_to_text
from jeepdolly.structures.inventory import StructureKind
from jeepdolly.structures.review import ReviewState
from support import road_log, structure_book

MONDAY = date(2025, 7, 7)


@pytest.fixture
def session():
    engine = memory_engine()
    create_all(engine)
    with session_factory(engine)() as opened:
        network = road_log()
        for node in network.nodes():
            opened.add(NodeRow.of(node))
        for segment in network.segments():
            opened.add(SegmentRow.of(segment))
        opened.commit()
        yield opened


def loaded(session):
    book = structure_book()
    for structure in book.on_segments(tuple(one.ident for one in road_log().segments())):
        session.add(StructureRow.of(structure))
    session.commit()
    return book


def test_the_spans_go_out_as_a_list_and_come_back_as_numbers():
    assert spans_to_text((120, 120, 120)) == "120,120,120"
    assert spans_from_text("120,120,120") == (120, 120, 120)
    assert spans_from_text("") == ()


def test_a_span_list_that_cannot_be_read_is_refused_rather_than_guessed():
    with pytest.raises(Impossible):
        spans_from_text("long,short")


def test_every_structure_goes_in_and_comes_back_the_same(session):
    book = loaded(session)
    session.expunge_all()
    rows = session.scalars(select(StructureRow).order_by(StructureRow.ident)).all()
    assert len(rows) == len(book)
    assert rows[0].to_domain() == book.get("1247")


def test_a_structure_keeps_its_ratings_and_its_posting(session):
    loaded(session)
    row = session.get(StructureRow, "1533")
    assert row.operating_rating == Pounds(60000)
    assert row.posted == Pounds(48000)
    assert row.to_domain().is_posted


def test_a_new_inspection_edits_the_row_rather_than_adding_one(session):
    loaded(session)
    row = session.get(StructureRow, "1247")
    row.operating_rating = Pounds(120000)
    row.inspected = date(2025, 6, 2)
    session.commit()
    assert len(session.scalars(select(StructureRow)).all()) == 6
    assert session.get(StructureRow, "1247").to_domain().operating_rating == Pounds(120000)


def test_a_daily_rating_above_the_permit_rating_is_refused_by_the_database(session):
    loaded(session)
    row = session.get(StructureRow, "1247")
    row.inventory_rating = Pounds(200000)
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_structure_on_a_segment_that_is_not_there_is_refused(session):
    session.add(
        StructureRow(
            ident="9999",
            name="Nowhere",
            segment_ident="NOPE-1",
            kind=StructureKind.BRIDGE,
            spans="40",
        )
    )
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_review_carries_its_moves_in_the_order_they_happened(session):
    review = ReviewRow(
        reference="25-04471",
        opened=MONDAY,
        state=ReviewState.WAITING,
        structures="1247,1533",
    )
    review.moves.append(
        ReviewMoveRow(
            position=0, happened=MONDAY, who="counter", state=ReviewState.WAITING, note="opened"
        )
    )
    review.moves.append(
        ReviewMoveRow(
            position=1,
            happened=MONDAY,
            who="R Pelletier",
            state=ReviewState.WITH_ENGINEER,
            note="picked up",
        )
    )
    session.add(review)
    session.commit()
    session.expunge_all()
    found = session.get(ReviewRow, "25-04471")
    assert [move.position for move in found.moves] == [0, 1]
    assert found.moves[1].who == "R Pelletier"
    assert repr(found) == "<ReviewRow 25-04471 waiting>"


def test_a_move_without_a_review_is_refused(session):
    session.add(
        ReviewMoveRow(
            reference="25-99999",
            position=0,
            happened=MONDAY,
            who="counter",
            state=ReviewState.WAITING,
        )
    )
    with pytest.raises(IntegrityError):
        session.commit()


def test_deleting_a_review_takes_its_moves_with_it(session):
    review = ReviewRow(reference="25-04471", opened=MONDAY, state=ReviewState.WAITING)
    review.moves.append(
        ReviewMoveRow(position=0, happened=MONDAY, who="counter", state=ReviewState.WAITING)
    )
    session.add(review)
    session.commit()
    session.delete(review)
    session.commit()
    assert session.scalars(select(ReviewMoveRow)).all() == []


def test_the_review_state_is_stored_as_its_own_words(session):
    session.add(ReviewRow(reference="25-04471", opened=MONDAY, state=ReviewState.WITH_ENGINEER))
    session.commit()
    read = session.connection().exec_driver_sql("select state from reviews")
    assert read.scalar() == "with engineer"
