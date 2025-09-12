"""Duplicate copies over the interface, and the rung of the ladder they want."""

from datetime import date

import pytest

from jeepdolly.api.security import Role
from jeepdolly.permits.reprint import FREELY
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services import audit
from support import (
    a_permit,
    a_seeded_database,
    an_application,
    clients_for,
    keyed_client,
)

INSIDE = "2025-04-09"
AFTER = "2025-05-01"
NUMBER = "25-P-004471"


@pytest.fixture
def factory():
    return a_seeded_database()


@pytest.fixture
def issued(factory):
    with UnitOfWork(factory) as work:
        application = an_application()
        work.applications.add(application)
        work.commit()
        work.permits.add(a_permit(application))
        work.commit()
    return factory


@pytest.fixture
def client(issued):
    return keyed_client(role=Role.CLERK, factory=issued)


@pytest.fixture
def supervisor(issued):
    return keyed_client(factory=issued)


def ask(client, reason="lost", asked_by="D Bickford", note="", on=INSIDE, number=NUMBER):
    body = {"reason": reason, "asked_by": asked_by, "note": note}
    return client.post(f"/permits/{number}/copies?on={on}", json=body)


def test_a_first_copy_is_copy_two(client):
    answer = ask(client)
    assert answer.status_code == 201
    assert answer.json()["copy_number"] == 2


def test_the_sheet_comes_back_with_the_banner_on_it(client):
    written = ask(client).json()["sheet"]
    assert "*** DUPLICATE - COPY 2 ***" in written
    assert written.count("*** DUPLICATE - COPY 2 ***") == 2


def test_the_sheet_is_the_same_permit_with_the_same_conditions(client):
    written = ask(client).json()["sheet"]
    assert "PERMIT 25-P-004471" in written
    assert "CONDITIONS" in written


def test_copies_carry_on_the_sequence(client):
    ask(client)
    assert ask(client).json()["copy_number"] == 3


def test_the_register_lists_every_copy(client):
    ask(client, reason="lost")
    ask(client, reason="damaged")
    found = client.get(f"/permits/{NUMBER}/copies").json()
    assert [one["copy_number"] for one in found] == [2, 3]
    assert [one["reason"] for one in found] == ["lost", "damaged"]


def test_a_permit_nobody_copied_has_an_empty_register(client):
    assert client.get(f"/permits/{NUMBER}/copies").json() == []


def test_a_permit_nobody_has_is_a_404(client):
    assert client.get("/permits/25-P-999999/copies").status_code == 404
    assert ask(client, number="25-P-999999").status_code == 404


def test_a_reason_this_office_does_not_record_is_refused(client):
    answer = ask(client, reason="the dog ate it")
    assert answer.status_code == 422
    assert "the dog ate it" in answer.json()["message"]


def test_another_reason_wants_a_sentence(client):
    assert ask(client, reason="other").status_code == 422
    assert ask(client, reason="other", note="wanted one for the file").status_code == 201


def test_a_copy_of_an_expired_permit_is_refused(client):
    answer = ask(client, on=AFTER)
    assert answer.status_code == 409
    assert "expired on" in answer.json()["message"]


def test_a_copy_of_a_void_permit_is_refused(supervisor):
    supervisor.post(f"/permits/{NUMBER}/void?reason=load%20cancelled&on={INSIDE}")
    answer = ask(supervisor)
    assert answer.status_code == 409


def test_a_clerk_may_print_up_to_the_free_limit(client):
    for _ in range(FREELY - 1):
        assert ask(client).status_code == 201
    assert client.get(f"/permits/{NUMBER}/copies").json()[-1]["copy_number"] == FREELY


def test_a_clerk_is_refused_past_it(client):
    for _ in range(FREELY - 1):
        ask(client)
    answer = ask(client)
    assert answer.status_code == 409
    assert "wants a supervisor" in answer.json()["message"]


def test_the_same_request_from_a_supervisor_goes_through(issued):
    both = clients_for(issued)
    for _ in range(FREELY - 1):
        assert ask(both[Role.CLERK]).status_code == 201
    assert ask(both[Role.CLERK]).status_code == 409
    answer = ask(both[Role.SUPERVISOR])
    assert answer.status_code == 201
    assert answer.json()["copy_number"] == FREELY + 1
    assert answer.json()["needed_a_supervisor"] is True


def test_the_free_limit_can_be_asked_for(client):
    assert client.get("/permits/copies/free-limit").json() == {"freely": FREELY}


def test_a_copy_lands_on_the_trail_with_its_number_and_reason(client, factory):
    ask(client, reason="damaged")
    with UnitOfWork(factory) as work:
        found = audit.for_document(work.db, "permit", NUMBER)
    assert found[-1].action == "permit.reprinted"
    assert found[-1].detail == "copy 2, damaged"


def test_the_copy_is_recorded_against_whoever_printed_it(client):
    found = ask(client).json()
    assert found["asked_by"] == "D Bickford"
    assert found["issued_by"]


def test_a_reader_cannot_print_a_copy(issued):
    reader = keyed_client(role=Role.READER, factory=issued)
    assert ask(reader).status_code == 403


def test_a_reader_can_read_the_register(issued, client):
    ask(client)
    reader = keyed_client(role=Role.READER, factory=issued)
    assert len(reader.get(f"/permits/{NUMBER}/copies").json()) == 1


def test_the_permit_itself_is_untouched_by_a_copy(client):
    before = client.get(f"/permits/{NUMBER}").json()
    ask(client)
    assert client.get(f"/permits/{NUMBER}").json() == before


class TestTheStore:
    """The register on the database, without the interface in the way."""

    @pytest.fixture
    def work(self, issued):
        with UnitOfWork(issued) as unit:
            yield unit

    def a_reprint(self, copy=2):
        from jeepdolly.permits.reprint import Reason, Reprint

        return Reprint(
            permit=NUMBER,
            copy=copy,
            on=date(2025, 4, 9),
            reason=Reason.LOST,
            asked_by="D Bickford",
            issued_by="M Cyr",
        )

    def test_a_copy_goes_in_and_comes_back_the_same_copy(self, work):
        one = self.a_reprint()
        work.reprints.add(one)
        work.db.flush()
        assert work.reprints.for_permit(NUMBER)[0].to_domain() == one

    def test_the_copy_numbers_taken_come_back_in_order(self, work):
        work.reprints.add(self.a_reprint(copy=3))
        work.reprints.add(self.a_reprint(copy=2))
        work.db.flush()
        assert work.reprints.copies_taken(NUMBER) == (2, 3)

    def test_the_same_copy_number_is_refused_before_the_database_sees_it(self, work):
        from jeepdolly.errors import Conflict

        work.reprints.add(self.a_reprint())
        work.db.flush()
        with pytest.raises(Conflict):
            work.reprints.add(self.a_reprint())

    def test_a_window_of_copies_comes_back_for_the_yearly_question(self, work):
        work.reprints.add(self.a_reprint())
        work.db.flush()
        assert len(work.reprints.between(date(2025, 1, 1), date(2025, 12, 31))) == 1
        assert work.reprints.between(date(2024, 1, 1), date(2024, 12, 31)) == ()

    def test_the_register_counts_what_it_holds(self, work):
        work.reprints.add(self.a_reprint())
        work.db.flush()
        assert work.reprints.count() == 1

    def test_the_row_says_what_it_is_when_something_prints_it(self, work):
        from jeepdolly.persistence.models.reprints import ReprintRow

        work.reprints.add(self.a_reprint())
        work.db.flush()
        row = work.db.get(ReprintRow, (NUMBER, 2))
        assert repr(row) == "<ReprintRow 25-P-004471 copy 2>"
