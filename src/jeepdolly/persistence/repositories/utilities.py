"""Overhead lines, and the survey that remeasures them."""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ...errors import Conflict, NotOnFile
from ...measure import Inches
from ...utilities.lines import Crossing, Owner
from ..models.network import SegmentRow
from ..models.utilities import LineCrossingRow, LineOwnerRow


class LineRepository:
    """Overhead lines and the people who own them.

    A crossing is edited here, which none of the other repositories allow.
    That is the difference between a record of the world and a record of a
    decision: when the crew raises a span, the height on the card is simply
    wrong until somebody changes it, and there is no question anybody asks
    about what it used to be.
    """

    def __init__(self, session: Session) -> None:
        self.session = session

    def owner_row(self, ident: str) -> LineOwnerRow:
        found = self.session.get(LineOwnerRow, ident)
        if found is None:
            raise NotOnFile(f"no line owner {ident}", ident=ident)
        return found

    def row(self, ident: str) -> LineCrossingRow:
        found = self.session.get(LineCrossingRow, ident)
        if found is None:
            raise NotOnFile(f"no crossing {ident}", ident=ident)
        return found

    def owners(self) -> tuple[LineOwnerRow, ...]:
        query = select(LineOwnerRow).order_by(LineOwnerRow.ident)
        return tuple(self.session.scalars(query).all())

    def crossings(self, segment: str | None = None) -> tuple[LineCrossingRow, ...]:
        query = select(LineCrossingRow).order_by(LineCrossingRow.ident)
        if segment is not None:
            query = query.where(LineCrossingRow.segment_ident == segment)
        return tuple(self.session.scalars(query).all())

    def add_owner(self, owner: Owner) -> LineOwnerRow:
        if self.session.get(LineOwnerRow, owner.ident) is not None:
            raise Conflict(f"line owner {owner.ident} is already on file")
        row = LineOwnerRow.of(owner)
        self.session.add(row)
        return row

    def add(self, crossing: Crossing, measured: date | None = None) -> LineCrossingRow:
        """File a measured wire, refusing one that hangs over no known road.

        The road is checked here rather than left to the foreign key. The
        database would refuse it too, at commit time, as a five hundred at
        whoever typed the segment wrong, and a survey being keyed in at speed
        is exactly where a wrong segment comes from.
        """
        if self.session.get(LineCrossingRow, crossing.ident) is not None:
            raise Conflict(f"crossing {crossing.ident} is already on file")
        self.owner_row(crossing.owner)
        if self.session.get(SegmentRow, crossing.segment) is None:
            raise NotOnFile(f"no segment {crossing.segment}", segment=crossing.segment)
        row = LineCrossingRow.of(crossing, measured)
        self.session.add(row)
        return row

    def remeasure(
        self,
        ident: str,
        height: Inches,
        on: date,
        lift_to: Inches | None = None,
    ) -> LineCrossingRow:
        """Write a new measurement onto a crossing, which is what a survey does.

        The lift height is passed every time rather than left alone when it is
        omitted. A crew that raises a span permanently changes both numbers,
        and a call that quietly kept the old lift height would leave the card
        saying a wire lifts to a height it now hangs above.
        """
        row = self.row(ident)
        if lift_to is not None and lift_to <= height:
            raise Conflict(f"crossing {ident} cannot be lifted to below where it hangs")
        row.height = height
        row.lift_to = lift_to
        row.measured = on
        return row

    def under(self, height: Inches) -> tuple[LineCrossingRow, ...]:
        """Every wire hanging below a height, lowest first.

        The survey list. Somebody asks which wires are under twenty feet
        before they decide where next year's crew goes.
        """
        query = (
            select(LineCrossingRow)
            .where(LineCrossingRow.height < height)
            .order_by(LineCrossingRow.height, LineCrossingRow.ident)
        )
        return tuple(self.session.scalars(query).all())

    def count(self) -> int:
        return len(self.session.scalars(select(LineCrossingRow)).all())
