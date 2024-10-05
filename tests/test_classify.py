"""The ladder: nothing, ordinary, study, superload, and the two things that beat it."""

from datetime import date

from jeepdolly.domain.commodity import standard_book as commodities
from jeepdolly.domain.envelope import Envelope
from jeepdolly.domain.highway import HighwayClass
from jeepdolly.limits import weight
from jeepdolly.limits.classify import STANDARD, PermitKind, Thresholds, classify
from jeepdolly.limits.size import SizeClass
from jeepdolly.limits.size import standard_book as sizes
from jeepdolly.limits.statutory import standard_book as statutes
from jeepdolly.measure import Inches, Pounds
from support import make_stream, tractor_lowboy

MARCH = date(2025, 3, 3)
BOOK = commodities()
SIZE = sizes().on(HighwayClass.ARTERIAL)
TABLE = statutes().on(HighwayClass.ARTERIAL, MARCH)


def legal_semi():
    return make_stream([12000, 17000, 17000, 17000, 17000], [200, 54, 324, 54])


def box(width="8'", height="13'", length="70'"):
    return Envelope.read(width, height, length)


def read(stream):
    return weight.read(stream, TABLE)


def test_a_legal_load_needs_no_permit_and_is_told_so():
    verdict = classify(box(), read(legal_semi()), SIZE, BOOK.get("EXCV"))
    assert verdict.kind is PermitKind.NONE
    assert verdict.size is SizeClass.LEGAL
    assert not verdict.overweight
    assert not verdict.permittable
    assert str(verdict) == "no permit needed"


def test_a_wide_excavator_inside_the_routine_box_is_an_ordinary_permit():
    verdict = classify(box(width="12'"), read(legal_semi()), SIZE, BOOK.get("EXCV"))
    assert verdict.kind is PermitKind.ORDINARY
    assert verdict.kind.issuable_at_the_counter
    assert not verdict.kind.needs_engineer
    assert str(verdict) == "ordinary"


def test_a_legal_size_load_that_is_overweight_is_still_an_ordinary_permit():
    verdict = classify(box(), read(tractor_lowboy()), SIZE, BOOK.get("EXCV"))
    assert verdict.kind is PermitKind.ORDINARY
    assert verdict.overweight
    assert verdict.size is SizeClass.LEGAL


def test_a_load_past_the_routine_box_goes_to_an_engineer_and_says_why():
    verdict = classify(box(height="15'8"), read(legal_semi()), SIZE, BOOK.get("EXCV"))
    assert verdict.kind is PermitKind.STUDY
    assert verdict.kind.needs_engineer
    assert verdict.review_reasons == ("height",)
    assert str(verdict) == "study on height"


def test_a_load_past_a_published_threshold_is_a_superload():
    heavy = make_stream([12000, 30000, 30000, 30000, 30000, 30000], [200, 54, 240, 54, 54])
    verdict = classify(box(), read(heavy), SIZE, BOOK.get("XFMR"))
    assert verdict.kind is PermitKind.SUPERLOAD
    assert "gross" in verdict.superload_reasons
    assert "axle group" in verdict.superload_reasons


def test_the_superload_thresholds_beat_the_review_reasons_in_the_report():
    huge = Envelope.read("20'", "18'", "160'")
    verdict = classify(huge, read(legal_semi()), SIZE, BOOK.get("XFMR"))
    assert verdict.kind is PermitKind.SUPERLOAD
    assert verdict.superload_reasons == ("width", "height", "length")
    assert verdict.review_reasons == ()
    assert str(verdict) == "superload on width, height, length"


def test_a_divisible_load_gets_nothing_however_big_the_numbers_are():
    verdict = classify(box(width="17'"), read(tractor_lowboy()), SIZE, BOOK.get("GRVL"))
    assert verdict.kind is PermitKind.NONE
    assert not verdict.permittable
    assert verdict.refusal == "GRVL could have been loaded to legal weight"
    assert str(verdict) == "no permit: GRVL could have been loaded to legal weight"


def test_a_divisible_load_with_an_allowance_walks_the_ladder_like_anything_else():
    verdict = classify(box(), read(tractor_lowboy()), SIZE, BOOK.get("WOOD"))
    assert verdict.kind is PermitKind.ORDINARY
    assert verdict.permittable


def test_a_divisible_load_that_is_legal_anyway_is_never_refused():
    verdict = classify(box(), read(legal_semi()), SIZE, BOOK.get("GRVL"))
    assert verdict.kind is PermitKind.NONE
    assert verdict.refusal is None


def test_the_thresholds_are_data_and_a_stricter_set_moves_the_line():
    strict = Thresholds(
        gross=Pounds(90000),
        axle_group=Pounds(40000),
        width=Inches.of(10),
        height=Inches.of(14),
        length=Inches.of(80),
    )
    ordinary = classify(box(width="12'"), read(legal_semi()), SIZE, BOOK.get("EXCV"))
    tightened = classify(box(width="12'"), read(legal_semi()), SIZE, BOOK.get("EXCV"), strict)
    assert ordinary.kind is PermitKind.ORDINARY
    assert tightened.kind is PermitKind.SUPERLOAD
    assert tightened.superload_reasons == ("width",)


def test_the_published_thresholds_are_the_ones_the_office_works_to():
    assert STANDARD.gross == Pounds(150000)
    assert STANDARD.width == Inches.of(16)
    assert str(PermitKind.SUPERLOAD) == "superload"
