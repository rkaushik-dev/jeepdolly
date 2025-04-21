"""Numbers about the office, counted off the database rather than in memory.

Everything here is a query. There is no counter incremented in a process, and
that is deliberate: this service runs behind more than one worker, workers are
restarted, and a count that lives in a process is a count that disagrees with
itself between two scrapes.

So a scrape is a handful of counting queries against tables that are already
indexed for the reports. It costs a few milliseconds and it is always right,
which is the trade the office wants.

The names are flat and boring on purpose. Whatever collects them will be
graphing them next to a hundred other services and the graph wants a name it
can autocomplete, not a sentence.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from sqlalchemy import ColumnElement, func, select
from sqlalchemy.orm import Session

from ..permits.application import ApplicationState
from ..permits.permit import PermitState
from ..persistence.models.applications import ApplicationRow
from ..persistence.models.carriers import CarrierRow
from ..persistence.models.network import RestrictionRow, SegmentRow
from ..persistence.models.permits import PermitRow
from ..persistence.models.structures import ReviewRow, StructureRow
from ..structures.review import ReviewState

PREFIX = "jeepdolly"


@dataclass(frozen=True)
class Measure:
    """One number, its name, and one line of what it means."""

    name: str
    value: int
    about: str

    def lines(self) -> tuple[str, ...]:
        full = f"{PREFIX}_{self.name}"
        return (f"# HELP {full} {self.about}", f"# TYPE {full} gauge", f"{full} {self.value}")

    def __str__(self) -> str:
        return f"{PREFIX}_{self.name} {self.value}"


def _count(session: Session, model: type, *where: ColumnElement[bool]) -> int:
    query = select(func.count()).select_from(model)
    for one in where:
        query = query.where(one)
    return int(session.scalar(query) or 0)


def snapshot(session: Session, day: date) -> tuple[Measure, ...]:
    """Every number the office publishes, counted now."""
    week = day - timedelta(days=7)
    return (
        Measure(
            "permits_on_the_road",
            _count(
                session,
                PermitRow,
                PermitRow.state == PermitState.ISSUED,
                PermitRow.valid_from <= day,
                PermitRow.valid_to >= day,
            ),
            "permits valid for travel today",
        ),
        Measure(
            "permits_issued_week",
            _count(session, PermitRow, PermitRow.issued >= week, PermitRow.issued <= day),
            "permits written in the last seven days",
        ),
        Measure(
            "permits_void",
            _count(session, PermitRow, PermitRow.state == PermitState.VOID),
            "permits voided since the beginning",
        ),
        Measure(
            "applications_waiting",
            _count(session, ApplicationRow, ApplicationRow.state == ApplicationState.SUBMITTED),
            "applications submitted and not yet decided",
        ),
        Measure(
            "applications_draft",
            _count(session, ApplicationRow, ApplicationRow.state == ApplicationState.DRAFT),
            "applications typed and not submitted",
        ),
        Measure(
            "studies_open",
            _count(
                session,
                ReviewRow,
                ReviewRow.state.in_(
                    (ReviewState.WAITING, ReviewState.WITH_ENGINEER, ReviewState.HELD)
                ),
            ),
            "studies that have not been decided",
        ),
        Measure(
            "segments",
            _count(session, SegmentRow),
            "segments on the road log",
        ),
        Measure(
            "restrictions",
            _count(session, RestrictionRow),
            "restrictions on file, in force or not",
        ),
        Measure(
            "structures_posted",
            _count(session, StructureRow, StructureRow.posted.is_not(None)),
            "structures carrying a posted weight",
        ),
        Measure(
            "carriers",
            _count(session, CarrierRow),
            "carriers the office has ever written for",
        ),
    )


def exposition(session: Session, day: date) -> str:
    """The scrape, in the text format every collector reads."""
    lines: list[str] = []
    for measure in snapshot(session, day):
        lines.extend(measure.lines())
    return "\n".join(lines) + "\n"


def by_name(session: Session, day: date) -> dict[str, int]:
    """The same numbers as a dictionary, for a test or a console."""
    return {one.name: one.value for one in snapshot(session, day)}
