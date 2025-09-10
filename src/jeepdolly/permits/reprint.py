"""Duplicate copies, which the office would rather not issue and has to.

A permit lives on the dash of a truck for a week. It gets wet, it blows out of
the window at the weigh station, the driver leaves it in the other tractor. So
the office reprints it, and every reprint is a small risk: two pieces of paper
with the same number on them, one of which may be in somebody else's cab.

Three rules keep that in hand and none of them is a refusal to reprint.

Every copy is numbered and the number is on the paper. Copy two says copy two,
so an officer holding one can ask which copy the other one is and the office
can answer. The original is copy one and is never recorded, because printing
the permit when it is issued is not a reprint.

Every copy says why. Four reasons cover everything anybody has ever said at
the counter and the fifth is other, which wants a sentence. The reason is not
used to decide anything. It is used once a year, when somebody asks whether
the office should be emailing these instead.

Past a threshold a supervisor signs. Three copies of one permit is a carrier
with a filing problem rather than a wet piece of paper, and the fourth wants
somebody to have looked. That is the only place the count changes the answer.

A reprint is not an amendment and never touches the permit. Nothing about what
was allowed changes, the number does not change, and the permit's own record
is untouched. The reprints are a list beside it.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from ..errors import Impossible, NotAllowed
from .permit import Permit, PermitState

#: Copies past this many want a supervisor rather than a clerk.
FREELY = 3


class Reason(str, Enum):
    """Why a copy was asked for, in the words the counter uses."""

    LOST = "lost"
    DAMAGED = "damaged"
    LEFT_BEHIND = "left in another unit"
    SECOND_DRIVER = "second driver"
    OTHER = "other"

    @property
    def wants_words(self) -> bool:
        return self is Reason.OTHER

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Reprint:
    """One duplicate copy, as it was issued."""

    permit: str
    copy: int
    on: date
    reason: Reason
    asked_by: str
    issued_by: str
    note: str = ""

    def __post_init__(self) -> None:
        if self.copy < 2:
            raise Impossible("the original is copy one and is not a reprint")
        if not self.asked_by.strip():
            raise Impossible("a reprint names who asked for it")
        if self.reason.wants_words and not self.note.strip():
            raise Impossible("a reprint for another reason says what the reason was")

    @property
    def needed_a_supervisor(self) -> bool:
        return self.copy > FREELY

    def banner(self) -> str:
        """What goes across the face of the paper."""
        return f"DUPLICATE - COPY {self.copy}"

    def __str__(self) -> str:
        words = f", {self.note}" if self.note else ""
        return f"{self.permit} copy {self.copy}  {self.on.isoformat()}  {self.reason}{words}"


def next_copy(taken: tuple[int, ...]) -> int:
    """The number of the copy about to be printed.

    Taken from the highest rather than from the count. A reprint record that
    was deleted in a tidy up must not cause the office to print two copy
    threes, and the highest number ever issued is the only thing that cannot
    go backwards.
    """
    return max(taken, default=1) + 1


def allowed(permit: Permit, on: date, copy: int, supervisor: bool) -> None:
    """Whether this copy may be printed at all, raising if it may not.

    A void permit is refused because there is nothing to be on the road with.
    An expired one is refused for the same reason and it surprises people: a
    hauler who wants a copy of last month's permit for his own file is asking
    for a document that looks exactly like a live permit, and the office will
    not print one. He gets the register entry instead.
    """
    if permit.state is not PermitState.ISSUED:
        raise NotAllowed(f"{permit.number} is {permit.state} and cannot be copied")
    if permit.expired_on(on):
        raise NotAllowed(
            f"{permit.number} expired on {permit.end.isoformat()} and cannot be copied"
        )
    if copy > FREELY and not supervisor:
        raise NotAllowed(
            f"copy {copy} of {permit.number} wants a supervisor",
            permit=str(permit.number),
            copy=copy,
        )


def reprint(
    permit: Permit,
    on: date,
    reason: Reason,
    asked_by: str,
    issued_by: str,
    taken: tuple[int, ...] = (),
    supervisor: bool = False,
    note: str = "",
) -> Reprint:
    """Work out the copy number, check it may be printed, and record it."""
    copy = next_copy(taken)
    allowed(permit, on, copy, supervisor)
    return Reprint(
        permit=str(permit.number),
        copy=copy,
        on=on,
        reason=reason,
        asked_by=asked_by,
        issued_by=issued_by,
        note=note,
    )
