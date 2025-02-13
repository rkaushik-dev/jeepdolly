"""The command line, and the three answers it can give.

Exit codes, because this is run from cron and from a Makefile and both of them
only understand numbers.

Zero is done. One is a refusal, which is the office saying no: a load that is
over, a permit that is void, a carrier that is suspended. Two is the caller
using the command wrongly, which the framework produces on its own.

Nothing else is ever raised out of a command. An unexpected exception is a
defect and it comes out as a traceback on purpose, because a cron job that
swallows a defect and exits zero is how a database goes unmigrated for a
month.

Every command prints plain lines, one record to a line, with the fields
separated by two spaces. No tables, no colour, no box drawing. The output of
these commands is read by people and piped into grep and awk, and the first
of those two is the one that gets forgotten.
"""

from __future__ import annotations

import sys
from typing import Any

import click
from sqlalchemy import text
from sqlalchemy.orm import Session, sessionmaker

from .. import __version__, logs
from ..config import Settings, from_environment
from ..errors import Refusal
from ..persistence.base import create_all, engine_for, session_factory
from ..persistence.unitofwork import UnitOfWork

REFUSED = 1


class Console:
    """What every command is handed: the settings and a way to open work."""

    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self._factory: sessionmaker[Session] | None = None

    @property
    def sessions(self) -> sessionmaker[Session]:
        if self._factory is None:
            engine = engine_for(self.settings.database_url, echo=self.settings.echo_sql)
            self._factory = session_factory(engine)
        return self._factory

    def work(self) -> UnitOfWork:
        return UnitOfWork(self.sessions)

    def build(self) -> None:
        """Build the schema straight from the models, for a scratch database."""
        engine = engine_for(self.settings.database_url)
        create_all(engine)
        self._factory = session_factory(engine)


def say(*fields: Any) -> None:
    """One record, one line, two spaces between the fields."""
    click.echo("  ".join("" if one is None else str(one) for one in fields))


@click.group()
@click.option("--database", default=None, help="Database url, overriding the environment.")
@click.option("--quiet", is_flag=True, help="Say nothing on the log.")
@click.version_option(version=__version__, prog_name="jeepdolly")
@click.pass_context
def main(context: click.Context, database: str | None, quiet: bool) -> None:
    """Permits for the motor carrier office."""
    settings = from_environment()
    if database:
        settings = Settings(
            database_url=database,
            office_name=settings.office_name,
            minimum_miles=settings.minimum_miles,
            review_days=settings.review_days,
            echo_sql=settings.echo_sql,
        )
    if not quiet:
        logs.configure(stream=sys.stderr)
    if context.obj is None:
        # A caller that already has a console keeps it. That is how the tests
        # run against an in memory database, and how a future daemon will run
        # a command without opening a second connection pool.
        context.obj = Console(settings)


@main.command()
@click.pass_obj
def health(console: Console) -> None:
    """Say whether the database is reachable."""
    with console.work() as work:
        work.db.execute(text("select 1"))
    say("database", "up", console.settings.safe_url)


@main.command()
@click.pass_obj
def build(console: Console) -> None:
    """Build the schema from the models. For a scratch database only."""
    console.build()
    say("schema", "built", console.settings.safe_url)


def load_commands() -> None:
    """Import the command groups, which register themselves on the way in.

    They import this module, so this cannot happen at the top of the file.
    It happens once, at the entry point, before anything is parsed.
    """
    from . import permits, reports  # noqa: F401


def run() -> int:
    """The entry point, which turns a refusal into an exit code."""
    load_commands()
    try:
        main.main(standalone_mode=False)
    except Refusal as refusal:
        click.echo(str(refusal), err=True)
        return REFUSED
    except click.ClickException as trouble:
        trouble.show()
        return trouble.exit_code
    except click.exceptions.Abort:
        click.echo("stopped", err=True)
        return REFUSED
    return 0


if __name__ == "__main__":  # pragma: no cover - the console entry point
    raise SystemExit(run())
