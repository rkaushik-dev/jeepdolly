"""Amending a permit, which is issuing a new one and killing the old one.

Nobody edits a permit. The document has been printed, faxed to a customer and
filed in a truck, and a system that quietly changes what it says is a system
that cannot tell an enforcement officer what the driver is carrying.

So an amendment is a replacement. The revised application is assessed from
scratch, exactly as a new one would be, a new number is issued in the
replacement series, and the old permit is marked superseded with a pointer to
the new one. Both are kept forever.

Two rules keep this honest.

The replacement is assessed against today's road, not the road as it was when
the first permit was written. A bridge posted last week is posted for the
amendment, and a hauler who amends a permit to add a day may find he has lost
the route. That surprises people and it is right: the amendment is a new
decision about a trip that has not happened yet.

The price is the differential and never a refund. If the amended trip prices
lower, the office keeps the difference and charges the amendment fee anyway,
because the work is the same work.

What changed is worked out and recorded, because the letter that goes with the
new permit says what moved and the file has to show it.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from ..errors import Impossible, NotAllowed
from ..fees.price import Quote, amendment
from .application import Application
from .assessment import Assessment, assess
from .numbering import Series
from .office import Office
from .permit import Permit, PermitState


@dataclass(frozen=True)
class Change:
    """One thing that moved between the old permit and the new one."""

    what: str
    before: str
    after: str

    def __str__(self) -> str:
        return f"{self.what}: {self.before} to {self.after}"


def changes_between(permit: Permit, application: Application) -> tuple[Change, ...]:
    """What the revised application asks to move on an issued permit."""
    found = []
    if permit.start != application.start or permit.end != application.end:
        found.append(
            Change(
                "dates",
                f"{permit.start.isoformat()} to {permit.end.isoformat()}",
                f"{application.start.isoformat()} to {application.end.isoformat()}",
            )
        )
    if permit.origin != application.origin or permit.destination != application.destination:
        found.append(
            Change(
                "trip",
                f"{permit.origin} to {permit.destination}",
                f"{application.origin} to {application.destination}",
            )
        )
    if permit.gross != application.gross:
        found.append(Change("gross", str(permit.gross), str(application.gross)))
    if permit.envelope != application.combination.envelope:
        after = application.combination.envelope
        found.append(Change("size", str(permit.envelope), str(after)))
    if application.route and permit.route != application.route:
        found.append(Change("route", ", ".join(permit.route), ", ".join(application.route)))
    return tuple(found)


@dataclass(frozen=True)
class Amendment:
    """The result of an amendment: two permits, a price and a list."""

    original: Permit
    replacement: Permit
    changes: tuple[Change, ...]
    quote: Quote
    assessment: Assessment

    def __str__(self) -> str:
        moved = ", ".join(one.what for one in self.changes) or "nothing"
        return f"{self.original.number} to {self.replacement.number}, {moved} changed"


def amend(
    office: Office,
    permit: Permit,
    application: Application,
    on: date,
) -> Amendment:
    """Assess the revision, issue a replacement, and supersede the original."""
    if permit.state is not PermitState.ISSUED:
        raise NotAllowed(f"{permit.number} is {permit.state} and cannot be amended")
    if permit.expired_on(on):
        raise NotAllowed(f"{permit.number} expired on {permit.end.isoformat()}")
    changes = changes_between(permit, application)
    if not changes:
        raise Impossible(f"{permit.number} is not being changed by this application")

    found = assess(office, application, on)
    if not found.permittable:
        raise NotAllowed(
            f"{permit.number} cannot be amended as asked",
            reasons=found.refusals,
        )
    itinerary = found.itinerary
    quote = found.quote
    if itinerary is None or quote is None:
        raise Impossible("an amendment with no route or no price cannot be issued")

    number = office.numbering.next_number(on, Series.REPLACEMENT)
    carrier = office.carriers.get(application.usdot)
    replacement = Permit(
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
        conditions=found.conditions,
        quote=quote,
        supersedes=permit.number,
    )
    permit.supersede(on, number)
    charged = amendment(office.schedules.on(on), permit.quote, quote)
    return Amendment(permit, replacement, changes, charged, found)
