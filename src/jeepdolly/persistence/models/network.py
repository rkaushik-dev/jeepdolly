"""The road log and the restrictions hanging off it.

The road log changes twice a year and the restrictions change every week, so
they are two tables joined by segment ident rather than one table with a lot of
nullable columns.

Every child table has a relationship back to its parent even where nothing
reads it. That is not decoration. SQLAlchemy orders its inserts by mapper
relationships and not by foreign key columns, so a segment and its restriction
added in one flush go in configuration order without one, and the restriction
lands first and the foreign key fails. The relationship is what tells the unit
of work which row is the parent.

A restriction is deleted rather than retired, which is the opposite of the
reference tables and it is deliberate. A lifted restriction is not history
anybody needs: the office keeps the order that lifted it in the audit trail,
and a restriction table full of dead rows makes every route check slower for
no benefit at all.
"""

from __future__ import annotations

from datetime import date, time

from sqlalchemy import Boolean, CheckConstraint, Date, ForeignKey, Integer, String, Time
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ...domain.highway import Jurisdiction, Route
from ...domain.network import Node, Segment
from ...domain.restriction import RestrictionKind
from ...measure import Miles
from ..base import (
    CODE_LENGTH,
    NAME_LENGTH,
    TEXT_LENGTH,
    Base,
    EnumValue,
    InchesType,
    MilesType,
    PoundsType,
)


class NodeRow(Base):
    """A place a route can turn, start or end."""

    __tablename__ = "nodes"

    name: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    town: Mapped[str] = mapped_column(String(NAME_LENGTH))

    def to_domain(self) -> Node:
        return Node(self.name, self.town)

    @classmethod
    def of(cls, node: Node) -> NodeRow:
        return cls(name=node.name, town=node.town)

    def __repr__(self) -> str:
        return f"<NodeRow {self.name}>"


class SegmentRow(Base):
    """One piece of road between two nodes."""

    __tablename__ = "segments"
    __table_args__ = (
        CheckConstraint("length_hundredths > 0", name="length_positive"),
        CheckConstraint("start_node <> end_node", name="ends_differ"),
        CheckConstraint("district between 1 and 9", name="district_in_range"),
    )

    ident: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    route_kind: Mapped[str] = mapped_column(String(4))
    route_number: Mapped[int] = mapped_column(Integer)
    route_suffix: Mapped[str] = mapped_column(String(2), default="")
    start_node: Mapped[str] = mapped_column(ForeignKey("nodes.name"), index=True)
    end_node: Mapped[str] = mapped_column(ForeignKey("nodes.name"), index=True)
    length: Mapped[Miles] = mapped_column("length_hundredths", MilesType)
    county: Mapped[str] = mapped_column(String(NAME_LENGTH))
    town: Mapped[str] = mapped_column(String(NAME_LENGTH))
    district: Mapped[int] = mapped_column(Integer, index=True)
    one_way: Mapped[bool] = mapped_column(Boolean, default=False)

    start: Mapped[NodeRow] = relationship(foreign_keys=[start_node])
    end: Mapped[NodeRow] = relationship(foreign_keys=[end_node])

    def to_domain(self) -> Segment:
        return Segment(
            ident=self.ident,
            route=Route(self.route_kind, self.route_number, self.route_suffix),
            start=self.start_node,
            end=self.end_node,
            length=self.length,
            jurisdiction=Jurisdiction(self.county, self.town, self.district),
            one_way=self.one_way,
        )

    @classmethod
    def of(cls, segment: Segment) -> SegmentRow:
        return cls(
            ident=segment.ident,
            route_kind=segment.route.kind,
            route_number=segment.route.number,
            route_suffix=segment.route.suffix,
            start_node=segment.start,
            end_node=segment.end,
            length=segment.length,
            county=segment.jurisdiction.county,
            town=segment.jurisdiction.town,
            district=segment.jurisdiction.district,
            one_way=segment.one_way,
        )

    def __repr__(self) -> str:
        return f"<SegmentRow {self.ident}>"


class RestrictionRow(Base):
    """One thing wrong with one segment, for a while or for good."""

    __tablename__ = "restrictions"
    __table_args__ = (
        CheckConstraint(
            "expires is null or effective is null or expires >= effective",
            name="lifts_after_it_starts",
        ),
    )

    ident: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    segment_ident: Mapped[str] = mapped_column(ForeignKey("segments.ident"), index=True)
    kind: Mapped[RestrictionKind] = mapped_column(EnumValue(RestrictionKind), index=True)
    note: Mapped[str] = mapped_column(String(TEXT_LENGTH))
    height = mapped_column(InchesType, nullable=True)
    width = mapped_column(InchesType, nullable=True)
    weight = mapped_column(PoundsType, nullable=True)
    region: Mapped[str | None] = mapped_column(String(NAME_LENGTH), nullable=True)
    curfew_days: Mapped[str | None] = mapped_column(String(16), nullable=True)
    curfew_from: Mapped[time | None] = mapped_column(Time, nullable=True)
    curfew_to: Mapped[time | None] = mapped_column(Time, nullable=True)
    effective: Mapped[date | None] = mapped_column(Date, nullable=True)
    expires: Mapped[date | None] = mapped_column(Date, nullable=True)

    segment: Mapped[SegmentRow] = relationship()

    def __repr__(self) -> str:
        return f"<RestrictionRow {self.ident} {self.kind}>"
