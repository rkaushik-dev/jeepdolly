"""The application, what makes it incomplete, and the states it moves through."""

from datetime import date

import pytest

from jeepdolly.errors import Impossible, NotAllowed
from jeepdolly.measure import Pounds
from jeepdolly.permits.application import Applicant, ApplicationState
from support import an_application

FILED = date(2025, 4, 7)
START = date(2025, 4, 8)


def test_a_new_application_is_a_draft_with_one_line_of_history():
    application = an_application()
    assert application.state is ApplicationState.DRAFT
    assert application.state.open
    assert application.history == [(FILED, ApplicationState.DRAFT, "typed")]
    assert str(application) == "25-04471 waterville to pittsfield draft"


def test_the_application_carries_the_gross_off_the_combination():
    assert an_application().gross == Pounds(94000)
    assert an_application().span_days == 3


def test_a_complete_application_has_nothing_to_say_about_itself():
    application = an_application()
    assert application.complete
    assert application.problems() == ()


def test_a_span_that_ends_before_it_starts_is_incomplete():
    application = an_application(start=date(2025, 4, 10), end=date(2025, 4, 8))
    assert not application.complete
    assert application.problems() == ("the travel span ends before it starts",)


def test_a_span_longer_than_the_office_writes_is_incomplete():
    application = an_application(end=date(2025, 4, 30))
    assert "10 days at most" in application.problems()[0]


def test_travel_before_the_application_was_filed_is_incomplete():
    application = an_application(start=date(2025, 4, 1), end=date(2025, 4, 3))
    assert "starts before the application was filed" in application.problems()[0]


def test_an_empty_trailer_is_incomplete():
    assert an_application(pieces=()).problems() == ("nothing is described on the trailer",)


def test_a_trip_to_where_it_started_is_incomplete():
    application = an_application(destination="waterville")
    assert application.problems() == ("the trip starts and ends in the same place",)


def test_an_application_with_no_reference_is_not_an_application():
    with pytest.raises(Impossible):
        an_application(reference="   ")


def test_an_applicant_needs_a_name_and_a_number_to_ring():
    with pytest.raises(Impossible):
        Applicant("  ", "207 555 0143")
    with pytest.raises(Impossible):
        Applicant("Dana Bickford", "")
    assert str(Applicant("Dana Bickford", "207 555 0143")) == "Dana Bickford 207 555 0143"


def test_submitting_moves_it_on_and_records_the_day():
    application = an_application()
    application.submit(FILED)
    assert application.state is ApplicationState.SUBMITTED
    assert application.history[-1] == (FILED, ApplicationState.SUBMITTED, "submitted")


def test_an_incomplete_application_will_not_submit_and_says_the_first_reason():
    application = an_application(pieces=())
    with pytest.raises(Impossible) as refusal:
        application.submit(FILED)
    assert refusal.value.facts["reference"] == "25-04471"
    assert application.state is ApplicationState.DRAFT


def test_the_same_application_does_not_submit_twice():
    application = an_application()
    application.submit(FILED)
    with pytest.raises(NotAllowed):
        application.submit(FILED)


def test_it_can_go_to_an_engineer_and_then_be_issued():
    application = an_application()
    application.submit(FILED)
    application.send_for_review(FILED, "superload on width")
    assert application.state is ApplicationState.IN_REVIEW
    application.issue(START, "25-004471")
    assert application.state is ApplicationState.ISSUED
    assert not application.state.open
    assert application.history[-1][2] == "issued as 25-004471"


def test_a_draft_cannot_be_issued_and_a_review_cannot_be_asked_for_twice():
    application = an_application()
    with pytest.raises(NotAllowed):
        application.issue(START, "25-004471")
    application.submit(FILED)
    application.send_for_review(FILED)
    with pytest.raises(NotAllowed):
        application.send_for_review(FILED)


def test_a_refusal_records_its_reason_and_closes_the_file():
    application = an_application()
    application.submit(FILED)
    application.refuse(START, "the Carrabassett bridge is posted at 48,000 lb")
    assert application.state is ApplicationState.REFUSED
    assert not application.state.open
    with pytest.raises(NotAllowed):
        application.withdraw(START)


def test_a_carrier_can_withdraw_anything_still_open():
    application = an_application()
    application.submit(FILED)
    application.withdraw(START)
    assert application.state is ApplicationState.WITHDRAWN
    assert application.history[-1][2] == "withdrawn by the carrier"
