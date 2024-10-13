"""Repairing the route the hauler wrote rather than replacing it."""

from datetime import date

from jeepdolly.domain.envelope import Envelope
from jeepdolly.domain.restriction import Restriction, RestrictionBook, RestrictionKind
from jeepdolly.limits.statutory import standard_book as statutes
from jeepdolly.measure import Inches, Miles, Pounds
from jeepdolly.routing.detour import first_trouble, repair
from jeepdolly.routing.itinerary import Itinerary
from jeepdolly.routing.select import Search
from jeepdolly.seasons import SeasonBook
from support import road_log

MARCH = date(2025, 3, 3)
WRITTEN = ("US201-1", "US201-2", "US2-1", "SA148-1", "SR43-1")
LOAD = Search(envelope=Envelope.read("12'", "13'9", "82'"), gross=Pounds(94000), day=MARCH)


def closed(segment, ident="R-1"):
    book = RestrictionBook()
    book.add(Restriction(ident, segment, RestrictionKind.CLOSURE, "bridge out"))
    return book


def written(network):
    return Itinerary(network, "waterville", WRITTEN)


def mend(restrictions, network=None, load=LOAD):
    network = network or road_log()
    return repair(network, written(network), load, restrictions, SeasonBook(), statutes())


def test_a_route_with_nothing_wrong_needs_no_repair():
    network = road_log()
    clear = first_trouble(written(network), LOAD, RestrictionBook(), SeasonBook(), statutes())
    assert clear is None
    assert mend(RestrictionBook()) is None


def test_the_first_leg_that_cannot_be_driven_is_the_one_reported():
    network = road_log()
    book = closed("SA148-1")
    assert first_trouble(written(network), LOAD, book, SeasonBook(), statutes()) == 3


def test_a_closure_in_the_middle_is_gone_round_and_the_good_part_is_kept():
    mended = mend(closed("SA148-1"))
    assert mended is not None
    assert mended.repaired.idents == ("US201-1", "US201-2", "SA148-2", "SR43-1")
    assert mended.dropped == ("US2-1", "SA148-1")
    assert mended.added == ("SA148-2",)


def test_the_repair_says_how_much_further_it_made_the_trip():
    mended = mend(closed("SA148-1"))
    assert mended is not None
    assert mended.original.distance == Miles.of(43, 20)
    assert mended.repaired.distance == Miles.of(35, 20)
    assert mended.extra == Miles.of(-8)


def test_a_repair_prints_what_was_dropped_and_what_replaced_it():
    mended = mend(closed("SA148-1"))
    assert str(mended) == "round US2-1, SA148-1 by SA148-2, 8.00 mi shorter"


def test_a_closure_at_the_very_start_replaces_the_whole_route():
    mended = mend(closed("US201-1"))
    assert mended is not None
    assert mended.repaired.idents == ("I95-1", "I95-2")
    assert mended.repaired.origin == "waterville"
    assert mended.repaired.destination == "pittsfield"


def test_a_closure_with_nothing_beyond_it_cannot_be_repaired():
    book = closed("SR43-1")
    book.add(Restriction("R-2", "I95-2", RestrictionKind.CLOSURE, "bridge out"))
    assert mend(book) is None


def test_a_clearance_the_load_will_not_fit_under_is_repaired_like_a_closure():
    book = RestrictionBook()
    book.add(
        Restriction("R-3", "SA148-1", RestrictionKind.CLEARANCE, "stone arch", height=Inches.of(12))
    )
    mended = mend(book)
    assert mended is not None
    assert "SA148-1" in mended.dropped


def test_a_load_that_fits_under_the_arch_leaves_the_route_alone():
    book = RestrictionBook()
    book.add(
        Restriction("R-3", "SA148-1", RestrictionKind.CLEARANCE, "stone arch", height=Inches.of(15))
    )
    assert mend(book) is None


def test_a_repaired_route_still_joins_end_to_end():
    mended = mend(closed("US2-1"))
    assert mended is not None
    legs = list(mended.repaired)
    assert legs[0].entered_at == "waterville"
    assert legs[-1].left_at == "pittsfield"
