"""Pilot car companies and the cards the office has issued.

Two tables, and the split is the same one the carrier tables make: the company
row is edited, and the card row is appended.

A card is appended because a renewal is a new card with a new number, not a
new date on the old one. Somebody asks in a dispute which card was in force on
the day of the move, and a card that had its expiry edited cannot answer.

Endorsements are stored as a sorted comma separated string rather than a
second table. There are two of them and there have been two of them since the
order was made, and a join table for a set that never grows past two is a
table nobody remembers to read. If a third arrives this becomes a table and
the migration is twenty lines.

The company suspension is a flag rather than a dated order, which is not the
shape the carrier suspensions use. That is deliberate and it is a known
shortcoming: a pilot car company's insurance either is on file or is not, the
office has never needed the history, and the day it does this becomes a third
table like the carrier one.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import Boolean, CheckConstraint, Date, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ...escorts.register import Company, Endorsement, Operator
from ..base import CODE_LENGTH, NAME_LENGTH, TEXT_LENGTH, Base

#: How the endorsement set is written into the one column that holds it.
SEPARATOR = ","


def pack(endorsements: frozenset[Endorsement]) -> str:
    """The endorsement set as one sorted string, for the column."""
    return SEPARATOR.join(sorted(one.value for one in endorsements))


def unpack(written: str) -> frozenset[Endorsement]:
    """The column back into a set, ignoring anything the code does not know.

    An unknown endorsement is dropped rather than raised. The office adds one
    by writing rows first and shipping the code that reads them afterwards,
    and a deployment order that has to be exactly right is one that will not
    be exactly right.
    """
    found = set()
    for part in written.split(SEPARATOR):
        cleaned = part.strip()
        if not cleaned:
            continue
        try:
            found.add(Endorsement(cleaned))
        except ValueError:
            continue
    return frozenset(found)


class PilotCompanyRow(Base):
    """One pilot car company, edited in place."""

    __tablename__ = "pilot_companies"

    ident: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    name: Mapped[str] = mapped_column(String(NAME_LENGTH), index=True)
    town: Mapped[str] = mapped_column(String(NAME_LENGTH))
    telephone: Mapped[str] = mapped_column(String(CODE_LENGTH), default="")
    suspended: Mapped[bool] = mapped_column(Boolean, default=False)
    note: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")

    cards: Mapped[list[PilotCardRow]] = relationship(
        back_populates="company",
        cascade="all, delete-orphan",
        order_by="PilotCardRow.card",
    )

    def to_domain(self) -> Company:
        return Company(
            ident=self.ident,
            name=self.name,
            town=self.town,
            telephone=self.telephone,
            suspended=self.suspended,
            note=self.note,
        )

    @classmethod
    def of(cls, company: Company) -> PilotCompanyRow:
        return cls(
            ident=company.ident,
            name=company.name,
            town=company.town,
            telephone=company.telephone,
            suspended=company.suspended,
            note=company.note,
        )

    def __repr__(self) -> str:
        return f"<PilotCompanyRow {self.ident}>"


class PilotCardRow(Base):
    """One certificate, as issued. Renewed by a new row, never edited."""

    __tablename__ = "pilot_cards"
    __table_args__ = (CheckConstraint("expires > issued", name="expires_after_issued"),)

    card: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    name: Mapped[str] = mapped_column(String(NAME_LENGTH), index=True)
    company_ident: Mapped[str] = mapped_column(ForeignKey("pilot_companies.ident"), index=True)
    issued: Mapped[date] = mapped_column(Date)
    expires: Mapped[date] = mapped_column(Date, index=True)
    endorsements: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")
    suspended: Mapped[bool] = mapped_column(Boolean, default=False)

    company: Mapped[PilotCompanyRow] = relationship(back_populates="cards")

    def to_domain(self) -> Operator:
        return Operator(
            card=self.card,
            name=self.name,
            company=self.company_ident,
            issued=self.issued,
            expires=self.expires,
            endorsements=unpack(self.endorsements),
            suspended=self.suspended,
        )

    @classmethod
    def of(cls, operator: Operator) -> PilotCardRow:
        return cls(
            card=operator.card,
            name=operator.name,
            company_ident=operator.company,
            issued=operator.issued,
            expires=operator.expires,
            endorsements=pack(operator.endorsements),
            suspended=operator.suspended,
        )

    def __repr__(self) -> str:
        return f"<PilotCardRow {self.card}>"
