"""Rating one structure against one load, and what the answer allows.

The rating factor is capacity over demand. Above one the structure carries the
load. At one it carries it exactly, which is not a number anybody signs
without conditions attached.

The office works in whole percent because the inspection reports come in whole
percent and because a rating carried to four decimal places invites an argument
about the fourth. A factor of 112 means the operating rating is twelve percent
above what this load asks of it.

Three bands and they are the whole of the engineering judgement in this file.

At 110 or above the load crosses normally, in traffic, at the posted speed.

Between 100 and 110 it crosses under conditions: alone on the structure, at
crawl speed, straddling the centre line so the load sits over the girders that
were designed to take it. Those three conditions come out of this file and get
printed on the permit by the conditions package, which does not know why.

Below 100 the load does not cross. There is no discretion here and there is no
condition that fixes it. The hauler goes another way, or the load comes apart,
or the state runs a full analysis of that structure for that load, which is a
month and a fee and is not this office's decision.

A posting beats all three. If somebody signed an order posting the structure,
the sign is the limit whatever the arithmetic says, until somebody signs
another order.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from ..errors import Impossible
from ..measure import Pounds
from .inventory import Structure

#: Factor at or above which a load crosses with no conditions.
CLEAR_PERCENT = 110

#: Factor below which no condition will make the crossing acceptable.
REFUSE_PERCENT = 100

#: Postings are set from the inventory rating, rounded down to this step.
POSTING_STEP = 1000


class Verdict(Enum):
    """What the rating allows."""

    CLEARS = "clears"
    CONDITIONAL = "conditional"
    REFUSED = "refused"

    @property
    def crosses(self) -> bool:
        return self is not Verdict.REFUSED

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Rating:
    """One load over one structure, with the factor that decided it."""

    structure: Structure
    gross: Pounds
    factor: int
    verdict: Verdict
    posted_out: bool = False

    @property
    def capacity(self) -> Pounds:
        return self.structure.operating_rating or Pounds(0)

    def __str__(self) -> str:
        if self.posted_out:
            return f"{self.structure.ident} posted at {self.structure.posted}, load refused"
        return f"{self.structure.ident} rates {self.factor}% for {self.gross}, {self.verdict}"


def factor_for(structure: Structure, gross: Pounds) -> int:
    """Operating rating over the load, in whole percent, rounded down."""
    if gross.value <= 0:
        raise Impossible("a load with no weight cannot be rated")
    capacity = structure.operating_rating
    if capacity is None:
        raise Impossible(f"{structure.ident} has no operating rating to rate against")
    return capacity.value * 100 // gross.value


def rate(structure: Structure, gross: Pounds) -> Rating:
    """Rate a load over a structure, posting first and arithmetic second."""
    if not structure.carries(gross):
        return Rating(structure, gross, 0, Verdict.REFUSED, posted_out=True)
    factor = factor_for(structure, gross)
    if factor >= CLEAR_PERCENT:
        verdict = Verdict.CLEARS
    elif factor >= REFUSE_PERCENT:
        verdict = Verdict.CONDITIONAL
    else:
        verdict = Verdict.REFUSED
    return Rating(structure, gross, factor, verdict)


def crossing_conditions(rating: Rating) -> tuple[str, ...]:
    """What has to be true for a conditional crossing to be acceptable."""
    if rating.verdict is not Verdict.CONDITIONAL:
        return ()
    return (
        f"cross {rating.structure.name} alone, no other vehicle on the structure",
        f"cross {rating.structure.name} at walking pace",
        f"cross {rating.structure.name} centred on the roadway",
    )


def posting_for(structure: Structure) -> Pounds:
    """What the sign would read if it were set from the inventory rating."""
    rating = structure.inventory_rating
    if rating is None:
        raise Impossible(f"{structure.ident} has no inventory rating to post from")
    return Pounds(rating.value // POSTING_STEP * POSTING_STEP)


def posting_is_current(structure: Structure) -> bool:
    """Whether the sign agrees with the rating the last inspection produced."""
    if structure.posted is None or structure.inventory_rating is None:
        return True
    return structure.posted == posting_for(structure)
