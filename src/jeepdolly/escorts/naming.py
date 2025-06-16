"""Checking the pilot cars a hauler names against the register.

The escort table says a load needs two pilot cars. Naming them is a separate
thing and it happens later, usually the day before the move, because a carrier
does not know in the morning who is free on Thursday.

So naming is optional. An application with no cards on it is assessed and
priced and, if everything else is right, issued. The permit prints a line
saying the pilot cars have not been named and that the office is to be told
before the load moves, which is what the counter says on the telephone anyway.

What is not optional is that a card, once named, is good. Three ways it can
fail and all three are refusals rather than conditions.

The card is not on the register at all. That is usually a typing mistake and
the refusal quotes what was typed so the counter can see it.

The card is not good on the day of travel. Expired, suspended, or belonging to
a company that has been suspended. The register works this out and this file
only reports it, because whether a company suspension reaches its operators is
the register's business.

The card does not carry an endorsement the load needs. A load over fourteen
six wants a height pole, and a height pole wants somebody who has been
examined on one. There is no equivalent for the police escort, because the
trooper is ordered through a different office and does not hold one of these
cards.

Naming more cards than the load needs is allowed and always has been. A
carrier that runs three cars on a load needing two is a carrier doing more
than it must, and an office that refused that would deserve the telephone call
it got.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from .register import Endorsement, EscortRegister, Operator
from .rules import Escorts

#: What the permit prints when nobody has been named yet.
NOT_NAMED = "pilot cars not named, tell the office before the load moves"


@dataclass(frozen=True)
class Naming:
    """The cards a hauler named, and what the office makes of them."""

    named: tuple[str, ...]
    good: tuple[Operator, ...]
    refusals: tuple[str, ...]
    wanted: int

    @property
    def complete(self) -> bool:
        """Whether enough good cards were named to cover the escorts wanted."""
        return len(self.good) >= self.wanted

    @property
    def acceptable(self) -> bool:
        return not self.refusals

    def line(self) -> str:
        """The line that goes on the permit face."""
        if not self.named:
            return NOT_NAMED
        return ", ".join(f"{one.name} ({one.card})" for one in self.good)

    def __str__(self) -> str:
        if self.refusals:
            return f"{len(self.refusals)} of {len(self.named)} named cards are no good"
        if not self.named:
            return "no pilot cars named"
        return f"{len(self.good)} of {self.wanted} named"


def check(
    register: EscortRegister,
    cards: tuple[str, ...],
    escorts: Escorts,
    day: date,
) -> Naming:
    """Read every named card against the register for the day of travel."""
    wanted = escorts.total
    good: list[Operator] = []
    refusals: list[str] = []
    seen: set[str] = set()
    for card in cards:
        if card in seen:
            refusals.append(f"pilot car card {card} is named twice")
            continue
        seen.add(card)
        trouble = _one(register, card, day)
        if trouble is not None:
            refusals.append(trouble)
            continue
        good.append(register.operator(card))
    if escorts.height_pole and good and not _has_pole(good):
        refusals.append("this load needs a height pole and no card named carries the endorsement")
    return Naming(tuple(cards), tuple(good), tuple(refusals), wanted)


def _one(register: EscortRegister, card: str, day: date) -> str | None:
    """What is wrong with one card, or nothing."""
    if card not in register:
        return f"pilot car card {card} is not on the register"
    standing = register.standing(card, day)
    if not standing.available:
        return f"pilot car card {card} is {standing} on {day.isoformat()}"
    return None


def _has_pole(good: list[Operator]) -> bool:
    return any(one.carries(Endorsement.HEIGHT_POLE) for one in good)


def shortfall(naming: Naming) -> int:
    """How many more good cards the load wants, which is never below zero."""
    return max(0, naming.wanted - len(naming.good))
