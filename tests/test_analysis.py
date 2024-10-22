"""Every structure on a route against one load, in driven order."""

from jeepdolly.routing.itinerary import Itinerary
from jeepdolly.structures.analysis import analyse, analyse_route
from jeepdolly.structures.rating import Verdict
from support import make_stream, road_log, structure_book, tractor_lowboy

BOOK = structure_book()
ACROSS = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")


def trip(idents=ACROSS, origin="waterville"):
    return Itinerary(road_log(), origin, idents)


def light():
    return make_stream([12000, 14000, 14000], [200, 54])


def middling():
    return make_stream([12000, 19000, 19000, 25000, 25000, 25000], [200, 54, 180, 52, 52])


def test_a_light_load_clears_every_structure_on_the_route():
    reading = analyse_route(BOOK, trip(), light())
    assert reading.clear
    assert len(reading) == 3
    assert reading.conditions() == ()
    assert str(reading) == "3 structures clear"


def test_the_findings_come_back_in_the_order_they_are_driven():
    reading = analyse_route(BOOK, trip(), light())
    assert [one.structure.ident for one in reading.findings] == ["1247", "1533", "3310"]


def test_a_structure_that_carries_no_traffic_is_skipped_and_not_rated():
    reading = analyse_route(BOOK, trip(idents=("US201-1", "US201-2", "US2-1")), light())
    assert [one.structure.ident for one in reading.findings] == ["1247"]


def test_one_posted_structure_refuses_the_whole_route():
    reading = analyse_route(BOOK, trip(), tractor_lowboy())
    assert not reading.clear
    assert reading.structures_in_the_way() == ("1533",)
    assert str(reading) == "refused at 1533"


def test_the_others_are_still_rated_so_the_letter_can_say_everything():
    reading = analyse_route(BOOK, trip(), tractor_lowboy())
    kennebec = reading.findings[0]
    assert kennebec.verdict is Verdict.CLEARS
    assert kennebec.rating.factor == 140
    assert str(kennebec) == "Kennebec River: 94,000 lb of it, 140%"


def test_a_short_span_is_rated_on_the_window_and_not_the_scale_ticket():
    reading = analyse_route(BOOK, trip(), tractor_lowboy())
    culvert = reading.findings[2]
    assert culvert.window.weight.value == 48000
    assert culvert.rating.factor == 187


def test_a_middling_load_crosses_under_conditions_and_says_which():
    reading = analyse_route(BOOK, trip(idents=("US201-1", "US201-2")), middling())
    assert reading.clear
    assert len(reading.conditional) == 1
    conditions = reading.conditions()
    assert len(conditions) == 3
    assert all("Kennebec River" in one for one in conditions)
    assert str(reading) == "1 structures crossed under conditions"


def test_the_worst_structure_is_the_one_with_the_lowest_factor():
    reading = analyse_route(BOOK, trip(), light())
    worst = reading.worst()
    assert worst is not None
    assert worst.structure.ident == "1533"


def test_a_posted_refusal_is_not_read_as_the_lowest_factor():
    reading = analyse_route(BOOK, trip(), tractor_lowboy())
    worst = reading.worst()
    assert worst is not None
    assert worst.structure.ident == "1247"


def test_a_route_with_no_structures_on_it_says_so():
    reading = analyse_route(BOOK, trip(idents=("US201-1",)), tractor_lowboy())
    assert len(reading) == 0
    assert reading.clear
    assert reading.worst() is None
    assert str(reading) == "no structures on the route"


def test_a_bare_list_of_structures_can_be_rated_without_a_route():
    reading = analyse((BOOK.get("1247"), BOOK.get("3310")), tractor_lowboy())
    assert len(reading) == 2
    assert reading.clear
