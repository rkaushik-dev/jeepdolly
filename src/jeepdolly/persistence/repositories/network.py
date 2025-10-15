"""The road log and what is written against it."""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ...errors import NotOnFile
from ..models.network import RestrictionRow, SegmentRow
from ..models.structures import StructureRow


class NetworkRepository:
    """The road log and what is wrong with it."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def segment(self, ident: str) -> SegmentRow:
        found = self.session.get(SegmentRow, ident)
        if found is None:
            raise NotOnFile(f"no segment {ident}", segment=ident)
        return found

    def segments(self) -> tuple[SegmentRow, ...]:
        return tuple(self.session.scalars(select(SegmentRow).order_by(SegmentRow.ident)).all())

    def in_district(self, district: int) -> tuple[SegmentRow, ...]:
        query = select(SegmentRow).where(SegmentRow.district == district)
        return tuple(self.session.scalars(query.order_by(SegmentRow.ident)).all())

    def restrictions_on(self, ident: str, day: date) -> tuple[RestrictionRow, ...]:
        query = select(RestrictionRow).where(RestrictionRow.segment_ident == ident)
        found = self.session.scalars(query).all()
        return tuple(one for one in found if _in_force(one, day))

    def structure(self, ident: str) -> StructureRow:
        found = self.session.get(StructureRow, ident)
        if found is None:
            raise NotOnFile(f"no structure {ident}", structure=ident)
        return found

    def structures_on(self, ident: str) -> tuple[StructureRow, ...]:
        query = (
            select(StructureRow)
            .where(StructureRow.segment_ident == ident)
            .order_by(StructureRow.ident)
        )
        return tuple(self.session.scalars(query).all())

    def posted_structures(self) -> tuple[StructureRow, ...]:
        query = (
            select(StructureRow)
            .where(StructureRow.posted.is_not(None))
            .order_by(StructureRow.ident)
        )
        return tuple(self.session.scalars(query).all())


def _in_force(restriction: RestrictionRow, day: date) -> bool:
    if restriction.effective is not None and day < restriction.effective:
        return False
    return not (restriction.expires is not None and day > restriction.expires)
