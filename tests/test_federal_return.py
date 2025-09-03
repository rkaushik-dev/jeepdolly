"""The federal return, which is eighty columns and rejected on one bad one."""

from datetime import date

import pytest

from jeepdolly.exports.federal import (
    LAYOUT,
    STATE_CODE,
    WIDTH,
    Detail,
    Return,
    build,
    day,
    detail_of,
    issued_between,
    money,
    number,
    superseded_count,
    text,
)
from jeepdolly.money import Dollars
from jeepdolly.permits.numbering import PermitNumber
from jeepdolly.persistence.unitofwork import UnitOfWork
from support import a_permit, a_seeded_database, an_application

APRIL = date(2025, 4, 1)
LAST = date(2025, 4, 30)


@pytest.fixture
def work():
    with UnitOfWork(a_seeded_database()) as unit:
        yield unit


def a_detail(**kw):
    fields = {
        "number": "25-P-004471",
        "issued": date(2025, 4, 7),
        "usdot": 1204471,
        "carrier": "Bickford Heavy Haul",
        "kind": "P",
        "gross_pounds": 94000,
        "miles": 35,
        "fee": Dollars.of(46, 12),
    }
    fields.update(kw)
    return Detail(**fields)


def issued(work, reference="25-04471", **kw):
    application = an_application(reference=reference)
    work.applications.add(application)
    work.db.flush()
    permit = a_permit(application, **kw)
    work.permits.add(permit)
    work.db.flush()
    return permit


def test_text_is_left_justified_and_blank_filled():
    assert text("ME", 4) == "ME  "


def test_text_too_long_is_cut_on_the_right_rather_than_refused():
    assert text("Bickford Heavy Haulage Company", 10) == "Bickford H"


def test_a_field_with_no_width_is_a_mistake():
    with pytest.raises(ValueError):
        text("ME", 0)
    with pytest.raises(ValueError):
        number(1, 0)


def test_numbers_are_right_justified_and_zero_filled():
    assert number(35, 5) == "00035"
    assert number(0, 3) == "000"


def test_a_number_too_wide_is_cut_on_the_left_so_the_record_stays_eighty():
    assert number(1234567, 4) == "4567"


def test_the_layout_carries_no_sign():
    with pytest.raises(ValueError):
        number(-1, 5)


def test_dates_are_eight_digits_year_first():
    assert day(date(2025, 4, 7)) == "20250407"


def test_money_is_whole_dollars_with_no_point():
    assert money(Dollars.of(46, 12)) == "00000046"
    assert money(Dollars.of(1234, 99)) == "00001234"


def test_a_detail_record_is_exactly_eighty_columns():
    assert len(a_detail().render()) == WIDTH


def test_a_detail_record_starts_with_its_kind_letter():
    assert a_detail().render().startswith("D25-P-004471")


def test_a_detail_record_carries_the_fields_in_their_columns():
    printed = a_detail().render()
    assert printed[13:21] == "20250407"
    assert printed[21:30] == "001204471"
    assert printed[30:54] == "Bickford Heavy Haul".ljust(24)
    assert printed[54] == "P"
    assert printed[55:62] == "0094000"
    assert printed[62:67] == "00035"
    assert printed[67:75] == "00000046"


def test_a_long_carrier_name_does_not_lengthen_the_record():
    long_one = a_detail(carrier="Aroostook County Crane and Rigging Service")
    assert len(long_one.render()) == WIDTH


def test_a_header_is_eighty_columns_and_names_the_window():
    made = Return(APRIL, LAST, ())
    printed = made.header()
    assert len(printed) == WIDTH
    assert printed.startswith("H" + STATE_CODE + LAYOUT + "20250401" + "20250430")


def test_a_trailer_is_eighty_columns_and_carries_the_counts():
    made = Return(APRIL, LAST, (a_detail(), a_detail(number="25-P-004472")))
    printed = made.trailer()
    assert len(printed) == WIDTH
    assert printed[1:8] == "0000002"
    assert printed[8:16] == "00000092"
    assert printed[16:28] == "000000188000"


def test_an_empty_return_is_two_records_and_still_valid():
    made = Return(APRIL, LAST, ())
    assert len(made.lines()) == 2
    assert all(len(one) == WIDTH for one in made.lines())
    assert made.count == 0
    assert made.fees == Dollars.of(0)


def test_the_totals_are_added_from_the_details_that_were_written():
    made = Return(APRIL, LAST, (a_detail(), a_detail(fee=Dollars.of(100))))
    assert made.fees == Dollars.of(146, 12)
    assert made.pounds == 188000


def test_the_file_ends_with_a_newline_so_it_can_be_piped():
    assert Return(APRIL, LAST, ()).render().endswith("\n")


def test_every_line_of_a_rendered_return_is_eighty_columns():
    made = Return(APRIL, LAST, (a_detail(),))
    assert all(len(one) == WIDTH for one in made.render().splitlines())


def test_the_return_says_what_it_is_in_one_line():
    made = Return(APRIL, LAST, (a_detail(),))
    assert str(made) == "2025-04-01 to 2025-04-30, 1 permits"


def test_a_permit_row_becomes_a_detail_record(work):
    permit = issued(work)
    found = detail_of(work.permits.row(str(permit.number)))
    assert found.number == "25-P-004471"
    assert found.usdot == 1204471
    assert found.kind == "P"
    assert found.gross_pounds == 94000
    assert found.fee == Dollars.of(46, 12)


def test_the_mileage_is_whole_miles_rounded_down(work):
    permit = issued(work)
    found = detail_of(work.permits.row(str(permit.number)))
    assert found.miles == 35


def test_a_month_with_nothing_in_it_builds_an_empty_return(work):
    made = build(work.db, APRIL, LAST)
    assert made.count == 0
    assert len(made.lines()) == 2


def test_a_month_with_one_permit_builds_three_records(work):
    issued(work)
    made = build(work.db, APRIL, LAST)
    assert made.count == 1
    assert len(made.lines()) == 3
    assert made.lines()[1].startswith("D25-P-004471")


def test_a_permit_issued_outside_the_window_is_not_in_the_return(work):
    issued(work)
    made = build(work.db, date(2025, 5, 1), date(2025, 5, 31))
    assert made.count == 0


def test_a_voided_permit_is_still_in_the_return(work):
    permit = issued(work)
    permit.void(date(2025, 4, 9), "load cancelled")
    work.permits.save_state(permit)
    work.db.flush()
    assert build(work.db, APRIL, LAST).count == 1


def test_the_permits_of_a_window_come_back_in_number_order(work):
    issued(work, reference="25-04472", number=PermitNumber.parse("25-P-004472"))
    issued(work)
    found = issued_between(work.db, APRIL, LAST)
    assert [one.number for one in found] == ["25-P-004471", "25-P-004472"]


def test_the_covering_letter_counts_what_has_since_been_replaced(work):
    permit = issued(work)
    assert superseded_count(work.db, APRIL, LAST) == 0
    permit.supersede(date(2025, 4, 9), "25-R-000001")
    work.permits.save_state(permit)
    work.db.flush()
    assert superseded_count(work.db, APRIL, LAST) == 1
