"""The application factory.

There is one function here and it takes everything it needs as arguments. No
module level application, no import time database connection, no reading of
the environment anywhere in this file. A test builds one with an in memory
database in a millisecond, the server builds one with a real engine, and
neither knows about the other.

It is a list of routers and nothing else now. The health route used to be
written out in the middle of it, with its own unit of work built by hand,
which is exactly the sort of thing that grows a second copy. It lives on the
service router with the rest of the routes about the service itself.
"""

from __future__ import annotations

from fastapi import FastAPI
from sqlalchemy.orm import Session, sessionmaker

from .. import __version__
from ..persistence.reference import ReferenceCache, watch
from . import errors, requestlog
from .routers import (
    annual,
    applications,
    carriers,
    citations,
    lines,
    permits,
    pilots,
    receipts,
    renewals,
    reports,
    reprints,
    restrictions,
    routes,
    seasons,
    service,
    structures,
    tariff,
)

TITLE = "jeepdolly"
DESCRIPTION = "Oversize and overweight permits for the motor carrier office"

ROUTERS = (
    service.router,
    annual.router,
    applications.router,
    carriers.router,
    citations.router,
    lines.router,
    permits.router,
    pilots.router,
    receipts.router,
    renewals.router,
    reports.router,
    reprints.router,
    restrictions.router,
    routes.router,
    seasons.router,
    structures.router,
    tariff.router,
)


def create_app(factory: sessionmaker[Session]) -> FastAPI:
    """Build an application over one session factory.

    The cache belongs to the application rather than to the module, so two
    applications over two databases in one process do not share books. The
    tests build one per test and that is the whole reason it is shaped this
    way.
    """
    app = FastAPI(title=TITLE, description=DESCRIPTION, version=__version__)
    app.state.sessions = factory
    app.state.reference = ReferenceCache()
    watch(factory, app.state.reference)
    errors.install(app)
    requestlog.install(app)
    for router in ROUTERS:
        app.include_router(router)
    return app
