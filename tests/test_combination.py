"""The equipment string, and making it agree with the axles under it."""

import pytest

from jeepdolly.domain.combination import Combination, Unit, UnitKind
from jeepdolly.domain.envelope import Envelope
from jeepdolly.errors import Impossible
from jeepdolly.measure import Pounds
from support import jeeped_lowboy, tractor_lowboy

BOX = Envelope.read("12'", "13'6\"", "82'", "0", "6'")


def unit(kind, axles, plate="ME 4471", tare=18000, **kw):
    return Unit(kind, axles, plate, Pounds(tare), **kw)


def simple():
    return Combination(
        (
            unit(UnitKind.TRACTOR, 3, "ME 4471", 19000, unit_number="207"),
            unit(UnitKind.LOWBOY, 3, "ME 7712", 24000),
        ),
        tractor_lowboy(),
        BOX,
    )


def with_jeep():
    return Combination(
        (
            unit(UnitKind.TRACTOR, 3, "ME 4471", 19000),
            unit(UnitKind.JEEP, 2, "ME 8801", 9000),
            unit(UnitKind.LOWBOY, 3, "ME 7712", 24000),
            unit(UnitKind.BOOSTER, 1, "ME 8802", 5000),
        ),
        jeeped_lowboy(),
        BOX,
    )


def test_a_unit_prints_by_its_number_when_it_has_one():
    assert str(unit(UnitKind.TRACTOR, 3, unit_number="207")) == "tractor 207 (3 axle)"
    assert str(unit(UnitKind.LOWBOY, 3, "ME 7712")) == "lowboy ME 7712 (3 axle)"


def test_a_unit_needs_axles_a_plate_and_an_honest_tare():
    with pytest.raises(Impossible):
        unit(UnitKind.LOWBOY, 0)
    with pytest.raises(Impossible):
        unit(UnitKind.LOWBOY, 3, plate="  ")
    with pytest.raises(Impossible):
        unit(UnitKind.LOWBOY, 3, tare=-1)


def test_a_combination_reads_back_the_way_a_hauler_says_it():
    assert simple().describe() == "tractor, lowboy"
    assert with_jeep().describe() == "tractor, jeep dolly, lowboy, booster"
    assert str(simple()) == "tractor, lowboy, 6 axles, 94,000 lb"


def test_the_gross_is_the_stream_and_the_payload_is_what_is_left():
    combination = simple()
    assert combination.gross == Pounds(94000)
    assert combination.tare == Pounds(43000)
    assert combination.payload == Pounds(51000)


def test_the_equipment_axle_count_has_to_match_the_stream():
    with pytest.raises(Impossible) as refusal:
        Combination(
            (unit(UnitKind.TRACTOR, 3), unit(UnitKind.LOWBOY, 4)),
            tractor_lowboy(),
            BOX,
        )
    assert refusal.value.facts == {"declared": 7, "stream": 6}


def test_a_combination_with_nothing_to_pull_it_is_refused():
    with pytest.raises(Impossible):
        Combination((unit(UnitKind.LOWBOY, 6),), tractor_lowboy(), BOX)


def test_two_power_units_are_not_a_combination_this_office_writes():
    with pytest.raises(Impossible) as refusal:
        Combination(
            (unit(UnitKind.TRACTOR, 3), unit(UnitKind.TRUCK, 3)),
            tractor_lowboy(),
            BOX,
        )
    assert "one power unit" in str(refusal.value)


def test_a_string_with_nothing_to_put_the_load_on_is_refused():
    with pytest.raises(Impossible):
        Combination(
            (unit(UnitKind.TRACTOR, 3), unit(UnitKind.JEEP, 2), unit(UnitKind.BOOSTER, 1)),
            tractor_lowboy(),
            BOX,
        )


def test_a_jeep_behind_the_trailer_is_the_form_filled_in_backwards():
    with pytest.raises(Impossible) as refusal:
        Combination(
            (
                unit(UnitKind.TRACTOR, 3),
                unit(UnitKind.LOWBOY, 3),
                unit(UnitKind.JEEP, 2),
                unit(UnitKind.BOOSTER, 1),
            ),
            jeeped_lowboy(),
            BOX,
        )
    assert "ahead of the trailer" in str(refusal.value)


def test_a_booster_ahead_of_the_trailer_is_refused_the_same_way():
    with pytest.raises(Impossible) as refusal:
        Combination(
            (
                unit(UnitKind.TRACTOR, 3),
                unit(UnitKind.BOOSTER, 1),
                unit(UnitKind.JEEP, 2),
                unit(UnitKind.LOWBOY, 3),
            ),
            jeeped_lowboy(),
            BOX,
        )
    assert "behind the trailer" in str(refusal.value)


def test_the_parts_of_the_string_can_be_asked_for_by_what_they_are():
    combination = with_jeep()
    assert combination.power_unit.kind is UnitKind.TRACTOR
    assert combination.deck.kind is UnitKind.LOWBOY
    assert len(combination.jeeps) == 1
    assert len(combination.boosters) == 1
    assert combination.axle_count == 9
    assert combination.plates() == ("ME 4471", "ME 8801", "ME 7712", "ME 8802")


def test_the_kinds_know_what_they_do_in_the_string():
    assert UnitKind.TRACTOR.powered
    assert not UnitKind.JEEP.powered
    assert UnitKind.RGN.carries_load
    assert not UnitKind.BOOSTER.carries_load
    assert str(UnitKind.RGN) == "removable gooseneck"


def test_an_empty_combination_is_refused_before_anything_is_asked_of_it():
    with pytest.raises(Impossible):
        Combination((), tractor_lowboy(), BOX)
