"""The wire bodies, and the rule that every measure crosses as a string."""

from datetime import date

import pytest
from pydantic import ValidationError

from jeepdolly.api.schemas import (
    ApplicationIn,
    ApplicationOut,
    AssessmentOut,
    ConditionOut,
    PermitOut,
    QuoteOut,
)
from jeepdolly.conditions.library import standard_library
from jeepdolly.domain.commodity import standard_book as commodities
from jeepdolly.errors import Impossible, NotOnFile
from jeepdolly.measure import Inches, Pounds
from support import a_permit, an_application

BOOK = commodities()
LIBRARY = standard_library()
FILED = date(2025, 4, 7)


def body(**kw):
    fields = {
        "usdot": 1204471,
        "commodity": "EXCV",
        "origin": "waterville",
        "destination": "pittsfield",
        "start": "2025-04-08",
        "end": "2025-04-10",
        "applicant": {"name": "Dana Bickford", "phone": "207 555 0143"},
        "envelope": {
            "width": "12'",
            "height": "13'6\"",
            "length": "82 ft",
            "rear_overhang": "6'",
        },
        "axles": [
            {"kind": "steer", "weight": "12,000 lb", "tires": 2, "tire_width": "16"},
            {"kind": "drive", "weight": "17000", "spacing_before": "200"},
            {"kind": "drive", "weight": "17000", "spacing_before": "54"},
            {"kind": "trailer", "weight": "16000", "spacing_before": "180"},
            {"kind": "trailer", "weight": "16000", "spacing_before": "52"},
            {"kind": "trailer", "weight": "16000", "spacing_before": "52"},
        ],
        "units": [
            {"kind": "tractor", "axles": 3, "plate": "ME 4471", "tare": "19000"},
            {"kind": "lowboy", "axles": 3, "plate": "ME 7712", "tare": "24000"},
        ],
        "pieces": [{"commodity": "EXCV", "description": "Cat 336 excavator"}],
    }
    fields.update(kw)
    return fields


def test_an_application_body_becomes_the_domain_object():
    application = ApplicationIn(**body()).to_domain("25-04471", FILED, BOOK)
    assert application.gross == Pounds(94000)
    assert application.combination.envelope.width == Inches.of(12)
    assert application.combination.stream.count == 6
    assert application.pieces[0].commodity.code == "EXCV"


def test_the_measures_are_read_in_whatever_the_hauler_writes():
    written = body()
    written["envelope"]["height"] = "14-2"
    written["axles"][0]["weight"] = "6 tons"
    application = ApplicationIn(**written).to_domain("25-04471", FILED, BOOK)
    assert application.combination.envelope.height == Inches(170)
    assert application.combination.stream.axles[0].weight == Pounds(12000)


def test_an_axle_with_no_spacing_in_front_of_it_is_refused():
    written = body()
    del written["axles"][2]["spacing_before"]
    with pytest.raises(Impossible) as refusal:
        ApplicationIn(**written).to_domain("25-04471", FILED, BOOK)
    assert "how far behind it stands" in str(refusal.value)


def test_a_commodity_nobody_filed_is_refused_when_the_pieces_are_read():
    written = body()
    written["pieces"][0]["commodity"] = "ZZZZ"
    with pytest.raises(NotOnFile):
        ApplicationIn(**written).to_domain("25-04471", FILED, BOOK)


def test_a_body_with_no_axles_at_all_is_refused_by_the_schema():
    with pytest.raises(ValidationError):
        ApplicationIn(**body(axles=[]))
    with pytest.raises(ValidationError):
        ApplicationIn(**body(pieces=[]))


def test_a_body_missing_something_it_needs_is_refused_by_the_schema():
    written = body()
    del written["usdot"]
    with pytest.raises(ValidationError):
        ApplicationIn(**written)


def test_an_application_goes_out_with_its_measures_as_words():
    out = ApplicationOut.of(an_application())
    assert out.gross == "94,000 lb"
    assert out.width == "12 ft"
    assert out.height == "13 ft 6 in"
    assert out.state == "draft"


def test_a_permit_goes_out_whole_with_conditions_and_fees():
    application = an_application()
    out = PermitOut.of(a_permit(application))
    assert out.number == "25-P-004471"
    assert out.gross == "94,000 lb"
    assert out.distance == "35.20 mi"
    assert out.route == ["US201-1", "US201-2", "SA148-2", "SR43-1"]
    assert out.conditions[0].code == "R-01"
    assert out.fees.total == "$46.12"


def test_the_fee_lines_come_out_as_money_the_way_the_receipt_prints_it():
    application = an_application()
    quote = QuoteOut.of(a_permit(application).quote)
    assert quote.lines[0].amount == "$40.00"
    assert quote.schedule == "schedule of 2024-01-01"


def test_a_condition_carries_its_code_and_the_words_that_were_printed():
    out = ConditionOut.of(LIBRARY.render("G-01"))
    assert out.code == "G-01"
    assert out.category == "general"
    assert out.text.startswith("Carry this permit")


def test_an_assessment_that_was_refused_carries_the_reasons_and_no_price():
    out = AssessmentOut(
        reference="25-04471",
        permittable=False,
        kind="none",
        refusals=["GRVL could have been loaded to legal weight"],
        route=[],
    )
    assert out.fees is None
    assert out.conditions == []
    assert out.refusals[0].startswith("GRVL")
