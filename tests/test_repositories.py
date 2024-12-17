"""The repositories, and the questions each of them is named after."""

from datetime import date

import pytest

from jeepdolly.domain.commodity import standard_book as commodities
from jeepdolly.domain.restriction import RestrictionKind
from jeepdolly.errors import NotOnFile
from jeepdolly.measure import Inches
from jeepdolly.permits.application import ApplicationState
from jeepdolly.permits.numbering import PermitNumber, Series
from jeepdolly.permits.permit import PermitState
from jeepdolly.persistence.base import create_all, memory_engine, session_factory
from jeepdolly.persistence.models import (
    CarrierRow,
    CommodityRow,
    NodeRow,
    RestrictionRow,
    SegmentRow,
    StructureRow,
)
from jeepdolly.persistence.repositories import (
    ApplicationRepository,
    CarrierRepository,
    CommodityRepository,
    NetworkRepository,
    PermitRepository,
)
from support import (
    a_carrier_register,
    a_permit,
    an_application,
    road_log,
    structure_book,
)

BOOK = commodities()
FILED = date(2025, 4, 7)
START = date(2025, 4, 8)


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
        network = road_log()
        for node in network.nodes():
            opened.add(NodeRow.of(node))
        for segment in network.segments():
            opened.add(SegmentRow.of(segment))
        opened.commit()
        for structure in structure_book().on_segments(
            tuple(one.ident for one in network.segments())
        ):
            opened.add(StructureRow.of(structure))
        opened.commit()
        yield opened


def test_a_carrier_is_found_by_number_and_by_part_of_its_name(session):
    carriers = CarrierRepository(session)
    assert carriers.get(1204471, START).name == "Bickford Heavy Haul"
    assert [one.usdot for one in carriers.search("crane")] == [2299001]
    assert carriers.search("  ") == ()
    assert carriers.count() == 2


def test_a_carrier_nobody_registered_says_so(session):
    with pytest.raises(NotOnFile) as refusal:
        CarrierRepository(session).row(9999999)
    assert refusal.value.facts["usdot"] == 9999999


def test_the_commodity_book_comes_back_whole_and_skips_the_retired(session):
    book = CommodityRepository(session).book()
    assert len(book) == len(BOOK)
    CommodityRepository(session).retire("CHIP", START)
    session.commit()
    assert len(CommodityRepository(session).book(on=START)) == len(BOOK) - 1
    assert len(CommodityRepository(session).book(on=date(2025, 1, 1))) == len(BOOK)


def test_retiring_something_that_is_not_there_says_so(session):
    with pytest.raises(NotOnFile):
        CommodityRepository(session).retire("ZZZZ", START)


def test_an_application_is_written_and_read_back_through_the_repository(session):
    applications = ApplicationRepository(session, BOOK)
    applications.add(an_application())
    session.commit()
    assert applications.get("25-04471").origin == "waterville"
    assert applications.row("25-04471").state is ApplicationState.DRAFT


def test_the_waiting_queue_is_what_the_counter_works_through(session):
    applications = ApplicationRepository(session, BOOK)
    first = an_application(reference="25-00001")
    second = an_application(reference="25-00002")
    first.submit(FILED)
    applications.add(first)
    applications.add(second)
    session.commit()
    assert [one.reference for one in applications.waiting()] == ["25-00001"]
    assert len(applications.in_state(ApplicationState.DRAFT)) == 1


def test_an_application_state_is_saved_back_onto_the_row(session):
    applications = ApplicationRepository(session, BOOK)
    application = an_application()
    applications.add(application)
    session.commit()
    application.submit(FILED)
    applications.save_state(application)
    session.commit()
    assert applications.row("25-04471").state is ApplicationState.SUBMITTED


def test_the_applications_of_a_carrier_come_back_newest_first(session):
    applications = ApplicationRepository(session, BOOK)
    applications.add(an_application(reference="25-00001", filed=date(2025, 4, 1)))
    applications.add(an_application(reference="25-00002"))
    session.commit()
    assert [one.reference for one in applications.for_carrier(1204471)][0] == "25-00002"
    assert applications.for_carrier(2299001) == ()


def test_the_applications_of_a_week_are_the_weeks_work(session):
    applications = ApplicationRepository(session, BOOK)
    applications.add(an_application(reference="25-00001", filed=date(2025, 4, 1)))
    applications.add(an_application(reference="25-00002"))
    session.commit()
    found = applications.filed_between(date(2025, 4, 6), date(2025, 4, 12))
    assert [one.reference for one in found] == ["25-00002"]


def test_a_permit_is_written_and_read_back_through_the_repository(session):
    applications = ApplicationRepository(session, BOOK)
    application = an_application()
    applications.add(application)
    session.commit()
    permits = PermitRepository(session)
    permits.add(a_permit(application))
    session.commit()
    assert permits.get("25-P-004471").carrier_name == "Bickford Heavy Haul"
    with pytest.raises(NotOnFile):
        permits.get("25-P-000009")


def test_what_is_on_the_road_and_what_is_expiring_are_different_questions(session):
    applications = ApplicationRepository(session, BOOK)
    application = an_application()
    applications.add(application)
    session.commit()
    permits = PermitRepository(session)
    permits.add(a_permit(application))
    permits.add(
        a_permit(
            application,
            number=PermitNumber(2025, Series.SINGLE, 4472),
            end=date(2025, 4, 30),
        )
    )
    session.commit()
    assert len(permits.on_the_road(START)) == 2
    assert len(permits.on_the_road(date(2025, 4, 20))) == 1
    expiring = permits.expiring(START, date(2025, 4, 11))
    assert [one.number for one in expiring] == ["25-P-004471"]


def test_a_voided_permit_leaves_the_road_but_stays_on_file(session):
    applications = ApplicationRepository(session, BOOK)
    application = an_application()
    applications.add(application)
    session.commit()
    permits = PermitRepository(session)
    permit = a_permit(application)
    permits.add(permit)
    session.commit()
    permit.void(START, "load cancelled")
    permits.save_state(permit)
    session.commit()
    assert permits.on_the_road(START) == ()
    assert permits.row("25-P-004471").state is PermitState.VOID
    assert permits.row("25-P-004471").void_reason == "load cancelled"


def test_the_permits_over_a_segment_are_what_a_closure_notice_needs(session):
    applications = ApplicationRepository(session, BOOK)
    application = an_application()
    applications.add(application)
    session.commit()
    permits = PermitRepository(session)
    permits.add(a_permit(application))
    session.commit()
    assert len(permits.touching_segment("SA148-2")) == 1
    assert permits.touching_segment("US2-1") == ()


def test_the_permits_issued_in_a_week_are_the_weeks_revenue(session):
    applications = ApplicationRepository(session, BOOK)
    application = an_application()
    applications.add(application)
    session.commit()
    permits = PermitRepository(session)
    permits.add(a_permit(application))
    session.commit()
    assert len(permits.issued_between(date(2025, 4, 1), date(2025, 4, 12))) == 1
    assert permits.issued_between(date(2025, 5, 1), date(2025, 5, 12)) == ()
    assert len(permits.for_carrier(1204471)) == 1


def test_the_network_repository_answers_by_segment_and_by_district(session):
    network = NetworkRepository(session)
    assert network.segment("US201-2").route_number == 201
    assert len(network.segments()) == 12
    assert len(network.in_district(4)) == 7
    with pytest.raises(NotOnFile):
        network.segment("NOPE-1")


def test_the_restrictions_of_a_day_are_the_ones_in_force_that_day(session):
    session.add(
        RestrictionRow(
            ident="R-1",
            segment_ident="US2-1",
            kind=RestrictionKind.CLEARANCE,
            note="railroad bridge",
            height=Inches.of(13, 9),
        )
    )
    session.add(
        RestrictionRow(
            ident="R-2",
            segment_ident="US2-1",
            kind=RestrictionKind.CLOSURE,
            note="culvert",
            effective=date(2025, 6, 1),
            expires=date(2025, 6, 30),
        )
    )
    session.commit()
    network = NetworkRepository(session)
    assert len(network.restrictions_on("US2-1", START)) == 1
    assert len(network.restrictions_on("US2-1", date(2025, 6, 15))) == 2
    assert network.restrictions_on("SR43-1", START) == ()


def test_the_structures_of_a_segment_and_the_posted_ones_come_off_the_same_table(session):
    network = NetworkRepository(session)
    assert [one.ident for one in network.structures_on("US201-2")] == ["1247"]
    assert [one.ident for one in network.posted_structures()] == ["1533"]
