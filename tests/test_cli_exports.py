"""Writing the federal return, and reading it back the way it will be read."""

from datetime import date
from pathlib import Path

import pytest

from jeepdolly.cli import exports as commands
from jeepdolly.exports.federal import WIDTH, build, verify, window_of
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_permit, an_application, call, shown

# The import above registers the group. Without it this file passes in a whole
# run, where some other test module imported it first, and fails on its own.
assert commands.export.name == "export"

APRIL = "2025-04-01"
LAST = "2025-04-30"


@pytest.fixture
def issued(factory):
    with UnitOfWork(factory) as work:
        application = an_application()
        work.applications.add(application)
        work.commit()
        work.permits.add(a_permit(application))
        work.commit()
    return factory


def written(console, tmp_path, name="ooretn.txt"):
    path = tmp_path / name
    call(console, ["export", "federal", "--from", APRIL, "--to", LAST, "--to-file", str(path)])
    return path


def lines_of(path):
    return tuple(Path(path).read_text(encoding="ascii").splitlines())


def test_the_return_prints_to_the_screen_when_no_file_is_named(console, issued):
    answer = call(console, ["export", "federal", "--from", APRIL, "--to", LAST])
    assert answer.exit_code == 0
    printed = shown(answer)
    assert len(printed) == 3
    assert printed[0].startswith("H23")
    assert printed[-1].startswith("T")


def test_every_printed_record_is_eighty_columns(console, issued):
    answer = call(console, ["export", "federal", "--from", APRIL, "--to", LAST])
    assert all(len(one) == WIDTH for one in shown(answer))


def test_a_quiet_month_still_prints_a_header_and_a_trailer(console):
    answer = call(console, ["export", "federal", "--from", APRIL, "--to", LAST])
    assert len(shown(answer)) == 2


def test_the_return_can_be_written_to_a_file(console, issued, tmp_path):
    path = tmp_path / "ooretn.txt"
    answer = call(
        console, ["export", "federal", "--from", APRIL, "--to", LAST, "--to-file", str(path)]
    )
    assert answer.exit_code == 0
    assert answer.output.strip().startswith("wrote")
    assert len(lines_of(path)) == 3


def test_the_written_file_uses_unix_line_endings(console, issued, tmp_path):
    path = written(console, tmp_path)
    assert b"\r\n" not in path.read_bytes()


def test_a_freshly_written_file_verifies(console, issued, tmp_path):
    path = written(console, tmp_path)
    answer = call(console, ["export", "verify", str(path)])
    assert answer.exit_code == 0
    assert answer.output.strip().startswith("good")


def test_verifying_names_the_window_off_the_header(console, issued, tmp_path):
    path = written(console, tmp_path)
    assert "2025-04-01 to 2025-04-30" in call(console, ["export", "verify", str(path)]).output


def test_a_record_somebody_shortened_by_hand_is_found(console, issued, tmp_path):
    path = written(console, tmp_path)
    body = lines_of(path)
    path.write_text("\n".join([body[0], body[1].rstrip(), body[2]]) + "\n", encoding="ascii")
    answer = call(console, ["export", "verify", str(path)])
    assert answer.exit_code == 1
    assert "columns and not 80" in answer.output


def test_a_deleted_detail_record_is_found_by_the_trailer(console, issued, tmp_path):
    path = written(console, tmp_path)
    body = lines_of(path)
    path.write_text("\n".join([body[0], body[2]]) + "\n", encoding="ascii")
    answer = call(console, ["export", "verify", str(path)])
    assert answer.exit_code == 1
    assert "says 1 details and the file has 0" in answer.output


def test_a_file_with_no_header_is_found(console, tmp_path):
    path = tmp_path / "ooretn.txt"
    path.write_text("D" + " " * 79 + "\n", encoding="ascii")
    answer = call(console, ["export", "verify", str(path)])
    assert answer.exit_code == 1
    assert "is not a header" in answer.output


def test_an_empty_file_is_found(console, tmp_path):
    path = tmp_path / "ooretn.txt"
    path.write_text("", encoding="ascii")
    answer = call(console, ["export", "verify", str(path)])
    assert answer.exit_code == 1
    assert "the file is empty" in answer.output


def test_a_file_nobody_wrote_is_a_usage_error(console, tmp_path):
    assert call(console, ["export", "verify", str(tmp_path / "nothing.txt")]).exit_code == 2


def test_the_covering_letter_count_prints_when_something_was_replaced(console, factory, tmp_path):
    with UnitOfWork(factory) as work:
        application = an_application()
        work.applications.add(application)
        work.commit()
        permit = a_permit(application)
        work.permits.add(permit)
        work.commit()
        permit.supersede(date(2025, 4, 9), "25-R-000001")
        work.permits.save_state(permit)
        work.commit()
    path = tmp_path / "ooretn.txt"
    answer = call(
        console, ["export", "federal", "--from", APRIL, "--to", LAST, "--to-file", str(path)]
    )
    assert "since superseded" in answer.output


def test_the_verifier_reads_the_file_rather_than_the_database(console, issued, tmp_path):
    path = written(console, tmp_path)
    body = lines_of(path)
    broken = body[2][:8] + "99999999" + body[2][16:]
    path.write_text("\n".join([body[0], body[1], broken]) + "\n", encoding="ascii")
    answer = call(console, ["export", "verify", str(path)])
    assert answer.exit_code == 1
    assert "in fees and the details add to" in answer.output


class TestTheVerifierOnItsOwn:
    """The checks, without a file or a console in the way."""

    def test_a_good_file_has_no_problems(self, factory):
        with UnitOfWork(factory) as work:
            made = build(work.db, date(2025, 4, 1), date(2025, 4, 30))
        assert verify(made.lines()) == ()

    def test_a_window_can_be_read_off_a_good_header(self, factory):
        with UnitOfWork(factory) as work:
            made = build(work.db, date(2025, 4, 1), date(2025, 4, 30))
        assert window_of(made.lines()) == (date(2025, 4, 1), date(2025, 4, 30))

    def test_a_window_cannot_be_read_off_a_file_with_no_header(self):
        from jeepdolly.errors import Unreadable

        with pytest.raises(Unreadable):
            window_of(("D" + " " * 79,))

    def test_a_record_that_is_not_a_detail_in_the_body_is_found(self, factory):
        with UnitOfWork(factory) as work:
            made = build(work.db, date(2025, 4, 1), date(2025, 4, 30))
        body = list(made.lines())
        body.insert(1, "X" + " " * 79)
        found = verify(tuple(body))
        assert [one.record for one in found] == [2]
        assert "is not a detail record" in str(found[0])
