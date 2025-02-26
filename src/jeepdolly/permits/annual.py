"""The annual permit, which is a different animal from a single trip.

A single trip permit is written against a load, a route and a week. An annual
is written against a combination and a year, with no route at all, and the
hauler is trusted to keep inside a published envelope and to obey what is
posted. It exists because a crane company that moves the same crane four
times a week cannot ring the office four times a week.

Three things make it different and all three are worth stating.

There is no route. The permit says all state maintained highways, and that is
why the envelope is tight: the office is agreeing to a load it has not walked
a route for. Anything past the annual envelope needs a trip permit, whatever
the hauler would prefer.

There is no bridge analysis, for the same reason and with the same answer. The
weight cap is set below what the posted structures on the system will take, so
that an annual holder crossing a posted bridge is inside the posting.

It is valid for a year from the day it is issued, not for a calendar year. A
hauler who buys one in November gets twelve months, because the alternative is
a queue at the counter every January.

What the annual does not excuse is a posting or a closure. The conditions say
so in as many words, because the one argument the office has every spring is a
hauler with an annual on a road under a thaw order.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from ..conditions.library import Condition, ConditionLibrary, sort_conditions
from ..domain.commodity import Commodity
from ..domain.envelope import Envelope
from ..errors import NotAllowed
from ..fees.price import Line, Quote
from ..fees.schedule import FeeSchedule
from ..measure import Inches, Miles, Pounds
from ..permits.numbering import Numbering, Series
from ..permits.permit import Permit

#: The box an annual permit covers. Past it, the hauler wants a trip permit.
ANNUAL_ENVELOPE = Envelope(
    width=Inches.of(12),
    height=Inches.of(14),
    length=Inches.of(100),
    front_overhang=Inches.of(4),
    rear_overhang=Inches.of(10),
)

#: Set below what the posted structures on the system will carry.
ANNUAL_GROSS = Pounds(120000)

#: What an annual permit says instead of a route.
EVERYWHERE = "all state maintained highways"

#: The conditions an annual carries on top of the ordinary ones.
ANNUAL_CONDITIONS = ("R-01", "T-02", "Q-01", "Q-03", "G-01", "G-02", "G-03")


@dataclass(frozen=True)
class Eligibility:
    """Whether a combination may hold an annual, and why not."""

    allowed: bool
    reasons: tuple[str, ...] = ()

    def __str__(self) -> str:
        if self.allowed:
            return "eligible for an annual permit"
        return "not eligible: " + "; ".join(self.reasons)


def eligible(envelope: Envelope, gross: Pounds, commodity: Commodity) -> Eligibility:
    """Read the annual envelope and the weight cap against one combination."""
    reasons = []
    excess = envelope.excess_over(ANNUAL_ENVELOPE)
    for name in excess.over():
        reasons.append(f"{name.replace('_', ' ')} is past the annual envelope")
    if gross > ANNUAL_GROSS:
        reasons.append(f"gross is over the annual cap of {ANNUAL_GROSS}")
    if not commodity.permittable:
        reasons.append(f"{commodity.code} could have been loaded to legal weight")
    if commodity.restricted_to_allowance:
        reasons.append(f"{commodity.code} moves under an allowance and not on an annual")
    return Eligibility(not reasons, tuple(reasons))


def price_annual(schedule: FeeSchedule) -> Quote:
    """One line, one number, and no mileage at all."""
    return Quote((Line("annual permit", schedule.annual),), str(schedule))


def conditions_for(library: ConditionLibrary, start: date, end: date) -> tuple[Condition, ...]:
    """What an annual prints, which is short and says what it does not cover."""
    found = [
        library.render("T-01", {"start": start.isoformat(), "end": end.isoformat()}),
    ]
    for code in ANNUAL_CONDITIONS:
        found.append(library.render(code))
    return sort_conditions(tuple(found))


def issue_annual(
    numbering: Numbering,
    library: ConditionLibrary,
    schedule: FeeSchedule,
    application_reference: str,
    usdot: int,
    carrier_name: str,
    envelope: Envelope,
    gross: Pounds,
    commodity: Commodity,
    on: date,
) -> Permit:
    """Write an annual, refusing anything the envelope does not cover."""
    standing = eligible(envelope, gross, commodity)
    if not standing.allowed:
        raise NotAllowed(
            f"{application_reference} cannot hold an annual permit",
            reasons=standing.reasons,
        )
    end = on + timedelta(days=364)
    return Permit(
        number=numbering.next_number(on, Series.ANNUAL),
        application=application_reference,
        usdot=usdot,
        carrier_name=carrier_name,
        issued=on,
        start=on,
        end=end,
        origin=EVERYWHERE,
        destination=EVERYWHERE,
        route=(EVERYWHERE,),
        narrative=f"{EVERYWHERE}, except as posted or closed",
        gross=gross,
        envelope=envelope,
        distance=Miles(0),
        conditions=conditions_for(library, on, end),
        quote=price_annual(schedule),
    )
