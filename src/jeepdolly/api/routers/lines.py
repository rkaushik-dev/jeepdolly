"""Overhead line routes: the owners, the wires, and the survey that moves them.

Remeasuring is a PUT rather than a POST because it replaces what the card
says. There is no history and no version, and the reason is in the model
docstring: a wire is a fact about the world today, and nobody has ever asked
this office what a span used to hang at.

The clearance question is the one route the counter actually uses. A
dispatcher rings up and asks whether nineteen feet gets from Waterville to
Skowhegan, and this answers with the wires in the way and the telephone
numbers to ring, without writing anything down.

Surveying is a supervisor's job and reading is everybody's, which is the same
split every other reference table uses.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from ...domain.envelope import Envelope
from ...measure import Inches
from ...persistence.books import lines_from, network_from
from ...persistence.models.utilities import LineCrossingRow, LineOwnerRow
from ...persistence.unitofwork import UnitOfWork
from ...routing.itinerary import Itinerary
from ...utilities.lines import Crossing, Owner, under
from ..deps import unit_of_work
from ..security import Caller, Role, needs

router = APIRouter(prefix="/lines", tags=["lines"])


class OwnerIn(BaseModel):
    """A line owner as the counter types it in."""

    ident: str
    name: str
    telephone: str = ""
    notice_days: int = Field(default=5, ge=0, le=60)
    note: str = ""

    def to_domain(self) -> Owner:
        return Owner(
            ident=self.ident,
            name=self.name,
            telephone=self.telephone,
            notice_days=self.notice_days,
            note=self.note,
        )


class OwnerOut(BaseModel):
    """An owner as the office holds it."""

    ident: str
    name: str
    telephone: str
    notice_days: int
    wires: int

    @classmethod
    def of(cls, row: LineOwnerRow) -> OwnerOut:
        return cls(
            ident=row.ident,
            name=row.name,
            telephone=row.telephone,
            notice_days=row.notice_days,
            wires=len(row.wires),
        )


class CrossingIn(BaseModel):
    """A wire as the survey wrote it down."""

    ident: str
    segment: str
    place: str
    height: str
    owner: str
    lift_to: str | None = None
    measured: date | None = None
    note: str = ""

    def to_domain(self) -> Crossing:
        return Crossing(
            ident=self.ident,
            segment=self.segment,
            place=self.place,
            height=Inches.parse(self.height),
            owner=self.owner,
            lift_to=None if self.lift_to is None else Inches.parse(self.lift_to),
            note=self.note,
        )


class MeasureIn(BaseModel):
    """What the crew found when they went back to it."""

    height: str
    lift_to: str | None = None
    measured: date


class CrossingOut(BaseModel):
    """A wire as the office holds it."""

    ident: str
    segment: str
    place: str
    height: str
    owner: str
    lift_to: str | None
    measured: date | None

    @classmethod
    def of(cls, row: LineCrossingRow) -> CrossingOut:
        return cls(
            ident=row.ident,
            segment=row.segment_ident,
            place=row.place,
            height=str(row.height),
            owner=row.owner_ident,
            lift_to=None if row.lift_to is None else str(row.lift_to),
            measured=row.measured,
        )


class LiftOut(BaseModel):
    """One wire in the way, and who to ring about it."""

    crossing: str
    segment: str
    place: str
    hangs_at: str
    owner: str
    telephone: str
    notice_by: date


class BlockedOut(BaseModel):
    """One wire nothing can be done about."""

    crossing: str
    segment: str
    place: str
    reason: str


class ClearanceOut(BaseModel):
    """What one load meets overhead on one written route."""

    wanted: str
    clear: bool
    lifts: list[LiftOut] = Field(default_factory=list)
    blocked: list[BlockedOut] = Field(default_factory=list)


@router.get("/owners", response_model=list[OwnerOut])
def list_owners(
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[OwnerOut]:
    """Every line owner, in ident order, with how many wires each has."""
    return [OwnerOut.of(one) for one in work.lines.owners()]


@router.post("/owners", response_model=OwnerOut, status_code=201)
def add_owner(
    body: OwnerIn,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> OwnerOut:
    """Put a line owner on file."""
    row = work.lines.add_owner(body.to_domain())
    work.commit()
    return OwnerOut.of(row)


@router.get("", response_model=list[CrossingOut])
def list_crossings(
    segment: str | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[CrossingOut]:
    """Every wire, or every wire over one segment."""
    return [CrossingOut.of(one) for one in work.lines.crossings(segment)]


@router.post("", response_model=CrossingOut, status_code=201)
def add_crossing(
    body: CrossingIn,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> CrossingOut:
    """Put a measured wire on file."""
    row = work.lines.add(body.to_domain(), body.measured)
    work.commit()
    return CrossingOut.of(row)


@router.get("/low", response_model=list[CrossingOut])
def low(
    under_height: str = Query(default="20'", alias="under"),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[CrossingOut]:
    """Every wire hanging below a height, lowest first, for the survey."""
    return [CrossingOut.of(one) for one in work.lines.under(Inches.parse(under_height))]


@router.get("/clearance", response_model=ClearanceOut)
def clearance(
    origin: str = Query(...),
    segments: list[str] = Query(...),
    height: str = Query(...),
    travel: date = Query(...),
    filed: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> ClearanceOut:
    """What a load of this height meets on this written route."""
    envelope = Envelope.read("8'6", height, "70'")
    itinerary = Itinerary(network_from(work.db), origin, tuple(segments))
    found = under(itinerary, envelope, lines_from(work.db), filed or date.today(), travel)
    return ClearanceOut(
        wanted=str(envelope.height),
        clear=found.clear,
        lifts=[
            LiftOut(
                crossing=one.crossing.ident,
                segment=one.crossing.segment,
                place=one.crossing.place,
                hangs_at=str(one.crossing.height),
                owner=one.owner.name,
                telephone=one.owner.telephone,
                notice_by=one.notice_by,
            )
            for one in found.lifts
        ],
        blocked=[
            BlockedOut(
                crossing=one.crossing.ident,
                segment=one.crossing.segment,
                place=one.crossing.place,
                reason=str(one),
            )
            for one in found.blocked
        ],
    )


@router.get("/{ident}", response_model=CrossingOut)
def read_crossing(
    ident: str,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> CrossingOut:
    """One wire, as last measured."""
    return CrossingOut.of(work.lines.row(ident))


@router.put("/{ident}/measurement", response_model=CrossingOut)
def remeasure(
    ident: str,
    body: MeasureIn,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> CrossingOut:
    """Write what the crew found, replacing what the card said."""
    row = work.lines.remeasure(
        ident,
        Inches.parse(body.height),
        body.measured,
        None if body.lift_to is None else Inches.parse(body.lift_to),
    )
    work.commit()
    return CrossingOut.of(row)
