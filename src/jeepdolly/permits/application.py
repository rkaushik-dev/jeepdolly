"""An application: everything the hauler says, before anybody checks it.

The application is deliberately a dumb record. It holds what was submitted,
exactly as submitted, and it does not correct anything. If a hauler declares a
combination that cannot exist, the application still holds it, because the
refusal letter has to quote what was asked for and not a tidied version of it.

What the application does do is know whether it is complete enough to be
looked at. That is a different question from whether it can be permitted, and
keeping the two apart is the reason the counter can hand something back in
thirty seconds instead of after a route check.

Five states. Draft is being typed. Submitted is in the queue. In review is with
an engineer. Issued and refused are the ends of it, and withdrawn is the sixth
which nobody counts because the hauler rang up and cancelled.

The travel span is on the application rather than the permit because the
hauler asks for dates and the office grants them, and a permit that quietly
starts on a different day from the one asked for is a permit that gets used on
the wrong day.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from enum import Enum

from ..domain.combination import Combination
from ..domain.commodity import Piece
from ..errors import Impossible, NotAllowed
from ..measure import Pounds

#: A single trip permit may not be asked for over a longer span than this.
MAX_SPAN_DAYS = 10


class ApplicationState(Enum):
    """Where the application stands."""

    DRAFT = "draft"
    SUBMITTED = "submitted"
    IN_REVIEW = "in review"
    ISSUED = "issued"
    REFUSED = "refused"
    WITHDRAWN = "withdrawn"

    @property
    def open(self) -> bool:
        return self in (
            ApplicationState.DRAFT,
            ApplicationState.SUBMITTED,
            ApplicationState.IN_REVIEW,
        )

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Applicant:
    """Who to ring when something is wrong with it."""

    name: str
    phone: str
    email: str = ""

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise Impossible("an application names somebody to ring")
        if not self.phone.strip():
            raise Impossible("an application carries a telephone number")

    def __str__(self) -> str:
        return f"{self.name} {self.phone}"


@dataclass
class Application:
    """One request for one permit, as it was submitted."""

    reference: str
    usdot: int
    commodity: str
    pieces: tuple[Piece, ...]
    combination: Combination
    origin: str
    destination: str
    start: date
    end: date
    applicant: Applicant
    filed: date
    route: tuple[str, ...] = ()
    state: ApplicationState = ApplicationState.DRAFT
    notes: str = ""
    # Not part of equality. Two applications are the same application when they
    # ask for the same thing, whatever has happened to each of them since.
    history: list[tuple[date, ApplicationState, str]] = field(
        default_factory=list, compare=False, repr=False
    )

    def __post_init__(self) -> None:
        if not self.reference.strip():
            raise Impossible("an application carries a reference")
        if not self.history:
            self.history.append((self.filed, self.state, "typed"))

    @property
    def span_days(self) -> int:
        return (self.end - self.start).days + 1

    @property
    def gross(self) -> Pounds:
        return self.combination.gross

    def problems(self) -> tuple[str, ...]:
        """What stops this being looked at, which is not why it might fail."""
        found = []
        if self.end < self.start:
            found.append("the travel span ends before it starts")
        elif self.span_days > MAX_SPAN_DAYS:
            found.append(f"a single trip permit runs {MAX_SPAN_DAYS} days at most")
        if self.start < self.filed:
            found.append("the travel span starts before the application was filed")
        if not self.pieces:
            found.append("nothing is described on the trailer")
        if self.origin == self.destination:
            found.append("the trip starts and ends in the same place")
        if not self.origin.strip() or not self.destination.strip():
            found.append("the trip has an origin and a destination")
        return tuple(found)

    @property
    def complete(self) -> bool:
        return not self.problems()

    def _move(self, state: ApplicationState, on: date, note: str) -> None:
        self.state = state
        self.history.append((on, state, note))

    def submit(self, on: date) -> None:
        if self.state is not ApplicationState.DRAFT:
            raise NotAllowed(f"{self.reference} is {self.state} and cannot be submitted")
        problems = self.problems()
        if problems:
            raise Impossible(
                f"{self.reference} is not complete: {problems[0]}",
                reference=self.reference,
                problems=problems,
            )
        self._move(ApplicationState.SUBMITTED, on, "submitted")

    def send_for_review(self, on: date, note: str = "") -> None:
        if self.state is not ApplicationState.SUBMITTED:
            raise NotAllowed(f"{self.reference} is {self.state} and cannot go to review")
        self._move(ApplicationState.IN_REVIEW, on, note or "sent to an engineer")

    def issue(self, on: date, number: str) -> None:
        if self.state not in (ApplicationState.SUBMITTED, ApplicationState.IN_REVIEW):
            raise NotAllowed(f"{self.reference} is {self.state} and cannot be issued")
        self._move(ApplicationState.ISSUED, on, f"issued as {number}")

    def refuse(self, on: date, reason: str) -> None:
        if not self.state.open:
            raise NotAllowed(f"{self.reference} is {self.state} and cannot be refused")
        self._move(ApplicationState.REFUSED, on, reason)

    def withdraw(self, on: date, reason: str = "withdrawn by the carrier") -> None:
        if not self.state.open:
            raise NotAllowed(f"{self.reference} is {self.state} and cannot be withdrawn")
        self._move(ApplicationState.WITHDRAWN, on, reason)

    def __str__(self) -> str:
        return f"{self.reference} {self.origin} to {self.destination} {self.state}"
