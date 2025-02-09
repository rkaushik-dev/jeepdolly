"""Where the money came from, in the three cuts the office is asked for.

By kind of charge, which is what the department wants: how much of last year
was base fees and how much was weight. That one comes off the fee lines
grouped by the first word or two of the description, so the grouping is done
here and not by string matching at the call site.

By carrier, which is what a carrier asks for when it reconciles its own
account, and which the office would rather hand over than argue about.

By month, which is what the budget is written against.

Every figure is the sum of what was printed. A permit that was voided is still
counted, because the money was taken and voiding a permit does not refund it.
That is the office rule and it is the one people query, so it is stated on the
report rather than left to be discovered.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..errors import Impossible
from ..money import Dollars, total
from ..persistence.models.permits import FeeLineRow, PermitRow

#: How a fee line description is grouped into a kind of charge.
KINDS = (
    ("permit", "base fee"),
    ("miles", "mileage"),
    ("over legal gross", "weight"),
    ("engineering review", "review"),
    ("minimum", "minimum"),
    ("amendment", "amendment"),
)


def kind_of(description: str) -> str:
    """Which bucket a fee line falls in, by what it says."""
    lowered = description.lower()
    for needle, name in KINDS:
        if needle in lowered:
            return name
    return "other"


@dataclass(frozen=True)
class Revenue:
    """What was taken over a span, cut three ways."""

    start: date
    end: date
    by_kind: dict[str, Dollars]
    by_carrier: dict[str, Dollars]
    by_month: dict[str, Dollars]
    permits: int

    @property
    def taken(self) -> Dollars:
        return total(self.by_kind.values())

    def kind(self, name: str) -> Dollars:
        return self.by_kind.get(name, Dollars(0))

    def lines(self) -> tuple[str, ...]:
        found = [f"revenue {self.start.isoformat()} to {self.end.isoformat()}"]
        for name in sorted(self.by_kind):
            found.append(f"{name}  {self.by_kind[name]}")
        found.append(f"{self.permits} permits  {self.taken}")
        found.append("voided permits are counted, because the money was taken")
        return tuple(found)

    def __str__(self) -> str:
        return f"{self.permits} permits, {self.taken}"


def revenue(session: Session, start: date, end: date) -> Revenue:
    """Add up the fee lines of every permit issued in a span."""
    if end < start:
        raise Impossible("a report ends after it starts")
    query = (
        select(PermitRow)
        .where(PermitRow.issued >= start, PermitRow.issued <= end)
        .order_by(PermitRow.issued)
    )
    by_kind: dict[str, Dollars] = defaultdict(lambda: Dollars(0))
    by_carrier: dict[str, Dollars] = defaultdict(lambda: Dollars(0))
    by_month: dict[str, Dollars] = defaultdict(lambda: Dollars(0))
    permits = 0
    for row in session.scalars(query).all():
        permits += 1
        month = f"{row.issued.year:04d}-{row.issued.month:02d}"
        lines = session.scalars(select(FeeLineRow).where(FeeLineRow.number == row.number)).all()
        for line in lines:
            by_kind[kind_of(line.description)] = by_kind[kind_of(line.description)] + line.amount
            by_carrier[row.carrier_name] = by_carrier[row.carrier_name] + line.amount
            by_month[month] = by_month[month] + line.amount
    return Revenue(
        start=start,
        end=end,
        by_kind=dict(by_kind),
        by_carrier=dict(by_carrier),
        by_month=dict(by_month),
        permits=permits,
    )


def owed_by(session: Session, carrier_name: str, start: date, end: date) -> Dollars:
    """What one carrier was charged over a span, for its own reconciliation."""
    return revenue(session, start, end).by_carrier.get(carrier_name, Dollars(0))
