"""The condition library, its codes, and the slots that must be filled."""

import pytest

from jeepdolly.conditions.library import (
    Category,
    ConditionLibrary,
    ConditionText,
    sort_conditions,
    standard_library,
)
from jeepdolly.errors import Conflict, Impossible, NotOnFile

LIBRARY = standard_library()


def test_the_library_holds_the_office_wording_under_stable_codes():
    assert len(LIBRARY) == 33
    assert "T-02" in LIBRARY
    assert LIBRARY.get("T-02").category is Category.TRAVEL
    assert str(LIBRARY.get("T-02")) == "T-02 (travel)"


def test_a_condition_with_no_slots_renders_as_written():
    rendered = LIBRARY.render("R-01")
    assert rendered.text == "Route as written. Any deviation voids this permit."
    assert str(rendered) == rendered.text
    assert rendered.category is Category.ROUTE


def test_a_condition_with_slots_is_filled_from_the_facts_given():
    rendered = LIBRARY.render("S-01", {"structure": "Kennebec River bridge"})
    assert rendered.text == "Cross Kennebec River bridge alone with no other vehicle on it."


def test_a_missing_fact_is_a_refusal_and_not_a_gap_on_the_permit():
    with pytest.raises(Impossible) as refusal:
        LIBRARY.render("S-01")
    assert refusal.value.facts == {"code": "S-01", "wants": "structure"}


def test_an_unknown_code_says_which_code():
    with pytest.raises(NotOnFile) as refusal:
        LIBRARY.render("Z-99")
    assert refusal.value.facts["code"] == "Z-99"


def test_a_category_comes_back_in_the_order_it_prints():
    travel = LIBRARY.of_category(Category.TRAVEL)
    assert [one.code for one in travel] == ["T-01", "T-02", "T-03", "T-04", "T-05", "T-06", "T-07"]
    assert travel[0].order == 10


def test_the_categories_print_in_the_order_the_permit_face_uses():
    rendered = (
        LIBRARY.render("G-01"),
        LIBRARY.render("E-04"),
        LIBRARY.render("T-02"),
        LIBRARY.render("M-01"),
    )
    assert [one.code for one in sort_conditions(rendered)] == ["T-02", "E-04", "M-01", "G-01"]


def test_two_conditions_of_one_category_sort_by_the_order_on_the_record():
    rendered = (LIBRARY.render("T-03"), LIBRARY.render("T-02"))
    assert [one.code for one in sort_conditions(rendered)] == ["T-02", "T-03"]


def test_a_condition_with_no_code_or_no_words_is_refused():
    with pytest.raises(Impossible):
        ConditionText("  ", Category.GENERAL, "something")
    with pytest.raises(Impossible):
        ConditionText("X-01", Category.GENERAL, "   ")


def test_the_library_refuses_the_same_code_twice():
    library = ConditionLibrary()
    library.add(ConditionText("X-01", Category.GENERAL, "something"))
    with pytest.raises(Conflict):
        library.add(ConditionText("X-01", Category.GENERAL, "something else"))


def test_the_general_conditions_are_the_ones_on_every_permit():
    codes = [one.code for one in LIBRARY.of_category(Category.GENERAL)]
    assert codes == ["G-01", "G-02", "G-03"]
    assert "produce it on demand" in LIBRARY.get("G-01").text


def test_the_codes_come_back_sorted_for_the_printed_appendix():
    codes = LIBRARY.codes()
    assert codes[0] == "E-01"
    assert codes[-1] == "T-07"
