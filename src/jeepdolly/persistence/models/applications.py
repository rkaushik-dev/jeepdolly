"""The application tables: what was asked for, exactly as it was asked.

An application is four tables because a combination is a list of things and a
list of things in a text column is a list nobody can query. The office does
query them: how many nine axle trailers came through last year, which carriers
run boosters, how many applications named a piece with no serial number.

The children carry a position rather than leaning on insertion order. Axle
three is axle three whatever order the rows came back in, and the spacing on
an axle row is the gap to the axle in front of it, which makes the first axle
the only one with no spacing and saves a separate table for four inches of
information.

Nothing here validates. The domain objects do that, and the row is allowed to
hold something the domain would refuse, because an application that was typed
wrong still has to be stored, quoted back and refused in writing.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import Boolean, CheckConstraint, Date, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ...domain.axle import AxleKind
from ...domain.combination import UnitKind
from ...permits.application import ApplicationState
from ..base import (
    CODE_LENGTH,
    NAME_LENGTH,
    TEXT_LENGTH,
    Base,
    EnumValue,
    InchesType,
    PoundsType,
)


class ApplicationRow(Base):
    """One request for one permit, as it was submitted."""

    __tablename__ = "applications"
    __table_args__ = (
        CheckConstraint("travel_end >= travel_start", name="span_ends_after_it_starts"),
        CheckConstraint("origin <> destination", name="trip_goes_somewhere"),
    )

    reference: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    usdot: Mapped[int] = mapped_column(ForeignKey("carriers.usdot"), index=True)
    commodity: Mapped[str] = mapped_column(ForeignKey("commodities.code"))
    origin: Mapped[str] = mapped_column(String(CODE_LENGTH))
    destination: Mapped[str] = mapped_column(String(CODE_LENGTH))
    travel_start: Mapped[date] = mapped_column(Date, index=True)
    travel_end: Mapped[date] = mapped_column(Date)
    filed: Mapped[date] = mapped_column(Date, index=True)
    state: Mapped[ApplicationState] = mapped_column(
        EnumValue(ApplicationState), default=ApplicationState.DRAFT, index=True
    )
    applicant_name: Mapped[str] = mapped_column(String(NAME_LENGTH))
    applicant_phone: Mapped[str] = mapped_column(String(CODE_LENGTH))
    applicant_email: Mapped[str] = mapped_column(String(NAME_LENGTH), default="")
    route: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")
    escort_cards: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")
    notes: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")
    width = mapped_column(InchesType)
    height = mapped_column(InchesType)
    length = mapped_column(InchesType)
    front_overhang = mapped_column(InchesType)
    rear_overhang = mapped_column(InchesType)

    # These two are here to order the inserts, not because anything reads
    # them. Without a relationship the unit of work writes the application
    # before the carrier it points at, and the foreign key fails.
    carrier = relationship("CarrierRow")
    commodity_line = relationship("CommodityRow")

    axles: Mapped[list[ApplicationAxleRow]] = relationship(
        back_populates="application",
        cascade="all, delete-orphan",
        order_by="ApplicationAxleRow.position",
    )
    units: Mapped[list[ApplicationUnitRow]] = relationship(
        back_populates="application",
        cascade="all, delete-orphan",
        order_by="ApplicationUnitRow.position",
    )
    pieces: Mapped[list[ApplicationPieceRow]] = relationship(
        back_populates="application",
        cascade="all, delete-orphan",
        order_by="ApplicationPieceRow.position",
    )

    @property
    def route_idents(self) -> tuple[str, ...]:
        """The written route, empty before the column default has been applied."""
        return tuple(one for one in (self.route or "").split(",") if one)

    @property
    def cards(self) -> tuple[str, ...]:
        """The pilot car cards named, in the order the hauler wrote them.

        Order is kept because the first card named is the one the counter
        telephones, and a set would lose that for no gain at all.
        """
        return tuple(one for one in (self.escort_cards or "").split(",") if one)

    @property
    def axle_count(self) -> int:
        return len(self.axles)

    def __repr__(self) -> str:
        return f"<ApplicationRow {self.reference} {self.state}>"


class ApplicationAxleRow(Base):
    """One axle of the declared combination, with the gap in front of it."""

    __tablename__ = "application_axles"
    __table_args__ = (
        CheckConstraint("tires >= 2 and tires % 2 = 0", name="tires_are_an_even_pair"),
        CheckConstraint("position >= 0", name="position_not_negative"),
    )

    reference: Mapped[str] = mapped_column(ForeignKey("applications.reference"), primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    kind: Mapped[AxleKind] = mapped_column(EnumValue(AxleKind))
    weight = mapped_column(PoundsType)
    tires: Mapped[int] = mapped_column(Integer, default=4)
    tire_width = mapped_column(InchesType)
    liftable: Mapped[bool] = mapped_column(Boolean, default=False)
    spacing_before = mapped_column(InchesType, nullable=True)

    application: Mapped[ApplicationRow] = relationship(back_populates="axles")

    def __repr__(self) -> str:
        return f"<ApplicationAxleRow {self.reference} {self.position}>"


class ApplicationUnitRow(Base):
    """One piece of equipment in the string, in road order."""

    __tablename__ = "application_units"
    __table_args__ = (CheckConstraint("axles >= 1", name="a_unit_has_axles"),)

    reference: Mapped[str] = mapped_column(ForeignKey("applications.reference"), primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    kind: Mapped[UnitKind] = mapped_column(EnumValue(UnitKind))
    axles: Mapped[int] = mapped_column(Integer)
    plate: Mapped[str] = mapped_column(String(CODE_LENGTH))
    tare = mapped_column(PoundsType)
    make: Mapped[str] = mapped_column(String(NAME_LENGTH), default="")
    unit_number: Mapped[str] = mapped_column(String(CODE_LENGTH), default="")

    application: Mapped[ApplicationRow] = relationship(back_populates="units")

    def __repr__(self) -> str:
        return f"<ApplicationUnitRow {self.reference} {self.kind}>"


class ApplicationPieceRow(Base):
    """One thing on the trailer."""

    __tablename__ = "application_pieces"

    reference: Mapped[str] = mapped_column(ForeignKey("applications.reference"), primary_key=True)
    position: Mapped[int] = mapped_column(Integer, primary_key=True)
    commodity: Mapped[str] = mapped_column(ForeignKey("commodities.code"))
    description: Mapped[str] = mapped_column(String(NAME_LENGTH))
    serial: Mapped[str | None] = mapped_column(String(CODE_LENGTH), nullable=True)

    application: Mapped[ApplicationRow] = relationship(back_populates="pieces")
    commodity_line = relationship("CommodityRow")

    def __repr__(self) -> str:
        return f"<ApplicationPieceRow {self.reference} {self.description}>"
