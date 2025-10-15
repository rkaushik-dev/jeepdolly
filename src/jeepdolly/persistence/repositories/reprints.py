"""The register of duplicate copies."""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ...errors import Conflict
from ...permits.reprint import Reprint
from ..models.reprints import ReprintRow


class ReprintRepository:
    """The reprint register, which is a list beside each permit."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def for_permit(self, number: str) -> tuple[ReprintRow, ...]:
        query = select(ReprintRow).where(ReprintRow.permit == number).order_by(ReprintRow.copy)
        return tuple(self.session.scalars(query).all())

    def copies_taken(self, number: str) -> tuple[int, ...]:
        """Every copy number ever issued for a permit, for the next one."""
        return tuple(one.copy for one in self.for_permit(number))

    def add(self, reprint: Reprint) -> ReprintRow:
        """Record a copy, refusing a number that has already been handed out.

        The database would refuse it too, on the composite key. This says
        conflict first so that two clerks racing each other get a sentence
        rather than an integrity error.
        """
        if self.session.get(ReprintRow, (reprint.permit, reprint.copy)) is not None:
            raise Conflict(
                f"copy {reprint.copy} of {reprint.permit} has already been printed",
                permit=reprint.permit,
                copy=reprint.copy,
            )
        row = ReprintRow.of(reprint)
        self.session.add(row)
        return row

    def between(self, start: date, end: date) -> tuple[ReprintRow, ...]:
        """Every copy issued in a window, which is the yearly question."""
        query = (
            select(ReprintRow)
            .where(ReprintRow.issued_on >= start, ReprintRow.issued_on <= end)
            .order_by(ReprintRow.issued_on, ReprintRow.permit, ReprintRow.copy)
        )
        return tuple(self.session.scalars(query).all())

    def count(self) -> int:
        return len(self.session.scalars(select(ReprintRow)).all())
