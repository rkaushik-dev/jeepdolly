"""Every structure on a route against one load, in one pass.

The three pieces before this each answer part of the question. The inventory
says which structures are on the route. The loading says what each of them
feels. The rating says what that means. This puts them together and hands back
one answer for the whole trip, because that is the shape the counter and the
permit both want.

The order of the findings is the order they are driven, not the order of the
idents and not worst first. A hauler reading conditions on a permit is reading
them in the cab as he goes, and a condition about the Kennebec bridge printed
after one about a culvert forty miles later is a condition he will miss.

One structure refusing is the whole route refused. There is no partial answer
here and no averaging: a route with nineteen fine structures and one that will
not take the load is a route the load does not take.

Structures that carry no traffic are skipped rather than rated. An overpass
over the road is a clearance question and the route evaluator has already
asked it.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..domain.stream import AxleStream
from ..routing.itinerary import Itinerary
from .inventory import Structure, StructureBook
from .loading import Window, load_on
from .rating import Rating, Verdict, crossing_conditions, rate


@dataclass(frozen=True)
class Finding:
    """One structure, what it feels, and what that allows."""

    structure: Structure
    window: Window
    rating: Rating

    @property
    def verdict(self) -> Verdict:
        return self.rating.verdict

    @property
    def conditions(self) -> tuple[str, ...]:
        return crossing_conditions(self.rating)

    def __str__(self) -> str:
        return f"{self.structure.name}: {self.window.weight} of it, {self.rating.factor}%"


@dataclass(frozen=True)
class Reading:
    """Every structure on one route against one load."""

    findings: tuple[Finding, ...]

    @property
    def refusals(self) -> tuple[Finding, ...]:
        return tuple(one for one in self.findings if one.verdict is Verdict.REFUSED)

    @property
    def conditional(self) -> tuple[Finding, ...]:
        return tuple(one for one in self.findings if one.verdict is Verdict.CONDITIONAL)

    @property
    def clear(self) -> bool:
        return not self.refusals

    def conditions(self) -> tuple[str, ...]:
        found: list[str] = []
        for finding in self.findings:
            found.extend(finding.conditions)
        return tuple(found)

    def worst(self) -> Finding | None:
        rated = [one for one in self.findings if not one.rating.posted_out]
        if not rated:
            return self.findings[0] if self.findings else None
        return min(rated, key=lambda one: one.rating.factor)

    def structures_in_the_way(self) -> tuple[str, ...]:
        return tuple(one.structure.ident for one in self.refusals)

    def __len__(self) -> int:
        return len(self.findings)

    def __str__(self) -> str:
        if not self.findings:
            return "no structures on the route"
        if self.refusals:
            return f"refused at {', '.join(self.structures_in_the_way())}"
        if self.conditional:
            return f"{len(self.conditional)} structures crossed under conditions"
        return f"{len(self.findings)} structures clear"


def analyse(structures: tuple[Structure, ...], stream: AxleStream) -> Reading:
    """Rate a load over a list of structures in the order given."""
    findings = []
    for structure in structures:
        if not structure.kind.carries_traffic:
            continue
        window = load_on(structure, stream)
        findings.append(Finding(structure, window, rate(structure, window.weight)))
    return Reading(tuple(findings))


def analyse_route(book: StructureBook, itinerary: Itinerary, stream: AxleStream) -> Reading:
    """Rate a load over every structure of a trip, in driven order."""
    ordered: list[Structure] = []
    for leg in itinerary:
        ordered.extend(book.on_segment(leg.segment.ident))
    return analyse(tuple(ordered), stream)
