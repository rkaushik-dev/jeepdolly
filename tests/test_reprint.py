"""Duplicate copies, which the office would rather not issue and has to."""

from datetime import date

import pytest

from jeepdolly.errors import Impossible, NotAllowed
from jeepdolly.permits.printing import WIDTH, printed, sheet
from jeepdolly.permits.reprint import (
    FREELY,
    Reason,
    Reprint,
    allowed,
    next_copy,
    reprint,
)
from support import a_permit, an_application

START = date(2025, 4, 8)
INSIDE = date(2025, 4, 9)
AFTER = date(2025, 5, 1)


def permit(**kw):
    return a_permit(an_application(), **kw)


def a_reprint(copy=2, reason=Reason.LOST, note="", **kw):
    fields = {
        "permit": "25-P-004471",
        "copy": copy,
        "on": INSIDE,
        "reason": reason,
        "asked_by": "D Bickford",
        "issued_by": "M Cyr",
        "note": note,
    }
    fields.update(kw)
    return Reprint(**fields)


def test_the_reasons_are_the_words_the_counter_uses():
    assert str(Reason.LEFT_BEHIND) == "left in another unit"
    assert Reason.OTHER.wants_words
    assert not Reason.LOST.wants_words


def test_the_original_is_copy_one_and_is_not_a_reprint():
    with pytest.raises(Impossible):
        a_reprint(copy=1)


def test_a_reprint_names_who_asked_for_it():
    with pytest.raises(Impossible):
        a_reprint(asked_by="  ")


def test_a_reprint_for_another_reason_says_what_the_reason_was():
    with pytest.raises(Impossible):
        a_reprint(reason=Reason.OTHER)
    assert a_reprint(reason=Reason.OTHER, note="wanted one for the file").copy == 2


def test_a_reprint_prints_the_way_the_register_reads():
    assert str(a_reprint()) == "25-P-004471 copy 2  2025-04-09  lost"
    assert str(a_reprint(reason=Reason.OTHER, note="for the file")).endswith("other, for the file")


def test_the_banner_says_which_copy_it_is():
    assert a_reprint(copy=3).banner() == "DUPLICATE - COPY 3"


def test_a_copy_inside_the_threshold_wants_no_supervisor():
    assert not a_reprint(copy=FREELY).needed_a_supervisor


def test_a_copy_past_it_does():
    assert a_reprint(copy=FREELY + 1).needed_a_supervisor


def test_the_first_reprint_is_copy_two():
    assert next_copy(()) == 2


def test_the_next_copy_comes_off_the_highest_and_not_off_the_count():
    assert next_copy((2, 3)) == 4
    assert next_copy((3,)) == 4


def test_a_live_permit_may_be_copied():
    allowed(permit(), INSIDE, 2, supervisor=False)


def test_a_void_permit_may_not():
    one = permit()
    one.void(INSIDE, "load cancelled")
    with pytest.raises(NotAllowed):
        allowed(one, INSIDE, 2, supervisor=False)


def test_an_expired_permit_may_not_even_for_the_haulers_own_file():
    with pytest.raises(NotAllowed) as trouble:
        allowed(permit(), AFTER, 2, supervisor=False)
    assert "expired on" in str(trouble.value)


def test_a_fourth_copy_wants_a_supervisor():
    with pytest.raises(NotAllowed) as trouble:
        allowed(permit(), INSIDE, FREELY + 1, supervisor=False)
    assert "wants a supervisor" in str(trouble.value)


def test_a_supervisor_may_print_the_fourth():
    allowed(permit(), INSIDE, FREELY + 1, supervisor=True)


def test_reprinting_works_out_the_copy_number_and_records_it():
    found = reprint(permit(), INSIDE, Reason.DAMAGED, "D Bickford", "M Cyr")
    assert found.copy == 2
    assert found.permit == "25-P-004471"
    assert found.reason is Reason.DAMAGED


def test_reprinting_a_permit_that_already_has_copies_carries_on_the_sequence():
    found = reprint(permit(), INSIDE, Reason.LOST, "D Bickford", "M Cyr", taken=(2,))
    assert found.copy == 3
    assert not found.needed_a_supervisor


def test_reprinting_past_the_threshold_without_a_supervisor_is_refused():
    with pytest.raises(NotAllowed):
        reprint(permit(), INSIDE, Reason.LOST, "D Bickford", "M Cyr", taken=(2, 3, 4))


def test_reprinting_past_it_with_one_is_allowed():
    found = reprint(
        permit(), INSIDE, Reason.LOST, "D Bickford", "M Cyr", taken=(2, 3, 4), supervisor=True
    )
    assert found.copy == 5
    assert found.needed_a_supervisor


def test_a_reprint_never_touches_the_permit():
    one = permit()
    before = (one.state, one.number, one.conditions)
    reprint(one, INSIDE, Reason.LOST, "D Bickford", "M Cyr")
    assert (one.state, one.number, one.conditions) == before


class TestThePrintedCopy:
    """What the duplicate sheet says that the original does not."""

    def test_the_original_carries_no_banner(self):
        assert "DUPLICATE" not in sheet(permit())

    def test_a_copy_carries_one_at_the_top_and_one_at_the_foot(self):
        lines = printed(permit(), copy=2)
        assert lines[3].strip() == "*** DUPLICATE - COPY 2 ***"
        assert lines[-2].strip() == "*** DUPLICATE - COPY 2 ***"

    def test_the_copy_number_is_on_both_banners(self):
        assert sheet(permit(), copy=4).count("*** DUPLICATE - COPY 4 ***") == 2

    def test_a_copy_is_still_eighty_columns(self):
        assert all(len(one) <= WIDTH for one in printed(permit(), copy=2))

    def test_a_void_copy_says_both_things(self):
        one = permit()
        one.void(INSIDE, "load cancelled")
        written = sheet(one, copy=2)
        assert "*** DUPLICATE - COPY 2 ***" in written
        assert "*** VOID ***" in written

    def test_the_conditions_are_the_same_on_a_copy(self):
        assert printed(permit()).count("CONDITIONS") == printed(permit(), copy=2).count(
            "CONDITIONS"
        )
