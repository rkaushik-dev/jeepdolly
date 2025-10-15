"""Carriers, which everything else is written against."""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ...domain.carrier import Carrier
from ...errors import Conflict, NotOnFile
from ..models.carriers import CarrierRow


class CarrierRepository:
    """Carriers, by number and by name."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def row(self, usdot: int) -> CarrierRow:
        found = self.session.get(CarrierRow, usdot)
        if found is None:
            raise NotOnFile(f"no carrier with usdot {usdot}", usdot=usdot)
        return found

    def get(self, usdot: int, on: date) -> Carrier:
        return self.row(usdot).to_domain(on)

    def search(self, text: str) -> tuple[CarrierRow, ...]:
        needle = text.strip().lower()
        if not needle:
            return ()
        query = select(CarrierRow).order_by(CarrierRow.name)
        found = self.session.scalars(query).all()
        return tuple(one for one in found if needle in one.name.lower())

    def add(self, carrier: Carrier) -> CarrierRow:
        """File a new carrier, refusing a number that is already registered.

        The database would refuse this too, with an integrity error at commit
        time and a five hundred at the caller. The office would rather say
        conflict, and say it before anything else in the transaction is done.
        """
        if self.session.get(CarrierRow, carrier.usdot) is not None:
            raise Conflict(f"usdot {carrier.usdot} is already registered", usdot=carrier.usdot)
        row = CarrierRow.of(carrier)
        self.session.add(row)
        return row

    def count(self) -> int:
        return len(self.session.scalars(select(CarrierRow)).all())
