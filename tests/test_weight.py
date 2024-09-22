"""One reading of a stream against the gross, the groups, the formula, the tires."""

from datetime import date

from jeepdolly.domain.highway import HighwayClass
from jeepdolly.limits import weight
from jeepdolly.limits.statutory import standard_book
from jeepdolly.measure import Pounds
from jeepdolly.seasons import SeasonKind, SeasonOrder
from support import make_stream, tractor_lowboy

MARCH = date(2025, 3, 3)
THAW = SeasonOrder("25-04", "district 3", SeasonKind.THAW, MARCH, date(2025, 5, 2), 65)


def table(highway_class=HighwayClass.ARTERIAL):
    return standard_book().on(highway_class, MARCH)


def legal_semi():
    return make_stream([12000, 17000, 17000, 17000, 17000], [200, 54, 324, 54])


def test_a_legal_semi_reads_legal_on_every_rule():
    reading = weight.read(legal_semi(), table())
    assert reading.legal
    assert not reading.overweight
    assert reading.reasons() == ()
    assert str(reading) == "legal weight"


def test_the_lowboy_reads_overweight_on_the_formula_and_nothing_else():
    reading = weight.read(tractor_lowboy(), table())
    assert reading.overweight
    assert reading.gross_over == Pounds(0)
    assert reading.group_failures == ()
    assert len(reading.formula_failures) == 6


def test_the_same_lowboy_misses_a_group_limit_on_a_secondary_road():
    reading = weight.read(tractor_lowboy(), table(HighwayClass.STATE_AID))
    assert len(reading.group_failures) == 1
    assert reading.group_failures[0].group.name == "tridem"
    assert reading.group_failures[0].over == Pounds(4000)


def test_the_group_finding_reads_as_a_line_of_the_letter():
    reading = weight.read(tractor_lowboy(), table(HighwayClass.STATE_AID))
    line = str(reading.group_failures[0])
    assert line == "group 3, tridem 48,000 lb, allowed 44,000 lb, over by 4,000 lb"


def test_the_gross_is_reported_against_the_road_it_is_on():
    stream = make_stream([12000, 20000, 20000, 20000, 20000, 20000], [200, 54, 240, 54, 54])
    on_state = weight.read(stream, table())
    on_interstate = weight.read(stream, table(HighwayClass.INTERSTATE))
    assert stream.gross == Pounds(112000)
    assert on_state.gross_over == Pounds(12000)
    assert on_interstate.gross_over == Pounds(32000)


def test_a_narrow_tire_is_caught_even_when_the_group_is_inside_its_limit():
    stream = make_stream([12000, 17000, 17000], [200, 54])
    thin = weight.read(stream, table(HighwayClass.INTERSTATE))
    assert thin.tire_failures == ()
    heavy = make_stream([12000, 30000, 30000], [200, 54])
    reading = weight.read(heavy, table(HighwayClass.INTERSTATE))
    assert len(reading.tire_failures) == 2
    assert str(reading.tire_failures[0]) == "axle 2 carries 30,000 lb on tires rated 22,000 lb"


def test_a_thaw_order_cuts_the_group_and_gross_limits_together():
    stream = legal_semi()
    open_road = weight.read(stream, table())
    posted = weight.read(stream, table(), order=THAW)
    assert open_road.legal
    assert posted.overweight
    assert posted.gross_limit == Pounds(65000)
    assert posted.gross_over == Pounds(15000)
    assert posted.order is THAW


def test_a_thaw_order_does_not_move_the_formula_because_bridges_do_not_thaw():
    stream = legal_semi()
    posted = weight.read(stream, table(), order=THAW)
    tandems = next(one for one in posted.formula if one.span.first == 1 and one.span.last == 4)
    assert tandems.allowed == Pounds(65000) or tandems.exception == "two tandem exception"
    formula_only = weight.read(stream, table())
    assert len(formula_only.formula) == len(posted.formula)


def test_the_worst_overage_is_what_the_fee_will_be_priced_on():
    reading = weight.read(tractor_lowboy(), table())
    assert reading.worst_overage() == Pounds(16500)


def test_the_reasons_lead_with_the_gross_and_then_walk_the_rules():
    stream = make_stream([12000, 20000, 20000, 20000, 20000, 20000], [200, 54, 240, 54, 54])
    reading = weight.read(stream, table())
    first = reading.reasons()[0]
    assert first == "gross 112,000 lb is 12,000 lb over 100,000 lb"
    assert any(line.startswith("group") for line in reading.reasons())


def test_a_legal_reading_has_nothing_to_say_and_says_it_quickly():
    reading = weight.read(legal_semi(), table())
    assert reading.group_failures == ()
    assert reading.formula_failures == ()
    assert reading.tire_failures == ()
    assert reading.worst_overage() == Pounds(0)
