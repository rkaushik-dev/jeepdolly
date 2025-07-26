"""The one pass that decides an application, and everything it worked out.

Everything before this file measures one thing. This runs them all in the right
order against one application and produces either a permit or a list of
reasons. It is the only place that knows the order, and the order is the
office procedure rather than a technical dependency.

The pilot cars a hauler has named are read after the escort table, because
the table says how many are wanted and the naming is checked against that. A
card that is no good is a refusal even when the load would otherwise be fine,
and naming nobody at all is not.

Carrier first, because a suspended carrier is not entitled to the office's
afternoon, and the enforcement hold with it: a carrier that has neither paid
nor contested a citation for seven weeks has stopped answering letters, and
the office holds new permits until it starts again.

Commodity next, because a divisible load is refused before anybody walks a
route. Then the route, either the one written on the application or one
the router finds. Then weight, which needs the route because the limits differ
by class of road. Then classification, which needs both. Then the structures,
the escorts, the marking, the travel limits, and last the price, because the
price depends on the classification and the mileage.

Three of the steps are named functions rather than paragraphs of this one,
because they are the three somebody reads on their own: whether the carrier
is entitled to anything, what table the load is weighed against, and what is
in the way of it. The rest is a sequence and reads as one.

An assessment is produced whether or not it can be permitted. A refused
application still gets the whole assessment, because the letter has to say
everything that is wrong rather than the first thing found.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, time

from ..conditions.assemble import assemble
from ..conditions.library import Condition
from ..domain.envelope import Envelope
from ..domain.highway import HighwayClass
from ..domain.stream import AxleStream
from ..errors import Impossible
from ..escorts.marking import Marking, marking_for
from ..escorts.naming import Naming
from ..escorts.naming import check as check_cards
from ..escorts.rules import Escorts
from ..escorts.rules import required as escorts_required
from ..fees.price import Quote, price
from ..limits import weight as weight_rules
from ..limits.classify import Classification, PermitKind, classify
from ..limits.statutory import StatutoryBook
from ..limits.weight import WeightReading
from ..measure import Pounds
from ..routing.evaluate import RouteReading, evaluate
from ..routing.itinerary import Itinerary
from ..routing.mileage import Mileage, chargeable
from ..routing.select import Search, find
from ..structures.analysis import Reading as StructureReading
from ..structures.analysis import analyse_route
from ..travel.windows import TravelLimits, limits_for
from .application import Application
from .office import Office


@dataclass(frozen=True)
class Assessment:
    """Everything the office worked out about one application."""

    application: Application
    refusals: tuple[str, ...]
    # Everything below here is absent when the procedure stopped before it got
    # that far, which is why they all default. They used to be required, and a
    # second constructor existed to pass None to every one of them. Adding a
    # field then meant editing two lists and forgetting the second one twice.
    itinerary: Itinerary | None = None
    classification: Classification | None = None
    weight: WeightReading | None = None
    route: RouteReading | None = None
    structures: StructureReading | None = None
    escorts: Escorts | None = None
    naming: Naming | None = None
    marking: Marking | None = None
    limits: TravelLimits | None = None
    mileage: Mileage | None = None
    quote: Quote | None = None
    conditions: tuple[Condition, ...] = ()

    @property
    def permittable(self) -> bool:
        return not self.refusals

    @property
    def kind(self) -> PermitKind:
        return self.classification.kind if self.classification else PermitKind.NONE

    def __str__(self) -> str:
        if self.permittable:
            return f"{self.application.reference}: {self.kind} permit"
        return f"{self.application.reference}: refused, {len(self.refusals)} reasons"


def governing_class(itinerary: Itinerary, statutes: StatutoryBook, day: date) -> HighwayClass:
    """The class of road on the route with the least generous gross limit."""
    permittable = [one for one in itinerary.classes() if one.permittable]
    if not permittable:
        raise Impossible("the route runs over no road this office writes permits for")
    return min(permittable, key=lambda one: statutes.on(one, day).gross.value)


def entitled(office: Office, application: Application, day: date, gross: Pounds) -> tuple[str, ...]:
    """Whether this carrier is entitled to the office's afternoon at all.

    Two questions and neither of them looks at the load. A suspended carrier
    is refused, and so is one that has neither paid nor argued a citation for
    seven weeks. Both are about the carrier rather than the trip, which is
    why they are read before anything is measured.
    """
    found = []
    carrier = office.carriers.get(application.usdot)
    standing = carrier.standing(application.start, application.end, gross.value)
    if standing:
        found.append(standing)
    hold = office.enforcement.hold(application.usdot, day)
    if hold:
        found.append(hold)
    return tuple(found)


def weigh(
    office: Office,
    itinerary: Itinerary,
    stream: AxleStream,
    day: date,
) -> WeightReading:
    """Read the load against the tightest table the route touches.

    A trip that runs sixteen miles on the interstate and two hundred yards on
    a state aid road is held to the state aid table for the whole trip. That
    is conservative and it is what the office does, because a permit legal
    for part of a route is not a thing that can be printed on one sheet.
    """
    table = office.statutes.on(governing_class(itinerary, office.statutes, day), day)
    order = office.seasons.in_force(f"district {itinerary.districts()[0]}", day)
    return weight_rules.read(stream, table, order)


def walk_route(
    office: Office,
    itinerary: Itinerary,
    envelope: Envelope,
    gross: Pounds,
    day: date,
) -> RouteReading:
    """Everything in the way of this load on this route on this day."""
    return evaluate(
        itinerary,
        envelope,
        gross,
        day,
        office.restrictions,
        office.seasons,
        office.statutes,
        turns=office.turns,
    )


def assess(office: Office, application: Application, day: date | None = None) -> Assessment:
    """Run the whole procedure once and report everything it found."""
    day = day or application.start
    refusals: list[str] = []
    envelope = application.combination.envelope
    stream = application.combination.stream
    gross = stream.gross

    refusals.extend(entitled(office, application, day, gross))
    commodity = office.commodities.get(application.commodity)

    itinerary = _route_for(office, application, envelope, gross, day)
    if itinerary is None:
        refusals.append("no route this load can take on the days asked for")
        return Assessment(application, tuple(refusals))

    weight = weigh(office, itinerary, stream, day)
    size_table = office.sizes.tightest(tuple(one for one in itinerary.classes() if one.permittable))
    classification = classify(envelope, weight, size_table, commodity)
    if classification.refusal:
        refusals.append(classification.refusal)

    route = walk_route(office, itinerary, envelope, gross, day)
    refusals.extend(str(one) for one in route.fatal)

    structures = analyse_route(office.structures, itinerary, stream)
    for finding in structures.refusals:
        refusals.append(f"{finding.structure.name} will not take this load")

    superload = classification.kind is PermitKind.SUPERLOAD
    escorts = escorts_required(envelope, itinerary.classes(), superload=superload)
    naming = check_cards(office.escorts, application.escort_cards, escorts, day)
    refusals.extend(naming.refusals)
    marking = marking_for(envelope, superload=superload)
    limits = limits_for(envelope, superload=superload, high_centre=commodity.high_centre)
    mileage = chargeable(itinerary)

    quote = None
    conditions: tuple[Condition, ...] = ()
    if classification.kind is not PermitKind.NONE:
        quote = price(
            office.schedules.on(day),
            classification.kind,
            mileage,
            weight.worst_overage(),
            review=classification.kind.needs_engineer,
        )
        conditions = assemble(
            office.library,
            application.start.isoformat(),
            application.end.isoformat(),
            limits,
            escorts,
            marking,
            route=route,
            structures=structures,
            liftable_axles=stream.liftable_count(),
            naming=naming,
        )
    elif not refusals:
        refusals.append("this load is legal and needs no permit")

    return Assessment(
        application=application,
        refusals=tuple(refusals),
        itinerary=itinerary,
        classification=classification,
        weight=weight,
        route=route,
        structures=structures,
        escorts=escorts,
        naming=naming,
        marking=marking,
        limits=limits,
        mileage=mileage,
        quote=quote,
        conditions=conditions,
    )


def _route_for(
    office: Office,
    application: Application,
    envelope: Envelope,
    gross: Pounds,
    day: date,
) -> Itinerary | None:
    if application.route:
        return Itinerary(office.network, application.origin, application.route)
    search = Search(envelope=envelope, gross=gross, day=day, departure=time(9, 0))
    return find(
        office.network,
        application.origin,
        application.destination,
        search,
        office.restrictions,
        office.seasons,
        office.statutes,
        turns=office.turns,
    )
