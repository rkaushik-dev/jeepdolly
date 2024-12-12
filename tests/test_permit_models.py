"""The permit tables, which copy the words rather than pointing at them."""

from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from jeepdolly.domain.commodity import standard_book as commodities
from jeepdolly.measure import Inches, Miles, Pounds
from jeepdolly.money import Dollars
from jeepdolly.permits.permit import PermitState
from jeepdolly.persistence.base import create_all, memory_engine, session_factory
from jeepdolly.persistence.models import (
    ApplicationRow,
    CarrierRow,
    CommodityRow,
    CounterRow,
    FeeLineRow,
    PermitConditionRow,
    PermitRow,
)
from support import a_carrier_register

ISSUED = date(2025, 4, 7)
START = date(2025, 4, 8)
END = date(2025, 4, 10)


@pytest.fixture
def session():
    engine = memory_engine()
    create_all(engine)
    with session_factory(engine)() as opened:
        book = commodities()
        opened.add(CommodityRow.of(book.get("EXCV")))
        register = a_carrier_register()
        opened.add(CarrierRow.of(register.get(1204471)))
        opened.add(
            ApplicationRow(
                reference="25-04471",
                usdot=1204471,
                commodity="EXCV",
                origin="waterville",
                destination="pittsfield",
                travel_start=START,
                travel_end=END,
                filed=ISSUED,
                applicant_name="Dana Bickford",
                applicant_phone="207 555 0143",
                width=Inches.of(12),
                height=Inches.of(13, 6),
                length=Inches.of(82),
                front_overhang=Inches(0),
                rear_overhang=Inches.of(6),
            )
        )
        opened.commit()
        yield opened


def a_permit_row(number="25-P-004471", **kw):
    fields = {
        "series": "P",
        "application": "25-04471",
        "usdot": 1204471,
        "carrier_name": "Bickford Heavy Haul",
        "issued": ISSUED,
        "valid_from": START,
        "valid_to": END,
        "origin": "waterville",
        "destination": "pittsfield",
        "route": "US201-1,US201-2,SA148-2,SR43-1",
        "narrative": "from Waterville, US 201 to Skowhegan",
        "gross": Pounds(94000),
        "width": Inches.of(12),
        "height": Inches.of(13, 6),
        "length": Inches.of(82),
        "front_overhang": Inches(0),
        "rear_overhang": Inches.of(6),
        "distance": Miles.of(35, 20),
        "schedule": "schedule of 2024-01-01",
    }
    fields.update(kw)
    return PermitRow(number=number, **fields)


def filled(session):
    row = a_permit_row()
    row.conditions.append(
        PermitConditionRow(
            position=0, code="T-01", category="travel", body="Valid from 2025-04-08."
        )
    )
    row.conditions.append(
        PermitConditionRow(position=1, code="R-01", category="route", body="Route as written.")
    )
    row.fees.append(FeeLineRow(position=0, description="ordinary permit", amount=Dollars.of(40)))
    row.fees.append(
        FeeLineRow(position=1, description="34 miles on arterial", amount=Dollars.of(6, 12))
    )
    session.add(row)
    session.commit()
    return row


def test_a_permit_and_its_lines_go_in_together(session):
    filled(session)
    session.expunge_all()
    row = session.get(PermitRow, "25-P-004471")
    assert row.state is PermitState.ISSUED
    assert len(row.conditions) == 2
    assert len(row.fees) == 2
    assert row.days == 3
    assert repr(row) == "<PermitRow 25-P-004471 issued>"


def test_the_conditions_keep_the_words_that_were_printed(session):
    filled(session)
    row = session.get(PermitRow, "25-P-004471")
    assert row.conditions[0].body == "Valid from 2025-04-08."
    assert [one.code for one in row.conditions] == ["T-01", "R-01"]


def test_the_fee_lines_add_back_to_what_was_taken(session):
    filled(session)
    row = session.get(PermitRow, "25-P-004471")
    total = sum(one.amount.cents for one in row.fees)
    assert total == 4612
    assert row.fees[1].description == "34 miles on arterial"


def test_the_route_comes_back_as_idents_in_driven_order(session):
    filled(session)
    row = session.get(PermitRow, "25-P-004471")
    assert row.route_idents == ("US201-1", "US201-2", "SA148-2", "SR43-1")


def test_a_permit_with_no_route_is_refused_by_the_database(session):
    session.add(a_permit_row(number="25-P-000002", route=""))
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_permit_valid_before_it_was_issued_is_refused_by_the_database(session):
    session.add(a_permit_row(number="25-P-000003", valid_from=date(2025, 4, 1)))
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_permit_that_ends_before_it_starts_is_refused_by_the_database(session):
    session.add(a_permit_row(number="25-P-000004", valid_from=END, valid_to=START))
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_permit_for_an_application_nobody_filed_is_refused(session):
    session.add(a_permit_row(number="25-P-000005", application="25-99999"))
    with pytest.raises(IntegrityError):
        session.commit()


def test_a_condition_with_no_words_is_refused_by_the_database(session):
    row = filled(session)
    row.conditions[0].body = ""
    with pytest.raises(IntegrityError):
        session.commit()


def test_voiding_is_an_edit_to_the_row_and_the_lines_stay(session):
    filled(session)
    row = session.get(PermitRow, "25-P-004471")
    row.state = PermitState.VOID
    row.void_reason = "load cancelled"
    session.commit()
    assert session.get(PermitRow, "25-P-004471").state is PermitState.VOID
    assert len(session.scalars(select(PermitConditionRow)).all()) == 2


def test_a_replacement_points_at_what_it_replaced_and_back(session):
    filled(session)
    session.add(a_permit_row(number="25-R-000001", series="R", supersedes="25-P-004471"))
    original = session.get(PermitRow, "25-P-004471")
    original.superseded_by = "25-R-000001"
    original.state = PermitState.SUPERSEDED
    session.commit()
    assert session.get(PermitRow, "25-R-000001").supersedes == "25-P-004471"
    assert session.get(PermitRow, "25-P-004471").superseded_by == "25-R-000001"


def test_the_counter_holds_one_row_for_each_year_and_series(session):
    session.add(CounterRow(year=2025, series="P", next_sequence=4472))
    session.add(CounterRow(year=2025, series="S", next_sequence=13))
    session.commit()
    row = session.get(CounterRow, (2025, "P"))
    assert row.next_sequence == 4472
    assert repr(row) == "<CounterRow 2025 P at 4472>"


def test_a_counter_below_one_is_refused_by_the_database(session):
    session.add(CounterRow(year=2025, series="P", next_sequence=0))
    with pytest.raises(IntegrityError):
        session.commit()
