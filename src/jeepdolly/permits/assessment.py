"""The one pass that decides an application, and everything it worked out.

Everything before this file measures one thing. This runs them all in the right
order against one application and produces either a permit or a list of
reasons. It is the only place that knows the order, and the order is the
office procedure rather than a technical dependency.

Carrier first, because a suspended carrier is not entitled to the office's
afternoon. Commodity next, because a divisible load is refused before anybody
walks a route. Then the route, either the one written on the application or one
the router finds. Then weight, which needs the route because the limits differ
by class of road. Then classification, which needs both. Then the structures,
the escorts, the marking, the travel limits, and last the price, because the
price depends on the classification and the mileage.

The governing weight table is the tightest one the route touches. A trip that
runs sixteen miles on the interstate and two hundred yards on a state aid road
is held to the state aid table for those two hundred yards, and this system
holds the whole trip to it. That is conservative and it is what the office
does, because a permit that is legal for part of a route is not a thing that
can be printed on one piece of paper.

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
from ..errors import Impossible
from ..escorts.marking import Marking, marking_for
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
    itinerary: Itinerary | None
    classification: Classification | None
    weight: WeightReading | None
    route: RouteReading | None
    structures: StructureReading | None
    escorts: Escorts | None
    marking: Marking | None
    limits: TravelLimits | None
    mileage: Mileage | None
    quote: Quote | None
    conditions: tuple[Condition, ...]
    refusals: tuple[str, ...]

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


def assess(office: Office, application: Application, day: date | None = None) -> Assessment:
    """Run the whole procedure once and report everything it found."""
    day = day or application.start
    refusals: list[str] = []
    envelope = application.combination.envelope
    stream = application.combination.stream
    gross = stream.gross

    carrier = office.carriers.get(application.usdot)
    standing = carrier.standing(application.start, application.end, gross.value)
    if standing:
        refusals.append(standing)

    commodity = office.commodities.get(application.commodity)

    itinerary = _route_for(office, application, envelope, gross, day)
    if itinerary is None:
        refusals.append("no route this load can take on the days asked for")
        return _nothing(application, tuple(refusals))

    table = office.statutes.on(governing_class(itinerary, office.statutes, day), day)
    order = office.seasons.in_force(f"district {itinerary.districts()[0]}", day)
    weight = weight_rules.read(stream, table, order)
    size_table = office.sizes.tightest(tuple(one for one in itinerary.classes() if one.permittable))
    classification = classify(envelope, weight, size_table, commodity)
    if classification.refusal:
        refusals.append(classification.refusal)

    route = evaluate(
        itinerary,
        envelope,
        gross,
        day,
        office.restrictions,
        office.seasons,
        office.statutes,
        turns=office.turns,
    )
    refusals.extend(str(one) for one in route.fatal)

    structures = analyse_route(office.structures, itinerary, stream)
    for finding in structures.refusals:
        refusals.append(f"{finding.structure.name} will not take this load")

    superload = classification.kind is PermitKind.SUPERLOAD
    escorts = escorts_required(envelope, itinerary.classes(), superload=superload)
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
        )
    elif not refusals:
        refusals.append("this load is legal and needs no permit")

    return Assessment(
        application=application,
        itinerary=itinerary,
        classification=classification,
        weight=weight,
        route=route,
        structures=structures,
        escorts=escorts,
        marking=marking,
        limits=limits,
        mileage=mileage,
        quote=quote,
        conditions=conditions,
        refusals=tuple(refusals),
    )


def _nothing(application: Application, refusals: tuple[str, ...]) -> Assessment:
    """An assessment that stopped before it had anything to report."""
    return Assessment(
        application=application,
        itinerary=None,
        classification=None,
        weight=None,
        route=None,
        structures=None,
        escorts=None,
        marking=None,
        limits=None,
        mileage=None,
        quote=None,
        conditions=(),
        refusals=refusals,
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
    )
