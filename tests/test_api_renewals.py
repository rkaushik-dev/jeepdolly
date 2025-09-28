"""The renewal run over the interface and over the command line."""

from datetime import date

import pytest

from jeepdolly.api.deps import office_from
from jeepdolly.api.security import Role
from jeepdolly.cli import renewals as commands
from jeepdolly.domain.envelope import Envelope
from jeepdolly.measure import Pounds
from jeepdolly.permits.annual import issue_annual
from jeepdolly.permits.numbering import Numbering
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import an_application, call, keyed_client, shown

# The import above registers the group. Without it this file passes in a whole
# run, where some other test module imported it first, and fails on its own.
assert commands.renewal.name == "renewal"

ISSUED = "2025-01-06"
NEAR = "2025-12-15"


def an_annual(factory, reference="25-04471", usdot=1204471):
    with UnitOfWork(factory) as work:
        application = an_application(reference=reference, usdot=usdot)
        work.applications.add(application)
        work.commit()
        office = office_from(work, date(2025, 1, 6))
        made = issue_annual(
            numbering=Numbering(work.counter),
            library=office.library,
            schedule=office.schedules.on(date(2025, 1, 6)),
            application_reference=reference,
            usdot=usdot,
            carrier_name=office.carriers.get(usdot).name,
            envelope=Envelope.read("11'", "13'6", "70'"),
            gross=Pounds(99000),
            commodity=office.commodities.get(application.commodity),
            on=date(2025, 1, 6),
        )
        work.permits.add(made)
        work.commit()
    return str(made.number)


@pytest.fixture
def annual(factory):
    return an_annual(factory)


@pytest.fixture
def client(factory):
    return keyed_client(role=Role.CLERK, factory=factory)


def test_nothing_is_due_on_a_quiet_day(client):
    assert client.get("/renewals?on=2025-06-01").json() == []


def test_an_annual_running_out_is_due_with_the_days_left(client, annual):
    found = client.get(f"/renewals?on={NEAR}").json()
    assert [one["number"] for one in found] == [annual]
    assert found[0]["days_left"] == 21
    assert found[0]["carrier"] == "Bickford Heavy Haul"


def test_the_window_can_be_widened(client, annual):
    assert client.get("/renewals?on=2025-06-01&within=365").json() != []


def test_a_window_wider_than_a_year_is_refused(client):
    assert client.get("/renewals?on=2025-06-01&within=400").status_code == 422


def test_the_letters_are_one_per_carrier(client, factory):
    an_annual(factory, reference="25-04471")
    an_annual(factory, reference="25-04472")
    found = client.get(f"/renewals/letters?on={NEAR}").json()
    assert len(found) == 1
    assert found[0]["count"] == 2
    assert len(found[0]["numbers"]) == 2


def test_the_letter_carries_the_lines_it_prints(client, annual):
    found = client.get(f"/renewals/letters?on={NEAR}").json()
    assert found[0]["lines"][0] == "Bickford Heavy Haul (1204471)"


def test_renewing_issues_a_new_permit(client, annual):
    answer = client.post(f"/renewals/1204471?on={NEAR}", json={"numbers": [annual]})
    assert answer.status_code == 201
    found = answer.json()
    assert found["replaced"] == [annual]
    assert found["issued"][0]["number"] != annual


def test_renewing_nothing_is_refused_by_the_schema(client):
    assert client.post(f"/renewals/1204471?on={NEAR}", json={"numbers": []}).status_code == 422


def test_a_permit_belonging_to_somebody_else_is_refused(client, factory):
    mine = an_annual(factory, reference="25-04471")
    theirs = an_annual(factory, reference="25-04480", usdot=2299001)
    answer = client.post(f"/renewals/1204471?on={NEAR}", json={"numbers": [mine, theirs]})
    assert answer.status_code == 409
    assert "does not belong" in answer.json()["message"]


def test_a_refused_batch_issues_nothing_at_all(client, factory):
    mine = an_annual(factory, reference="25-04471")
    theirs = an_annual(factory, reference="25-04480", usdot=2299001)
    before = len(client.get(f"/renewals?on={NEAR}").json())
    client.post(f"/renewals/1204471?on={NEAR}", json={"numbers": [mine, theirs]})
    assert len(client.get(f"/renewals?on={NEAR}").json()) == before


def test_a_permit_nobody_has_is_a_404(client):
    answer = client.post(f"/renewals/1204471?on={NEAR}", json={"numbers": ["25-A-999999"]})
    assert answer.status_code == 404


def test_a_reader_cannot_renew(factory, annual):
    reader = keyed_client(role=Role.READER, factory=factory)
    answer = reader.post(f"/renewals/1204471?on={NEAR}", json={"numbers": [annual]})
    assert answer.status_code == 403


class TestTheCommands:
    """The same run from a terminal in the back office."""

    def test_the_due_list_prints_one_permit_to_a_line(self, console, factory):
        number = an_annual(factory)
        answer = call(console, ["renewal", "due", "--day", NEAR])
        assert answer.exit_code == 0
        assert shown(answer) == [f"{number}  2026-01-05  21  Bickford Heavy Haul"]

    def test_a_quiet_window_says_so(self, console):
        answer = call(console, ["renewal", "due", "--day", "2025-06-01"])
        assert answer.output.strip() == "nothing is due in that window"

    def test_the_merge_row_runs_the_numbers_together(self, console, factory):
        first = an_annual(factory, reference="25-04471")
        second = an_annual(factory, reference="25-04472")
        answer = call(console, ["renewal", "letters", "--day", NEAR])
        assert shown(answer) == [f"1204471  Bickford Heavy Haul  2  2026-01-05  {first};{second}"]

    def test_the_long_form_prints_the_letter_itself(self, console, factory):
        an_annual(factory)
        answer = call(console, ["renewal", "letters", "--day", NEAR, "--long"])
        assert shown(answer)[0] == "Bickford Heavy Haul (1204471)"

    def test_nobody_due_says_so(self, console):
        answer = call(console, ["renewal", "letters", "--day", "2025-06-01"])
        assert answer.output.strip() == "nobody is due a letter"

    def test_renewing_from_the_command_line_issues_and_reports(self, console, factory):
        number = an_annual(factory)
        answer = call(console, ["renewal", "renew", "1204471", number, "--on", NEAR])
        assert answer.exit_code == 0
        printed = shown(answer)
        assert printed[0].startswith("renewed  25-A-")
        assert printed[-1] == "1  renewed for  1204471"

    def test_a_refused_batch_is_a_refusal_rather_than_a_traceback(self, console, factory):
        an_annual(factory)
        answer = call(console, ["renewal", "renew", "1204471", "25-A-999999", "--on", NEAR])
        assert answer.exit_code != 0
