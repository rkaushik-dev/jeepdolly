"""The nightly command, which is the only one cron runs.

It commits, which none of the other commands do without being told to, and it
says so in the help, because a command run unattended that silently changes
records is a command somebody will run by hand to see what it does.

There is a dry run for exactly that reason. It walks the same four passes and
rolls back at the end, so the office can see what tonight would do before it
does it. That is not a separate code path: the difference is one commit.

The exit code is zero whether or not the sweep found anything. A night with
nothing to do is a normal night, and a cron job that exits non zero on a quiet
night sends a mail every night and gets filtered within a fortnight.
"""

from __future__ import annotations

from datetime import date, datetime

import click

from .. import logs
from ..services.nightly import sweep
from .main import Console, main

DAY = click.DateTime(["%Y-%m-%d"])


@main.command("nightly")
@click.option("--day", type=DAY, default=None, help="The night to run, for a replay.")
@click.option("--within", type=int, default=2, help="Days ahead to look for expiries.")
@click.option("--dry-run", is_flag=True, help="Walk the passes and roll back.")
@click.pass_obj
def nightly(console: Console, day: datetime | None, within: int, dry_run: bool) -> None:
    """Run the sweep. This one writes, unless it is a dry run."""
    when = day.date() if day else date.today()
    with console.work() as work:
        found = sweep(work.db, when, expiring_within=within)
        if dry_run:
            work.rollback()
        else:
            work.commit()
    for line in found.lines():
        click.echo(line)
    logs.event(
        "nightly.done",
        day=when.isoformat(),
        expiring=len(found.expiring),
        withdrawn=len(found.withdrawn),
        slow=len(found.slow_studies),
        lapsing=len(found.lapsing),
        dry_run=dry_run,
    )
