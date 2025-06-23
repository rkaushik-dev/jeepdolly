"""The trail, its fixed vocabulary, and the questions asked of it."""

from datetime import datetime

import pytest
from sqlalchemy import select

from jeepdolly.errors import Impossible
from jeepdolly.persistence.base import create_all, memory_engine, session_factory
from jeepdolly.persistence.models import AuditRow
from jeepdolly.services import audit

NINE = datetime(2025, 4, 7, 9, 0)
TEN = datetime(2025, 4, 7, 10, 0)
LATER = datetime(2025, 4, 8, 9, 0)


@pytest.fixture
def session():
    engine = memory_engine()
    create_all(engine)
    with session_factory(engine)() as opened:
        yield opened


def test_a_line_says_who_did_what_to_which_document(session):
    audit.record(session, "permit.issued", "D Bickford", "permit", "25-P-004471", at=NINE)
    session.commit()
    row = session.scalars(select(AuditRow)).one()
    assert row.action == "permit.issued"
    assert row.actor == "D Bickford"
    assert row.kind == "permit"
    assert row.reference == "25-P-004471"
    assert row.happened == NINE
    assert repr(row) == "<AuditRow permit.issued permit:25-P-004471>"


def test_the_detail_is_the_only_place_anybody_writes_a_sentence(session):
    audit.record(
        session,
        "permit.voided",
        "R Pelletier",
        "permit",
        "25-P-004471",
        at=NINE,
        detail="load cancelled by the customer",
    )
    session.commit()
    assert session.scalars(select(AuditRow)).one().detail.startswith("load cancelled")


def test_an_action_the_trail_does_not_know_is_refused(session):
    with pytest.raises(Impossible) as refusal:
        audit.record(session, "issued a permit", "D Bickford", "permit", "25-P-004471")
    assert refusal.value.facts["action"] == "issued a permit"


def test_a_line_with_nobody_behind_it_is_refused(session):
    with pytest.raises(Impossible):
        audit.record(session, "permit.issued", "   ", "permit", "25-P-004471")


def test_the_trail_of_one_document_is_oldest_first(session):
    audit.record(session, "application.filed", "clerk", "application", "25-F-1", at=NINE)
    audit.record(session, "application.submitted", "clerk", "application", "25-F-1", at=TEN)
    audit.record(session, "permit.issued", "clerk", "permit", "25-P-1", at=LATER)
    session.commit()
    found = audit.for_document(session, "application", "25-F-1")
    assert [one.action for one in found] == ["application.filed", "application.submitted"]


def test_the_trail_of_one_person_is_newest_first(session):
    audit.record(session, "application.filed", "clerk", "application", "25-F-1", at=NINE)
    audit.record(session, "permit.issued", "clerk", "permit", "25-P-1", at=LATER)
    audit.record(session, "permit.issued", "somebody else", "permit", "25-P-2", at=TEN)
    session.commit()
    found = audit.by_person(session, "clerk")
    assert [one.reference for one in found] == ["25-P-1", "25-F-1"]


def test_one_action_can_be_counted_for_the_month(session):
    for number in range(3):
        audit.record(session, "permit.issued", "clerk", "permit", f"25-P-{number}", at=NINE)
    audit.record(session, "permit.voided", "clerk", "permit", "25-P-0", at=TEN)
    session.commit()
    assert len(audit.of_action(session, "permit.issued")) == 3
    assert len(audit.of_action(session, "permit.voided")) == 1


def test_a_span_of_the_trail_is_what_an_auditor_asks_for(session):
    audit.record(session, "permit.issued", "clerk", "permit", "25-P-1", at=NINE)
    audit.record(session, "permit.issued", "clerk", "permit", "25-P-2", at=LATER)
    session.commit()
    found = audit.between(session, NINE, TEN)
    assert [one.reference for one in found] == ["25-P-1"]


def test_a_span_that_ends_before_it_starts_is_refused(session):
    with pytest.raises(Impossible):
        audit.between(session, LATER, NINE)


def test_the_trail_goes_back_with_the_work_that_was_rolled_back(session):
    audit.record(session, "permit.issued", "clerk", "permit", "25-P-1", at=NINE)
    session.rollback()
    assert session.scalars(select(AuditRow)).all() == []


def test_the_vocabulary_covers_the_things_the_office_does():
    assert "permit.issued" in audit.ACTIONS
    assert "carrier.suspended" in audit.ACTIONS
    assert "road.closed" in audit.ACTIONS
    assert "tariff.filed" in audit.ACTIONS
    assert len(audit.ACTIONS) == 22
