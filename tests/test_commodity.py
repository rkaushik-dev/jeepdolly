"""The commodity table and the divisibility test it answers."""

import pytest

from jeepdolly.domain.commodity import Commodity, CommodityBook, Family, Piece, standard_book
from jeepdolly.errors import Conflict, Impossible, NotOnFile


def test_a_load_that_could_not_have_been_split_can_be_permitted():
    book = standard_book()
    assert book.get("EXCV").permittable
    assert not book.get("EXCV").divisible
    assert not book.get("EXCV").restricted_to_allowance


def test_a_load_that_could_have_been_two_trips_cannot_be_permitted():
    gravel = standard_book().get("GRVL")
    assert gravel.divisible
    assert not gravel.permittable


def test_a_divisible_load_with_a_standing_allowance_moves_anyway():
    wood = standard_book().get("WOOD")
    assert wood.divisible
    assert wood.permittable
    assert wood.restricted_to_allowance
    assert wood.allowance == "forest products"


def test_an_allowance_on_something_that_was_never_divisible_is_a_mistake():
    with pytest.raises(Impossible) as refusal:
        Commodity("BEAM", "steel beam", Family.STRUCTURAL, allowance="forest products")
    assert "needs no allowance" in str(refusal.value)


def test_a_commodity_code_is_a_code_and_not_a_sentence():
    with pytest.raises(Impossible):
        Commodity("excv", "excavator", Family.MACHINERY)
    with pytest.raises(Impossible):
        Commodity("", "excavator", Family.MACHINERY)


def test_a_description_too_long_for_the_permit_face_is_refused():
    with pytest.raises(Impossible) as refusal:
        Commodity("EXCV", "e" * 61, Family.MACHINERY)
    assert "will not fit" in str(refusal.value)


def test_a_tip_limit_that_is_not_an_angle_is_refused():
    with pytest.raises(Impossible):
        Commodity("GIRD", "girder", Family.STRUCTURAL, tip_limit_degrees=0)
    with pytest.raises(Impossible):
        Commodity("GIRD", "girder", Family.STRUCTURAL, tip_limit_degrees=90)


def test_a_commodity_prints_as_the_permit_face_prints_it():
    assert str(standard_book().get("XFMR")) == "XFMR power transformer"
    assert str(Family.UTILITY) == "utility"


def test_the_book_refuses_to_file_one_code_twice():
    book = CommodityBook()
    book.add(Commodity("EXCV", "excavator", Family.MACHINERY))
    with pytest.raises(Conflict):
        book.add(Commodity("EXCV", "shovel", Family.MACHINERY))
    assert len(book) == 1


def test_asking_for_a_commodity_nobody_filed_says_so_with_the_code():
    book = standard_book()
    with pytest.raises(NotOnFile) as refusal:
        book.get("ZZZZ")
    assert refusal.value.facts["code"] == "ZZZZ"
    assert not book.has("ZZZZ")
    assert "ZZZZ" not in book


def test_the_standard_table_holds_the_lines_the_counter_uses():
    book = standard_book()
    assert len(book) == 17
    assert "EXCV" in book
    assert book.codes()[0] == "BEAM"


def test_a_family_comes_back_sorted_so_a_report_reads_the_same_twice():
    forest = standard_book().in_family(Family.FOREST)
    assert [item.code for item in forest] == ["CHIP", "WOOD"]
    assert standard_book().in_family(Family.MACHINERY)[0].code == "CRNE"


def test_the_commodities_under_one_allowance_can_be_listed():
    book = standard_book()
    assert [item.code for item in book.under_allowance("forest products")] == ["WOOD"]
    assert [item.code for item in book.under_allowance("forage")] == ["HAYB"]
    assert book.under_allowance("nothing") == ()


def test_the_flags_that_later_rules_read_are_on_the_record():
    book = standard_book()
    assert book.get("XFMR").tip_limit_degrees == 3
    assert book.get("BOAT").high_centre
    assert not book.get("BEAM").high_centre
    assert book.get("BEAM").tip_limit_degrees is None


def test_a_piece_names_itself_and_carries_a_serial_when_there_is_one():
    book = standard_book()
    piece = Piece(book.get("EXCV"), "Cat 336 excavator", serial="TMD01297")
    assert str(piece) == "Cat 336 excavator (TMD01297)"
    assert str(Piece(book.get("BEAM"), "W36x300 girder")) == "W36x300 girder"


def test_a_piece_with_no_description_is_not_on_the_permit():
    with pytest.raises(Impossible):
        Piece(standard_book().get("EXCV"), "   ")
