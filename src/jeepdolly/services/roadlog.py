"""Loading the road log, which arrives twice a year as a text file.

The state publishes the log as one line per segment, pipe separated, in the
order the fields are written on the paper form. It is produced by a system
older than anybody here and it will not be changing, so this reads it as it
is rather than asking for something nicer.

Loading is idempotent and it is not destructive. A segment already on file is
updated in place. A segment on file and not in the load is left alone, because
half the loads that arrive are partial and deleting on absence would empty the
log the first time somebody sent a district instead of the state.

Every line that cannot be read is refused with its line number and the file
carries on. A load of nine thousand segments with three bad lines is a load
the office wants, with three lines to type in by hand, and not a load that
failed.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from sqlalchemy.orm import Session

from ..domain.highway import Jurisdiction, Route
from ..domain.network import Node, Segment
from ..errors import Impossible, Refusal
from ..measure import Miles
from ..persistence.models.network import NodeRow, SegmentRow

SEPARATOR = "|"
FIELDS = 9


@dataclass
class Loaded:
    """What a load did, and what it could not read."""

    added: list[str] = field(default_factory=list)
    updated: list[str] = field(default_factory=list)
    refused: list[tuple[int, str]] = field(default_factory=list)

    @property
    def read(self) -> int:
        return len(self.added) + len(self.updated)

    def lines(self) -> tuple[str, ...]:
        found = [
            f"{len(self.added)} added  {len(self.updated)} updated  {len(self.refused)} refused"
        ]
        for number, why in self.refused:
            found.append(f"line {number}  {why}")
        return tuple(found)

    def __str__(self) -> str:
        return f"{self.read} read, {len(self.refused)} refused"


def written(segment: Segment) -> str:
    """One segment as the published log writes it."""
    return SEPARATOR.join(
        [
            segment.ident,
            str(segment.route),
            segment.start,
            segment.end,
            str(segment.length.hundredths),
            segment.jurisdiction.county,
            segment.jurisdiction.town,
            str(segment.jurisdiction.district),
            "1" if segment.one_way else "0",
        ]
    )


def parse(line: str) -> Segment:
    """One line of the published log, or a refusal naming what is wrong."""
    parts = [one.strip() for one in line.split(SEPARATOR)]
    if len(parts) != FIELDS:
        raise Impossible(f"a log line has {FIELDS} fields, this one has {len(parts)}")
    ident, route, start, end, hundredths, county, town, district, one_way = parts
    if not hundredths.isdigit():
        raise Impossible(f"{hundredths!r} is not a length in hundredths")
    if not district.isdigit():
        raise Impossible(f"{district!r} is not a district")
    return Segment(
        ident=ident,
        route=Route.parse(route),
        start=start,
        end=end,
        length=Miles(int(hundredths)),
        jurisdiction=Jurisdiction(county, town, int(district)),
        one_way=one_way in ("1", "y", "Y"),
    )


def export_log(session: Session) -> tuple[str, ...]:
    """The whole log as text, in ident order, for a diff against the state."""
    rows = session.query(SegmentRow).order_by(SegmentRow.ident).all()
    return tuple(written(one.to_domain()) for one in rows)


def load_log(session: Session, lines: tuple[str, ...], make_nodes: bool = True) -> Loaded:
    """Read a published log into the tables, keeping what is not mentioned."""
    found = Loaded()
    for number, line in enumerate(lines, start=1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        try:
            segment = parse(line)
        except Refusal as refusal:
            found.refused.append((number, refusal.message))
            continue
        if make_nodes:
            for name in (segment.start, segment.end):
                if session.get(NodeRow, name) is None:
                    session.add(NodeRow.of(Node(name, name.title())))
            session.flush()
        row = session.get(SegmentRow, segment.ident)
        if row is None:
            session.add(SegmentRow.of(segment))
            found.added.append(segment.ident)
        else:
            fresh = SegmentRow.of(segment)
            for name in (
                "route_kind",
                "route_number",
                "route_suffix",
                "start_node",
                "end_node",
                "length",
                "county",
                "town",
                "district",
                "one_way",
            ):
                setattr(row, name, getattr(fresh, name))
            found.updated.append(segment.ident)
    return found
