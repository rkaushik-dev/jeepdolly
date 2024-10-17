"""The structure inventory, and what a record has to carry to be one."""

from datetime import date

import pytest

from jeepdolly.errors import Conflict, Impossible, NotOnFile
from jeepdolly.measure import Inches, Pounds
from jeepdolly.structures.inventory import Structure, StructureBook, StructureKind
from support import structure_book

BRIDGE = StructureKind.BRIDGE


def bridge(ident="9001", segment="US2-1", **kw):
    fields = {
        "spans_feet": (60, 60),
        "built": 1958,
        "operating_rating": Pounds(96000),
        "inventory_rating": Pounds(64000),
    }
    fields.update(kw)
    return Structure(ident, "Test Stream", segment, BRIDGE, **fields)


def test_the_book_holds_the_structures_of_the_network():
    book = structure_book()
    assert len(book) == 6
    assert "1247" in book
    assert book.get("1247").name == "Kennebec River"
    assert book.get("1247").kind is BRIDGE


def test_a_structure_prints_with_its_posting_when_it_has_one():
    book = structure_book()
    assert str(book.get("1533")) == "1533 Carrabassett Stream on SA148-1, posted 48,000 lb"
    assert str(book.get("1247")) == "1247 Kennebec River on US201-2"


def test_the_spans_give_the_length_and_the_longest_of_them():
    kennebec = structure_book().get("1247")
    assert kennebec.span_count == 3
    assert kennebec.longest_span == 120
    assert kennebec.length_feet == 360


def test_a_structure_that_carries_traffic_needs_spans_and_an_operating_rating():
    with pytest.raises(Impossible) as refusal:
        Structure("9002", "No Spans", "US2-1", BRIDGE, operating_rating=Pounds(90000))
    assert "has spans" in str(refusal.value)
    with pytest.raises(Impossible):
        bridge(operating_rating=None)
    with pytest.raises(Impossible):
        bridge(spans_feet=(60, 0))


def test_a_structure_over_the_road_needs_a_clearance_instead():
    with pytest.raises(Impossible) as refusal:
        Structure("9003", "Overpass", "US2-1", StructureKind.OVERPASS)
    assert "has a clearance" in str(refusal.value)
    over = Structure("9003", "Overpass", "US2-1", StructureKind.OVERPASS, clearance=Inches(170))
    assert over.kind.crosses_over
    assert not over.kind.carries_traffic


def test_a_daily_rating_above_the_permit_rating_is_a_typing_mistake():
    with pytest.raises(Impossible) as refusal:
        bridge(inventory_rating=Pounds(120000))
    assert "higher for daily traffic" in str(refusal.value)


def test_a_structure_with_no_ident_is_not_a_structure():
    with pytest.raises(Impossible):
        bridge(ident="   ")


def test_the_posted_sign_decides_what_may_go_on_at_all():
    book = structure_book()
    carrabassett = book.get("1533")
    assert carrabassett.is_posted
    assert carrabassett.carries(Pounds(48000))
    assert not carrabassett.carries(Pounds(48001))
    assert book.get("1247").carries(Pounds(200000))


def test_the_structures_of_a_segment_come_back_together():
    book = structure_book()
    assert [one.ident for one in book.on_segment("US201-2")] == ["1247"]
    assert book.on_segment("US201-1") == ()
    idents = [one.ident for one in book.on_segments(("US201-2", "SA148-1", "SR43-1"))]
    assert idents == ["1247", "1533", "3310"]


def test_the_posted_structures_are_the_list_the_router_cares_about():
    assert [one.ident for one in structure_book().posted()] == ["1533"]


def test_the_old_ones_can_be_listed_for_the_inspection_programme():
    old = structure_book().older_than(1960)
    assert [one.ident for one in old] == ["1247", "1533"]
    assert old[0].age(date(2025, 6, 1)) == 88


def test_asking_for_a_structure_that_is_not_on_file_says_which():
    with pytest.raises(NotOnFile) as refusal:
        structure_book().get("9999")
    assert refusal.value.facts["structure"] == "9999"


def test_the_same_structure_may_not_be_filed_twice():
    book = StructureBook()
    book.add(bridge())
    with pytest.raises(Conflict):
        book.add(bridge())
