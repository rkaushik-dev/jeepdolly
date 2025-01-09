"""The application factory.

There is one function here and it takes everything it needs as arguments. No
module level application, no import time database connection, no reading of
the environment anywhere in this file. A test builds one with an in memory
database in a millisecond, the server builds one with a real engine, and
neither knows about the other.

The health route is deliberately dull and deliberately checks something. A
health check that returns two hundred without touching the database tells the
load balancer the process is alive while every request is failing, which is
the worst answer it could give.
"""

from __future__ import annotations

from typing import Any

from fastapi import FastAPI
from sqlalchemy import text
from sqlalchemy.orm import Session, sessionmaker

from .. import __version__
from ..persistence.unitofwork import UnitOfWork
from . import errors
from .routers import applications, permits

TITLE = "jeepdolly"
DESCRIPTION = "Oversize and overweight permits for the motor carrier office"


def create_app(factory: sessionmaker[Session]) -> FastAPI:
    """Build an application over one session factory."""
    app = FastAPI(title=TITLE, description=DESCRIPTION, version=__version__)
    app.state.sessions = factory
    errors.install(app)
    app.include_router(applications.router)
    app.include_router(permits.router)

    @app.get("/health", tags=["service"])
    def health() -> dict[str, Any]:
        """Alive, and able to reach the database it was given."""
        with UnitOfWork(factory) as work:
            session = work.session
            assert session is not None
            session.execute(text("select 1"))
            return {"service": TITLE, "version": __version__, "database": "up"}

    return app
