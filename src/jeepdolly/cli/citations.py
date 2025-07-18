"""Citation commands, for the two people who never use the screen.

The clerk who types the post in uses the screen. The supervisor who writes the
letters at the end of the quarter does not, because the letters are a mail
merge and a mail merge wants lines.

The chase list is the other one. It is printed on a Monday morning and worked
down a telephone, and it wants the carrier's name and the amount and nothing
else on the line.

Filing from here exists for the mornings when the web process is down, which
is the same reason every other command group exists. It takes the number off
the ticket rather than allocating one, exactly as the route does.
"""

from __future__ import annotations

from datetime import date, datetime

import click

from ..enforcement.penalty import Offence
from ..errors import Unreadable
from ..measure import Pounds
from ..reports.enforcement import report as enforcement_report
from ..reports.enforcement import watch_list
from ..services import enforcement
from .main import Console, main, say

#: Who a command line filing is recorded as having been done by.
BY_HAND = "command line"


@main.group()
def citations() -> None:
    """Citations: what is owed, what was written, and who to write to."""


@citations.command("owing")
@click.pass_obj
def owing(console: Console) -> None:
    """The chase list, oldest first, worked down a telephone."""
    with console.work() as work:
        found = work.citations.owing()
        for row in found:
            say(row.number, row.written.isoformat(), row.carrier_name, row.amount, row.state)
    if not found:
        say("nothing is owed")


@citations.command("carrier")
@click.argument("usdot", type=int)
@click.pass_obj
def carrier(console: Console, usdot: int) -> None:
    """Everything against one carrier, newest first."""
    with console.work() as work:
        for row in work.citations.for_carrier(usdot):
            say(row.number, row.written.isoformat(), row.offence, row.amount, row.state)


@citations.command("show")
@click.argument("number")
@click.pass_obj
def show(console: Console, number: str) -> None:
    """One citation, in the order it is read out over the telephone."""
    with console.work() as work:
        for line in work.citations.get(number).lines():
            click.echo(line)


@citations.command("report")
@click.option("--from", "start", type=click.DateTime(["%Y-%m-%d"]), required=True)
@click.option("--to", "end", type=click.DateTime(["%Y-%m-%d"]), required=True)
@click.pass_obj
def report(console: Console, start: datetime, end: datetime) -> None:
    """The quarter: counts by offence, the money, and who to write to."""
    with console.work() as work:
        for line in enforcement_report(work.db, start.date(), end.date()):
            click.echo(line)


@citations.command("watch")
@click.option("--from", "start", type=click.DateTime(["%Y-%m-%d"]), required=True)
@click.option("--to", "end", type=click.DateTime(["%Y-%m-%d"]), required=True)
@click.option("--at", type=int, default=2, help="How many citations puts a carrier on the list.")
@click.pass_obj
def watch(console: Console, start: datetime, end: datetime, at: int) -> None:
    """The carriers that get a letter before they get a suspension."""
    with console.work() as work:
        found = watch_list(work.db, start.date(), end.date(), at)
        for one in found:
            say(one.usdot, one.name, one.count, one.owing)
    if not found:
        say("nobody is over the line")


@citations.command("file")
@click.argument("number")
@click.argument("usdot", type=int)
@click.argument("offence")
@click.option("--officer", required=True)
@click.option("--place", required=True)
@click.option("--written", type=click.DateTime(["%Y-%m-%d"]), required=True)
@click.option("--filed", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--over", default=None, help="Pounds over, for an overweight ticket.")
@click.option("--permit", default=None)
@click.pass_obj
def file_one(
    console: Console,
    number: str,
    usdot: int,
    offence: str,
    officer: str,
    place: str,
    written: datetime,
    filed: datetime | None,
    over: str | None,
    permit: str | None,
) -> None:
    """Put a ticket on file, priced against the carrier's year."""
    day = written.date()
    with console.work() as work:
        one = enforcement.file_citation(
            work,
            number=number,
            usdot=usdot,
            officer=officer,
            offence=_offence(offence),
            place=place,
            written=day,
            filed=filed.date() if filed else day,
            who=BY_HAND,
            over=None if over is None else Pounds.parse(over),
            permit=permit,
        )
        work.commit()
    say("filed", one.number, one.carrier_name, one.offence, one.amount)


@citations.command("pay")
@click.argument("number")
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--reference", default="")
@click.pass_obj
def pay(console: Console, number: str, on: datetime | None, reference: str) -> None:
    """Money arrived at the till and this is the paper catching up."""
    with console.work() as work:
        one = enforcement.pay(work, number, on.date() if on else date.today(), BY_HAND, reference)
        work.commit()
    say("paid", one.number, one.amount)


def _offence(written: str) -> Offence:
    """One offence by the words it prints, refusing anything else."""
    try:
        return Offence(written)
    except ValueError:
        raise Unreadable(
            f"{written!r} is not an offence this office prices",
            offence=written,
        ) from None
