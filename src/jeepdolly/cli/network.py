"""Road log commands, which is how the log actually gets loaded.

Twice a year a file arrives and somebody has to put it in. That is this, and
it is a command rather than a route because the file is forty thousand lines
and nobody is uploading that through a form.

The load prints what it did and what it could not read, and it commits only if
it is not a dry run. The dry run is what gets used first, every time, because
the state's file has three bad lines in it every time and the office wants to
see them before the good lines go in.

The corner commands are here rather than under reports because they are about
the road rather than about the money, and because the counter looks for them
where the segment lookup is.
"""

from __future__ import annotations

import sys
from datetime import date
from pathlib import Path
from typing import Any

import click

from ..domain.envelope import Envelope
from ..persistence.books import network_from
from ..reports.corners import route_report, wall_list
from ..routing.itinerary import Itinerary
from ..routing.turns import coded_corners
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


@roads.command("corners")
@click.option("--from", "start", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--to", "end", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--under", type=int, default=None, help="Radius in feet, tighter than the default.")
@click.pass_obj
def corners(console: Console, start: Any, end: Any, under: int | None) -> None:
    """The list on the wall: the tight corners and what turned at each."""
    first, last = _year(start, end)
    with console.work() as work:
        found = wall_list(work.db, coded_corners(), first, last, feet=under)
    if not found:
        say("nothing on the card index is under that radius")
        return
    for one in found:
        say(one.corner.node, one.corner.into, one.corner.out_of, one.corner.radius_feet, one.turned)


@roads.command("turn")
@click.argument("origin")
@click.argument("segments", nargs=-1, required=True)
@click.option("--length", default="80'", help="Overall length of the combination.")
@click.option("--rear", default="0", help="Rear overhang.")
@click.pass_obj
def turn(console: Console, origin: str, segments: tuple[str, ...], length: str, rear: str) -> None:
    """What one load meets on one written route, corner by corner."""
    envelope = Envelope.read("8'6", "13'6", length, "0", rear)
    with console.work() as work:
        itinerary = Itinerary(network_from(work.db), origin, segments)
    for line in route_report(itinerary, envelope, coded_corners()):
        click.echo(line)


def _year(start: Any, end: Any) -> tuple[date, date]:
    """The two dates, defaulting to the calendar year the office is in."""
    if start is not None and end is not None:
        return start.date(), end.date()
    today = date.today()
    return date(today.year, 1, 1), date(today.year, 12, 31)
