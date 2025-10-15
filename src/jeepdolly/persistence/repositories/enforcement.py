"""Citations, and the count that makes the next one a repeat."""

from __future__ import annotations

from datetime import date, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from ...enforcement.citation import Citation, CitationState
from ...enforcement.penalty import REPEAT_DAYS
from ...errors import Conflict, NotOnFile
from ..models.enforcement import CitationRow


class CitationRepository:
    """Citations, by number, by carrier and by what is still owing.

    The prior count is the interesting method. It drives the repeat
    multiplier, and it counts only citations that stuck: a dismissed one and a
    voided one are not prior offences, because the carrier was found not to
    have committed them. A contested one that has not been heard yet does
    count, which is the office's reading of the statute and is the one thing
    in this file a lawyer has ever argued with.
    """

    def __init__(self, session: Session) -> None:
        self.session = session

    def row(self, number: str) -> CitationRow:
        found = self.session.get(CitationRow, number)
        if found is None:
            raise NotOnFile(f"no citation {number}", number=number)
        return found

    def get(self, number: str) -> Citation:
        return self.row(number).to_domain()

    def add(self, citation: Citation, schedule: date) -> CitationRow:
        if self.session.get(CitationRow, citation.number) is not None:
            raise Conflict(f"citation {citation.number} is already on file")
        row = CitationRow.of(citation, schedule)
        self.session.add(row)
        return row

    def save_state(self, citation: Citation, reason: str = "") -> CitationRow:
        """Write the state back, and the reason when the state is a closing one."""
        row = self.row(citation.number)
        row.state = citation.state
        if reason:
            row.closing_reason = reason
        return row

    def for_carrier(self, usdot: int) -> tuple[CitationRow, ...]:
        query = (
            select(CitationRow)
            .where(CitationRow.usdot == usdot)
            .order_by(CitationRow.written.desc(), CitationRow.number)
        )
        return tuple(self.session.scalars(query).all())

    def owing(self) -> tuple[CitationRow, ...]:
        """Everything still expecting money, oldest first, which is the chase list."""
        query = (
            select(CitationRow)
            .where(
                CitationRow.state.in_(
                    [CitationState.WRITTEN, CitationState.CONTESTED, CitationState.UPHELD]
                )
            )
            .order_by(CitationRow.written, CitationRow.number)
        )
        return tuple(self.session.scalars(query).all())

    def written_between(self, start: date, end: date) -> tuple[CitationRow, ...]:
        query = (
            select(CitationRow)
            .where(CitationRow.written >= start, CitationRow.written <= end)
            .order_by(CitationRow.written, CitationRow.number)
        )
        return tuple(self.session.scalars(query).all())

    def priors(self, usdot: int, before: date, within_days: int = REPEAT_DAYS) -> int:
        """How many citations stuck against this carrier in the window.

        The window ends the day before the one asked about, so a citation
        written today is never a prior offence against itself. Two written on
        the same day are both first offences, which is generous and is what
        the office has always done: the officer wrote them a minute apart and
        the carrier had no chance to mend anything in between.
        """
        opened = before - timedelta(days=within_days)
        query = select(CitationRow).where(
            CitationRow.usdot == usdot,
            CitationRow.written >= opened,
            CitationRow.written < before,
            CitationRow.state.notin_([CitationState.DISMISSED, CitationState.VOID]),
        )
        return len(self.session.scalars(query).all())

    def count(self) -> int:
        return len(self.session.scalars(select(CitationRow)).all())
