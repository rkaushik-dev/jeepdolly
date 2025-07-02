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

The state of the search is a place and the road it was reached on, not a place
on its own. That is because of the corners. Whether a load can leave Skowhegan
on US 2 depends on which road it came into Skowhegan by, so the cost of being
at a place is not one number, and a search that keeps one number per place will
quietly hand out a route with a turn in it that nothing can get round.

It costs what it sounds like it costs. There are as many states as there are
ends of segments, which on a road network is a small multiple of the places,
and the frontier grows the same way. On a state of eleven thousand segments
this is still under a second, and the office does not route in a loop.
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
from .sweep import radius_wanted
from .turns import TurnBook

#: Extra hundredths of a mile charged per hundredth on the lesser roads.
PENALTY = {
    HighwayClass.INTERSTATE: 0,
    HighwayClass.ARTERIAL: 0,
    HighwayClass.STATE_AID: 1,
    HighwayClass.TOWN_WAY: 4,
}


#: A place and the segment the load arrived on, which is empty at the origin.
State = tuple[str, str]


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
    turns: TurnBook | None = None,
) -> Itinerary | None:
    """The cheapest route this load can take today, or None if there is none."""
    network.node(origin)
    network.node(destination)
    if origin == destination:
        return None
    start: State = (origin, "")
    seen: set[State] = set()
    best: dict[State, int] = {start: 0}
    came: dict[State, tuple[State, str]] = {}
    frontier: list[tuple[int, str, str]] = [(0, origin, "")]
    while frontier:
        cost, here, arrived = heapq.heappop(frontier)
        standing: State = (here, arrived)
        if standing in seen:
            continue
        seen.add(standing)
        if here == destination:
            return _rebuild(network, origin, standing, came)
        for segment in network.leaving(here):
            beyond = segment.other_end(here)
            onward: State = (beyond, segment.ident)
            if onward in seen:
                continue
            if _wont_turn(here, arrived, segment.ident, search, turns):
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
            if through < best.get(onward, through + 1):
                best[onward] = through
                came[onward] = (standing, segment.ident)
                heapq.heappush(frontier, (through, beyond, segment.ident))
    return None


def _wont_turn(
    node: str,
    arrived: str,
    onto: str,
    search: Search,
    turns: TurnBook | None,
) -> bool:
    """Whether this load can get from one road onto the other at this place.

    A corner nobody measured is not in the way. That is deliberate: the card
    index covers the corners somebody has had trouble at, and the office does
    not refuse a route because a junction is missing from it.
    """
    if turns is None or not arrived:
        return False
    corner = turns.between(node, arrived, onto)
    if corner is None:
        return False
    return corner.radius_feet < radius_wanted(search.envelope)


def _cost(hundredths: int, highway_class: HighwayClass, penalties: dict[HighwayClass, int]) -> int:
    return hundredths * (1 + penalties.get(highway_class, 0))


def _rebuild(
    network: Network,
    origin: str,
    arrived: State,
    came: dict[State, tuple[State, str]],
) -> Itinerary:
    idents: list[str] = []
    standing = arrived
    while standing[1]:
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
    turns: TurnBook | None = None,
) -> tuple[str, ...]:
    """Every place this load can get to from here today.

    The counter uses this the other way round from the router: a hauler asks
    whether a mill is reachable at all before anybody argues about the route.

    This walks the same states the router does, for the same reason. A place
    reachable only through a corner the load will not get round is not a place
    the load can get to, however many roads run into it.
    """
    start: State = (origin, "")
    found = {origin}
    seen: set[State] = {start}
    frontier: list[State] = [start]
    while frontier:
        here, arrived = frontier.pop()
        for segment in network.leaving(here):
            beyond = segment.other_end(here)
            onward: State = (beyond, segment.ident)
            if onward in seen:
                continue
            if _wont_turn(here, arrived, segment.ident, search, turns):
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
            seen.add(onward)
            found.add(beyond)
            frontier.append(onward)
    return tuple(sorted(found))
