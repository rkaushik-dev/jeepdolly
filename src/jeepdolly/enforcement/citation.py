"""A citation: what an officer wrote at the roadside, and what became of it.

The office does not write these. An inspector at a scale or a trooper on US 2
writes one on a pad, and a copy reaches the office three days later, which is
why the day it was written and the day it was filed are two different fields
and neither is today.

A citation names the carrier and it may name a permit. Both happen. A load
stopped with no permit at all still names a carrier, because the usdot number
is on the door, and a load stopped over the weight on a good permit names the
permit as well, because the argument that follows is about what the permit
allowed.

Five states and the moves between them are the whole of the record.

Written is a citation on file and unpaid. Paid is the end of it. Contested is
the carrier saying no, in writing, inside the window. From contested it goes
to upheld, which then wants paying, or dismissed, which is the end of it.
Voided is the office's own mistake: a citation written against the wrong
carrier or the wrong day, killed by a supervisor with a reason on it.

There is no route from paid back to anything. A carrier who pays and then
finds the citation was wrong gets a refund through the till, which is a
different record and a different signature, and a system that let a paid
citation be reopened would let the money and the paper disagree.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta
from enum import Enum

from ..errors import Impossible, NotAllowed
from ..measure import Pounds
from ..money import Dollars
from .penalty import Assessed, Offence

#: How long a carrier has to contest one, counted from the day it was written.
CONTEST_DAYS = 21


class CitationState(Enum):
    """Where the citation stands."""

    WRITTEN = "written"
    CONTESTED = "contested"
    UPHELD = "upheld"
    DISMISSED = "dismissed"
    PAID = "paid"
    VOID = "void"

    @property
    def owing(self) -> bool:
        """Whether money is still expected on it."""
        return self in (CitationState.WRITTEN, CitationState.CONTESTED, CitationState.UPHELD)

    @property
    def closed(self) -> bool:
        return self in (CitationState.PAID, CitationState.DISMISSED, CitationState.VOID)

    def __str__(self) -> str:
        return self.value


@dataclass
class Citation:
    """One citation, as written and as it stands now."""

    number: str
    written: date
    filed: date
    officer: str
    usdot: int
    carrier_name: str
    offence: Offence
    place: str
    assessed: Assessed
    permit: str | None = None
    over: Pounds | None = None
    note: str = ""
    state: CitationState = CitationState.WRITTEN
    # Not part of equality, the same way an application's is not. Two
    # citations are the same citation when they say the same thing.
    history: list[tuple[date, CitationState, str]] = field(
        default_factory=list, compare=False, repr=False
    )

    def __post_init__(self) -> None:
        if not self.number.strip():
            raise Impossible("a citation carries a number")
        if self.filed < self.written:
            raise Impossible(f"{self.number} was filed before it was written")
        if self.offence.by_weight and self.over is None:
            raise Impossible(f"{self.number} is overweight and does not say by how much")
        if not self.history:
            self.history.append((self.filed, self.state, "filed"))

    @property
    def amount(self) -> Dollars:
        return self.assessed.total

    def contest_closes(self) -> date:
        """The last day a carrier may contest it."""

        return self.written + timedelta(days=CONTEST_DAYS)

    def contestable_on(self, day: date) -> bool:
        return self.state is CitationState.WRITTEN and day <= self.contest_closes()

    def _move(self, state: CitationState, on: date, note: str) -> None:
        self.state = state
        self.history.append((on, state, note))

    def contest(self, on: date, reason: str) -> None:
        """The carrier says no, in writing, inside the window."""
        if self.state is not CitationState.WRITTEN:
            raise NotAllowed(f"{self.number} is {self.state} and cannot be contested")
        if on > self.contest_closes():
            raise NotAllowed(
                f"{self.number} could be contested until {self.contest_closes().isoformat()}",
                number=self.number,
            )
        if not reason.strip():
            raise Impossible("a contested citation says on what grounds")
        self._move(CitationState.CONTESTED, on, reason)

    def uphold(self, on: date, note: str = "") -> None:
        """The hearing went the office's way and it still wants paying."""
        if self.state is not CitationState.CONTESTED:
            raise NotAllowed(f"{self.number} is {self.state} and there is nothing to uphold")
        self._move(CitationState.UPHELD, on, note or "upheld at hearing")

    def dismiss(self, on: date, reason: str) -> None:
        """The hearing went the carrier's way."""
        if self.state is not CitationState.CONTESTED:
            raise NotAllowed(f"{self.number} is {self.state} and there is nothing to dismiss")
        if not reason.strip():
            raise Impossible("a dismissed citation says why")
        self._move(CitationState.DISMISSED, on, reason)

    def pay(self, on: date, reference: str = "") -> None:
        """Money in, which is the end of it whichever way it got here."""
        if self.state not in (CitationState.WRITTEN, CitationState.UPHELD):
            raise NotAllowed(f"{self.number} is {self.state} and is not owing")
        self._move(CitationState.PAID, on, reference or "paid")

    def void(self, on: date, reason: str) -> None:
        """The office's own mistake, killed by a supervisor."""
        if self.state is CitationState.PAID:
            raise NotAllowed(f"{self.number} is paid and cannot be voided, refund it instead")
        if self.state is CitationState.VOID:
            raise NotAllowed(f"{self.number} is already void")
        if not reason.strip():
            raise Impossible("a voided citation says why")
        self._move(CitationState.VOID, on, reason)

    def lines(self) -> tuple[str, ...]:
        """The citation as the office reads it out over the telephone."""
        found = [
            f"{self.number}  {self.written.isoformat()}  {self.carrier_name} ({self.usdot})",
            f"  {self.offence} at {self.place}, written by {self.officer}",
        ]
        if self.permit:
            found.append(f"  under permit {self.permit}")
        found.extend(f"  {one}" for one in self.assessed.lines())
        found.append(f"  {self.state}")
        return tuple(found)

    def __str__(self) -> str:
        return f"{self.number} {self.carrier_name} {self.offence} {self.amount} {self.state}"
