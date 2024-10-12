"""Finding a route for a load, rather than checking the one it was handed.

Haulers write their own route on the application and most of the time it is
fine. When it is not, the counter needs an answer better than no, and that
answer is the shortest route this load can actually take today.

The search is an ordinary shortest path with two differences.

A segment is only usable if nothing fatal sits on it for this load on this
day, which means the clearance, the posting, the closure and the class of road
are all read before the segment is added to the frontier. That is the whole
composition of this file: routing is the restriction reader run over a graph
instead of over a list.

Cost is not distance. Cost is distance with a penalty per mile for the lesser
roads, because a route that saves two miles by sending a hundred and forty
thousand pounds through a village is not a route the office wants to have
signed. The penalties are data, and a caller that wants pure distance passes
penalties of zero.

Ties are broken by segment ident so the same request gives the same route
twice. A router that returns two different answers to the same question is a
router nobody trusts and everybody works around.
"""

from __future__ import annotations

import heapq
from dataclasses import dataclass, field
from datetime import date, time

from ..domain.envelope import Envelope
from ..domain.highway import HighwayClass
from ..domain.network import Network
from ..domain.restriction import RestrictionBook
from ..limits.statutory import StatutoryBook
from ..measure import Pounds
from ..seasons import SeasonBook
from .evaluate import segment_obstructions
from .itinerary import Itinerary

#: Extra hundredths of a mile charged per hundredth on the lesser roads.
PENALTY = {
    HighwayClass.INTERSTATE: 0,
    HighwayClass.ARTERIAL: 0,
    HighwayClass.STATE_AID: 1,
    HighwayClass.TOWN_WAY: 4,
}


@dataclass(frozen=True)
class Search:
    """Everything the router needs to know about the load and the day."""

    envelope: Envelope
    gross: Pounds
    day: date
    departure: time | None = None
    penalties: dict[HighwayClass, int] = field(default_factory=lambda: dict(PENALTY))


def find(
    network: Network,
    origin: str,
    destination: str,
    search: Search,
    restrictions: RestrictionBook,
    seasons: SeasonBook,
    statutes: StatutoryBook,
) -> Itinerary | None:
    """The cheapest route this load can take today, or None if there is none."""
    network.node(origin)
    network.node(destination)
    if origin == destination:
        return None
    seen: set[str] = set()
    best: dict[str, int] = {origin: 0}
    came: dict[str, tuple[str, str]] = {}
    frontier: list[tuple[int, str, str]] = [(0, origin, "")]
    while frontier:
        cost, here, _ = heapq.heappop(frontier)
        if here in seen:
            continue
        seen.add(here)
        if here == destination:
            return _rebuild(network, origin, destination, came)
        for segment in network.leaving(here):
            beyond = segment.other_end(here)
            if beyond in seen:
                continue
            if segment_obstructions(
                segment,
                search.envelope,
                search.gross,
                search.day,
                restrictions,
                seasons,
                statutes,
                search.departure,
            ):
                continue
            step = _cost(segment.length.hundredths, segment.highway_class, search.penalties)
            through = cost + step
            if through < best.get(beyond, through + 1):
                best[beyond] = through
                came[beyond] = (here, segment.ident)
                heapq.heappush(frontier, (through, beyond, segment.ident))
    return None


def _cost(hundredths: int, highway_class: HighwayClass, penalties: dict[HighwayClass, int]) -> int:
    return hundredths * (1 + penalties.get(highway_class, 0))


def _rebuild(
    network: Network,
    origin: str,
    destination: str,
    came: dict[str, tuple[str, str]],
) -> Itinerary:
    idents: list[str] = []
    standing = destination
    while standing != origin:
        before, ident = came[standing]
        idents.append(ident)
        standing = before
    idents.reverse()
    return Itinerary(network, origin, tuple(idents))


def reachable(
    network: Network,
    origin: str,
    search: Search,
    restrictions: RestrictionBook,
    seasons: SeasonBook,
    statutes: StatutoryBook,
) -> tuple[str, ...]:
    """Every place this load can get to from here today.

    The counter uses this the other way round from the router: a hauler asks
    whether a mill is reachable at all before anybody argues about the route.
    """
    found = {origin}
    frontier = [origin]
    while frontier:
        here = frontier.pop()
        for segment in network.leaving(here):
            beyond = segment.other_end(here)
            if beyond in found:
                continue
            if segment_obstructions(
                segment,
                search.envelope,
                search.gross,
                search.day,
                restrictions,
                seasons,
                statutes,
                search.departure,
            ):
                continue
            found.add(beyond)
            frontier.append(beyond)
    return tuple(sorted(found))
