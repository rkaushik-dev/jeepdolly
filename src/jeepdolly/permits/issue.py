"""Turning a clean assessment into a numbered permit.

There is one function here and it is deliberately dull. Everything hard was
decided by the assessment, and this writes it down: a number off the counter,
the carrier as it stands today, the route and the conditions copied rather
than referenced, and the price as it was quoted.

It refuses an assessment that was refused. That check is not redundant with
the one in the assessment: a caller that has an assessment in its hand can
issue against it, and this is the last place that can say no.
"""

from __future__ import annotations

from datetime import date

from ..errors import Impossible, NotAllowed
from ..limits.classify import PermitKind
from .assessment import Assessment
from .numbering import PermitNumber, Series
from .office import Office
from .permit import Permit


def issue(office: Office, assessment: Assessment, on: date) -> Permit:
    """Turn a clean assessment into a numbered permit."""
    if not assessment.permittable:
        raise NotAllowed(
            f"{assessment.application.reference} cannot be issued",
            reasons=assessment.refusals,
        )
    application = assessment.application
    itinerary = assessment.itinerary
    quote = assessment.quote
    if itinerary is None or quote is None:
        raise Impossible("an assessment with no route or no price cannot be issued")
    series = Series.SUPERLOAD if assessment.kind is PermitKind.SUPERLOAD else Series.SINGLE
    number: PermitNumber = office.numbering.next_number(on, series)
    carrier = office.carriers.get(application.usdot)
    application.issue(on, str(number))
    return Permit(
        number=number,
        application=application.reference,
        usdot=application.usdot,
        carrier_name=carrier.name,
        issued=on,
        start=application.start,
        end=application.end,
        origin=application.origin,
        destination=application.destination,
        route=itinerary.idents,
        narrative=itinerary.narrative(),
        gross=application.gross,
        envelope=application.combination.envelope,
        distance=itinerary.distance,
        conditions=assessment.conditions,
        quote=quote,
    )
