"""Application routes, now that the sequence lives in the counter service.

What is left in each route is the http and nothing else: the rung of the
ladder it needs, the body it reads, the day it is deciding for, and the shape
that goes back. The order of the office procedure moved to ``services.counter``
and this file no longer knows it.

Assessing is still a GET, because asking what a load would cost changes
nothing and a dispatcher asks it four times while a hauler holds the line.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query

from ...errors import Unreadable
from ...permits.application import Application, ApplicationState
from ...permits.assessment import Assessment
from ...persistence.unitofwork import UnitOfWork
from ...services import counter
from ..deps import office_from, unit_of_work
from ..schemas import ApplicationIn, ApplicationOut, AssessmentOut, ConditionOut, QuoteOut
from ..security import Caller, Role, needs

router = APIRouter(prefix="/applications", tags=["applications"])


@router.post("", response_model=ApplicationOut, status_code=201)
def file_application(
    body: ApplicationIn,
    filed: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> ApplicationOut:
    """Take an application in and give it a reference."""
    day = filed or date.today()
    reference = counter.next_reference(work, day)
    application = body.to_domain(reference, day, work.commodities.book(day))
    counter.file_application(work, application, who.person, day)
    work.commit()
    return ApplicationOut.of(application)


@router.get("/{reference}", response_model=ApplicationOut)
def read_application(
    reference: str,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> ApplicationOut:
    """One application as it was submitted."""
    return ApplicationOut.of(work.applications.get(reference))


@router.get("", response_model=list[ApplicationOut])
def list_applications(
    state: str = Query(default="submitted"),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[ApplicationOut]:
    """The queue, by state, oldest first."""
    try:
        wanted = ApplicationState(state)
    except ValueError:
        raise Unreadable(f"{state!r} is not a state an application can be in") from None
    rows = work.applications.in_state(wanted)
    return [ApplicationOut.of(work.applications.get(one.reference)) for one in rows]


@router.post("/{reference}/submit", response_model=ApplicationOut)
def submit_application(
    reference: str,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> ApplicationOut:
    """Move an application out of draft and into the queue."""
    application = counter.submit(work, reference, who.person, on or date.today())
    work.commit()
    return ApplicationOut.of(application)


@router.post("/{reference}/withdraw", response_model=ApplicationOut)
def withdraw_application(
    reference: str,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> ApplicationOut:
    """The carrier rang up and cancelled."""
    application = counter.withdraw(work, reference, who.person, on or date.today())
    work.commit()
    return ApplicationOut.of(application)


@router.post("/{reference}/refuse", response_model=ApplicationOut)
def refuse_application(
    reference: str,
    reason: str = Query(..., min_length=3),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> ApplicationOut:
    """Close an application the office will not write, with the reason."""
    application = counter.refuse(work, reference, who.person, on or date.today(), reason)
    work.commit()
    return ApplicationOut.of(application)


@router.get("/{reference}/assessment", response_model=AssessmentOut)
def assess_application(
    reference: str,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> AssessmentOut:
    """What the office makes of it, without deciding anything yet."""
    application = work.applications.get(reference)
    day = on or application.start
    return shape(application, counter.look_at(office_from(work, day), application, day))


def shape(application: Application, found: Assessment) -> AssessmentOut:
    """The assessment as it goes over the wire."""
    return AssessmentOut(
        reference=application.reference,
        permittable=found.permittable,
        kind=found.kind.value,
        refusals=list(found.refusals),
        route=list(found.itinerary.idents) if found.itinerary else [],
        distance=str(found.itinerary.distance) if found.itinerary else None,
        conditions=[ConditionOut.of(one) for one in found.conditions],
        fees=QuoteOut.of(found.quote) if found.quote else None,
    )
