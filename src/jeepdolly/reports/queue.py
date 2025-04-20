"""How old the work is, which is the only number the supervisor watches.

Everything else in this system says what happened. This says what has not
happened yet, and it says it as an age rather than a count, because forty
applications filed this morning is a busy day and four filed a fortnight ago
is a problem.

Three queues and they age differently.

Applications waiting to be looked at should be hours old. Anything over a day
means somebody is off.

Studies waiting for an engineer are counted in days and the office promises a
fortnight. The oldest one is the number that gets quoted on the telephone.

Studies on hold are the ones nobody is looking at, because the office is
waiting for the hauler. They are reported separately, so that a fortnight
spent waiting for a spacing diagram is not counted against the engineer.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..permits.application import ApplicationState
from ..persistence.models.applications import ApplicationRow
from ..persistence.models.structures import ReviewRow
from ..structures.review import ReviewState


@dataclass(frozen=True)
class Waiting:
    """One thing that has not been dealt with, and how long it has waited."""

    reference: str
    since: date
    days: int
    note: str = ""

    def __str__(self) -> str:
        return f"{self.reference}  {self.since.isoformat()}  {self.days} days  {self.note}"


@dataclass(frozen=True)
class Ageing:
    """The three queues on one page."""

    day: date
    applications: tuple[Waiting, ...]
    studies: tuple[Waiting, ...]
    held: tuple[Waiting, ...]

    @property
    def oldest_application(self) -> int:
        return max((one.days for one in self.applications), default=0)

    @property
    def oldest_study(self) -> int:
        return max((one.days for one in self.studies), default=0)

    @property
    def quiet(self) -> bool:
        return not (self.applications or self.studies or self.held)

    def over(self, days: int) -> tuple[Waiting, ...]:
        """Everything in any queue older than a promise."""
        found = [one for one in self.applications + self.studies if one.days > days]
        return tuple(sorted(found, key=lambda one: one.days, reverse=True))

    def lines(self) -> tuple[str, ...]:
        found = [f"waiting on {self.day.isoformat()}"]
        for one in self.applications:
            found.append(f"application  {one}")
        for one in self.studies:
            found.append(f"study  {one}")
        for one in self.held:
            found.append(f"held  {one}")
        found.append(
            f"{len(self.applications)} applications  {len(self.studies)} studies  "
            f"{len(self.held)} held  oldest study {self.oldest_study} days"
        )
        return tuple(found)

    def __str__(self) -> str:
        return f"{len(self.applications)} applications, {len(self.studies)} studies"


def ageing(session: Session, day: date) -> Ageing:
    """Read the three queues and age everything in them."""
    applications = []
    query = (
        select(ApplicationRow)
        .where(ApplicationRow.state == ApplicationState.SUBMITTED)
        .order_by(ApplicationRow.filed, ApplicationRow.reference)
    )
    for row in session.scalars(query).all():
        applications.append(Waiting(row.reference, row.filed, (day - row.filed).days, row.origin))
    studies: list[Waiting] = []
    held: list[Waiting] = []
    reviews = select(ReviewRow).order_by(ReviewRow.opened, ReviewRow.reference)
    for study in session.scalars(reviews).all():
        waited = Waiting(
            study.reference, study.opened, (day - study.opened).days, study.engineer or ""
        )
        if study.state in (ReviewState.WAITING, ReviewState.WITH_ENGINEER):
            studies.append(waited)
        elif study.state is ReviewState.HELD:
            held.append(waited)
    return Ageing(day, tuple(applications), tuple(studies), tuple(held))
