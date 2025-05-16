"""Repairing a route the hauler wrote, instead of throwing it away.

When a route fails, the counter has two answers. One is the shortest route the
load can take, which the router already gives and which sometimes bears no
resemblance to the trip the hauler planned. The other is the route he wrote
with the broken part replaced, which is usually what he wants, because he
knows where the yard entrance is and the router does not.

So a repair keeps the itinerary up to the last place the load could still get
to, and searches from there to the same destination. What comes back is one
itinerary with the good part kept, and a note of which segments were dropped
and how many miles the fix cost.

A repair can fail, and it fails in two ways worth telling apart. There may be
no route at all, in which case the load is not going today. Or the only route
found may be the one already written, which happens when the trouble is on the
last segment and there is nothing else into the destination. Both come back as
nothing, and the caller reads the reading rather than guessing.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..domain.network import Network
from ..domain.restriction import RestrictionBook
from ..limits.statutory import StatutoryBook
from ..measure import Miles
from ..seasons import SeasonBook
from .evaluate import segment_obstructions
from .itinerary import Itinerary
from .select import Search, find
from .turns import TurnBook


@dataclass(frozen=True)
class Repair:
    """A route with the broken part replaced, and what it cost."""

    original: Itinerary
    repaired: Itinerary
    dropped: tuple[str, ...]
    added: tuple[str, ...]

    @property
    def extra(self) -> Miles:
        return self.repaired.distance - self.original.distance

    def __str__(self) -> str:
        dropped = ", ".join(self.dropped)
        added = ", ".join(self.added)
        extra = self.extra
        if extra.hundredths < 0:
            return f"round {dropped} by {added}, {Miles(-extra.hundredths)} shorter"
        return f"round {dropped} by {added}, {extra} further"


def first_trouble(
    itinerary: Itinerary,
    search: Search,
    restrictions: RestrictionBook,
    seasons: SeasonBook,
    statutes: StatutoryBook,
) -> int | None:
    """The index of the first leg this load cannot drive, if there is one."""
    for index, leg in enumerate(itinerary):
        trouble = segment_obstructions(
            leg.segment,
            search.envelope,
            search.gross,
            search.day,
            restrictions,
            seasons,
            statutes,
            search.departure,
        )
        if any(one.fatal for one in trouble):
            return index
    return None


def repair(
    network: Network,
    itinerary: Itinerary,
    search: Search,
    restrictions: RestrictionBook,
    seasons: SeasonBook,
    statutes: StatutoryBook,
    turns: TurnBook | None = None,
) -> Repair | None:
    """Keep the good part of a route and search from where it stops."""
    index = first_trouble(itinerary, search, restrictions, seasons, statutes)
    if index is None:
        return None
    for cut in range(index, -1, -1):
        kept = itinerary.idents[:cut]
        onward = find(
            network,
            itinerary.legs[cut].entered_at,
            itinerary.destination,
            search,
            restrictions,
            seasons,
            statutes,
            turns=turns,
        )
        if onward is None:
            continue
        joined = kept + onward.idents
        if len(set(joined)) != len(joined):
            # Backing up to here would drive a piece of road twice, which is
            # not a route anybody would sign. Give up more of the written one.
            continue
        repaired = Itinerary(network, itinerary.origin, joined)
        if repaired.idents == itinerary.idents:
            return None
        dropped = tuple(one for one in itinerary.idents if one not in repaired.idents)
        added = tuple(one for one in repaired.idents if one not in itinerary.idents)
        return Repair(itinerary, repaired, dropped, added)
    return None
