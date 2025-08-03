"""Line owners and the wires they own, which the office remeasures every year.

Two tables and the same split the carrier and pilot tables make: the owner row
is edited and the crossing row is edited too, which is the exception.

A crossing is edited because a wire is a fact about the world today rather
than a document. When the line crew raises a span permanently, the height on
the card changes, and there is no question anybody asks about what the height
was last March. That is different from a permit, where the whole argument is
about what it said on the day.

The measured day is on the row because a crossing measured in 1998 is not
evidence about a wire in 2025, and the office reruns its survey on a rolling
five year cycle. Nothing reads it yet. It is there so that when somebody asks
which crossings are overdue for a look, the answer is a query rather than a
survey.

The lift height is nullable and null means the wire cannot be lifted at all.
That is a real distinction and not a missing value: a transmission line on
steel towers is not going anywhere, and recording that as a very large lift
height would eventually let a permit through on it.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ...measure import Inches
from ...utilities.lines import Crossing, Owner
from ..base import CODE_LENGTH, NAME_LENGTH, TEXT_LENGTH, Base, InchesType


class LineOwnerRow(Base):
    """Whoever owns the wire, and how much warning they want."""

    __tablename__ = "line_owners"
    __table_args__ = (CheckConstraint("notice_days >= 0", name="notice_is_not_negative"),)

    ident: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    name: Mapped[str] = mapped_column(String(NAME_LENGTH), index=True)
    telephone: Mapped[str] = mapped_column(String(CODE_LENGTH), default="")
    notice_days: Mapped[int] = mapped_column(Integer, default=5)
    note: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")

    wires: Mapped[list[LineCrossingRow]] = relationship(
        back_populates="owner",
        cascade="all, delete-orphan",
        order_by="LineCrossingRow.ident",
    )

    def to_domain(self) -> Owner:
        return Owner(
            ident=self.ident,
            name=self.name,
            telephone=self.telephone,
            notice_days=self.notice_days,
            note=self.note,
        )

    @classmethod
    def of(cls, owner: Owner) -> LineOwnerRow:
        return cls(
            ident=owner.ident,
            name=owner.name,
            telephone=owner.telephone,
            notice_days=owner.notice_days,
            note=owner.note,
        )

    def __repr__(self) -> str:
        return f"<LineOwnerRow {self.ident}>"


class LineCrossingRow(Base):
    """One wire over one segment, as last measured."""

    __tablename__ = "line_crossings"
    __table_args__ = (
        CheckConstraint("height > 0", name="height_is_measured"),
        CheckConstraint("lift_to is null or lift_to > height", name="lift_is_upwards"),
    )

    ident: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    segment_ident: Mapped[str] = mapped_column(ForeignKey("segments.ident"), index=True)
    place: Mapped[str] = mapped_column(String(NAME_LENGTH))
    height = mapped_column(InchesType)
    owner_ident: Mapped[str] = mapped_column(ForeignKey("line_owners.ident"), index=True)
    lift_to = mapped_column(InchesType, nullable=True)
    measured: Mapped[date | None] = mapped_column(Date, nullable=True)
    note: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")

    owner: Mapped[LineOwnerRow] = relationship(back_populates="wires")

    def to_domain(self) -> Crossing:
        return Crossing(
            ident=self.ident,
            segment=self.segment_ident,
            place=self.place,
            height=self.height,
            owner=self.owner_ident,
            lift_to=self.lift_to,
            note=self.note or "",
        )

    @classmethod
    def of(cls, crossing: Crossing, measured: date | None = None) -> LineCrossingRow:
        return cls(
            ident=crossing.ident,
            segment_ident=crossing.segment,
            place=crossing.place,
            height=crossing.height,
            owner_ident=crossing.owner,
            lift_to=crossing.lift_to,
            measured=measured,
            note=crossing.note,
        )

    @property
    def clearance(self) -> Inches:
        """What it hangs at, which is what an enforcement officer asks."""
        return self.height

    def __repr__(self) -> str:
        return f"<LineCrossingRow {self.ident}>"
