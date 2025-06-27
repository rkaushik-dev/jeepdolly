"""Pilot car routes: the companies, the cards, and the two suspensions.

There is no route that renews a card, and there will not be one. A renewal is
a new card with a new number, issued by the same route that issues a first
one, and a route that moved an expiry date would quietly destroy the only
record of what was in force on the day of a move.

Suspending is two routes rather than one with a flag, because they are two
different decisions made by two different people. A company is suspended when
its liability insurance lapses, which the supervisor does. An operator is
suspended after an incident, which the supervisor also does but on a different
piece of paper and usually months apart.

Lifting either one is the same route with ``suspended=false``, which is not
elegant and is what the counter asked for: they wanted one screen with a
switch on it, not a suspend button and a reinstate button side by side.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from ...errors import Unreadable
from ...escorts.register import Company, Endorsement, Operator
from ...persistence.models.escorts import PilotCardRow, PilotCompanyRow
from ...persistence.unitofwork import UnitOfWork
from ...services import audit
from ..deps import unit_of_work
from ..security import Caller, Role, needs

router = APIRouter(prefix="/pilots", tags=["pilots"])


class CompanyIn(BaseModel):
    """A pilot car company as the counter types it in."""

    ident: str
    name: str
    town: str
    telephone: str = ""
    note: str = ""

    def to_domain(self) -> Company:
        return Company(
            ident=self.ident,
            name=self.name,
            town=self.town,
            telephone=self.telephone,
            note=self.note,
        )


class CompanyOut(BaseModel):
    """A company as the office holds it."""

    ident: str
    name: str
    town: str
    telephone: str
    suspended: bool
    cards: int

    @classmethod
    def of(cls, row: PilotCompanyRow) -> CompanyOut:
        return cls(
            ident=row.ident,
            name=row.name,
            town=row.town,
            telephone=row.telephone,
            suspended=row.suspended,
            cards=len(row.cards),
        )


class CardIn(BaseModel):
    """A certificate as it is issued after the examination."""

    card: str
    name: str
    company: str
    issued: date
    expires: date
    endorsements: list[str] = Field(default_factory=list)

    def to_domain(self) -> Operator:
        return Operator(
            card=self.card,
            name=self.name,
            company=self.company,
            issued=self.issued,
            expires=self.expires,
            endorsements=frozenset(_endorsement(one) for one in self.endorsements),
        )


def _endorsement(written: str) -> Endorsement:
    """One endorsement by its printed name, refusing anything else.

    The store drops an endorsement it does not know, because rows are written
    before the code that reads them ships. Coming the other way there is no
    such excuse: a clerk typing one that does not exist has made a mistake and
    wants to be told so.
    """
    try:
        return Endorsement(written)
    except ValueError:
        raise Unreadable(
            f"{written!r} is not an endorsement this office issues",
            endorsement=written,
        ) from None


class CardOut(BaseModel):
    """A card as the office holds it."""

    card: str
    name: str
    company: str
    issued: date
    expires: date
    endorsements: list[str]
    suspended: bool

    @classmethod
    def of(cls, row: PilotCardRow) -> CardOut:
        operator = row.to_domain()
        return cls(
            card=operator.card,
            name=operator.name,
            company=operator.company,
            issued=operator.issued,
            expires=operator.expires,
            endorsements=sorted(one.value for one in operator.endorsements),
            suspended=operator.suspended,
        )


@router.get("/companies", response_model=list[CompanyOut])
def list_companies(
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[CompanyOut]:
    """Every pilot car company, in ident order."""
    return [CompanyOut.of(one) for one in work.pilots.companies()]


@router.post("/companies", response_model=CompanyOut, status_code=201)
def add_company(
    body: CompanyIn,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> CompanyOut:
    """Put a pilot car company on the register."""
    row = work.pilots.add_company(body.to_domain())
    audit.record(work.db, "pilot.company_filed", who.person, "pilot company", body.ident)
    work.commit()
    return CompanyOut.of(row)


@router.get("/companies/{ident}", response_model=CompanyOut)
def read_company(
    ident: str,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> CompanyOut:
    """One company, with how many cards are on its books."""
    return CompanyOut.of(work.pilots.company_row(ident))


@router.post("/companies/{ident}/suspension", response_model=CompanyOut)
def suspend_company(
    ident: str,
    suspended: bool = Query(default=True),
    reason: str = Query(default="", max_length=200),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> CompanyOut:
    """Suspend a company, or let it back, which is the same switch."""
    row = work.pilots.suspend_company(ident, suspended)
    action = "pilot.company_suspended" if suspended else "pilot.company_restored"
    audit.record(work.db, action, who.person, "pilot company", ident, detail=reason)
    work.commit()
    return CompanyOut.of(row)


@router.get("/cards", response_model=list[CardOut])
def list_cards(
    company: str | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[CardOut]:
    """Every card, or every card of one company."""
    return [CardOut.of(one) for one in work.pilots.cards(company)]


@router.post("/cards", response_model=CardOut, status_code=201)
def certify(
    body: CardIn,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> CardOut:
    """Issue a card after the examination. A renewal comes through here too."""
    row = work.pilots.certify(body.to_domain())
    audit.record(work.db, "pilot.card_issued", who.person, "pilot card", body.card)
    work.commit()
    return CardOut.of(row)


@router.get("/cards/{card}", response_model=CardOut)
def read_card(
    card: str,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> CardOut:
    """One card, as it stands."""
    return CardOut.of(work.pilots.card_row(card))


@router.post("/cards/{card}/suspension", response_model=CardOut)
def suspend_card(
    card: str,
    suspended: bool = Query(default=True),
    reason: str = Query(default="", max_length=200),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> CardOut:
    """Suspend an operator, or let them back."""
    row = work.pilots.suspend_card(card, suspended)
    action = "pilot.card_suspended" if suspended else "pilot.card_restored"
    audit.record(work.db, action, who.person, "pilot card", card, detail=reason)
    work.commit()
    return CardOut.of(row)


@router.get("/expiring", response_model=list[CardOut])
def expiring(
    start: date = Query(...),
    end: date = Query(...),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[CardOut]:
    """The cards running out between two days, soonest first."""
    return [CardOut.of(one) for one in work.pilots.expiring(start, end)]
