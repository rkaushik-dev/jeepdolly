"""Structures and the reviews written against them.

Two tables here and they age differently.

The structure row is edited in place. When an inspection produces a new
operating rating, the row is updated, because the current rating is what every
route check needs and a table of every rating a bridge has ever had is a report
rather than a working table. The inspection date on the row says how old the
number is, which is the question anybody asks about it.

The review tables are append only. A review is a decision an engineer signed
and the moves that led to it, and neither is ever rewritten. The move rows
carry their own order column because two moves can land on the same day and the
history has to read in the order it happened.

The spans are stored as a comma separated string rather than a child table.
That is a deliberate shortcut and the reason is that nothing ever queries one
span: they are read together, always, by the loading calculation. A span table
would be five hundred rows of nothing anybody joins to.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ...errors import Impossible
from ...structures.inventory import Structure, StructureKind
from ...structures.review import ReviewState
from ..base import CODE_LENGTH, NAME_LENGTH, TEXT_LENGTH, Base, EnumValue, InchesType, PoundsType


def spans_to_text(spans: tuple[int, ...]) -> str:
    return ",".join(str(one) for one in spans)


def spans_from_text(text: str) -> tuple[int, ...]:
    if not text:
        return ()
    try:
        return tuple(int(one) for one in text.split(","))
    except ValueError:
        raise Impossible(f"cannot read {text!r} as a list of spans") from None


class StructureRow(Base):
    """One structure, as the last inspection left it."""

    __tablename__ = "structures"
    __table_args__ = (
        CheckConstraint("built = 0 or built between 1800 and 2200", name="built_is_a_year"),
        CheckConstraint(
            "inventory_rating is null or operating_rating is null "
            "or inventory_rating <= operating_rating",
            name="daily_rating_below_permit_rating",
        ),
    )

    ident: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    name: Mapped[str] = mapped_column(String(NAME_LENGTH))
    segment_ident: Mapped[str] = mapped_column(ForeignKey("segments.ident"), index=True)
    kind: Mapped[StructureKind] = mapped_column(EnumValue(StructureKind))
    spans: Mapped[str] = mapped_column(String(NAME_LENGTH), default="")
    built: Mapped[int] = mapped_column(Integer, default=0)
    inspected: Mapped[date | None] = mapped_column(Date, nullable=True)
    inventory_rating = mapped_column(PoundsType, nullable=True)
    operating_rating = mapped_column(PoundsType, nullable=True)
    posted = mapped_column(PoundsType, nullable=True)
    clearance = mapped_column(InchesType, nullable=True)
    deck_width = mapped_column(InchesType, nullable=True)

    segment = relationship("SegmentRow")

    def to_domain(self) -> Structure:
        return Structure(
            ident=self.ident,
            name=self.name,
            segment=self.segment_ident,
            kind=self.kind,
            spans_feet=spans_from_text(self.spans),
            built=self.built,
            inspected=self.inspected,
            inventory_rating=self.inventory_rating,
            operating_rating=self.operating_rating,
            posted=self.posted,
            clearance=self.clearance,
            deck_width=self.deck_width,
        )

    @classmethod
    def of(cls, structure: Structure) -> StructureRow:
        return cls(
            ident=structure.ident,
            name=structure.name,
            segment_ident=structure.segment,
            kind=structure.kind,
            spans=spans_to_text(structure.spans_feet),
            built=structure.built,
            inspected=structure.inspected,
            inventory_rating=structure.inventory_rating,
            operating_rating=structure.operating_rating,
            posted=structure.posted,
            clearance=structure.clearance,
            deck_width=structure.deck_width,
        )

    def __repr__(self) -> str:
        return f"<StructureRow {self.ident}>"


class ReviewRow(Base):
    """One study, waiting for or carrying an engineer's decision."""

    __tablename__ = "reviews"

    reference: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    opened: Mapped[date] = mapped_column(Date, index=True)
    state: Mapped[ReviewState] = mapped_column(EnumValue(ReviewState), index=True)
    engineer: Mapped[str | None] = mapped_column(String(NAME_LENGTH), nullable=True)
    structures: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")
    conditions: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")

    moves: Mapped[list[ReviewMoveRow]] = relationship(
        back_populates="review", cascade="all, delete-orphan", order_by="ReviewMoveRow.position"
    )

    def __repr__(self) -> str:
        return f"<ReviewRow {self.reference} {self.state}>"


class ReviewMoveRow(Base):
    """One thing that happened to a review, in the order it happened."""

    __tablename__ = "review_moves"
    __table_args__ = (CheckConstraint("position >= 0", name="position_not_negative"),)

    reference: Mapped[str] = mapped_column(ForeignKey("reviews.reference"), primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    happened: Mapped[date] = mapped_column(Date)
    who: Mapped[str] = mapped_column(String(NAME_LENGTH))
    state: Mapped[ReviewState] = mapped_column(EnumValue(ReviewState))
    note: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")

    review: Mapped[ReviewRow] = relationship(back_populates="moves")

    def __repr__(self) -> str:
        return f"<ReviewMoveRow {self.reference} {self.position}>"
