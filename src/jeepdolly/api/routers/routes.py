"""Route routes: check one, find one, and ask what is in the way.

This is the part of the interface carriers actually use. A dispatcher with a
load and a map wants to know whether the road will take it before anybody
fills in an application, and the office would much rather answer that question
a hundred times than refuse one application.

Nothing here writes anything and nothing here needs more than a reader. That
is why the load is described in the query rather than filed: there is no
document, there is a question.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from ...domain.envelope import Envelope
from ...errors import NotOnFile
from ...measure import Pounds
from ...permits.office import Office
from ...routing.evaluate import evaluate
from ...routing.itinerary import Itinerary
from ...routing.mileage import chargeable
from ...routing.select import Search, find, reachable
from ..deps import office
from ..security import Caller, Role, needs

router = APIRouter(prefix="/routes", tags=["routes"])


class ObstructionOut(BaseModel):
    """One thing in the way, and what it needed."""

    segment: str
    kind: str
    note: str
    needed: str
    allowed: str
    fatal: bool


class RouteOut(BaseModel):
    """A route, its mileage, and what is in the way of it."""

    segments: list[str]
    towns: list[str]
    narrative: str
    distance: str
    chargeable_miles: int
    clear: bool
    obstructions: list[ObstructionOut] = Field(default_factory=list)


class LoadQuery(BaseModel):
    """The load, as a dispatcher describes it over the telephone."""

    width: str = "8'6"
    height: str = "13'6"
    length: str = "70'"
    rear_overhang: str = "0"
    gross: str = "80000"

    def envelope(self) -> Envelope:
        return Envelope.read(self.width, self.height, self.length, "0", self.rear_overhang)

    def weight(self) -> Pounds:
        return Pounds.parse(self.gross)


def _shape(itinerary: Itinerary, desk: Office, load: LoadQuery, day: date) -> RouteOut:
    reading = evaluate(
        itinerary,
        load.envelope(),
        load.weight(),
        day,
        desk.restrictions,
        desk.seasons,
        desk.statutes,
        turns=desk.turns,
    )
    return RouteOut(
        segments=list(itinerary.idents),
        towns=[one.title() for one in itinerary.towns()],
        narrative=itinerary.narrative(),
        distance=str(itinerary.distance),
        chargeable_miles=chargeable(itinerary).charged,
        clear=reading.clear,
        obstructions=[
            ObstructionOut(
                segment=one.segment,
                kind=one.kind,
                note=one.note,
                needed=one.needed,
                allowed=one.allowed,
                fatal=one.fatal,
            )
            for one in reading.obstructions
        ],
    )


@router.post("/check", response_model=RouteOut)
def check_route(
    load: LoadQuery,
    segments: list[str] = Query(...),
    origin: str = Query(...),
    day: date | None = Query(default=None),
    desk: Office = Depends(office),
    who: Caller = Depends(needs(Role.READER)),
) -> RouteOut:
    """Walk a route the caller has written and say what is in the way."""
    itinerary = Itinerary(desk.network, origin, tuple(segments))
    return _shape(itinerary, desk, load, day or date.today())


@router.post("/find", response_model=RouteOut)
def find_route(
    load: LoadQuery,
    origin: str = Query(...),
    destination: str = Query(...),
    day: date | None = Query(default=None),
    desk: Office = Depends(office),
    who: Caller = Depends(needs(Role.READER)),
) -> RouteOut:
    """Find the cheapest route this load can take on a day."""
    when = day or date.today()
    search = Search(envelope=load.envelope(), gross=load.weight(), day=when)
    found = find(
        desk.network,
        origin,
        destination,
        search,
        desk.restrictions,
        desk.seasons,
        desk.statutes,
    )
    if found is None:
        raise NotOnFile(
            f"no route from {origin} to {destination} this load can take",
            origin=origin,
            destination=destination,
        )
    return _shape(found, desk, load, when)


@router.post("/reachable", response_model=list[str])
def reachable_places(
    load: LoadQuery,
    origin: str = Query(...),
    day: date | None = Query(default=None),
    desk: Office = Depends(office),
    who: Caller = Depends(needs(Role.READER)),
) -> list[str]:
    """Everywhere this load can get to from here today."""
    when = day or date.today()
    search = Search(envelope=load.envelope(), gross=load.weight(), day=when)
    return list(
        reachable(desk.network, origin, search, desk.restrictions, desk.seasons, desk.statutes)
    )
