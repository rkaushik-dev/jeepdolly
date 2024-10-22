"""The review queue, its five states, and the history nobody may edit."""

from datetime import date

import pytest

from jeepdolly.errors import Conflict, Impossible, NotAllowed, NotOnFile
from jeepdolly.structures.review import ReviewQueue, ReviewState

MONDAY = date(2025, 7, 7)
TUESDAY = date(2025, 7, 8)
FRIDAY = date(2025, 7, 11)
LATER = date(2025, 7, 28)


def queue_with(reference="25-04471", structures=("1247",), on=MONDAY):
    queue = ReviewQueue()
    queue.open(reference, structures, on)
    return queue


def test_a_new_item_waits_and_carries_the_line_that_opened_it():
    item = queue_with().get("25-04471")
    assert item.state is ReviewState.WAITING
    assert item.state.open
    assert not item.state.decided
    assert len(item.history) == 1
    assert str(item.history[0]) == "2025-07-07 counter waiting: opened"
    assert str(item) == "25-04471 waiting"


def test_an_engineer_picks_it_up_and_the_counter_can_say_who_has_it():
    item = queue_with().get("25-04471")
    item.assign("R Pelletier", TUESDAY)
    assert item.state is ReviewState.WITH_ENGINEER
    assert item.engineer == "R Pelletier"
    assert str(item) == "25-04471 with engineer with R Pelletier"


def test_a_cleared_item_carries_the_conditions_the_engineer_wrote():
    item = queue_with().get("25-04471")
    item.assign("R Pelletier", TUESDAY)
    item.clear(FRIDAY, ("cross the Kennebec bridge alone", "walking pace over the joint"))
    assert item.state is ReviewState.CLEARED
    assert item.state.decided
    assert not item.state.open
    assert item.conditions[0] == "cross the Kennebec bridge alone"
    assert item.decided_on() == FRIDAY


def test_a_refused_item_carries_the_reason_and_nothing_else():
    item = queue_with().get("25-04471")
    item.assign("R Pelletier", TUESDAY)
    item.refuse(FRIDAY, "the 1937 truss will not take the tridem")
    assert item.state is ReviewState.REFUSED
    assert item.conditions == ()
    assert item.history[-1].note == "the 1937 truss will not take the tridem"


def test_a_held_item_goes_back_to_waiting_when_the_answer_comes():
    item = queue_with().get("25-04471")
    item.assign("R Pelletier", TUESDAY)
    item.hold(TUESDAY, "send the axle spacings, not the estimates")
    assert item.state is ReviewState.HELD
    assert item.state.open
    item.resume(FRIDAY, "spacings received")
    assert item.state is ReviewState.WAITING
    assert item.engineer is None
    assert [move.state for move in item.history][-2:] == [ReviewState.HELD, ReviewState.WAITING]


def test_the_moves_that_do_not_make_sense_are_refused():
    item = queue_with().get("25-04471")
    with pytest.raises(NotAllowed):
        item.clear(TUESDAY)
    with pytest.raises(NotAllowed):
        item.hold(TUESDAY, "nothing")
    with pytest.raises(NotAllowed):
        item.resume(TUESDAY)
    item.assign("R Pelletier", TUESDAY)
    with pytest.raises(NotAllowed):
        item.assign("someone else", TUESDAY)


def test_a_decided_item_cannot_be_moved_again():
    item = queue_with().get("25-04471")
    item.assign("R Pelletier", TUESDAY)
    item.clear(FRIDAY)
    with pytest.raises(NotAllowed):
        item.refuse(LATER, "changed my mind")
    with pytest.raises(NotAllowed):
        item.hold(LATER, "changed my mind")


def test_a_move_dated_before_the_item_existed_is_refused():
    item = queue_with().get("25-04471")
    with pytest.raises(Impossible):
        item.assign("R Pelletier", date(2025, 1, 1))


def test_an_item_needs_an_application_to_be_opened_against():
    with pytest.raises(Impossible):
        ReviewQueue().open("   ", ("1247",), MONDAY)


def test_the_same_application_is_not_queued_twice():
    queue = queue_with()
    with pytest.raises(Conflict) as refusal:
        queue.open("25-04471", ("1533",), TUESDAY)
    assert refusal.value.facts["reference"] == "25-04471"


def test_asking_for_a_review_that_was_never_opened_says_so():
    with pytest.raises(NotOnFile):
        ReviewQueue().get("25-99999")


def test_the_waiting_list_is_oldest_first_because_that_is_the_order_worked():
    queue = ReviewQueue()
    queue.open("25-00003", ("1247",), FRIDAY)
    queue.open("25-00001", ("1533",), MONDAY)
    queue.open("25-00002", ("3310",), TUESDAY)
    assert [one.reference for one in queue.waiting()] == ["25-00001", "25-00002", "25-00003"]


def test_an_engineer_can_ask_for_what_is_on_their_own_desk():
    queue = ReviewQueue()
    first = queue.open("25-00001", ("1247",), MONDAY)
    second = queue.open("25-00002", ("1533",), MONDAY)
    first.assign("R Pelletier", TUESDAY)
    second.assign("J Ouellette", TUESDAY)
    assert [one.reference for one in queue.with_engineer("R Pelletier")] == ["25-00001"]
    assert len(queue.open_items()) == 2


def test_the_overdue_list_is_what_the_supervisor_reads_on_monday():
    queue = ReviewQueue()
    queue.open("25-00001", ("1247",), MONDAY)
    old = queue.open("25-00002", ("1533",), date(2025, 6, 2))
    assert old.waiting_days(LATER) == 56
    assert [one.reference for one in queue.overdue(LATER, days=21)] == ["25-00002"]


def test_a_decided_item_falls_off_the_overdue_list():
    queue = ReviewQueue()
    item = queue.open("25-00002", ("1533",), date(2025, 6, 2))
    item.assign("R Pelletier", date(2025, 6, 3))
    item.refuse(date(2025, 6, 4), "no")
    assert queue.overdue(LATER, days=21) == ()


def test_the_items_touching_one_structure_can_be_listed():
    queue = ReviewQueue()
    queue.open("25-00001", ("1247", "1533"), MONDAY)
    queue.open("25-00002", ("3310",), MONDAY)
    assert [one.reference for one in queue.touching("1533")] == ["25-00001"]
    assert len(queue) == 2
