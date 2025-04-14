"""Filing a new published table, which happens twice a legislative session.

The tables are rows now, so filing one is a request rather than a release. It
is a supervisor's job and it lands on the trail, because a limit that moved
without anybody knowing who moved it is the sort of thing that ends up in a
newspaper.

A table takes effect on a day and never before. Filing one dated next month
changes nothing today, which is the point: the office types it in when the
paper arrives and it starts working on the day the statute says.

Nothing here edits a table. There is no route to change a number, only to file
a new one, and the read routes say which is in force on which day.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel

from ...domain.highway import HighwayClass
from ...errors import Conflict, Unreadable
from ...measure import Pounds
from ...persistence.models.tariff import StatutoryRow
from ...persistence.tariffstore import statutory_book
from ...persistence.unitofwork import UnitOfWork
from ...services import audit
from ..deps import unit_of_work
from ..security import Caller, Role, needs

router = APIRouter(prefix="/tariff", tags=["tariff"])


def road_class(written: str) -> HighwayClass:
    """Read a class of road, refusing rather than raising a value error."""
    try:
        return HighwayClass(written)
    except ValueError:
        raise Unreadable(f"{written!r} is not a class of road", written=written) from None


class StatutoryIn(BaseModel):
    """A weight table as the statute publishes it."""

    highway_class: str
    effective: date
    single: str
    tandem: str
    tridem: str
    quad: str
    beyond: str
    gross: str
    tire_rate: int


class StatutoryOut(BaseModel):
    """A weight table as the office holds it."""

    highway_class: str
    effective: date
    single: str
    tandem: str
    tridem: str
    quad: str
    gross: str
    tire_rate: int

    @classmethod
    def of(cls, row: StatutoryRow) -> StatutoryOut:
        return cls(
            highway_class=row.highway_class.value,
            effective=row.effective,
            single=str(row.single),
            tandem=str(row.tandem),
            tridem=str(row.tridem),
            quad=str(row.quad),
            gross=str(row.gross),
            tire_rate=row.tire_rate,
        )


@router.post("/statutory", response_model=StatutoryOut, status_code=201)
def file_statutory_table(
    body: StatutoryIn,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> StatutoryOut:
    """File a weight table, which takes effect on the day it names."""
    highway_class = road_class(body.highway_class)
    key = (highway_class, body.effective)
    if work.db.get(StatutoryRow, key) is not None:
        raise Conflict(
            f"a {highway_class} table already takes effect on {body.effective.isoformat()}",
            highway_class=body.highway_class,
        )
    row = StatutoryRow(
        highway_class=highway_class,
        effective=body.effective,
        single=Pounds.parse(body.single),
        tandem=Pounds.parse(body.tandem),
        tridem=Pounds.parse(body.tridem),
        quad=Pounds.parse(body.quad),
        beyond=Pounds.parse(body.beyond),
        gross=Pounds.parse(body.gross),
        tire_rate=body.tire_rate,
    )
    work.db.add(row)
    audit.record(
        work.db,
        "tariff.filed",
        who.person,
        "tariff",
        f"{body.highway_class} {body.effective.isoformat()}",
        detail=f"gross {body.gross}",
    )
    work.commit()
    return StatutoryOut.of(row)


@router.get("/statutory", response_model=list[StatutoryOut])
def statutory_tables(
    day: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[StatutoryOut]:
    """What is in force on a day, one table per class of road."""
    when = day or date.today()
    book = statutory_book(work.db)
    found = []
    for highway_class in HighwayClass:
        table = book.on(highway_class, when)
        row = work.db.get(StatutoryRow, (highway_class, table.effective))
        if row is not None:
            found.append(StatutoryOut.of(row))
    return found


@router.get("/statutory/{highway_class}", response_model=list[StatutoryOut])
def tables_for_class(
    highway_class: str,
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[StatutoryOut]:
    """Every table one class of road has ever had, oldest first."""
    wanted = road_class(highway_class)
    rows = [
        one
        for one in work.db.query(StatutoryRow).order_by(StatutoryRow.effective).all()
        if one.highway_class is wanted
    ]
    return [StatutoryOut.of(one) for one in rows]
