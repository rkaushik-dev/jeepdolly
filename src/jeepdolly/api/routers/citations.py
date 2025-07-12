"""Citation routes, which are mostly the counter typing in what the post brought.

The filing route takes a number rather than allocating one, and that is not an
oversight. The number is on the ticket the officer wrote, printed on a pad in
a patrol car, and the office's job is to record it rather than to invent a
second one. The counter series exists for the handful the office writes
itself, and the route for those asks for a number first.

Every route that moves a citation takes the day it happened on, because the
post is three days behind and the day a hearing was held is not the day
somebody got round to typing it in.

Paying is a clerk's job and everything else that closes a citation is a
supervisor's. That split is the office's and not a technical one: a clerk
takes money over the counter all day, and a clerk who could dismiss a ticket
would be a clerk somebody leaned on.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from ...enforcement.citation import Citation
from ...enforcement.penalty import Offence
from ...errors import Unreadable
from ...measure import Pounds
from ...persistence.unitofwork import UnitOfWork
from ...services import enforcement
from ..deps import unit_of_work
from ..security import Caller, Role, needs

router = APIRouter(prefix="/citations", tags=["citations"])


class CitationIn(BaseModel):
    """A ticket as it arrives in the post."""

    number: str
    usdot: int
    officer: str
    offence: str
    place: str
    written: date
    filed: date | None = None
    over: str | None = None
    permit: str | None = None
    note: str = Field(default="", max_length=500)

    def kind(self) -> Offence:
        try:
            return Offence(self.offence)
        except ValueError:
            raise Unreadable(
                f"{self.offence!r} is not an offence this office prices",
                offence=self.offence,
            ) from None

    def overage(self) -> Pounds | None:
        return None if self.over is None else Pounds.parse(self.over)


class CitationOut(BaseModel):
    """A citation as the office holds it."""

    number: str
    written: date
    filed: date
    usdot: int
    carrier_name: str
    officer: str
    offence: str
    place: str
    permit: str | None
    over: str | None
    base: str
    multiplier: int
    amount: str
    state: str
    contest_closes: date

    @classmethod
    def of(cls, citation: Citation) -> CitationOut:
        return cls(
            number=citation.number,
            written=citation.written,
            filed=citation.filed,
            usdot=citation.usdot,
            carrier_name=citation.carrier_name,
            officer=citation.officer,
            offence=citation.offence.value,
            place=citation.place,
            permit=citation.permit,
            over=None if citation.over is None else str(citation.over),
            base=str(citation.assessed.base),
            multiplier=citation.assessed.multiplier,
            amount=str(citation.amount),
            state=citation.state.value,
            contest_closes=citation.contest_closes(),
        )


@router.post("", response_model=CitationOut, status_code=201)
def file_citation(
    body: CitationIn,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> CitationOut:
    """Put a roadside ticket on file, priced against the carrier's year."""
    citation = enforcement.file_citation(
        work,
        number=body.number,
        usdot=body.usdot,
        officer=body.officer,
        offence=body.kind(),
        place=body.place,
        written=body.written,
        filed=body.filed or body.written,
        who=who.person,
        over=body.overage(),
        permit=body.permit,
        note=body.note,
    )
    work.commit()
    return CitationOut.of(citation)


@router.get("/next-number")
def next_number(
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> dict[str, str]:
    """A number for the few the office writes itself."""
    number = enforcement.next_number(work, on or date.today())
    work.commit()
    return {"number": number}


@router.get("/owing", response_model=list[CitationOut])
def owing(
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[CitationOut]:
    """The chase list: everything still expecting money, oldest first."""
    return [CitationOut.of(one.to_domain()) for one in work.citations.owing()]


@router.get("/carrier/{usdot}", response_model=list[CitationOut])
def for_carrier(
    usdot: int,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[CitationOut]:
    """Everything against one carrier, newest first."""
    return [CitationOut.of(one.to_domain()) for one in work.citations.for_carrier(usdot)]


@router.get("/{number}", response_model=CitationOut)
def read_citation(
    number: str,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> CitationOut:
    """One citation, as it stands."""
    return CitationOut.of(work.citations.get(number))


@router.post("/{number}/contest", response_model=CitationOut)
def contest(
    number: str,
    reason: str = Query(..., min_length=3, max_length=500),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> CitationOut:
    """The carrier said no, in writing, inside the window."""
    citation = enforcement.contest(work, number, on or date.today(), reason, who.person)
    work.commit()
    return CitationOut.of(citation)


@router.post("/{number}/uphold", response_model=CitationOut)
def uphold(
    number: str,
    note: str = Query(default="", max_length=500),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> CitationOut:
    """The hearing went the office's way and it still wants paying."""
    citation = enforcement.uphold(work, number, on or date.today(), who.person, note)
    work.commit()
    return CitationOut.of(citation)


@router.post("/{number}/dismiss", response_model=CitationOut)
def dismiss(
    number: str,
    reason: str = Query(..., min_length=3, max_length=500),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> CitationOut:
    """The hearing went the carrier's way and it stops being a prior offence."""
    citation = enforcement.dismiss(work, number, on or date.today(), reason, who.person)
    work.commit()
    return CitationOut.of(citation)


@router.post("/{number}/payment", response_model=CitationOut)
def pay(
    number: str,
    reference: str = Query(default="", max_length=64),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> CitationOut:
    """Money arrived at the till and this is the paper catching up."""
    citation = enforcement.pay(work, number, on or date.today(), who.person, reference)
    work.commit()
    return CitationOut.of(citation)


@router.post("/{number}/void", response_model=CitationOut)
def void(
    number: str,
    reason: str = Query(..., min_length=3, max_length=500),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> CitationOut:
    """The office's own mistake, killed with a reason on it."""
    citation = enforcement.void(work, number, on or date.today(), reason, who.person)
    work.commit()
    return CitationOut.of(citation)
