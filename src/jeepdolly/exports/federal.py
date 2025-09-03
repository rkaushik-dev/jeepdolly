"""The monthly return to the federal office, which is a fixed width file.

Every state sends one and the layout has not changed since 1994. It is eighty
character records, blank padded, in three kinds: one header, one detail record
per permit issued in the month, and one trailer carrying the counts and the
totals. The receiving system reads it with a COBOL copybook and rejects the
whole file on one bad column.

So the rules here are about columns rather than about permits.

Every field is a fixed slice and every record is exactly eighty characters. A
value too long for its field is truncated on the right, not wrapped and not
rejected, because the federal office would rather have a shortened carrier
name than no return.

Numbers are right justified and zero filled. Text is left justified and blank
filled. Money is in whole dollars with no point and no sign, because the
layout has no room for cents and the return has never carried a refund.

Dates are eight digits, year first. That is the one part of the layout anybody
has ever thanked them for.

The trailer is what gets checked. It carries the record count and the sum of
the two money fields, and the receiving system recomputes both. A return whose
trailer disagrees with its detail is rejected in full and comes back three
weeks later, so both totals are built by adding up the detail records that
were actually written rather than by asking the database a second time.

A voided permit is in the return. It was issued, the money was taken, and the
federal count is of permits issued rather than of loads that moved. That has
been queried twice and is right both times.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..money import Dollars
from ..permits.permit import PermitState
from ..persistence.models.permits import PermitRow

#: Every record in the file is this wide, blank padded.
WIDTH = 80

#: What the receiving system calls this office.
STATE_CODE = "23"

#: The layout version, which has not moved since 1994 and is sent anyway.
LAYOUT = "0003"


def text(value: str, width: int) -> str:
    """Left justified, blank filled, truncated on the right."""
    if width <= 0:
        raise ValueError("a field has a width")
    return value[:width].ljust(width)


def number(value: int, width: int) -> str:
    """Right justified, zero filled, truncated on the left.

    Truncating a number on the left is wrong in every sense except the one
    that matters: the alternative is a record of the wrong length, and a file
    with one of those in it is rejected in full. It has never happened, the
    widths are generous, and it is asserted in the tests so that if it ever
    does happen somebody has already thought about it.
    """
    if width <= 0:
        raise ValueError("a field has a width")
    if value < 0:
        raise ValueError("the federal layout carries no sign")
    return str(value).rjust(width, "0")[-width:]


def day(value: date) -> str:
    """Eight digits, year first, which is the one civilised part of this."""
    return value.strftime("%Y%m%d")


def money(value: Dollars) -> str:
    """Whole dollars, no point, no sign, eight columns."""
    return number(value.whole_dollars, 8)


@dataclass(frozen=True)
class Detail:
    """One permit as one eighty character record."""

    number: str
    issued: date
    usdot: int
    carrier: str
    kind: str
    gross_pounds: int
    miles: int
    fee: Dollars

    def render(self) -> str:
        return (
            "D"
            + text(self.number, 12)
            + day(self.issued)
            + number(self.usdot, 9)
            + text(self.carrier, 24)
            + text(self.kind, 1)
            + number(self.gross_pounds, 7)
            + number(self.miles, 5)
            + money(self.fee)
            + " " * 5
        )


@dataclass(frozen=True)
class Return:
    """A whole month's return, header, detail and trailer."""

    start: date
    end: date
    details: tuple[Detail, ...]

    @property
    def count(self) -> int:
        return len(self.details)

    @property
    def fees(self) -> Dollars:
        found = Dollars.of(0)
        for one in self.details:
            found = found + one.fee
        return found

    @property
    def pounds(self) -> int:
        return sum(one.gross_pounds for one in self.details)

    def header(self) -> str:
        return (
            "H"
            + STATE_CODE
            + LAYOUT
            + day(self.start)
            + day(self.end)
            + text("OVERSIZE OVERWEIGHT PERMIT RETURN", 40)
            + " " * 17
        )

    def trailer(self) -> str:
        return "T" + number(self.count, 7) + money(self.fees) + number(self.pounds, 12) + " " * 52

    def lines(self) -> tuple[str, ...]:
        found = [self.header()]
        found.extend(one.render() for one in self.details)
        found.append(self.trailer())
        return tuple(found)

    def render(self) -> str:
        return "\n".join(self.lines()) + "\n"

    def __str__(self) -> str:
        return f"{self.start.isoformat()} to {self.end.isoformat()}, {self.count} permits"


def detail_of(row: PermitRow) -> Detail:
    """One permit row as the federal office wants it.

    The kind is one letter and it is the permit series, which is not the same
    thing as the classification. The federal office asked for the series in
    1994 and has never asked for anything else, and a return that started
    sending the classification would be a return nobody could compare with
    the last thirty years of them.
    """
    return Detail(
        number=row.number,
        issued=row.issued,
        usdot=row.usdot,
        carrier=row.carrier_name,
        kind=row.series,
        gross_pounds=row.gross.value,
        miles=row.distance.hundredths // 100,
        fee=_fee(row),
    )


def _fee(row: PermitRow) -> Dollars:
    found = Dollars.of(0)
    for line in row.fees:
        found = found + line.amount
    return found


def issued_between(session: Session, start: date, end: date) -> tuple[PermitRow, ...]:
    """Every permit issued in the window, voided ones included."""
    query = (
        select(PermitRow)
        .where(PermitRow.issued >= start, PermitRow.issued <= end)
        .order_by(PermitRow.number)
    )
    return tuple(session.scalars(query).all())


def build(session: Session, start: date, end: date) -> Return:
    """The whole return for a window, in permit number order."""
    rows = issued_between(session, start, end)
    return Return(start, end, tuple(detail_of(one) for one in rows))


def superseded_count(session: Session, start: date, end: date) -> int:
    """How many of the window's permits have since been replaced.

    Not in the file. It goes in the covering letter, because the federal
    office rings up about it every year and the answer used to be worked out
    by hand each time.
    """
    return len(
        [one for one in issued_between(session, start, end) if one.state is PermitState.SUPERSEDED]
    )
