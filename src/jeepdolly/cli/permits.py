"""Permit commands, for the mornings when the screen is down.

Every command here has an equivalent route on the interface and that is not
duplication to be tidied away. The counter uses the screen. The traffic office
at six in the morning uses this, over a telephone line, because the screen is
served from a building that is not open yet.

So these are not a thin wrapper over the http client. They go at the database
directly through the same unit of work, which means they work when the web
process is down, which is the point of them.

Printing is one record to a line. The show command is the exception and it
prints a block, because a permit read out over the telephone is read in the
order it prints on the paper.
"""

from __future__ import annotations

from datetime import date, datetime, timedelta

import click

from ..permits.permit import PermitState
from ..services import audit
from .main import Console, main, say


@main.group()
def permits() -> None:
    """Permits: what is out, what is expiring, and what to kill."""


@permits.command("on-the-road")
@click.option("--day", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_obj
def on_the_road(console: Console, day: datetime | None) -> None:
    """Every permit valid for travel on a day."""
    when = day.date() if day else date.today()
    with console.work() as work:
        for row in work.permits.on_the_road(when):
            say(row.number, row.carrier_name, row.gross, row.origin, row.destination)


@permits.command("expiring")
@click.option("--day", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--within", type=int, default=2, help="Days ahead to look.")
@click.pass_obj
def expiring(console: Console, day: datetime | None, within: int) -> None:
    """What the office rings about on a Friday afternoon."""
    when = day.date() if day else date.today()
    with console.work() as work:
        for row in work.permits.expiring(when, when + timedelta(days=within)):
            say(row.number, row.valid_to.isoformat(), row.carrier_name, row.destination)


@permits.command("show")
@click.argument("number")
@click.pass_obj
def show(console: Console, number: str) -> None:
    """One permit, in the order it prints on the paper."""
    with console.work() as work:
        permit = work.permits.get(number)
        for written in permit.face().splitlines():
            click.echo(written)
        click.echo(permit.narrative)
        for condition in permit.conditions:
            say(condition.code, condition.text)
        for line in permit.quote.lines:
            say(line.description, line.amount)
        say("total", permit.quote.total)


@permits.command("void")
@click.argument("number")
@click.option("--reason", required=True)
@click.option("--by", required=True, help="Who is voiding it.")
@click.option("--day", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_obj
def void(console: Console, number: str, reason: str, by: str, day: datetime | None) -> None:
    """Kill a permit, with the reason on the record."""
    when = day.date() if day else date.today()
    with console.work() as work:
        permit = work.permits.get(number)
        permit.void(when, reason)
        work.permits.save_state(permit)
        audit.record(work.db, "permit.voided", by, "permit", number, detail=reason)
        work.commit()
    say(number, "void", reason)


@permits.command("carrier")
@click.argument("usdot", type=int)
@click.pass_obj
def for_carrier(console: Console, usdot: int) -> None:
    """What one carrier has out, newest first."""
    with console.work() as work:
        for row in work.permits.for_carrier(usdot):
            state = "" if row.state is PermitState.ISSUED else row.state.value
            say(row.number, row.issued.isoformat(), row.valid_to.isoformat(), state)
