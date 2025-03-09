"""Seasonal orders, which are signed documents and are stored like one.

The order number is the primary key because that is what the district writes
on the paper and what everybody quotes on the telephone. It is not a serial
the database invents, and it never will be.

Two orders of the same region may not overlap, and the database cannot say so
in a constraint. That check lives in the season book, which is where it has to
be anyway because the book is what the routing reads. The table carries an
index on region and dates so the check is a lookup rather than a scan.

A lifted order keeps its dates and gets an end date on the day it lifted. The
row is never deleted, because a permit issued under it is priced against a
limit that only that row explains.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, Index, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from ...seasons import SeasonKind, SeasonOrder
from ..base import CODE_LENGTH, NAME_LENGTH, TEXT_LENGTH, Base, EnumValue


class SeasonOrderRow(Base):
    """One signed order, for one region, over one span of days."""

    __tablename__ = "season_orders"
    __table_args__ = (
        CheckConstraint("ends >= starts", name="ends_after_it_starts"),
        CheckConstraint("share between 1 and 200", name="share_is_a_percent"),
        Index("ix_season_orders_region_starts", "region", "starts"),
    )

    number: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    region: Mapped[str] = mapped_column(String(NAME_LENGTH), index=True)
    kind: Mapped[SeasonKind] = mapped_column(EnumValue(SeasonKind))
    starts: Mapped[date] = mapped_column(Date)
    ends: Mapped[date] = mapped_column(Date)
    share: Mapped[int] = mapped_column(Integer)
    signed_by: Mapped[str] = mapped_column(String(NAME_LENGTH), default="")
    note: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")

    def to_domain(self) -> SeasonOrder:
        return SeasonOrder(
            number=self.number,
            region=self.region,
            kind=self.kind,
            start=self.starts,
            end=self.ends,
            share=self.share,
        )

    @classmethod
    def of(cls, order: SeasonOrder, signed_by: str = "", note: str = "") -> SeasonOrderRow:
        return cls(
            number=order.number,
            region=order.region,
            kind=order.kind,
            starts=order.start,
            ends=order.end,
            share=order.share,
            signed_by=signed_by,
            note=note,
        )

    def __repr__(self) -> str:
        return f"<SeasonOrderRow {self.number} {self.region}>"
