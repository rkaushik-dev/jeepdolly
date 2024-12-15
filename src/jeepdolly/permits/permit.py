"""The permit itself: the document, its life, and what it is evidence of.

A permit is the only thing this office produces. Everything else is working
out whether to produce it. So the record is complete on its own: a permit that
can only be understood by joining it back to the application it came from is a
permit nobody can enforce at the roadside at two in the morning.

That means the permit copies rather than references. The route is copied as
segment idents and as printed narrative. The conditions are copied as rendered
text. The fee is copied as lines. If the fee schedule changes tomorrow, or the
road log is renumbered, or somebody edits a condition in the library, this
permit still says what it said when it was signed.

Four states.

Issued is live. Expired is past its last day, which is a fact about the
calendar rather than a decision, so it is computed and not stored.

Void is a decision: somebody cancelled it, or the carrier lost the load, or an
inspector found a lie on it. A void permit is kept, with the reason.

Superseded means an amendment replaced it. The replacement names the permit it
came from, so the chain can be walked either way, and the superseded one is
kept because the trip may already have started under it.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from enum import Enum

from ..conditions.library import Condition
from ..domain.envelope import Envelope
from ..errors import Impossible, NotAllowed
from ..fees.price import Quote
from ..measure import Miles, Pounds
from .numbering import PermitNumber


class PermitState(Enum):
    """What the document is."""

    ISSUED = "issued"
    VOID = "void"
    SUPERSEDED = "superseded"

    def __str__(self) -> str:
        return self.value


@dataclass
class Permit:
    """One issued permit, complete on its own."""

    number: PermitNumber
    application: str
    usdot: int
    carrier_name: str
    issued: date
    start: date
    end: date
    origin: str
    destination: str
    route: tuple[str, ...]
    narrative: str
    gross: Pounds
    envelope: Envelope
    distance: Miles
    conditions: tuple[Condition, ...]
    quote: Quote
    state: PermitState = PermitState.ISSUED
    void_reason: str = ""
    superseded_by: PermitNumber | None = None
    supersedes: PermitNumber | None = None
    # Not part of equality, for the same reason as on an application.
    history: list[tuple[date, str]] = field(default_factory=list, compare=False, repr=False)

    def __post_init__(self) -> None:
        if self.end < self.start:
            raise Impossible(f"{self.number} ends before it starts")
        if self.start < self.issued:
            raise Impossible(f"{self.number} is valid before it was issued")
        if not self.route:
            raise Impossible(f"{self.number} names no route")
        if not self.history:
            self.history.append((self.issued, f"issued to {self.carrier_name}"))

    @property
    def days(self) -> int:
        return (self.end - self.start).days + 1

    def expired_on(self, day: date) -> bool:
        return day > self.end

    def valid_on(self, day: date) -> bool:
        """Whether this permit is good for travel on a given day."""
        if self.state is not PermitState.ISSUED:
            return False
        return self.start <= day <= self.end

    def status_on(self, day: date) -> str:
        """What a clerk answering the telephone would say."""
        if self.state is PermitState.VOID:
            return f"void: {self.void_reason}"
        if self.state is PermitState.SUPERSEDED:
            return f"superseded by {self.superseded_by}"
        if day < self.start:
            return "issued, not yet in force"
        if self.expired_on(day):
            return "expired"
        return "in force"

    def void(self, on: date, reason: str) -> None:
        if self.state is not PermitState.ISSUED:
            raise NotAllowed(f"{self.number} is {self.state} and cannot be voided")
        if not reason.strip():
            raise Impossible("a permit is voided for a reason and the reason is written down")
        self.state = PermitState.VOID
        self.void_reason = reason
        self.history.append((on, f"void: {reason}"))

    def supersede(self, on: date, replacement: PermitNumber) -> None:
        if self.state is not PermitState.ISSUED:
            raise NotAllowed(f"{self.number} is {self.state} and cannot be superseded")
        self.state = PermitState.SUPERSEDED
        self.superseded_by = replacement
        self.history.append((on, f"superseded by {replacement}"))

    def condition_codes(self) -> tuple[str, ...]:
        return tuple(one.code for one in self.conditions)

    def face(self) -> str:
        """The top of the printed permit, which is what gets read first."""
        return (
            f"{self.number}  {self.carrier_name} ({self.usdot})\n"
            f"{self.origin.title()} to {self.destination.title()}, {self.distance}\n"
            f"{self.envelope}\n"
            f"{self.gross} gross, {self.start.isoformat()} to {self.end.isoformat()}"
        )

    def __str__(self) -> str:
        return f"{self.number} {self.carrier_name} {self.state}"
