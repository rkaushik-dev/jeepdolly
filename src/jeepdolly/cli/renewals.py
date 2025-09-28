"""Renewal commands, which is where the letters actually come from.

The letters go out as a mail merge. Somebody runs this on a Monday, pastes
the output into a spreadsheet, and the spreadsheet feeds a word processor.
That is why the letter command prints one line per carrier with the numbers
run together in a field rather than one line per permit: the merge wants a
row per envelope.

The due list is the other shape and prints one line per permit, because that
one goes on the wall and gets ticked off.

Renewing from the command line exists for the same reason every other
command does, and it is used more than the others: the renewal fortnight is
in February, the counter is busy, and the supervisor works the list down from
a terminal in the back office.
"""

from __future__ import annotations

from datetime import date, datetime

import click

from ..api.deps import office_from
from ..services import renewals
from ..services.renewals import AHEAD_DAYS
from .main import Console, main, say

#: What separates the permit numbers in a merge row.
JOIN = ";"


@main.group()
def renewal() -> None:
    """Annual permits running out, and the letters about them."""


@renewal.command("due")
@click.option("--day", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--within", type=int, default=AHEAD_DAYS, help="Days ahead to look.")
@click.pass_obj
def due(console: Console, day: datetime | None, within: int) -> None:
    """Every annual running out inside the window, soonest first."""
    when = day.date() if day else date.today()
    with console.work() as work:
        found = renewals.due_by(work, when, within)
        for one in found:
            say(one.number, one.expires.isoformat(), one.days_left(when), one.carrier)
    if not found:
        say("nothing is due in that window")


@renewal.command("letters")
@click.option("--day", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--within", type=int, default=AHEAD_DAYS, help="Days ahead to look.")
@click.option("--long", "long_form", is_flag=True, help="Print the letter rather than a row.")
@click.pass_obj
def letters(console: Console, day: datetime | None, within: int, long_form: bool) -> None:
    """One row per envelope, or the letters themselves."""
    when = day.date() if day else date.today()
    with console.work() as work:
        found = renewals.letters(work, when, within)
    if not found:
        say("nobody is due a letter")
        return
    for one in found:
        if long_form:
            for line in one.lines():
                click.echo(line)
            continue
        numbers = JOIN.join(each.number for each in one.due)
        say(one.usdot, one.carrier, one.count, one.soonest.isoformat(), numbers)


@renewal.command("renew")
@click.argument("usdot", type=int)
@click.argument("numbers", nargs=-1, required=True)
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_obj
def renew(console: Console, usdot: int, numbers: tuple[str, ...], on: datetime | None) -> None:
    """Renew a carrier's annuals in one transaction, or none of them."""
    when = on.date() if on else date.today()
    with console.work() as work:
        found = renewals.renew(work, office_from(work, when), usdot, numbers, when)
        work.commit()
        for made in found.issued:
            say("renewed", made.number, made.end.isoformat())
    say(found.count, "renewed for", usdot)
