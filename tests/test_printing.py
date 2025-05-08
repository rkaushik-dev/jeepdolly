"""The printed permit, which lives on a dashboard for a week."""

from datetime import date

import pytest

from jeepdolly.conditions.library import standard_library
from jeepdolly.errors import Impossible
from jeepdolly.permits.printing import WIDTH, centred, printed, sheet, two_up, widest, wrapped
from support import a_permit, an_application

LIBRARY = standard_library()
START = date(2025, 4, 8)


def permit(**kw):
    return a_permit(an_application(), **kw)


def test_the_sheet_is_eighty_columns_and_never_more():
    assert widest(permit()) <= WIDTH
    assert all(len(one) <= WIDTH for one in printed(permit()))


def test_the_rules_are_the_full_width():
    lines = printed(permit())
    assert lines[0] == "=" * WIDTH
    assert lines[-1] == "=" * WIDTH


def test_the_first_six_lines_are_what_an_officer_looks_for():
    lines = printed(permit())
    assert "MOTOR CARRIER PERMIT OFFICE" in lines[1]
    assert "OVERSIZE AND OVERWEIGHT PERMIT" in lines[2]
    assert lines[4].startswith("PERMIT 25-P-004471")
    assert lines[4].endswith("ISSUED 2025-04-07")
    assert lines[5].startswith("Bickford Heavy Haul (1204471)")
    assert "VALID 2025-04-08 TO 2025-04-10" in lines[5]


def test_the_size_and_the_weight_are_on_the_face():
    written = sheet(permit())
    assert "GROSS 94,000 lb" in written
    assert "SIZE  12 ft wide, 13 ft 6 in high, 82 ft long" in written
    assert "DISTANCE 35.20 mi" in written


def test_the_route_is_printed_as_the_driver_reads_it():
    written = sheet(permit())
    assert "ROUTE" in written
    assert "from Waterville, US 201 to Skowhegan" in written


def test_the_conditions_are_numbered_so_an_officer_can_name_one():
    lines = printed(permit())
    numbered = [one for one in lines if one.strip().startswith(("1.", "2."))]
    assert len(numbered) == 2
    assert numbered[0].strip().startswith("1. Route as written")


def test_a_long_condition_wraps_and_stays_under_the_margin():
    long_one = LIBRARY.render("S-01", {"structure": "Kennebec River bridge at Skowhegan"})
    lines = printed(permit(conditions=(LIBRARY.render("R-01"), long_one)))
    assert all(len(one) <= WIDTH for one in lines)


def test_the_fees_add_up_on_the_paper():
    written = sheet(permit())
    assert "ordinary permit" in written
    assert "TOTAL" in written
    assert "$46.12" in written


def test_a_void_permit_says_so_across_the_bottom():
    one = permit()
    one.void(START, "load cancelled")
    written = sheet(one)
    assert "*** VOID ***" in written


def test_a_live_permit_says_nothing_across_the_bottom():
    assert "***" not in sheet(permit())


def test_a_permit_with_no_conditions_cannot_be_printed():
    with pytest.raises(Impossible):
        printed(permit(conditions=()))


def test_the_helpers_do_what_the_layout_needs():
    assert len(centred("middle")) < WIDTH
    assert two_up("left", "right").endswith("right")
    assert len(two_up("left", "right")) == WIDTH
    assert wrapped("one two three", indent=76) == ("one", "two", "three")


def test_the_sheet_ends_with_a_newline_so_it_can_be_piped():
    assert sheet(permit()).endswith("\n")
