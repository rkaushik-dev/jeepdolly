"""Line commands, which are the survey van's list and the counter's question.

The van gets a printed list every May. It has one column of idents, one of
roads, and one of heights, and it goes on a clipboard on the dash. That is
this, and it is the reason the report exists at all.

The clearance question is here as well as on the interface for the ordinary
reason: the traffic office at six in the morning has a telephone and a
terminal and no web server yet.

Remeasuring from the command line is how the van's results go back in, thirty
at a time, from a text file the crew chief types up. There is no bulk load
command because there has never been more than thirty, and a command that read
a file would want a file format nobody has agreed.
"""

from __future__ import annotations

from datetime import date, datetime

import click

from ..domain.envelope import Envelope
from ..measure import Inches
from ..persistence.books import lines_from, network_from
from ..reports.lines import LOW
from ..reports.lines import report as survey_report
from ..routing.itinerary import Itinerary
from ..utilities.lines import Crossing, Owner, under
from .main import Console, main, say


@main.group()
def wires() -> None:
    """Overhead lines: the survey, the owners, and what is in the way."""


@wires.command("list")
@click.option("--segment", default=None, help="One road rather than all of them.")
@click.option("--under", "ceiling", default=None, help="Only wires below this height.")
@click.pass_obj
def listing(console: Console, segment: str | None, ceiling: str | None) -> None:
    """Every wire on file, or every wire over one road."""
    with console.work() as work:
        if ceiling is not None:
            rows = work.lines.under(Inches.parse(ceiling))
        else:
            rows = work.lines.crossings(segment)
        for row in rows:
            lift = row.lift_to if row.lift_to is not None else "fixed"
            when = row.measured.isoformat() if row.measured else "never"
            say(row.ident, row.segment_ident, row.place, row.height, lift, when)


@wires.command("owners")
@click.pass_obj
def owners(console: Console) -> None:
    """Every line owner, with how many wires and how much notice."""
    with console.work() as work:
        for row in work.lines.owners():
            say(row.ident, row.name, row.telephone, f"{row.notice_days} days", len(row.wires))


@wires.command("owner")
@click.argument("ident")
@click.argument("name")
@click.option("--telephone", default="")
@click.option("--notice", type=int, default=5)
@click.pass_obj
def add_owner(console: Console, ident: str, name: str, telephone: str, notice: int) -> None:
    """Put a line owner on file."""
    with console.work() as work:
        work.lines.add_owner(Owner(ident, name, telephone, notice))
        work.commit()
    say("filed", ident, name)


@wires.command("add")
@click.argument("ident")
@click.argument("segment")
@click.argument("owner")
@click.option("--place", required=True)
@click.option("--height", required=True)
@click.option("--lift-to", default=None, help="Leave it off for a wire nothing can lift.")
@click.option("--measured", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_obj
def add(
    console: Console,
    ident: str,
    segment: str,
    owner: str,
    place: str,
    height: str,
    lift_to: str | None,
    measured: datetime | None,
) -> None:
    """Put a measured wire on file."""
    crossing = Crossing(
        ident=ident,
        segment=segment,
        place=place,
        height=Inches.parse(height),
        owner=owner,
        lift_to=None if lift_to is None else Inches.parse(lift_to),
    )
    with console.work() as work:
        work.lines.add(crossing, measured.date() if measured else None)
        work.commit()
    say("filed", ident, segment, crossing.height)


@wires.command("measure")
@click.argument("ident")
@click.argument("height")
@click.option("--lift-to", default=None, help="Leave it off for a wire nothing can lift.")
@click.option("--on", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_obj
def measure(
    console: Console,
    ident: str,
    height: str,
    lift_to: str | None,
    on: datetime | None,
) -> None:
    """Write what the crew found, replacing what the card said."""
    day = on.date() if on else date.today()
    with console.work() as work:
        row = work.lines.remeasure(
            ident,
            Inches.parse(height),
            day,
            None if lift_to is None else Inches.parse(lift_to),
        )
        work.commit()
    say("measured", ident, row.height, day.isoformat())


@wires.command("survey")
@click.option("--day", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.option("--under", "ceiling", default=None, help="What counts as a low wire.")
@click.pass_obj
def survey(console: Console, day: datetime | None, ceiling: str | None) -> None:
    """The van's list: low wires nobody has walked inside the cycle."""
    when = day.date() if day else date.today()
    height = Inches.parse(ceiling) if ceiling else LOW
    with console.work() as work:
        for line in survey_report(work.db, when, height):
            click.echo(line)


@wires.command("clearance")
@click.argument("origin")
@click.argument("segments", nargs=-1, required=True)
@click.option("--height", required=True)
@click.option("--travel", type=click.DateTime(["%Y-%m-%d"]), required=True)
@click.option("--filed", type=click.DateTime(["%Y-%m-%d"]), default=None)
@click.pass_obj
def clearance(
    console: Console,
    origin: str,
    segments: tuple[str, ...],
    height: str,
    travel: datetime,
    filed: datetime | None,
) -> None:
    """What a load of this height meets on this written route."""
    envelope = Envelope.read("8'6", height, "70'")
    day = travel.date()
    asked = filed.date() if filed else date.today()
    with console.work() as work:
        itinerary = Itinerary(network_from(work.db), origin, segments)
        found = under(itinerary, envelope, lines_from(work.db), asked, day)
    for lift in found.lifts:
        say("lift", lift.crossing.ident, lift.owner.name, lift.owner.telephone, lift.notice_by)
    for blocked in found.blocked:
        say("blocked", blocked.crossing.ident, str(blocked))
    if found.clear and not found.lifts:
        say("clear", envelope.height)
