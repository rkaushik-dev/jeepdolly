"""The corner list, on the wall and along one route."""

from datetime import date

import pytest

from jeepdolly.domain.envelope import Envelope
from jeepdolly.persistence.books import network_from
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.reports.corners import along, report, route_report, turned_at, wall_list
from jeepdolly.routing.itinerary import Itinerary
from jeepdolly.routing.turns import Corner, TurnBook, coded_corners
from support import a_permit, a_seeded_database, an_application

FIRST = date(2025, 1, 1)
LAST = date(2025, 12, 31)
ACROSS = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")


@pytest.fixture
def work():
    with UnitOfWork(a_seeded_database()) as unit:
        yield unit


def load(length="80'", rear="0"):
    return Envelope.read("12'", "13'6", length, "0", rear)


def trip(work, idents=ACROSS, origin="waterville"):
    return Itinerary(network_from(work.db), origin, idents)


def a_written_permit(work, route=ACROSS):
    application = an_application()
    work.applications.add(application)
    work.db.flush()
    permit = a_permit(application, route=route)
    work.permits.add(permit)
    work.db.flush()
    return permit


def test_the_wall_list_is_the_tight_corners_and_nothing_else(work):
    found = wall_list(work.db, coded_corners(), FIRST, LAST)
    assert [one.node for one in found] == ["madison", "norridgewock"]


def test_the_wall_list_is_tightest_first_because_that_is_how_it_is_read(work):
    found = wall_list(work.db, coded_corners(), FIRST, LAST)
    radii = [one.corner.radius_feet for one in found]
    assert radii == sorted(radii)


def test_a_wider_radius_asked_for_puts_more_corners_on_the_list(work):
    found = wall_list(work.db, coded_corners(), FIRST, LAST, feet=70)
    assert len(found) > 2


def test_a_card_index_with_nothing_tight_in_it_gives_an_empty_list(work):
    book = TurnBook()
    book.add(Corner("skowhegan", "US201-2", "US2-1", 200))
    assert wall_list(work.db, book, FIRST, LAST) == ()


def test_nothing_has_turned_at_a_corner_on_a_quiet_year(work):
    corner = Corner("skowhegan", "US201-2", "US2-1", 55)
    assert turned_at(work.db, corner, FIRST, LAST) == 0


def test_a_permit_over_both_segments_counts_at_the_corner(work):
    a_written_permit(work)
    corner = Corner("skowhegan", "US201-2", "US2-1", 55)
    assert turned_at(work.db, corner, FIRST, LAST) == 1


def test_a_permit_that_misses_one_of_the_two_segments_does_not_count(work):
    a_written_permit(work, route=("US201-1", "US201-2"))
    corner = Corner("skowhegan", "US201-2", "US2-1", 55)
    assert turned_at(work.db, corner, FIRST, LAST) == 0


def test_a_permit_outside_the_days_asked_about_does_not_count(work):
    a_written_permit(work)
    corner = Corner("skowhegan", "US201-2", "US2-1", 55)
    assert turned_at(work.db, corner, date(2024, 1, 1), date(2024, 12, 31)) == 0


def test_the_traffic_shows_up_on_the_wall_list(work):
    a_written_permit(work)
    found = wall_list(work.db, coded_corners(), FIRST, LAST, feet=80)
    square = next(one for one in found if one.corner.out_of == "US2-1")
    assert square.turned == 1
    assert "1 permits" in str(square)


def test_a_corner_nothing_turned_at_says_so_in_words(work):
    found = wall_list(work.db, coded_corners(), FIRST, LAST)
    assert "nothing this year" in str(found[0])


def test_the_printed_wall_list_has_a_heading_and_a_line_each(work):
    lines = report(work.db, coded_corners(), FIRST, LAST)
    assert lines[0] == "corners on the wall 2025-01-01 to 2025-12-31"
    assert len(lines) == 3


def test_an_empty_card_index_prints_a_sentence_rather_than_nothing(work):
    lines = report(work.db, TurnBook(), FIRST, LAST)
    assert lines[1] == "nothing on the card index is under the tight radius"


def test_every_corner_on_the_route_comes_back_with_a_verdict(work):
    found = along(trip(work), load(), coded_corners())
    assert [one.corner.node for one in found] == ["skowhegan", "norridgewock", "canaan"]
    assert {one.verdict for one in found} == {"clear"}


def test_a_load_that_only_just_gets_round_is_marked_close(work):
    found = along(trip(work), load(length="95'"), coded_corners())
    close = [one for one in found if one.verdict == "close"]
    assert [one.corner.node for one in close] == ["skowhegan"]
    assert close[0].spare == 5


def test_a_load_that_will_not_get_round_is_marked_so(work):
    found = along(trip(work), load(length="150'"), coded_corners())
    assert {one.verdict for one in found} == {"will not get round"}
    assert found[0].spare < 0


def test_the_route_report_counts_what_stops_the_load(work):
    lines = route_report(trip(work), load(length="150'"), coded_corners())
    assert lines[0] == "corners on waterville to pittsfield"
    assert lines[-1] == "this load is stopped at 3 of them"


def test_a_route_with_no_recorded_corner_says_so(work):
    lines = route_report(trip(work, idents=("I95-1", "I95-2")), load(), TurnBook())
    assert lines[-1] == "no corner on this route is on the card index"


def test_a_clear_route_report_names_no_count_of_trouble(work):
    lines = route_report(trip(work), load(), coded_corners())
    assert not any("stopped at" in one for one in lines)
