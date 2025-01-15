"""Carrier routes: register one, file its paper, suspend it, let it back.

Suspending a carrier is the one route in the service with a side effect on
documents it was not asked about. A suspension voids every live permit the
carrier holds, because a suspended carrier is not entitled to be on the road
under a permit this office wrote yesterday.

That is done here rather than left to the supervisor to remember, and every
voided permit lands on the trail with the suspension as its reason.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel

from ...domain.carrier import Billing, Bond, Carrier, Insurance
from ...money import Dollars
from ...persistence.models.carriers import BondRow, CarrierRow, InsuranceRow, SuspensionRow
from ...persistence.unitofwork import UnitOfWork
from ...services import audit
from ..deps import unit_of_work
from ..security import Caller, Role, needs

router = APIRouter(prefix="/carriers", tags=["carriers"])


class CarrierIn(BaseModel):
    """A carrier as the counter types it in."""

    usdot: int
    name: str
    town: str
    state: str
    billing: str = "counter"

    def to_domain(self) -> Carrier:
        return Carrier(
            usdot=self.usdot,
            name=self.name,
            town=self.town,
            state=self.state,
            billing=Billing(self.billing),
        )


class InsuranceIn(BaseModel):
    """A certificate as it arrives by fax."""

    policy: str
    insurer: str
    liability: str
    effective: date
    expires: date

    def to_domain(self) -> Insurance:
        return Insurance(
            self.policy, self.insurer, Dollars.parse(self.liability), self.effective, self.expires
        )


class BondIn(BaseModel):
    """A surety bond, for the heavy end of the work."""

    number: str
    surety: str
    amount: str
    effective: date
    expires: date

    def to_domain(self) -> Bond:
        return Bond(
            self.number, self.surety, Dollars.parse(self.amount), self.effective, self.expires
        )


class CarrierOut(BaseModel):
    """A carrier and where it stands today."""

    usdot: int
    name: str
    town: str
    state: str
    billing: str
    standing: str

    @classmethod
    def of(cls, row: CarrierRow, on: date) -> CarrierOut:
        carrier = row.to_domain(on)
        return cls(
            usdot=row.usdot,
            name=row.name,
            town=row.town,
            state=row.state,
            billing=row.billing.value,
            standing=carrier.standing(on, on) or "in good standing",
        )


@router.post("", response_model=CarrierOut, status_code=201)
def register_carrier(
    body: CarrierIn,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> CarrierOut:
    """Put a carrier on file."""
    day = on or date.today()
    row = work.carriers.add(body.to_domain())
    audit.record(work.db, "carrier.registered", who.person, "carrier", str(body.usdot))
    work.commit()
    return CarrierOut.of(row, day)


@router.get("/{usdot}", response_model=CarrierOut)
def read_carrier(
    usdot: int,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> CarrierOut:
    """One carrier, and where it stands on a day."""
    return CarrierOut.of(work.carriers.row(usdot), on or date.today())


@router.get("", response_model=list[CarrierOut])
def search_carriers(
    name: str = Query(...),
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.READER)),
) -> list[CarrierOut]:
    """Name search, the way the counter does it."""
    day = on or date.today()
    return [CarrierOut.of(one, day) for one in work.carriers.search(name)]


@router.post("/{usdot}/insurance", response_model=CarrierOut, status_code=201)
def file_insurance(
    usdot: int,
    body: InsuranceIn,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> CarrierOut:
    """File a certificate. A renewal is a new row, never an edit."""
    day = on or date.today()
    row = work.carriers.row(usdot)
    row.certificates.append(InsuranceRow.of(usdot, body.to_domain(), filed=day))
    work.commit()
    return CarrierOut.of(row, day)


@router.post("/{usdot}/bond", response_model=CarrierOut, status_code=201)
def file_bond(
    usdot: int,
    body: BondIn,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.CLERK)),
) -> CarrierOut:
    """File a bond."""
    day = on or date.today()
    row = work.carriers.row(usdot)
    row.bonds.append(BondRow.of(usdot, body.to_domain()))
    work.commit()
    return CarrierOut.of(row, day)


@router.post("/{usdot}/suspend", response_model=CarrierOut)
def suspend_carrier(
    usdot: int,
    reason: str = Query(..., min_length=3),
    on: date | None = Query(default=None),
    until: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> CarrierOut:
    """Stop writing permits for somebody, and kill the ones they hold."""
    day = on or date.today()
    row = work.carriers.row(usdot)
    row.suspensions.append(
        SuspensionRow(usdot=usdot, reason=reason, from_day=day, until=until, ordered_by=who.person)
    )
    audit.record(work.db, "carrier.suspended", who.person, "carrier", str(usdot), detail=reason)
    for held in work.permits.for_carrier(usdot):
        permit = work.permits.get(held.number)
        if permit.state.value == "issued" and not permit.expired_on(day):
            permit.void(day, f"carrier suspended: {reason}")
            work.permits.save_state(permit)
            audit.record(work.db, "permit.voided", who.person, "permit", held.number, detail=reason)
    work.commit()
    return CarrierOut.of(row, day)


@router.post("/{usdot}/restore", response_model=CarrierOut)
def restore_carrier(
    usdot: int,
    on: date | None = Query(default=None),
    work: UnitOfWork = Depends(unit_of_work),
    who: Caller = Depends(needs(Role.SUPERVISOR)),
) -> CarrierOut:
    """Lift a suspension. The permits it killed stay dead."""
    day = on or date.today()
    row = work.carriers.row(usdot)
    for order in row.suspensions:
        if order.until is None or order.until > day:
            order.until = day
    audit.record(work.db, "carrier.restored", who.person, "carrier", str(usdot))
    work.commit()
    return CarrierOut.of(row, day)
