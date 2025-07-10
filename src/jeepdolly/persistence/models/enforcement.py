"""Citations, which are one table and deliberately not two.

The temptation is a citations table and a citation_history table, the way the
permits have their conditions. It was resisted, because the trail already
records every move a citation makes with the person who made it, and a second
history in a second place is a second thing to keep in step.

What that costs is the reason a citation is in the state it is in. The trail
has it, keyed on the citation number, and the office has never wanted it on
the same screen. The one exception is the reason a citation was dismissed or
voided, which is on the row, because that one goes in a letter.

The assessment is stored flattened: the base, the multiplier and the total,
rather than a reference to a schedule. That is on purpose. A citation is a
demand for a specific number of dollars, and re-deriving that number from a
schedule that has since been amended would quietly change a debt somebody has
already been billed for. The schedule date is kept alongside so the working
can be reconstructed when a carrier argues.

The permit is a nullable foreign key. Most citations name one and the ones
that matter most do not, because the whole offence is that there was no
permit at all.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ...enforcement.citation import Citation, CitationState
from ...enforcement.penalty import Assessed, Offence
from ..base import (
    CODE_LENGTH,
    NAME_LENGTH,
    TEXT_LENGTH,
    Base,
    CentsType,
    EnumValue,
    PoundsType,
)


class CitationRow(Base):
    """One citation, as written and as it stands."""

    __tablename__ = "citations"
    __table_args__ = (
        CheckConstraint("filed >= written", name="filed_after_written"),
        CheckConstraint("multiplier between 1 and 3", name="multiplier_in_range"),
    )

    number: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    written: Mapped[date] = mapped_column(Date, index=True)
    filed: Mapped[date] = mapped_column(Date, index=True)
    officer: Mapped[str] = mapped_column(String(NAME_LENGTH))
    usdot: Mapped[int] = mapped_column(ForeignKey("carriers.usdot"), index=True)
    carrier_name: Mapped[str] = mapped_column(String(NAME_LENGTH))
    offence: Mapped[Offence] = mapped_column(EnumValue(Offence), index=True)
    place: Mapped[str] = mapped_column(String(NAME_LENGTH))
    permit: Mapped[str | None] = mapped_column(
        ForeignKey("permits.number"), nullable=True, index=True
    )
    over = mapped_column(PoundsType, nullable=True)
    base = mapped_column(CentsType)
    multiplier: Mapped[int] = mapped_column(Integer, default=1)
    amount = mapped_column(CentsType)
    schedule: Mapped[date] = mapped_column(Date)
    state: Mapped[CitationState] = mapped_column(
        EnumValue(CitationState), default=CitationState.WRITTEN, index=True
    )
    closing_reason: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")
    note: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")

    # These exist so the flush orders the inserts. Nothing reads them.
    carrier = relationship("CarrierRow")
    permit_row = relationship("PermitRow")

    def to_domain(self) -> Citation:
        return Citation(
            number=self.number,
            written=self.written,
            filed=self.filed,
            officer=self.officer,
            usdot=self.usdot,
            carrier_name=self.carrier_name,
            offence=self.offence,
            place=self.place,
            assessed=Assessed(self.offence, self.base, self.multiplier, self.amount, self.over),
            permit=self.permit,
            over=self.over,
            note=self.note or "",
            state=self.state,
        )

    @classmethod
    def of(cls, citation: Citation, schedule: date) -> CitationRow:
        return cls(
            number=citation.number,
            written=citation.written,
            filed=citation.filed,
            officer=citation.officer,
            usdot=citation.usdot,
            carrier_name=citation.carrier_name,
            offence=citation.offence,
            place=citation.place,
            permit=citation.permit,
            over=citation.over,
            base=citation.assessed.base,
            multiplier=citation.assessed.multiplier,
            amount=citation.assessed.total,
            schedule=schedule,
            state=citation.state,
            note=citation.note,
        )

    def __repr__(self) -> str:
        return f"<CitationRow {self.number}>"
