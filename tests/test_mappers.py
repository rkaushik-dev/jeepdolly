"""Applications and permits out to rows and back, exactly."""

from datetime import date

import pytest
from sqlalchemy import select

from jeepdolly.conditions.library import standard_library
from jeepdolly.domain.commodity import standard_book as commodities
from jeepdolly.errors import Impossible
from jeepdolly.fees.price import Line, Quote
from jeepdolly.measure import Miles, Pounds
from jeepdolly.money import Dollars
from jeepdolly.permits.numbering import PermitNumber, Series
from jeepdolly.permits.permit import Permit, PermitState
from jeepdolly.persistence.base import create_all, memory_engine, session_factory
from jeepdolly.persistence.mappers import (
    application_to_row,
    permit_to_row,
    row_to_application,
    row_to_permit,
)
from jeepdolly.persistence.models import ApplicationRow, CarrierRow, CommodityRow, PermitRow
from support import a_carrier_register, an_application, jeeped_lowboy

BOOK = commodities()
LIBRARY = standard_library()
ISSUED = date(2025, 4, 7)


@pytest.fixture
def session():
    engine = memory_engine()
    create_all(engine)
    with session_factory(engine)() as opened:
        for code in BOOK.codes():
            opened.add(CommodityRow.of(BOOK.get(code)))
        register = a_carrier_register()
        for usdot in (1204471, 2299001):
            opened.add(CarrierRow.of(register.get(usdot)))
        opened.commit()
        yield opened


def a_permit(application, **kw):
    fields = {
        "number": PermitNumber(2025, Series.SINGLE, 4471),
        "application": application.reference,
        "usdot": application.usdot,
        "carrier_name": "Bickford Heavy Haul",
        "issued": ISSUED,
        "start": application.start,
        "end": application.end,
        "origin": application.origin,
        "destination": application.destination,
        "route": ("US201-1", "US201-2", "SA148-2", "SR43-1"),
        "narrative": "from Waterville, US 201 to Skowhegan",
        "gross": application.gross,
        "envelope": application.combination.envelope,
        "distance": Miles.of(35, 20),
        "conditions": (LIBRARY.render("R-01"), LIBRARY.render("G-01")),
        "quote": Quote(
            (
                Line("ordinary permit", Dollars.of(40)),
                Line("34 miles on arterial", Dollars.of(6, 12)),
            ),
            "schedule of 2024-01-01",
        ),
    }
    fields.update(kw)
    return Permit(**fields)


def test_an_application_survives_the_round_trip_exactly(session):
    application = an_application(route=("US201-1", "US201-2"))
    session.add(application_to_row(application))
    session.commit()
    session.expunge_all()
    row = session.get(ApplicationRow, "25-04471")
    assert row_to_application(row, BOOK) == application


def test_a_nine_axle_combination_survives_the_round_trip(session):
    from support import a_combination

    application = an_application(combination=a_combination(jeeped_lowboy()))
    session.add(application_to_row(application))
    session.commit()
    session.expunge_all()
    back = row_to_application(session.get(ApplicationRow, "25-04471"), BOOK)
    assert back.combination.stream.count == 9
    assert back.combination.stream.spacings == application.combination.stream.spacings
    assert back == application


def test_the_pieces_come_back_pointing_at_the_commodity_table(session):
    application = an_application()
    session.add(application_to_row(application))
    session.commit()
    back = row_to_application(session.get(ApplicationRow, "25-04471"), BOOK)
    assert back.pieces[0].commodity.code == "EXCV"
    assert back.pieces[0].serial == "TMD01297"


def test_an_application_with_no_axles_on_file_is_refused_rather_than_guessed(session):
    application = an_application()
    row = application_to_row(application)
    row.axles.clear()
    session.add(row)
    session.commit()
    with pytest.raises(Impossible):
        row_to_application(session.get(ApplicationRow, "25-04471"), BOOK)


def test_the_state_of_an_application_goes_out_and_comes_back(session):
    application = an_application()
    application.submit(ISSUED)
    session.add(application_to_row(application))
    session.commit()
    back = row_to_application(session.get(ApplicationRow, "25-04471"), BOOK)
    assert back.state is application.state


def test_a_permit_survives_the_round_trip_exactly(session):
    application = an_application()
    session.add(application_to_row(application))
    session.commit()
    permit = a_permit(application)
    session.add(permit_to_row(permit))
    session.commit()
    session.expunge_all()
    assert row_to_permit(session.get(PermitRow, "25-P-004471")) == permit


def test_the_conditions_come_back_in_the_order_they_were_printed(session):
    application = an_application()
    session.add(application_to_row(application))
    session.commit()
    session.add(permit_to_row(a_permit(application)))
    session.commit()
    back = row_to_permit(session.get(PermitRow, "25-P-004471"))
    assert [one.code for one in back.conditions] == ["R-01", "G-01"]
    assert back.conditions[0].text.startswith("Route as written")


def test_the_fee_lines_come_back_as_money_and_add_up(session):
    application = an_application()
    session.add(application_to_row(application))
    session.commit()
    session.add(permit_to_row(a_permit(application)))
    session.commit()
    back = row_to_permit(session.get(PermitRow, "25-P-004471"))
    assert back.quote.total == Dollars.of(46, 12)
    assert back.quote.schedule == "schedule of 2024-01-01"


def test_a_voided_permit_keeps_its_reason_through_the_round_trip(session):
    application = an_application()
    session.add(application_to_row(application))
    session.commit()
    permit = a_permit(application)
    permit.void(application.start, "load cancelled by the customer")
    session.add(permit_to_row(permit))
    session.commit()
    back = row_to_permit(session.get(PermitRow, "25-P-004471"))
    assert back.state is PermitState.VOID
    assert back.void_reason == "load cancelled by the customer"


def test_a_replacement_keeps_both_ends_of_the_chain(session):
    application = an_application()
    session.add(application_to_row(application))
    session.commit()
    original = a_permit(application)
    replacement = a_permit(
        application,
        number=PermitNumber(2025, Series.REPLACEMENT, 1),
        supersedes=original.number,
    )
    original.supersede(application.start, replacement.number)
    session.add(permit_to_row(original))
    session.add(permit_to_row(replacement))
    session.commit()
    back = row_to_permit(session.get(PermitRow, "25-P-004471"))
    assert back.superseded_by == replacement.number
    assert row_to_permit(session.get(PermitRow, "25-R-000001")).supersedes == original.number


def test_the_permit_series_is_stored_so_it_can_be_counted(session):
    application = an_application()
    session.add(application_to_row(application))
    session.commit()
    session.add(permit_to_row(a_permit(application)))
    session.commit()
    rows = session.scalars(select(PermitRow).where(PermitRow.series == "P")).all()
    assert len(rows) == 1
    assert rows[0].gross == Pounds(94000)
