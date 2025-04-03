"""Road log commands, which is how the log actually gets loaded.

Twice a year a file arrives and somebody has to put it in. That is this, and
it is a command rather than a route because the file is forty thousand lines
and nobody is uploading that through a form.

The load prints what it did and what it could not read, and it commits only if
it is not a dry run. The dry run is what gets used first, every time, because
the state's file has three bad lines in it every time and the office wants to
see them before the good lines go in.
"""

from __future__ import annotations

import sys
from pathlib import Path

import click

from ..services.roadlog import export_log, load_log
from .main import Console, main, say


@main.group()
def roads() -> None:
    """The road log: what is on it and how to put more on."""


@roads.command("export")
@click.option("--to", "path", type=click.Path(dir_okay=False), default=None)
@click.pass_obj
def export(console: Console, path: str | None) -> None:
    """Write the whole log out, for a diff against what the state sent."""
    with console.work() as work:
        lines = export_log(work.db)
    if path is None:
        for line in lines:
            click.echo(line)
        return
    Path(path).write_text("\n".join(lines) + "\n", encoding="utf-8")
    say("wrote", len(lines), path)


@roads.command("load")
@click.argument("path", type=click.Path(exists=True, dir_okay=False))
@click.option("--dry-run", is_flag=True, help="Read it and roll back.")
@click.pass_obj
def load(console: Console, path: str, dry_run: bool) -> None:
    """Read a published log in. Run it dry first, every time."""
    lines = tuple(Path(path).read_text(encoding="utf-8").splitlines())
    with console.work() as work:
        found = load_log(work.db, lines)
        if dry_run:
            work.rollback()
        else:
            work.commit()
    for line in found.lines():
        click.echo(line)
    if found.refused and not dry_run:
        sys.stderr.write(f"{len(found.refused)} lines were not read\n")


@roads.command("segment")
@click.argument("ident")
@click.pass_obj
def segment(console: Console, ident: str) -> None:
    """One segment, with what is on it and what is wrong with it."""
    with console.work() as work:
        row = work.network.segment(ident)
        say(row.ident, row.to_domain().route, row.start_node, row.end_node, row.length)
        for structure in work.network.structures_on(ident):
            say("  structure", structure.ident, structure.name, structure.posted)


@roads.command("district")
@click.argument("number", type=int)
@click.pass_obj
def district(console: Console, number: int) -> None:
    """Every segment in one district, which is what a thaw order reaches."""
    with console.work() as work:
        for row in work.network.in_district(number):
            say(row.ident, row.to_domain().route, row.town, row.length)
