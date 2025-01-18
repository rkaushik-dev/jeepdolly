"""The study workflow, kept on rows across days."""

from datetime import date

import pytest

from jeepdolly.errors import Conflict, NotAllowed, NotOnFile
from jeepdolly.persistence.base import create_all, memory_engine, session_factory
from jeepdolly.services import studies
from jeepdolly.structures.review import ReviewState

MONDAY = date(2025, 7, 7)
WEDNESDAY = date(2025, 7, 9)
FRIDAY = date(2025, 7, 11)
LATER = date(2025, 8, 4)


@pytest.fixture
def session():
    engine = memory_engine()
    create_all(engine)
    with session_factory(engine)() as opened:
        yield opened


def opened(session, reference="25-F-000001"):
    row = studies.open_study(session, reference, ("1247", "1533"), MONDAY)
    session.commit()
    return row


def test_a_new_study_waits_with_one_move_behind_it(session):
    row = opened(session)
    assert row.state is ReviewState.WAITING
    assert [one.note for one in row.moves] == ["opened"]
    assert row.structures == "1247,1533"


def test_the_same_application_is_not_queued_twice(session):
    opened(session)
    with pytest.raises(Conflict):
        studies.open_study(session, "25-F-000001", ("1247",), MONDAY)


def test_a_study_nobody_opened_says_so(session):
    with pytest.raises(NotOnFile):
        studies.row_for(session, "25-F-999999")


def test_an_engineer_picks_it_up_and_the_row_says_who(session):
    opened(session)
    row = studies.assign(session, "25-F-000001", "R Pelletier", WEDNESDAY)
    session.commit()
    assert row.state is ReviewState.WITH_ENGINEER
    assert row.engineer == "R Pelletier"
    assert [one.position for one in row.moves] == [0, 1]


def test_clearing_writes_the_conditions_the_engineer_signed(session):
    opened(session)
    studies.assign(session, "25-F-000001", "R Pelletier", WEDNESDAY)
    row = studies.clear(session, "25-F-000001", FRIDAY, ("cross alone", "walking pace"))
    session.commit()
    assert row.state is ReviewState.CLEARED
    assert row.conditions == "cross alone|walking pace"
    assert studies.to_domain(row).conditions == ("cross alone", "walking pace")


def test_refusing_writes_the_reason_and_no_conditions(session):
    opened(session)
    studies.assign(session, "25-F-000001", "R Pelletier", WEDNESDAY)
    row = studies.refuse(session, "25-F-000001", FRIDAY, "the truss will not take the tridem")
    session.commit()
    assert row.state is ReviewState.REFUSED
    assert row.moves[-1].note == "the truss will not take the tridem"


def test_a_hold_goes_back_to_waiting_when_the_answer_arrives(session):
    opened(session)
    studies.assign(session, "25-F-000001", "R Pelletier", WEDNESDAY)
    studies.hold(session, "25-F-000001", WEDNESDAY, "send the axle spacings")
    row = studies.resume(session, "25-F-000001", FRIDAY, "spacings received")
    session.commit()
    assert row.state is ReviewState.WAITING
    assert row.engineer is None
    assert [one.state for one in row.moves][-2:] == [ReviewState.HELD, ReviewState.WAITING]


def test_the_state_machine_cannot_be_got_round_by_the_rows(session):
    opened(session)
    with pytest.raises(NotAllowed):
        studies.clear(session, "25-F-000001", WEDNESDAY)
    with pytest.raises(NotAllowed):
        studies.hold(session, "25-F-000001", WEDNESDAY, "nothing")


def test_a_decided_study_will_not_move_again(session):
    opened(session)
    studies.assign(session, "25-F-000001", "R Pelletier", WEDNESDAY)
    studies.clear(session, "25-F-000001", FRIDAY)
    session.commit()
    with pytest.raises(NotAllowed):
        studies.refuse(session, "25-F-000001", LATER, "changed my mind")


def test_the_queue_is_oldest_first_and_loses_what_is_picked_up(session):
    opened(session, "25-F-000002")
    opened(session, "25-F-000001")
    assert [one.reference for one in studies.waiting(session)] == [
        "25-F-000001",
        "25-F-000002",
    ]
    studies.assign(session, "25-F-000001", "R Pelletier", WEDNESDAY)
    session.commit()
    assert [one.reference for one in studies.waiting(session)] == ["25-F-000002"]
    assert len(studies.with_engineer(session, "R Pelletier")) == 1


def test_the_overdue_list_counts_from_the_day_it_was_opened(session):
    opened(session)
    session.commit()
    assert [one.reference for one in studies.overdue(session, LATER, 14)] == ["25-F-000001"]
    assert studies.overdue(session, FRIDAY, 14) == ()


def test_a_decided_study_falls_off_the_overdue_list(session):
    opened(session)
    studies.assign(session, "25-F-000001", "R Pelletier", WEDNESDAY)
    studies.refuse(session, "25-F-000001", FRIDAY, "no")
    session.commit()
    assert studies.overdue(session, LATER, 14) == ()
