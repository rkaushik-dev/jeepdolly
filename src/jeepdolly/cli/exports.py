"""Export commands, which are run on the first working day of the month.

Two of them and the second is the one that matters. Writing the return is a
query and a format. Verifying it is reading the written file back and checking
it the way the receiving system will, and it is a separate command rather than
part of writing because the file gets edited by hand between the two.

That sounds like it should not happen and it happens every year. A carrier
name with a character the federal office will not take, a permit that should
not have been in the month, a line somebody deleted with a text editor that
helpfully stripped the trailing blanks. Verifying reads whatever is on disk
now, not what was written an hour ago.

Both commands take the window rather than working it out. The month is
obvious until the year somebody runs it on the second of January.
"""

from __future__ import annotations

import sys
from datetime import datetime
from pathlib import Path

import click

from ..exports.federal import build, superseded_count, verify, window_of
from .main import Console, main, say

#: What the file is called when nobody says otherwise.
NAME = "ooretn"


@main.group()
def export() -> None:
    """Files this office sends somebody else."""


@export.command("federal")
@click.option("--from", "start", type=click.DateTime(["%Y-%m-%d"]), required=True)
@click.option("--to", "end", type=click.DateTime(["%Y-%m-%d"]), required=True)
@click.option("--to-file", "path", type=click.Path(dir_okay=False), default=None)
@click.pass_obj
def federal(console: Console, start: datetime, end: datetime, path: str | None) -> None:
    """Write the monthly return, to a file or to the screen."""
    first, last = start.date(), end.date()
    with console.work() as work:
        made = build(work.db, first, last)
        replaced = superseded_count(work.db, first, last)
    if path is None:
        for line in made.lines():
            click.echo(line)
        return
    Path(path).write_text(made.render(), encoding="ascii", newline="\n")
    say("wrote", path, made.count, made.fees)
    if replaced:
        say("for the covering letter", replaced, "since superseded")


@export.command("verify")
@click.argument("path", type=click.Path(exists=True, dir_okay=False))
@click.pass_obj
def verify_file(console: Console, path: str) -> None:
    """Read a written return back and find what the federal office would find."""
    lines = tuple(Path(path).read_text(encoding="ascii").splitlines())
    found = verify(lines)
    if not found:
        first, last = window_of(lines)
        say("good", path, len(lines), f"{first.isoformat()} to {last.isoformat()}")
        return
    for problem in found:
        click.echo(str(problem))
    sys.stderr.write(f"{len(found)} problems, the file will be rejected\n")
    raise SystemExit(1)
