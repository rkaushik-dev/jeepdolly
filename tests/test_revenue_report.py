"""Where the money came from, cut three ways."""

from datetime import date

import pytest

from jeepdolly.errors import Impossible
from jeepdolly.fees.price import Line, Quote
from jeepdolly.money import Dollars
from jeepdolly.permits.numbering import PermitNumber, Series
from jeepdolly.persistence.unitofwork import UnitOfWork
from jeepdolly.reports.revenue import kind_of, owed_by, revenue
from support import a_permit, a_seeded_database, an_application

APRIL = date(2025, 4, 7)
MAY = date(2025, 5, 6)


@pytest.fixture
def factory():
    return a_seeded_database()


def quote(*lines):
    return Quote(tuple(Line(one, two) for one, two in lines), "schedule of 2024-01-01")


def written(factory, permits):
    application = an_application()
    with UnitOfWork(factory) as work:
        work.applications.add(application)
        work.commit()
        for sequence, issued, lines in permits:
            work.permits.add(
                a_permit(
                    application,
                    number=PermitNumber(2025, Series.SINGLE, sequence),
                    issued=issued,
                    start=max(issued, application.start),
                    end=max(issued, application.end),
                    quote=quote(*lines),
                )
            )
        work.commit()


def test_a_line_is_grouped_by_what_it_says_it_is():
    assert kind_of("ordinary permit") == "base fee"
    assert kind_of("34 miles on arterial") == "mileage"
    assert kind_of("25,000 lb over legal gross") == "weight"
    assert kind_of("engineering review") == "review"
    assert kind_of("minimum fee adjustment") == "minimum"
    assert kind_of("something nobody named") == "other"


def test_the_kinds_add_up_to_what_was_taken(factory):
    written(
        factory,
        [
            (
                1,
                APRIL,
                [("ordinary permit", Dollars.of(40)), ("34 miles on arterial", Dollars.of(6))],
            )
        ],
    )
    with UnitOfWork(factory) as work:
        found = revenue(work.db, APRIL, MAY)
    assert found.kind("base fee") == Dollars.of(40)
    assert found.kind("mileage") == Dollars.of(6)
    assert found.taken == Dollars.of(46)
    assert found.permits == 1


def test_a_kind_nobody_charged_is_nothing_rather_than_missing(factory):
    written(factory, [(1, APRIL, [("ordinary permit", Dollars.of(40))])])
    with UnitOfWork(factory) as work:
        assert revenue(work.db, APRIL, MAY).kind("weight") == Dollars(0)


def test_the_months_are_kept_apart_for_the_budget(factory):
    written(
        factory,
        [
            (1, APRIL, [("ordinary permit", Dollars.of(40))]),
            (2, MAY, [("ordinary permit", Dollars.of(40))]),
        ],
    )
    with UnitOfWork(factory) as work:
        found = revenue(work.db, APRIL, MAY)
    assert found.by_month == {"2025-04": Dollars.of(40), "2025-05": Dollars.of(40)}


def test_a_carrier_can_be_handed_its_own_total(factory):
    written(factory, [(1, APRIL, [("ordinary permit", Dollars.of(40))])])
    with UnitOfWork(factory) as work:
        assert owed_by(work.db, "Bickford Heavy Haul", APRIL, MAY) == Dollars.of(40)
        assert owed_by(work.db, "Somebody Else", APRIL, MAY) == Dollars(0)


def test_a_voided_permit_is_still_counted_because_the_money_was_taken(factory):
    written(factory, [(1, APRIL, [("ordinary permit", Dollars.of(40))])])
    with UnitOfWork(factory) as work:
        permit = work.permits.get("25-P-000001")
        permit.void(APRIL, "load cancelled")
        work.permits.save_state(permit)
        work.commit()
        found = revenue(work.db, APRIL, MAY)
    assert found.taken == Dollars.of(40)
    assert "voided permits are counted" in found.lines()[-1]


def test_the_report_prints_the_kinds_in_a_stable_order(factory):
    written(
        factory,
        [
            (
                1,
                APRIL,
                [
                    ("ordinary permit", Dollars.of(40)),
                    ("25,000 lb over legal gross", Dollars.of(140)),
                ],
            )
        ],
    )
    with UnitOfWork(factory) as work:
        lines = revenue(work.db, APRIL, MAY).lines()
    assert lines[1] == "base fee  $40.00"
    assert lines[2] == "weight  $140.00"
    assert lines[3] == "1 permits  $180.00"


def test_a_span_that_ends_before_it_starts_is_refused(factory):
    with UnitOfWork(factory) as work, pytest.raises(Impossible):
        revenue(work.db, MAY, APRIL)


def test_a_span_with_nothing_in_it_takes_nothing(factory):
    with UnitOfWork(factory) as work:
        found = revenue(work.db, APRIL, MAY)
    assert found.taken == Dollars(0)
    assert str(found) == "0 permits, $0.00"
