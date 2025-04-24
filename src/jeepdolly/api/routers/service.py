"""The routes about the service rather than about permits.

Health, version and metrics. None of them needs a key, and that is a decision
rather than an oversight: a load balancer has no credentials, a collector runs
inside the same network, and the numbers here say how much work the office
did, not what anybody's load weighs.

Health touches the database on purpose. A health check that returns two
hundred without reaching the database tells the load balancer the process is
alive while every request is failing, which is the worst answer it could give.
"""

from __future__ import annotations

from datetime import date
from typing import Any

from fastapi import APIRouter, Depends, Query
from fastapi.responses import PlainTextResponse
from sqlalchemy import text

from ... import __version__
from ...persistence.unitofwork import UnitOfWork
from ...services.metrics import exposition, snapshot
from ..deps import unit_of_work

router = APIRouter(tags=["service"])

TITLE = "jeepdolly"


@router.get("/health")
def health(work: UnitOfWork = Depends(unit_of_work)) -> dict[str, Any]:
    """Alive, and able to reach the database it was given."""
    work.db.execute(text("select 1"))
    return {"service": TITLE, "version": __version__, "database": "up"}


@router.get("/version")
def version() -> dict[str, str]:
    """What is deployed, for the person reading a deploy log."""
    return {"service": TITLE, "version": __version__}


@router.get("/metrics", response_class=PlainTextResponse)
def metrics(
    day: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
) -> str:
    """The scrape, in the text format every collector reads."""
    return exposition(work.db, day or date.today())


@router.get("/metrics.json")
def metrics_json(
    day: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
) -> dict[str, int]:
    """The same numbers for anything that would rather have json."""
    return {one.name: one.value for one in snapshot(work.db, day or date.today())}
