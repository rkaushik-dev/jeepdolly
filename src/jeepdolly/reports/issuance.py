"""What the office wrote, and what it took, for a day or a span.

This is the report that gets printed every evening and initialled, so its
shape is fixed by what somebody signs rather than by what is easy to query.

Three things are on it and they answer three different questions.

The count by series says what kind of work the day was. Forty single trips and
one superload is a normal Tuesday. Four superloads is a week where the
engineer never sat down.

The money says what to reconcile against the till. It is the sum of the fee
lines as they were printed, not a recomputation from the schedule, because the
till has what was charged and not what should have been.

The refusals say what the office turned away, which nobody asks for until the
year the legislature asks how much traffic the state is refusing, and then
they want five years of it.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..errors import Impossible
from ..money import Dollars, total
from ..permits.application import ApplicationState
from ..persistence.models.applications import ApplicationRow
from ..persistence.models.permits import FeeLineRow, PermitRow


@dataclass(frozen=True)
class Issued:
    """One permit as it appears on the report."""

    number: str
    series: str
    carrier: str
    gross: str
    destination: str
    fee: Dollars

    def __str__(self) -> str:
        return f"{self.number}  {self.carrier}  {self.gross}  {self.fee}"


@dataclass(frozen=True)
class Issuance:
    """The day, or the span, as it is signed off."""

    start: date
    end: date
    permits: tuple[Issued, ...]
    refused: int = 0
    withdrawn: int = 0
    by_series: dict[str, int] = field(default_factory=dict)

    @property
    def count(self) -> int:
        return len(self.permits)

    @property
    def taken(self) -> Dollars:
        return total(one.fee for one in self.permits)

    @property
    def one_day(self) -> bool:
        return self.start == self.end

    def of_series(self, series: str) -> tuple[Issued, ...]:
        return tuple(one for one in self.permits if one.series == series)

    def heading(self) -> str:
        if self.one_day:
            return f"permits issued {self.start.isoformat()}"
        return f"permits issued {self.start.isoformat()} to {self.end.isoformat()}"

    def lines(self) -> tuple[str, ...]:
        """The report as it prints, heading and footing included."""
        found = [self.heading()]
        found.extend(str(one) for one in self.permits)
        found.append(f"{self.count} permits  {self.taken}")
        if self.refused or self.withdrawn:
            found.append(f"{self.refused} refused  {self.withdrawn} withdrawn")
        return tuple(found)

    def __str__(self) -> str:
        return f"{self.count} permits, {self.taken}"


def _fee_for(session: Session, number: str) -> Dollars:
    query = select(FeeLineRow).where(FeeLineRow.number == number)
    return total(one.amount for one in session.scalars(query).all())


def issuance(session: Session, start: date, end: date | None = None) -> Issuance:
    """Everything written between two days, inclusive of both."""
    last = end or start
    if last < start:
        raise Impossible("a report ends after it starts")
    query = (
        select(PermitRow)
        .where(PermitRow.issued >= start, PermitRow.issued <= last)
        .order_by(PermitRow.issued, PermitRow.number)
    )
    permits = []
    counts: dict[str, int] = {}
    for row in session.scalars(query).all():
        permits.append(
            Issued(
                number=row.number,
                series=row.series,
                carrier=row.carrier_name,
                gross=str(row.gross),
                destination=row.destination,
                fee=_fee_for(session, row.number),
            )
        )
        counts[row.series] = counts.get(row.series, 0) + 1
    return Issuance(
        start=start,
        end=last,
        permits=tuple(permits),
        refused=_applications(session, start, last, ApplicationState.REFUSED),
        withdrawn=_applications(session, start, last, ApplicationState.WITHDRAWN),
        by_series=counts,
    )


def _applications(session: Session, start: date, end: date, state: ApplicationState) -> int:
    query = select(ApplicationRow).where(
        ApplicationRow.filed >= start,
        ApplicationRow.filed <= end,
        ApplicationRow.state == state,
    )
    return len(session.scalars(query).all())
