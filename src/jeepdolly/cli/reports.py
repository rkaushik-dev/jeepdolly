"""Report commands, which print exactly what the paper report prints.

Each of these is a thin call onto a report in the reports package, and the
lines it prints are the lines the report produced. The command does not
reformat, does not add a total, and does not sort. If a report is wrong on
paper it is wrong in the report module, which is where the test that holds it
lives.

The evening report is run from cron at seven, redirected to a file, and
printed in the morning. That is why it writes to standard output with nothing
on standard error: a cron job that writes a heading to stderr sends a mail
every night and gets filtered within a fortnight.
"""

from __future__ import annotations

from datetime import date, datetime

import click

from ..reports.calendar import changes, standing_on
from ..reports.issuance import issuance
from ..reports.revenue import revenue
from .main import Console, main, say

DAY = click.DateTime(["%Y-%m-%d"])


def _day(given: datetime | None) -> date:
    return given.date() if given else date.today()


@main.group()
def report() -> None:
    """The reports the office lives on."""


@report.command("issuance")
@click.option("--day", type=DAY, default=None)
@click.option("--to", "until", type=DAY, default=None, help="The last day of a span.")
@click.pass_obj
def issuance_report(console: Console, day: datetime | None, until: datetime | None) -> None:
    """What was written and what it took."""
    start = _day(day)
    with console.work() as work:
        for line in issuance(work.db, start, _day(until) if until else None).lines():
            click.echo(line)


@report.command("revenue")
@click.option("--from", "start", type=DAY, required=True)
@click.option("--to", "until", type=DAY, required=True)
@click.pass_obj
def revenue_report(console: Console, start: datetime, until: datetime) -> None:
    """Where the money came from."""
    with console.work() as work:
        for line in revenue(work.db, _day(start), _day(until)).lines():
            click.echo(line)


@report.command("carrier")
@click.argument("name")
@click.option("--from", "start", type=DAY, required=True)
@click.option("--to", "until", type=DAY, required=True)
@click.pass_obj
def carrier_report(console: Console, name: str, start: datetime, until: datetime) -> None:
    """What one carrier was charged, for its own reconciliation."""
    with console.work() as work:
        found = revenue(work.db, _day(start), _day(until))
    say(name, found.by_carrier.get(name, "$0.00"))


@report.command("roads")
@click.option("--day", type=DAY, default=None)
@click.option("--days", type=int, default=14)
@click.pass_obj
def road_report(console: Console, day: datetime | None, days: int) -> None:
    """What changes on the road over the next fortnight."""
    with console.work() as work:
        for line in changes(work.db, _day(day), days):
            click.echo(line)


@report.command("standing")
@click.option("--day", type=DAY, default=None)
@click.pass_obj
def standing_report(console: Console, day: datetime | None) -> None:
    """Everything in force on one day, for the fax to the state police."""
    with console.work() as work:
        for one in standing_on(work.db, _day(day)):
            click.echo(str(one))
