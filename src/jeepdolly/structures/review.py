"""The review queue, which is where a study waits for an engineer.

Anything the counter cannot issue lands here. The queue is small, four or five
a week in winter and thirty in July, and it is the part of the office people
telephone about, so the state of an item has to be exactly right and the
history of it has to be complete.

Five states and four moves between them.

Waiting means it is in the queue and nobody has picked it up.

With an engineer means somebody has. The name is on it, because the hauler will
ring and ask who has it and the counter will not say nobody.

Cleared means the engineer signed it, with conditions if there are any. Those
conditions go onto the permit unchanged. The office does not paraphrase an
engineer.

Refused means the engineer signed it the other way, with a reason. That is the
end of the application over that route at that weight.

Held means the engineer wants something first: a spacing diagram, a crane
chart, the actual axle weights instead of the estimates. A held item goes back
to waiting when the hauler sends it, and the clock starts again, which is the
part haulers hate and the part the office will not move on.

Every move is recorded with who and when. Nothing is overwritten and nothing is
deleted, because the one item anybody ever asks about in a lawsuit is the one
that was edited.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from enum import Enum

from ..errors import Conflict, Impossible, NotAllowed, NotOnFile


class ReviewState(Enum):
    """Where an item stands."""

    WAITING = "waiting"
    WITH_ENGINEER = "with engineer"
    CLEARED = "cleared"
    REFUSED = "refused"
    HELD = "held"

    @property
    def open(self) -> bool:
        return self in (ReviewState.WAITING, ReviewState.WITH_ENGINEER, ReviewState.HELD)

    @property
    def decided(self) -> bool:
        return self in (ReviewState.CLEARED, ReviewState.REFUSED)

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Move:
    """One thing that happened to an item, with who and when."""

    on: date
    by: str
    state: ReviewState
    note: str = ""

    def __str__(self) -> str:
        note = f": {self.note}" if self.note else ""
        return f"{self.on.isoformat()} {self.by} {self.state}{note}"


@dataclass
class Review:
    """One study, its state, and everything that has happened to it."""

    reference: str
    structures: tuple[str, ...]
    opened: date
    state: ReviewState = ReviewState.WAITING
    engineer: str | None = None
    conditions: tuple[str, ...] = ()
    history: list[Move] = field(default_factory=list)

    def __post_init__(self) -> None:
        if not self.reference.strip():
            raise Impossible("a review is opened against an application")
        if not self.history:
            self.history.append(Move(self.opened, "counter", ReviewState.WAITING, "opened"))

    def _move(self, state: ReviewState, by: str, on: date, note: str = "") -> Move:
        if on < self.opened:
            raise Impossible("a review cannot move before it was opened")
        move = Move(on, by, state, note)
        self.state = state
        self.history.append(move)
        return move

    def assign(self, engineer: str, on: date) -> Move:
        if self.state is not ReviewState.WAITING:
            raise NotAllowed(f"{self.reference} is {self.state} and cannot be picked up")
        self.engineer = engineer
        return self._move(ReviewState.WITH_ENGINEER, engineer, on, "picked up")

    def hold(self, on: date, reason: str) -> Move:
        if self.state is not ReviewState.WITH_ENGINEER:
            raise NotAllowed(f"{self.reference} is {self.state} and cannot be held")
        return self._move(ReviewState.HELD, self.engineer or "engineer", on, reason)

    def resume(self, on: date, note: str = "answered") -> Move:
        if self.state is not ReviewState.HELD:
            raise NotAllowed(f"{self.reference} is {self.state} and is not on hold")
        self.engineer = None
        return self._move(ReviewState.WAITING, "counter", on, note)

    def clear(self, on: date, conditions: tuple[str, ...] = ()) -> Move:
        if self.state is not ReviewState.WITH_ENGINEER:
            raise NotAllowed(f"{self.reference} is {self.state} and cannot be cleared")
        self.conditions = conditions
        return self._move(ReviewState.CLEARED, self.engineer or "engineer", on, "signed")

    def refuse(self, on: date, reason: str) -> Move:
        if self.state is not ReviewState.WITH_ENGINEER:
            raise NotAllowed(f"{self.reference} is {self.state} and cannot be refused")
        return self._move(ReviewState.REFUSED, self.engineer or "engineer", on, reason)

    def waiting_days(self, on: date) -> int:
        """Days since it was opened, which is the number the queue is sorted on."""
        return (on - self.opened).days

    def decided_on(self) -> date | None:
        for move in reversed(self.history):
            if move.state.decided:
                return move.on
        return None

    def __str__(self) -> str:
        with_whom = f" with {self.engineer}" if self.engineer else ""
        return f"{self.reference} {self.state}{with_whom}"


class ReviewQueue:
    """Every study, open and closed."""

    def __init__(self) -> None:
        self._by_reference: dict[str, Review] = {}

    def open(self, reference: str, structures: tuple[str, ...], on: date) -> Review:
        if reference in self._by_reference:
            raise Conflict(f"{reference} is already in the queue", reference=reference)
        review = Review(reference, structures, on)
        self._by_reference[reference] = review
        return review

    def get(self, reference: str) -> Review:
        try:
            return self._by_reference[reference]
        except KeyError:
            raise NotOnFile(f"no review for {reference}", reference=reference) from None

    def waiting(self) -> tuple[Review, ...]:
        found = [one for one in self._by_reference.values() if one.state is ReviewState.WAITING]
        return tuple(sorted(found, key=lambda one: (one.opened, one.reference)))

    def with_engineer(self, engineer: str) -> tuple[Review, ...]:
        found = [
            one
            for one in self._by_reference.values()
            if one.engineer == engineer and one.state is ReviewState.WITH_ENGINEER
        ]
        return tuple(sorted(found, key=lambda one: one.reference))

    def open_items(self) -> tuple[Review, ...]:
        found = [one for one in self._by_reference.values() if one.state.open]
        return tuple(sorted(found, key=lambda one: (one.opened, one.reference)))

    def overdue(self, on: date, days: int) -> tuple[Review, ...]:
        return tuple(one for one in self.open_items() if one.waiting_days(on) > days)

    def touching(self, structure: str) -> tuple[Review, ...]:
        found = [one for one in self._by_reference.values() if structure in one.structures]
        return tuple(sorted(found, key=lambda one: one.reference))

    def __len__(self) -> int:
        return len(self._by_reference)
