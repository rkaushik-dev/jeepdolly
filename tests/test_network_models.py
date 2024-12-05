"""The road log tables, their constraints, and the order rows have to go in."""

from datetime import date, time

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from jeepdolly.domain.highway import Jurisdiction, Route
from jeepdolly.domain.network import Node, Segment
from jeepdolly.domain.restriction import RestrictionKind
from jeepdolly.measure import Inches, Miles, Pounds
from jeepdolly.persistence.base import create_all, memory_engine, session_factory
from jeepdolly.persistence.models import NodeRow, RestrictionRow, SegmentRow
from support import road_log


@pytest.fixture
def session():
    engine = memory_engine()
    create_all(engine)
    with session_factory(engine)() as opened:
        yield opened


def loaded(session):
    network = road_log()
    for node in network.nodes():
        session.add(NodeRow.of(node))
    for segment in network.segments():
        session.add(SegmentRow.of(segment))
    session.commit()
    return network


def test_the_whole_road_log_goes_in_and_comes_back_the_same(session):
    network = loaded(session)
    session.expunge_all()
    rows = session.scalars(select(SegmentRow).order_by(SegmentRow.ident)).all()
    assert len(rows) == len(network)
    assert [row.to_domain() for row in rows] == list(network.segments())


def test_a_segment_keeps_its_route_designation_in_pieces(session):
    loaded(session)
    row = session.get(SegmentRow, "SA148-1")
    assert row.route_kind == "SA"
    assert row.route_number == 148
    assert row.to_domain().route == Route.parse("SA 148")


def test_a_segment_keeps_its_length_as_hundredths_and_its_type_on_the_way_out(session):
    loaded(session)
    row = session.get(SegmentRow, "US201-2")
    assert row.length == Miles.of(12, 40)
    read = session.connection().exec_driver_sql(
        "select length_hundredths from segments where ident = 'US201-2'"
    )
    assert read.scalar() == 1240


def test_a_node_and_its_segment_added_together_go_in_the_right_order(session):
    session.add(NodeRow(name="anson", town="Anson"))
    session.add(NodeRow(name="solon", town="Solon"))
    session.add(
        SegmentRow.of(
            Segment(
                "US201-4",
                Route.parse("US 201"),
                "anson",
                "solon",
                Miles.of(9, 10),
                Jurisdiction("Somerset", "Anson", 4),
            )
        )
    )
    session.commit()
    assert session.get(SegmentRow, "US201-4").start.town == "Anson"


def test_a_segment_naming_a_node_that_is_not_there_is_refused(session):
    session.add(NodeRow.of(Node("anson", "Anson")))
    session.commit()
    session.add(
        SegmentRow.of(
            Segment(
                "US201-4",
                Route.parse("US 201"),
                "anson",
                "solon",
                Miles.of(9, 10),
                Jurisdiction("Somerset", "Anson", 4),
            )
        )
    )
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_segment_of_no_length_is_refused_by_the_database(session):
    loaded(session)
    row = session.get(SegmentRow, "US2-1")
    row.length = Miles(0)
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_segment_that_starts_where_it_ends_is_refused_by_the_database(session):
    loaded(session)
    row = session.get(SegmentRow, "US2-1")
    row.end_node = row.start_node
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_district_the_state_does_not_have_is_refused_by_the_database(session):
    loaded(session)
    row = session.get(SegmentRow, "US2-1")
    row.district = 12
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_restriction_hangs_off_the_segment_it_restricts(session):
    loaded(session)
    session.add(
        RestrictionRow(
            ident="R-1",
            segment_ident="US2-1",
            kind=RestrictionKind.CLEARANCE,
            note="railroad bridge",
            height=Inches.of(13, 9),
        )
    )
    session.commit()
    session.expunge_all()
    row = session.get(RestrictionRow, "R-1")
    assert row.height == Inches(165)
    assert row.segment.route_number == 2
    assert repr(row) == "<RestrictionRow R-1 clearance>"


def test_a_restriction_on_a_segment_that_is_not_there_is_refused(session):
    session.add(
        RestrictionRow(
            ident="R-2",
            segment_ident="NOPE-1",
            kind=RestrictionKind.CLOSURE,
            note="bridge out",
        )
    )
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_restriction_that_lifts_before_it_starts_is_refused_by_the_database(session):
    loaded(session)
    session.add(
        RestrictionRow(
            ident="R-3",
            segment_ident="US2-1",
            kind=RestrictionKind.CLOSURE,
            note="culvert",
            effective=date(2025, 8, 1),
            expires=date(2025, 6, 1),
        )
    )
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_curfew_is_stored_as_days_and_two_clock_times(session):
    loaded(session)
    session.add(
        RestrictionRow(
            ident="R-4",
            segment_ident="US201-1",
            kind=RestrictionKind.CURFEW,
            note="rush hour",
            curfew_days="01234",
            curfew_from=time(6, 30),
            curfew_to=time(8, 30),
        )
    )
    session.commit()
    row = session.get(RestrictionRow, "R-4")
    assert row.curfew_days == "01234"
    assert row.curfew_from == time(6, 30)
    assert row.weight is None


def test_a_posted_structure_restriction_carries_a_weight(session):
    loaded(session)
    session.add(
        RestrictionRow(
            ident="R-5",
            segment_ident="SA148-1",
            kind=RestrictionKind.WEIGHT,
            note="Carrabassett bridge",
            weight=Pounds(48000),
        )
    )
    session.commit()
    assert session.get(RestrictionRow, "R-5").weight == Pounds(48000)


def test_a_lifted_restriction_is_deleted_rather_than_kept(session):
    loaded(session)
    session.add(
        RestrictionRow(
            ident="R-6",
            segment_ident="US2-1",
            kind=RestrictionKind.CLOSURE,
            note="culvert",
        )
    )
    session.commit()
    session.delete(session.get(RestrictionRow, "R-6"))
    session.commit()
    assert session.scalars(select(RestrictionRow)).all() == []
    assert session.get(SegmentRow, "US2-1") is not None
