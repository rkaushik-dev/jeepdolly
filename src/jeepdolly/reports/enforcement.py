"""What was written at the roadside, and what came of it.

Three questions, and they are asked by three different people.

The legislature wants the count by offence for the year, because that is the
number that gets quoted when the penalties are next amended. It wants
everything written in the window, dismissed ones included, because a dismissed
ticket is still an enforcement action and leaving it out would make the office
look busier than it was.

Accounting wants the money: what was assessed, what was taken, and what is
still out. Those three do not add up and are not supposed to. A dismissed
citation was assessed and will never be taken, and the difference between
assessed and taken is not a debt.

The supervisor wants the carriers with more than one, because that is the list
that gets a letter before it gets a suspension.

Nothing here decides anything. It counts rows. The deciding is in the service
and the repeat multiplier, and a report that started deciding would be a
second set of rules to keep in step with the first.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import date

from sqlalchemy.orm import Session

from ..enforcement.citation import CitationState
from ..enforcement.penalty import Offence
from ..money import Dollars
from ..persistence.models.enforcement import CitationRow
from ..persistence.repositories import CitationRepository

#: How many citations in the window put a carrier on the supervisor's list.
WATCH_AT = 2


@dataclass(frozen=True)
class Money:
    """The three amounts, which do not add up and are not meant to."""

    assessed: Dollars
    taken: Dollars
    owing: Dollars

    @property
    def written_off(self) -> Dollars:
        """What was assessed and will never be taken, which is not a debt."""
        return self.assessed - self.taken - self.owing

    def lines(self) -> tuple[str, ...]:
        return (
            f"assessed  {self.assessed}",
            f"taken     {self.taken}",
            f"owing     {self.owing}",
            f"closed unpaid  {self.written_off}",
        )

    def __str__(self) -> str:
        return f"{self.assessed} assessed, {self.taken} taken, {self.owing} out"


@dataclass(frozen=True)
class Watched:
    """One carrier with more than one citation in the window."""

    usdot: int
    name: str
    count: int
    owing: Dollars

    def __str__(self) -> str:
        return f"{self.usdot}  {self.name}  {self.count} citations  {self.owing} out"


def written(session: Session, start: date, end: date) -> tuple[CitationRow, ...]:
    """Everything written in the window, whatever became of it."""
    return CitationRepository(session).written_between(start, end)


def by_offence(session: Session, start: date, end: date) -> dict[Offence, int]:
    """The count by offence, which is the number the legislature quotes."""
    found: Counter[Offence] = Counter()
    for row in written(session, start, end):
        found[row.offence] += 1
    return dict(found)


def money(session: Session, start: date, end: date) -> Money:
    """What was assessed, what was taken, and what is still out."""
    assessed = Dollars.of(0)
    taken = Dollars.of(0)
    owing = Dollars.of(0)
    for row in written(session, start, end):
        assessed = assessed + row.amount
        if row.state is CitationState.PAID:
            taken = taken + row.amount
        elif CitationState(row.state).owing:
            owing = owing + row.amount
    return Money(assessed, taken, owing)


def watch_list(session: Session, start: date, end: date, at: int = WATCH_AT) -> tuple[Watched, ...]:
    """Carriers with enough citations in the window to want a letter.

    Voided ones are left out and dismissed ones are not. A dismissed citation
    was still a stop, and the supervisor's letter is about how often this
    carrier is being stopped rather than about what stuck.
    """
    counts: Counter[int] = Counter()
    names: dict[int, str] = {}
    out: dict[int, Dollars] = {}
    for row in written(session, start, end):
        if row.state is CitationState.VOID:
            continue
        counts[row.usdot] += 1
        names[row.usdot] = row.carrier_name
        standing = out.get(row.usdot, Dollars.of(0))
        if CitationState(row.state).owing:
            standing = standing + row.amount
        out[row.usdot] = standing
    found = [
        Watched(usdot, names[usdot], count, out[usdot])
        for usdot, count in counts.items()
        if count >= at
    ]
    return tuple(sorted(found, key=lambda one: (-one.count, one.usdot)))


def report(session: Session, start: date, end: date) -> tuple[str, ...]:
    """The whole thing as lines, which is what the command prints."""
    lines = [f"enforcement {start.isoformat()} to {end.isoformat()}"]
    counts = by_offence(session, start, end)
    if not counts:
        lines.append("nothing was written in that window")
        return tuple(lines)
    for offence in sorted(counts, key=lambda one: one.value):
        lines.append(f"{offence}  {counts[offence]}")
    lines.extend(money(session, start, end).lines())
    watched = watch_list(session, start, end)
    if watched:
        lines.append("carriers to write to")
        lines.extend(f"  {one}" for one in watched)
    return tuple(lines)
