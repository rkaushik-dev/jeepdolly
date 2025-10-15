"""Pilot car companies and the cards the office issues."""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ...errors import Conflict, NotOnFile
from ...escorts.register import Company, Operator
from ..models.escorts import PilotCardRow, PilotCompanyRow


class PilotRepository:
    """Pilot car companies and the cards the office has issued.

    A card is never edited except to be suspended or lifted. A renewal is a
    new card, which is why there is no method here that moves an expiry date.
    """

    def __init__(self, session: Session) -> None:
        self.session = session

    def company_row(self, ident: str) -> PilotCompanyRow:
        found = self.session.get(PilotCompanyRow, ident)
        if found is None:
            raise NotOnFile(f"no pilot car company {ident}", ident=ident)
        return found

    def card_row(self, card: str) -> PilotCardRow:
        found = self.session.get(PilotCardRow, card)
        if found is None:
            raise NotOnFile(f"no pilot car card {card}", card=card)
        return found

    def companies(self) -> tuple[PilotCompanyRow, ...]:
        query = select(PilotCompanyRow).order_by(PilotCompanyRow.ident)
        return tuple(self.session.scalars(query).all())

    def cards(self, company: str | None = None) -> tuple[PilotCardRow, ...]:
        query = select(PilotCardRow).order_by(PilotCardRow.card)
        if company is not None:
            query = query.where(PilotCardRow.company_ident == company)
        return tuple(self.session.scalars(query).all())

    def add_company(self, company: Company) -> PilotCompanyRow:
        if self.session.get(PilotCompanyRow, company.ident) is not None:
            raise Conflict(f"pilot car company {company.ident} is already on file")
        row = PilotCompanyRow.of(company)
        self.session.add(row)
        return row

    def certify(self, operator: Operator) -> PilotCardRow:
        """Issue a card, refusing one whose company is not on file."""
        if self.session.get(PilotCardRow, operator.card) is not None:
            raise Conflict(f"pilot car card {operator.card} is already on file")
        self.company_row(operator.company)
        row = PilotCardRow.of(operator)
        self.session.add(row)
        return row

    def suspend_company(self, ident: str, suspended: bool = True) -> PilotCompanyRow:
        row = self.company_row(ident)
        row.suspended = suspended
        return row

    def suspend_card(self, card: str, suspended: bool = True) -> PilotCardRow:
        row = self.card_row(card)
        row.suspended = suspended
        return row

    def expiring(self, start: date, end: date) -> tuple[PilotCardRow, ...]:
        """Cards running out between two days, soonest first."""
        query = (
            select(PilotCardRow)
            .where(
                PilotCardRow.suspended.is_(False),
                PilotCardRow.expires >= start,
                PilotCardRow.expires <= end,
            )
            .order_by(PilotCardRow.expires, PilotCardRow.card)
        )
        return tuple(self.session.scalars(query).all())
