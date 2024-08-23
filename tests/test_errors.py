"""The refusal root, its codes, and the shape a refusal is written down in."""

import pytest

from jeepdolly.errors import (
    BY_CODE,
    Blocked,
    Conflict,
    Impossible,
    NotAllowed,
    NotOnFile,
    OverLimit,
    Refusal,
    Unreadable,
)
from jeepdolly.measure import Inches


def test_every_refusal_carries_a_code_that_is_not_the_class_name():
    assert Unreadable.code == "unreadable"
    assert Impossible.code == "impossible"
    assert NotOnFile.code == "not_on_file"
    assert Conflict.code == "conflict"
    assert OverLimit.code == "over_limit"
    assert Blocked.code == "blocked"
    assert NotAllowed.code == "not_allowed"


def test_the_code_table_holds_all_seven_and_no_root():
    assert len(BY_CODE) == 7
    assert BY_CODE["conflict"] is Conflict
    assert "refused" not in BY_CODE


def test_a_refusal_prints_with_its_code_in_front():
    refusal = OverLimit("the tandem is 4,000 lb over")
    assert str(refusal) == "over_limit: the tandem is 4,000 lb over"
    assert refusal.message == "the tandem is 4,000 lb over"


def test_a_refusal_carries_the_facts_behind_it_for_the_letter():
    refusal = Blocked("route 27 is posted", segment="27-014", order="25-04")
    assert refusal.facts == {"segment": "27-014", "order": "25-04"}
    assert refusal.as_dict() == {
        "code": "blocked",
        "message": "route 27 is posted",
        "facts": {"segment": "27-014", "order": "25-04"},
    }


def test_the_facts_of_one_refusal_are_not_shared_with_the_next():
    first = Conflict("already on file", order="25-04")
    written = first.as_dict()
    written["facts"]["order"] = "25-99"
    assert first.facts["order"] == "25-04"


def test_a_refusal_with_nothing_behind_it_still_writes_a_body():
    assert NotOnFile("no such carrier").as_dict() == {
        "code": "not_on_file",
        "message": "no such carrier",
        "facts": {},
    }


def test_everything_can_be_caught_as_one_root():
    for kind in BY_CODE.values():
        with pytest.raises(Refusal):
            raise kind("no")


def test_a_measure_that_cannot_be_read_refuses_and_says_what_was_written():
    with pytest.raises(Unreadable) as refusal:
        Inches.parse("about fourteen feet")
    assert refusal.value.facts["written"] == "about fourteen feet"
    assert refusal.value.code == "unreadable"


def test_handing_the_wrong_type_in_is_a_defect_and_not_a_refusal():
    with pytest.raises(TypeError):
        Inches(14.5)
    assert not issubclass(TypeError, Refusal)
