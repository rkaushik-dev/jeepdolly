"""Annual permit routes, including the one that answers before anybody pays.

The eligibility route is the interesting one. A crane company asks it once a
year with the machine it is thinking of buying, and the answer decides whether
the machine gets bought. It takes no application, writes nothing, and needs
only a reader, because it is a question about the published envelope and not
about anybody's paperwork.

Issuing one still goes through an application, because the office wants the
combination on file and a reference to write the permit against. What it does
not do is walk a route, and that is the whole difference.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel

from ...errors import NotAllowed
from ...measure import Pounds
from ...permits.annual import ANNUAL_ENVELOPE, ANNUAL_GROSS, eligible, issue_annual
from ...persistence.unitofwork import UnitOfWork
from ...services import audit
from ..deps import office_from, unit_of_work
from ..schemas import EnvelopeIn, PermitOut
from ..security import Caller, Role, needs

router = APIRouter(prefix="/annual", tags=["annual"])


class EligibilityIn(BaseModel):
    """A combination somebody is thinking about."""

    envelope: EnvelopeIn
    gross: str
    commodity: str


class EligibilityOut(BaseModel):
    """Whether it can hold an annual, and the envelope it was read against."""

    allowed: bool
    reasons: list[str]
    envelope_width: str
    envelope_height: str
    envelope_length: str
    gross_cap: str


@router.post("/eligibility", response_model=EligibilityOut)
def check_eligibility(
    body: EligibilityIn,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> EligibilityOut:
    """Read the annual envelope against a combination, and write nothing."""
    commodity = work.commodities.book().get(body.commodity)
    found = eligible(body.envelope.to_domain(), Pounds.parse(body.gross), commodity)
    return EligibilityOut(
        allowed=found.allowed,
        reasons=list(found.reasons),
        envelope_width=str(ANNUAL_ENVELOPE.width),
        envelope_height=str(ANNUAL_ENVELOPE.height),
        envelope_length=str(ANNUAL_ENVELOPE.length),
        gross_cap=str(ANNUAL_GROSS),
    )


@router.post("", response_model=PermitOut, status_code=201)
def issue(
    reference: str = Query(...),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> PermitOut:
    """Write an annual against an application already on file."""
    day = on or date.today()
    application = work.applications.get(reference)
    desk = office_from(work, day)
    carrier = desk.carriers.get(application.usdot)
    standing = carrier.standing(day, day, application.gross.value)
    if standing:
        raise NotAllowed(standing, usdot=application.usdot)
    permit = issue_annual(
        desk.numbering,
        desk.library,
        desk.schedules.on(day),
        application.reference,
        application.usdot,
        carrier.name,
        application.combination.envelope,
        application.gross,
        desk.commodities.get(application.commodity),
        day,
    )
    application.issue(day, str(permit.number))
    work.applications.save_state(application)
    work.permits.add(permit)
    audit.record(
        work.db,
        "permit.issued",
        who.person,
        "permit",
        str(permit.number),
        detail=f"annual to {carrier.name}",
    )
    work.commit()
    return PermitOut.of(permit)
