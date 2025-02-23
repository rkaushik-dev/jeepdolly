"""Report routes, which hand back the report and not a rendering of it.

Every report here comes back as the same two things: the lines the paper
report prints, and the figures behind them. The lines are there because a
carrier's software wants to show something without understanding it, and the
figures are there because the department's spreadsheet wants the number.

Handing back both is duplication and it is the right duplication. The
alternative is a client reformatting the lines, which means a client parsing
them, which means the office can never change the wording.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from ...persistence.unitofwork import UnitOfWork
from ...reports.calendar import changes, standing_on
from ...reports.issuance import issuance
from ...reports.revenue import revenue
from ..deps import unit_of_work
from ..security import Caller, Role, needs

router = APIRouter(prefix="/reports", tags=["reports"])


class IssuanceOut(BaseModel):
    """What was written and what it took."""

    start: date
    end: date
    count: int
    taken: str
    by_series: dict[str, int]
    refused: int
    withdrawn: int
    lines: list[str]


class RevenueOut(BaseModel):
    """Where the money came from."""

    start: date
    end: date
    permits: int
    taken: str
    by_kind: dict[str, str]
    by_month: dict[str, str]
    lines: list[str]


class StandingOut(BaseModel):
    """One restriction in force."""

    segment: str
    kind: str
    limit: str
    note: str
    permanent: bool


class CalendarOut(BaseModel):
    """What changes on the road, and what is in force today."""

    start: date
    days: int
    changes: list[str]
    standing: list[StandingOut] = Field(default_factory=list)


@router.get("/issuance", response_model=IssuanceOut)
def issuance_report(
    day: date = Query(...),
    to: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> IssuanceOut:
    """The evening report, for a day or a span."""
    found = issuance(work.db, day, to)
    return IssuanceOut(
        start=found.start,
        end=found.end,
        count=found.count,
        taken=str(found.taken),
        by_series=found.by_series,
        refused=found.refused,
        withdrawn=found.withdrawn,
        lines=list(found.lines()),
    )


@router.get("/revenue", response_model=RevenueOut)
def revenue_report(
    start: date = Query(...),
    end: date = Query(...),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> RevenueOut:
    """Where the money came from over a span."""
    found = revenue(work.db, start, end)
    return RevenueOut(
        start=found.start,
        end=found.end,
        permits=found.permits,
        taken=str(found.taken),
        by_kind={name: str(amount) for name, amount in found.by_kind.items()},
        by_month={name: str(amount) for name, amount in found.by_month.items()},
        lines=list(found.lines()),
    )


@router.get("/roads", response_model=CalendarOut)
def road_report(
    day: date = Query(...),
    days: int = Query(default=14, ge=1, le=90),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> CalendarOut:
    """What changes on the road, with what is in force today beside it."""
    return CalendarOut(
        start=day,
        days=days,
        changes=list(changes(work.db, day, days)),
        standing=[
            StandingOut(
                segment=one.segment,
                kind=one.kind,
                limit=one.limit,
                note=one.note,
                permanent=one.permanent,
            )
            for one in standing_on(work.db, day)
        ],
    )
