"""Loading the published road log, badly formed lines and all."""

import pytest

from jeepdolly.errors import Impossible
from jeepdolly.measure import Miles
from jeepdolly.persistence.models import SegmentRow
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.services.roadlog import export_log, load_log, parse, written
from support import a_seeded_database, road_log

LINE = "US201-2|US 201|fairfield|skowhegan|1240|Somerset|Fairfield|3|0"


@pytest.fixture
def factory():
    return a_seeded_database()


def test_a_segment_is_written_the_way_the_state_publishes_it():
    assert written(road_log().segment("US201-2")) == LINE


def test_a_published_line_reads_back_into_the_segment_it_came_from():
    assert parse(LINE) == road_log().segment("US201-2")


def test_a_line_with_the_wrong_field_count_is_refused():
    with pytest.raises(Impossible) as refusal:
        parse("US201-2|US 201|fairfield")
    assert "9 fields" in str(refusal.value)


def test_a_length_that_is_not_a_number_is_refused():
    with pytest.raises(Impossible):
        parse("US201-2|US 201|fairfield|skowhegan|twelve|Somerset|Fairfield|3|0")


def test_a_district_that_is_not_a_number_is_refused():
    with pytest.raises(Impossible):
        parse("US201-2|US 201|fairfield|skowhegan|1240|Somerset|Fairfield|north|0")


def test_the_whole_log_comes_out_in_ident_order(factory):
    with UnitOfWork(factory) as work:
        lines = export_log(work.db)
    assert len(lines) == 12
    assert lines[0].startswith("I95-1|")
    assert LINE in lines


def test_the_log_round_trips_through_the_text(factory):
    with UnitOfWork(factory) as work:
        lines = export_log(work.db)
        found = load_log(work.db, lines)
        work.commit()
    assert found.read == 12
    assert found.added == []
    assert len(found.updated) == 12


def test_a_new_segment_is_added_and_its_nodes_are_made(factory):
    line = "US201-4|US 201|anson|solon|910|Somerset|Anson|4|0"
    with UnitOfWork(factory) as work:
        found = load_log(work.db, (line,))
        work.commit()
        assert work.network.segment("US201-4").length == Miles.of(9, 10)
    assert found.added == ["US201-4"]
    assert str(found) == "1 read, 0 refused"


def test_a_segment_already_on_file_is_updated_in_place(factory):
    line = "US201-2|US 201|fairfield|skowhegan|1300|Somerset|Fairfield|3|1"
    with UnitOfWork(factory) as work:
        found = load_log(work.db, (line,))
        work.commit()
        row = work.network.segment("US201-2")
    assert found.updated == ["US201-2"]
    assert row.length == Miles.of(13)
    assert row.one_way


def test_a_partial_load_leaves_everything_it_did_not_mention(factory):
    line = "US201-2|US 201|fairfield|skowhegan|1300|Somerset|Fairfield|3|0"
    with UnitOfWork(factory) as work:
        load_log(work.db, (line,))
        work.commit()
        assert len(work.db.query(SegmentRow).all()) == 12


def test_a_bad_line_is_refused_by_number_and_the_rest_still_loads(factory):
    lines = (
        "US201-4|US 201|anson|solon|910|Somerset|Anson|4|0",
        "this line is nonsense",
        "US201-5|US 201|solon|bingham|1120|Somerset|Solon|4|0",
    )
    with UnitOfWork(factory) as work:
        found = load_log(work.db, lines)
        work.commit()
    assert found.added == ["US201-4", "US201-5"]
    assert found.refused[0][0] == 2
    assert "9 fields" in found.refused[0][1]


def test_blank_lines_and_comments_are_skipped_without_complaint(factory):
    lines = ("", "# the state writes a header", LINE)
    with UnitOfWork(factory) as work:
        found = load_log(work.db, lines)
    assert found.refused == []
    assert found.updated == ["US201-2"]


def test_the_load_prints_a_summary_and_then_the_refusals(factory):
    with UnitOfWork(factory) as work:
        found = load_log(work.db, ("nonsense",))
    assert found.lines()[0] == "0 added  0 updated  1 refused"
    assert found.lines()[1].startswith("line 1  ")
