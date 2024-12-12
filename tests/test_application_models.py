"""The application tables, and the children that hold the combination."""

from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from jeepdolly.domain.axle import AxleKind
from jeepdolly.domain.combination import UnitKind
from jeepdolly.domain.commodity import standard_book as commodities
from jeepdolly.measure import Inches, Pounds
from jeepdolly.permits.application import ApplicationState
from jeepdolly.persistence.base import create_all, memory_engine, session_factory
from jeepdolly.persistence.models import (
    ApplicationAxleRow,
    ApplicationPieceRow,
    ApplicationRow,
    ApplicationUnitRow,
    CarrierRow,
    CommodityRow,
)
from support import a_carrier_register

FILED = date(2025, 4, 7)
START = date(2025, 4, 8)
END = date(2025, 4, 10)


@pytest.fixture
def session():
    engine = memory_engine()
    create_all(engine)
    with session_factory(engine)() as opened:
        book = commodities()
        for code in book.codes():
            opened.add(CommodityRow.of(book.get(code)))
        register = a_carrier_register()
        for usdot in (1204471, 2299001):
            opened.add(CarrierRow.of(register.get(usdot)))
        opened.commit()
        yield opened


def an_application_row(reference="25-04471", **kw):
    fields = {
        "usdot": 1204471,
        "commodity": "EXCV",
        "origin": "waterville",
        "destination": "pittsfield",
        "travel_start": START,
        "travel_end": END,
        "filed": FILED,
        "applicant_name": "Dana Bickford",
        "applicant_phone": "207 555 0143",
        "width": Inches.of(12),
        "height": Inches.of(13, 6),
        "length": Inches.of(82),
        "front_overhang": Inches(0),
        "rear_overhang": Inches.of(6),
    }
    fields.update(kw)
    return ApplicationRow(reference=reference, **fields)


def filled(session):
    row = an_application_row(route="US201-1,US201-2,SA148-2,SR43-1")
    for position, (kind, weight, spacing) in enumerate(
        [
            (AxleKind.STEER, 12000, None),
            (AxleKind.DRIVE, 17000, 200),
            (AxleKind.DRIVE, 17000, 54),
            (AxleKind.TRAILER, 16000, 180),
            (AxleKind.TRAILER, 16000, 52),
            (AxleKind.TRAILER, 16000, 52),
        ]
    ):
        row.axles.append(
            ApplicationAxleRow(
                position=position,
                kind=kind,
                weight=Pounds(weight),
                tires=2 if kind is AxleKind.STEER else 4,
                tire_width=Inches(16 if kind is AxleKind.STEER else 11),
                spacing_before=None if spacing is None else Inches(spacing),
            )
        )
    row.units.append(
        ApplicationUnitRow(
            position=0, kind=UnitKind.TRACTOR, axles=3, plate="ME 4471", tare=Pounds(19000)
        )
    )
    row.units.append(
        ApplicationUnitRow(
            position=1, kind=UnitKind.LOWBOY, axles=3, plate="ME 7712", tare=Pounds(24000)
        )
    )
    row.pieces.append(
        ApplicationPieceRow(
            position=0, commodity="EXCV", description="Cat 336 excavator", serial="TMD01297"
        )
    )
    session.add(row)
    session.commit()
    return row


def test_an_application_and_its_children_go_in_together(session):
    filled(session)
    session.expunge_all()
    row = session.get(ApplicationRow, "25-04471")
    assert row.axle_count == 6
    assert len(row.units) == 2
    assert len(row.pieces) == 1
    assert row.state is ApplicationState.DRAFT
    assert repr(row) == "<ApplicationRow 25-04471 draft>"


def test_the_axles_come_back_in_road_order_whatever_the_database_did(session):
    filled(session)
    session.expunge_all()
    row = session.get(ApplicationRow, "25-04471")
    assert [one.position for one in row.axles] == [0, 1, 2, 3, 4, 5]
    assert [one.weight.value for one in row.axles][:3] == [12000, 17000, 17000]


def test_the_first_axle_is_the_only_one_with_no_spacing_in_front_of_it(session):
    filled(session)
    row = session.get(ApplicationRow, "25-04471")
    assert row.axles[0].spacing_before is None
    assert row.axles[1].spacing_before == Inches(200)
    assert all(one.spacing_before is not None for one in row.axles[1:])


def test_the_envelope_is_stored_as_whole_inches(session):
    filled(session)
    read = session.connection().exec_driver_sql("select width, height from applications")
    assert read.one() == (144, 162)


def test_the_route_is_read_back_as_a_list_of_idents(session):
    filled(session)
    row = session.get(ApplicationRow, "25-04471")
    assert row.route_idents == ("US201-1", "US201-2", "SA148-2", "SR43-1")
    assert an_application_row(reference="25-1").route_idents == ()


def test_a_span_that_ends_before_it_starts_is_refused_by_the_database(session):
    session.add(an_application_row(reference="25-2", travel_start=END, travel_end=START))
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_trip_to_where_it_started_is_refused_by_the_database(session):
    session.add(an_application_row(reference="25-3", destination="waterville"))
    with pytest.raises(IntegrityError):
        session.commit()


def test_an_application_for_a_carrier_nobody_registered_is_refused(session):
    session.add(an_application_row(reference="25-4", usdot=9999999))
    with pytest.raises(IntegrityError):
        session.commit()


def test_an_application_naming_a_commodity_nobody_filed_is_refused(session):
    session.add(an_application_row(reference="25-5", commodity="ZZZZ"))
    with pytest.raises(IntegrityError):
        session.commit()


def test_an_axle_on_an_odd_count_of_tires_is_refused_by_the_database(session):
    row = filled(session)
    row.axles[0].tires = 3
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_unit_with_no_axles_is_refused_by_the_database(session):
    row = filled(session)
    row.units[0].axles = 0
    with pytest.raises(IntegrityError):
        session.commit()


def test_deleting_an_application_takes_its_children_with_it(session):
    filled(session)
    session.delete(session.get(ApplicationRow, "25-04471"))
    session.commit()
    assert session.scalars(select(ApplicationAxleRow)).all() == []
    assert session.scalars(select(ApplicationUnitRow)).all() == []
    assert session.scalars(select(ApplicationPieceRow)).all() == []


def test_the_state_moves_and_the_row_is_edited_in_place(session):
    filled(session)
    row = session.get(ApplicationRow, "25-04471")
    row.state = ApplicationState.SUBMITTED
    session.commit()
    read = session.connection().exec_driver_sql("select state from applications")
    assert read.scalar() == "submitted"
