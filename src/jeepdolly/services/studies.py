"""The study workflow, kept on the database instead of in memory.

The queue in ``structures.review`` is the rules: five states, four moves, and
a history nobody edits. This is the same thing with rows behind it, and it
exists because the queue is the one part of the office that spans days. A
review opened on Monday is picked up on Wednesday by somebody who was not
there on Monday, and none of that survives in a process.

The domain object is rebuilt from its rows for every move and written back
afterwards. That is slower than editing the row directly and it is the reason
the state machine cannot be got round: every move goes through the same
``assign`` and ``clear`` that the unit tests hold to.

The moves are appended, never rewritten, and their position is the length of
what is already there. Two moves on one day keep the order they happened in,
which the date alone would not give.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..errors import Conflict, NotOnFile
from ..persistence.models.structures import ReviewMoveRow, ReviewRow
from ..structures.review import Review, ReviewState


def open_study(
    session: Session, reference: str, structures: tuple[str, ...], on: date
) -> ReviewRow:
    """Put an application in the queue."""
    if session.get(ReviewRow, reference) is not None:
        raise Conflict(f"{reference} is already in the queue", reference=reference)
    row = ReviewRow(
        reference=reference,
        opened=on,
        state=ReviewState.WAITING,
        structures=",".join(structures),
    )
    row.moves.append(
        ReviewMoveRow(
            position=0,
            happened=on,
            who="counter",
            state=ReviewState.WAITING,
            note="opened",
        )
    )
    session.add(row)
    return row


def row_for(session: Session, reference: str) -> ReviewRow:
    found = session.get(ReviewRow, reference)
    if found is None:
        raise NotOnFile(f"no review for {reference}", reference=reference)
    return found


def to_domain(row: ReviewRow) -> Review:
    """The rows as the state machine sees them."""
    review = Review(
        reference=row.reference,
        structures=tuple(one for one in (row.structures or "").split(",") if one),
        opened=row.opened,
        state=row.state,
        engineer=row.engineer,
        conditions=tuple(one for one in (row.conditions or "").split("|") if one),
        history=[],
    )
    review.history.clear()
    return review


def _write_back(row: ReviewRow, review: Review, on: date, who: str, note: str) -> ReviewMoveRow:
    move = ReviewMoveRow(
        position=len(row.moves),
        happened=on,
        who=who,
        state=review.state,
        note=note,
    )
    row.moves.append(move)
    row.state = review.state
    row.engineer = review.engineer
    row.conditions = "|".join(review.conditions)
    return move


def assign(session: Session, reference: str, engineer: str, on: date) -> ReviewRow:
    """An engineer picks a study up."""
    row = row_for(session, reference)
    review = to_domain(row)
    review.assign(engineer, on)
    _write_back(row, review, on, engineer, "picked up")
    return row


def hold(session: Session, reference: str, on: date, reason: str) -> ReviewRow:
    """The engineer wants something before going further."""
    row = row_for(session, reference)
    review = to_domain(row)
    review.hold(on, reason)
    _write_back(row, review, on, row.engineer or "engineer", reason)
    return row


def resume(session: Session, reference: str, on: date, note: str = "answered") -> ReviewRow:
    """What was asked for arrived, and the clock starts again."""
    row = row_for(session, reference)
    review = to_domain(row)
    review.resume(on, note)
    _write_back(row, review, on, "counter", note)
    return row


def clear(
    session: Session, reference: str, on: date, conditions: tuple[str, ...] = ()
) -> ReviewRow:
    """The engineer signed it, with conditions if there are any."""
    row = row_for(session, reference)
    review = to_domain(row)
    signer = row.engineer or "engineer"
    review.clear(on, conditions)
    _write_back(row, review, on, signer, "signed")
    return row


def refuse(session: Session, reference: str, on: date, reason: str) -> ReviewRow:
    """The engineer signed it the other way."""
    row = row_for(session, reference)
    review = to_domain(row)
    signer = row.engineer or "engineer"
    review.refuse(on, reason)
    _write_back(row, review, on, signer, reason)
    return row


def waiting(session: Session) -> tuple[ReviewRow, ...]:
    """The queue, oldest first, which is the order it is worked."""
    query = (
        select(ReviewRow)
        .where(ReviewRow.state == ReviewState.WAITING)
        .order_by(ReviewRow.opened, ReviewRow.reference)
    )
    return tuple(session.scalars(query).all())


def with_engineer(session: Session, engineer: str) -> tuple[ReviewRow, ...]:
    query = (
        select(ReviewRow)
        .where(
            ReviewRow.engineer == engineer,
            ReviewRow.state == ReviewState.WITH_ENGINEER,
        )
        .order_by(ReviewRow.reference)
    )
    return tuple(session.scalars(query).all())


def overdue(session: Session, on: date, days: int) -> tuple[ReviewRow, ...]:
    """Open studies that have been waiting longer than the office promises."""
    open_states = (ReviewState.WAITING, ReviewState.WITH_ENGINEER, ReviewState.HELD)
    query = select(ReviewRow).where(ReviewRow.state.in_(open_states)).order_by(ReviewRow.opened)
    return tuple(one for one in session.scalars(query).all() if (on - one.opened).days > days)
