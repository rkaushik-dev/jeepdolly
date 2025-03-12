"""Seasonal order routes, and the day the routing starts reading them.

Until now the office assembled itself with an empty season book, which meant a
thaw order could be filed and nothing would act on it. That was fine while
there was nowhere to file one. Now there is, so the office reads them, and a
posted road under an order comes back as a condition on every assessment that
touches it.

Filing an order is a supervisor's job. The district engineer signs the paper
and the supervisor types it, and the number on the row is the number on the
paper.

Lifting one early is the same job. An order that lifts on the Tuesday because
the frost came out is ended on the Tuesday, and the row stays with that date
on it, because a permit written on the Monday was priced against it.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel

from ...persistence.models.seasons import SeasonOrderRow
from ...persistence.seasonstore import file_order, for_region, in_force, lift, row_for
from ...persistence.unitofwork import UnitOfWork
from ...seasons import SeasonKind, SeasonOrder
from ...services import audit
from ..deps import unit_of_work
from ..security import Caller, Role, needs

router = APIRouter(prefix="/seasons", tags=["seasons"])


class OrderIn(BaseModel):
    """An order as the district signed it."""

    number: str
    region: str
    kind: str
    start: date
    end: date
    share: int
    signed_by: str = ""
    note: str = ""

    def to_domain(self) -> SeasonOrder:
        return SeasonOrder(
            number=self.number,
            region=self.region,
            kind=SeasonKind(self.kind),
            start=self.start,
            end=self.end,
            share=self.share,
        )


class OrderOut(BaseModel):
    """An order as the office holds it."""

    number: str
    region: str
    kind: str
    start: date
    end: date
    share: int
    signed_by: str

    @classmethod
    def of(cls, row: SeasonOrderRow) -> OrderOut:
        order = row.to_domain()
        return cls(
            number=order.number,
            region=order.region,
            kind=order.kind.value,
            start=order.start,
            end=order.end,
            share=order.share,
            signed_by=row.signed_by,
        )


@router.post("", response_model=OrderOut, status_code=201)
def file_season_order(
    body: OrderIn,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> OrderOut:
    """Put a signed order on file, refusing an overlap."""
    row = file_order(work.db, body.to_domain(), signed_by=body.signed_by, note=body.note)
    audit.record(
        work.db,
        "road.closed" if body.kind == "thaw" else "road.opened",
        who.person,
        "season",
        body.number,
        detail=f"{body.region} at {body.share}%",
    )
    work.commit()
    return OrderOut.of(row)


@router.get("/{number}", response_model=OrderOut)
def read_order(
    number: str,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> OrderOut:
    """One order, by the number on the paper."""
    return OrderOut.of(row_for(work.db, number))


@router.get("", response_model=list[OrderOut])
def orders_for_region(
    region: str = Query(...),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[OrderOut]:
    """Every order a region has had, in the order they ran."""
    return [OrderOut.of(one) for one in for_region(work.db, region)]


@router.get("/in-force/{region}", response_model=OrderOut | None)
def order_in_force(
    region: str,
    day: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> OrderOut | None:
    """What is in force in a region on a day, if anything is."""
    found = in_force(work.db, region, day or date.today())
    return None if found is None else OrderOut.of(found)


@router.post("/{number}/lift", response_model=OrderOut)
def lift_order(
    number: str,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> OrderOut:
    """End an order early, keeping the row and the day it ended."""
    day = on or date.today()
    row = lift(work.db, number, day)
    audit.record(work.db, "road.opened", who.person, "season", number, detail=day.isoformat())
    work.commit()
    return OrderOut.of(row)
