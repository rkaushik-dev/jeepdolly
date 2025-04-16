"""The counter sequence, in one place instead of four."""

from contextlib import suppress
from datetime import date

import pytest

from jeepdolly.api.deps import office_from
from jeepdolly.domain.envelope import Envelope
from jeepdolly.errors import NotAllowed
from jeepdolly.permits.application import ApplicationState
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit, counter
from support import a_combination, a_seeded_database, an_application

FILED = date(2025, 4, 7)
CLERK = "D Bickford"


@pytest.fixture
def factory():
    return a_seeded_database()


def application(reference="25-04471", **kw):
    return an_application(reference=reference, **kw)


def test_a_reference_comes_off_the_same_counter_as_a_permit_number(factory):
    with UnitOfWork(factory) as work:
        assert counter.next_reference(work, FILED) == "25-F-000001"
        assert counter.next_reference(work, FILED) == "25-F-000002"
        work.commit()


def test_filing_writes_the_application_and_the_trail_line(factory):
    with UnitOfWork(factory) as work:
        counter.file_application(work, application(), CLERK, FILED)
        work.commit()
        found = audit.for_document(work.db, "application", "25-04471")
        assert work.applications.row("25-04471").state is ApplicationState.DRAFT
    assert [one.action for one in found] == ["application.filed"]
    assert found[0].detail == "waterville to pittsfield"


def test_submitting_moves_it_on_and_writes_its_own_line(factory):
    with UnitOfWork(factory) as work:
        counter.file_application(work, application(), CLERK, FILED)
        work.flush()
        counter.submit(work, "25-04471", CLERK, FILED)
        work.commit()
        assert work.applications.row("25-04471").state is ApplicationState.SUBMITTED
        assert len(audit.for_document(work.db, "application", "25-04471")) == 2


def test_a_walk_in_is_filed_submitted_and_issued_in_one_go(factory):
    with UnitOfWork(factory) as work:
        written = counter.over_the_counter(
            work, office_from(work, FILED), application(), CLERK, FILED
        )
        work.commit()
        assert work.applications.row("25-04471").state is ApplicationState.ISSUED
    assert written.number == "25-P-000001"
    assert written.assessment.permittable


def test_the_walk_in_leaves_three_lines_on_the_trail(factory):
    with UnitOfWork(factory) as work:
        counter.over_the_counter(work, office_from(work, FILED), application(), CLERK, FILED)
        work.commit()
        applied = audit.for_document(work.db, "application", "25-04471")
        permit = audit.for_document(work.db, "permit", "25-P-000001")
    assert [one.action for one in applied] == ["application.filed", "application.submitted"]
    assert [one.action for one in permit] == ["permit.issued"]


def test_a_walk_in_that_cannot_be_permitted_refuses_the_whole_thing(factory):
    with UnitOfWork(factory) as work:
        with pytest.raises(NotAllowed) as refusal:
            counter.over_the_counter(
                work,
                office_from(work, FILED),
                application(commodity="GRVL"),
                CLERK,
                FILED,
            )
        work.commit()
        assert work.applications.row("25-04471").state is ApplicationState.REFUSED
    assert refusal.value.facts["reference"] == "25-04471"
    assert refusal.value.facts["reasons"]


def test_a_refusal_is_written_down_with_the_first_reason(factory):
    with UnitOfWork(factory) as work:
        with suppress(NotAllowed):
            counter.over_the_counter(
                work, office_from(work, FILED), application(commodity="GRVL"), CLERK, FILED
            )
        work.commit()
        found = audit.for_document(work.db, "application", "25-04471")
    assert [one.action for one in found][-1] == "application.refused"
    assert "loaded to legal weight" in found[-1].detail


def test_a_permit_is_assessed_for_the_day_it_travels_and_not_for_today(factory):
    with UnitOfWork(factory) as work:
        found = counter.look_at(office_from(work, FILED), application())
        assert found.permittable
        later = counter.look_at(office_from(work, FILED), application(), on=date(2025, 4, 9))
        assert later.permittable


def test_voiding_goes_through_the_counter_too(factory):
    with UnitOfWork(factory) as work:
        counter.over_the_counter(work, office_from(work, FILED), application(), CLERK, FILED)
        work.commit()
        counter.void_permit(work, "25-P-000001", CLERK, FILED, "load cancelled")
        work.commit()
        assert work.permits.row("25-P-000001").state.value == "void"
        found = audit.for_document(work.db, "permit", "25-P-000001")
    assert [one.action for one in found] == ["permit.issued", "permit.voided"]


def test_a_wide_load_still_walks_the_whole_procedure(factory):
    combination = a_combination(envelope=Envelope.read("15'", "13'6", "82'", "0", "6'"))
    across = ("US201-1", "US201-2", "SA148-2", "SR43-1")
    with UnitOfWork(factory) as work:
        written = counter.over_the_counter(
            work,
            office_from(work, FILED),
            application(combination=combination, route=across),
            CLERK,
            FILED,
        )
        work.commit()
    assert "E-01" in written.permit.condition_codes()
    assert written.assessment.escorts is not None
