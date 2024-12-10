"""Carriers, their paper, and the orders against them.

Four tables, and the shape of them says what the office does with each.

The carrier row is edited: the name, the town, the billing arrangement. It is
the only row here that changes.

Insurance and bonds are appended. A renewed certificate is a new row, not an
edit, because the question asked in a dispute is what cover was in force on the
day of the move and the answer has to survive the renewal that came after it.
The current certificate is the one whose dates cover today, and if two do, the
one that was filed last.

Suspensions are appended and never lifted by deletion. A suspension that ends
gets an end date, so the record shows the carrier was suspended for a fortnight
in March and came back, which is exactly what somebody will ask about in
September.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ...domain.carrier import Billing, Bond, Carrier, Insurance, Suspension
from ..base import CODE_LENGTH, NAME_LENGTH, TEXT_LENGTH, Base, CentsType, EnumValue


class CarrierRow(Base):
    """One carrier, edited in place."""

    __tablename__ = "carriers"
    __table_args__ = (
        CheckConstraint("usdot > 0 and usdot < 100000000", name="usdot_is_a_number"),
        CheckConstraint("length(state) = 2", name="state_is_two_letters"),
    )

    usdot: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=False)
    name: Mapped[str] = mapped_column(String(NAME_LENGTH), index=True)
    town: Mapped[str] = mapped_column(String(NAME_LENGTH))
    state: Mapped[str] = mapped_column(String(2))
    billing: Mapped[Billing] = mapped_column(EnumValue(Billing), default=Billing.COUNTER)

    certificates: Mapped[list[InsuranceRow]] = relationship(
        back_populates="carrier", cascade="all, delete-orphan", order_by="InsuranceRow.effective"
    )
    bonds: Mapped[list[BondRow]] = relationship(
        back_populates="carrier", cascade="all, delete-orphan", order_by="BondRow.effective"
    )
    suspensions: Mapped[list[SuspensionRow]] = relationship(
        back_populates="carrier", cascade="all, delete-orphan", order_by="SuspensionRow.from_day"
    )

    def current_insurance(self, on: date) -> InsuranceRow | None:
        found = [one for one in self.certificates if one.effective <= on <= one.expires]
        return max(found, key=lambda one: one.effective) if found else None

    def current_bond(self, on: date) -> BondRow | None:
        found = [one for one in self.bonds if one.effective <= on <= one.expires]
        return max(found, key=lambda one: one.effective) if found else None

    def to_domain(self, on: date | None = None) -> Carrier:
        """The carrier as of a day, with the paper that was in force then."""
        day = on or date.today()
        cover = self.current_insurance(day)
        bond = self.current_bond(day)
        return Carrier(
            usdot=self.usdot,
            name=self.name,
            town=self.town,
            state=self.state,
            billing=self.billing,
            insurance=cover.to_domain() if cover else None,
            bond=bond.to_domain() if bond else None,
            suspensions=[one.to_domain() for one in self.suspensions],
        )

    @classmethod
    def of(cls, carrier: Carrier) -> CarrierRow:
        return cls(
            usdot=carrier.usdot,
            name=carrier.name,
            town=carrier.town,
            state=carrier.state,
            billing=carrier.billing,
        )

    def __repr__(self) -> str:
        return f"<CarrierRow {self.usdot}>"


class InsuranceRow(Base):
    """One certificate, as filed. Never edited."""

    __tablename__ = "insurance_certificates"
    __table_args__ = (CheckConstraint("expires > effective", name="expires_after_effective"),)

    policy: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    usdot: Mapped[int] = mapped_column(ForeignKey("carriers.usdot"), index=True)
    insurer: Mapped[str] = mapped_column(String(NAME_LENGTH))
    liability = mapped_column(CentsType)
    effective: Mapped[date] = mapped_column(Date)
    expires: Mapped[date] = mapped_column(Date)
    filed: Mapped[date | None] = mapped_column(Date, nullable=True)

    carrier: Mapped[CarrierRow] = relationship(back_populates="certificates")

    def to_domain(self) -> Insurance:
        return Insurance(self.policy, self.insurer, self.liability, self.effective, self.expires)

    @classmethod
    def of(cls, usdot: int, cover: Insurance, filed: date | None = None) -> InsuranceRow:
        return cls(
            policy=cover.policy,
            usdot=usdot,
            insurer=cover.insurer,
            liability=cover.liability,
            effective=cover.effective,
            expires=cover.expires,
            filed=filed,
        )

    def __repr__(self) -> str:
        return f"<InsuranceRow {self.policy}>"


class BondRow(Base):
    """One surety bond, as filed. Never edited."""

    __tablename__ = "bonds"
    __table_args__ = (CheckConstraint("expires > effective", name="expires_after_effective"),)

    number: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    usdot: Mapped[int] = mapped_column(ForeignKey("carriers.usdot"), index=True)
    surety: Mapped[str] = mapped_column(String(NAME_LENGTH))
    amount = mapped_column(CentsType)
    effective: Mapped[date] = mapped_column(Date)
    expires: Mapped[date] = mapped_column(Date)

    carrier: Mapped[CarrierRow] = relationship(back_populates="bonds")

    def to_domain(self) -> Bond:
        return Bond(self.number, self.surety, self.amount, self.effective, self.expires)

    @classmethod
    def of(cls, usdot: int, bond: Bond) -> BondRow:
        return cls(
            number=bond.number,
            usdot=usdot,
            surety=bond.surety,
            amount=bond.amount,
            effective=bond.effective,
            expires=bond.expires,
        )

    def __repr__(self) -> str:
        return f"<BondRow {self.number}>"


class SuspensionRow(Base):
    """One order against a carrier. Ended rather than deleted."""

    __tablename__ = "suspensions"
    __table_args__ = (
        CheckConstraint("until is null or until >= from_day", name="lifts_after_it_starts"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    usdot: Mapped[int] = mapped_column(ForeignKey("carriers.usdot"), index=True)
    reason: Mapped[str] = mapped_column(String(TEXT_LENGTH))
    from_day: Mapped[date] = mapped_column(Date)
    until: Mapped[date | None] = mapped_column(Date, nullable=True)
    ordered_by: Mapped[str] = mapped_column(String(NAME_LENGTH), default="")

    carrier: Mapped[CarrierRow] = relationship(back_populates="suspensions")

    def to_domain(self) -> Suspension:
        return Suspension(self.reason, self.from_day, self.until)

    @classmethod
    def of(cls, usdot: int, order: Suspension, by: str = "") -> SuspensionRow:
        return cls(
            usdot=usdot,
            reason=order.reason,
            from_day=order.from_day,
            until=order.until,
            ordered_by=by,
        )

    def __repr__(self) -> str:
        return f"<SuspensionRow {self.usdot} from {self.from_day.isoformat()}>"
