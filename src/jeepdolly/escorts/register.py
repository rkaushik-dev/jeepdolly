"""Who is allowed to pilot a load, which the permit says nothing about today.

The escort table decides how many pilot cars a load needs. It has never said
anything about who they are, and for twenty years the answer was that the
carrier hired somebody and the office found out afterwards if it went wrong.

That changed with the certification order. A pilot car operator now sits an
examination, holds a certificate for three years, and carries endorsements for
the two things that need more than the ordinary card: a height pole, and
working with the state police on a superload. The office keeps the register.

Three things about the shape of this file.

A certificate belongs to a person and a person works for a company. The
company matters because it is who the carrier telephones and who carries the
liability insurance, and because a company can be suspended in one go when its
insurance lapses. So a suspended company suspends every operator on its books,
and an operator whose own certificate is good is still not available.

Expiry is read against a day, never against today. A permit written in March
for a move in June needs an operator whose card is good in June, and an
operator whose card expires in May is not one. Everything here takes the day.

A certificate that has expired is kept. The register is the office's record of
who has ever been certified, because the renewal examination is shorter for
somebody renewing than for somebody sitting it fresh, and the only way to tell
the two apart is to have kept the old card.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from enum import Enum

from ..errors import Conflict, NotOnFile

#: How long a certificate runs from the day it is issued.
TERM_YEARS = 3


class Endorsement(str, Enum):
    """The two things an ordinary card does not cover."""

    HEIGHT_POLE = "height pole"
    POLICE_LIAISON = "police liaison"

    def __str__(self) -> str:
        return self.value


class Standing(str, Enum):
    """Whether an operator can be named on a permit today."""

    GOOD = "good"
    EXPIRED = "expired"
    SUSPENDED = "suspended"
    COMPANY_SUSPENDED = "company suspended"

    @property
    def available(self) -> bool:
        return self is Standing.GOOD

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Company:
    """A pilot car company, which is who the carrier telephones."""

    ident: str
    name: str
    town: str
    telephone: str
    suspended: bool = False
    note: str = ""

    def __post_init__(self) -> None:
        if not self.ident.strip():
            raise Conflict("a pilot car company has an ident")

    def __str__(self) -> str:
        mark = " (suspended)" if self.suspended else ""
        return f"{self.ident}  {self.name}, {self.town}{mark}"


@dataclass(frozen=True)
class Operator:
    """One certified pilot car operator, with the card they hold."""

    card: str
    name: str
    company: str
    issued: date
    expires: date
    endorsements: frozenset[Endorsement] = field(default_factory=frozenset)
    suspended: bool = False

    def __post_init__(self) -> None:
        if self.expires <= self.issued:
            raise Conflict(f"card {self.card} expires before it was issued")

    def expired_on(self, day: date) -> bool:
        return day > self.expires

    def carries(self, endorsement: Endorsement) -> bool:
        return endorsement in self.endorsements

    def standing(self, day: date, company_suspended: bool = False) -> Standing:
        """Whether this operator can be named on a permit for that day."""
        if self.suspended:
            return Standing.SUSPENDED
        if company_suspended:
            return Standing.COMPANY_SUSPENDED
        if self.expired_on(day):
            return Standing.EXPIRED
        return Standing.GOOD

    def __str__(self) -> str:
        marks = ", ".join(sorted(str(one) for one in self.endorsements))
        tail = f" [{marks}]" if marks else ""
        return f"{self.card}  {self.name}  {self.company}  to {self.expires.isoformat()}{tail}"


class EscortRegister:
    """Every pilot car company and every card the office has issued."""

    def __init__(self) -> None:
        self._companies: dict[str, Company] = {}
        self._operators: dict[str, Operator] = {}

    def add_company(self, company: Company) -> Company:
        if company.ident in self._companies:
            raise Conflict(f"pilot car company {company.ident} is already on file")
        self._companies[company.ident] = company
        return company

    def company(self, ident: str) -> Company:
        try:
            return self._companies[ident]
        except KeyError:
            raise NotOnFile(f"no pilot car company {ident}", ident=ident) from None

    def companies(self) -> tuple[Company, ...]:
        return tuple(self._companies[one] for one in sorted(self._companies))

    def certify(self, operator: Operator) -> Operator:
        """Put a card on the register, refusing one with no company behind it."""
        if operator.card in self._operators:
            raise Conflict(f"card {operator.card} is already on file")
        self.company(operator.company)
        self._operators[operator.card] = operator
        return operator

    def operator(self, card: str) -> Operator:
        try:
            return self._operators[card]
        except KeyError:
            raise NotOnFile(f"no pilot car card {card}", card=card) from None

    def operators(self, company: str | None = None) -> tuple[Operator, ...]:
        found = [self._operators[one] for one in sorted(self._operators)]
        if company is None:
            return tuple(found)
        return tuple(one for one in found if one.company == company)

    def standing(self, card: str, day: date) -> Standing:
        """The one question a permit asks: can this card be named for that day."""
        operator = self.operator(card)
        company = self.company(operator.company)
        return operator.standing(day, company_suspended=company.suspended)

    def available(self, day: date, endorsement: Endorsement | None = None) -> tuple[Operator, ...]:
        """Everybody who can be named for that day, in card order."""
        found = []
        for one in self.operators():
            if not self.standing(one.card, day).available:
                continue
            if endorsement is not None and not one.carries(endorsement):
                continue
            found.append(one)
        return tuple(found)

    def expiring(self, start: date, end: date) -> tuple[Operator, ...]:
        """Cards that run out between two days, soonest first.

        A suspended card is left out. It is not going to be renewed on the
        ordinary cycle and the letter that goes with a suspension is not the
        letter that goes with a renewal.
        """
        found = [
            one for one in self.operators() if not one.suspended and start <= one.expires <= end
        ]
        return tuple(sorted(found, key=lambda one: (one.expires, one.card)))

    def __len__(self) -> int:
        return len(self._operators)

    def __contains__(self, card: object) -> bool:
        return card in self._operators
