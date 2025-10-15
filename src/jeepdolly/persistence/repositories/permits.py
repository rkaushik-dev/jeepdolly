"""Applications and permits, which are one story in two tables."""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ...domain.commodity import CommodityBook
from ...errors import NotOnFile
from ...permits.application import Application, ApplicationState
from ...permits.permit import Permit, PermitState
from ..mappers import (
    application_to_row,
    permit_to_row,
    row_to_application,
    row_to_permit,
)
from ..models.applications import ApplicationRow
from ..models.permits import PermitRow


class ApplicationRepository:
    """Applications, by reference and by the queues the counter works."""

    def __init__(self, session: Session, commodities: CommodityBook) -> None:
        self.session = session
        self.commodities = commodities

    def row(self, reference: str) -> ApplicationRow:
        found = self.session.get(ApplicationRow, reference)
        if found is None:
            raise NotOnFile(f"no application {reference}", reference=reference)
        return found

    def get(self, reference: str) -> Application:
        return row_to_application(self.row(reference), self.commodities)

    def add(self, application: Application) -> ApplicationRow:
        row = application_to_row(application)
        self.session.add(row)
        return row

    def save_state(self, application: Application) -> ApplicationRow:
        row = self.row(application.reference)
        row.state = application.state
        return row

    def save_escort_cards(self, application: Application) -> ApplicationRow:
        """Write the named pilot cars back, which is the one column that moves.

        Everything else about an application is written once and read after
        that. The cards move because they are named late and changed twice,
        and rewriting the whole row for one column would drag the axles and
        the pieces along with it.
        """
        row = self.row(application.reference)
        row.escort_cards = ",".join(application.escort_cards)
        return row

    def in_state(self, state: ApplicationState) -> tuple[ApplicationRow, ...]:
        query = (
            select(ApplicationRow)
            .where(ApplicationRow.state == state)
            .order_by(ApplicationRow.filed, ApplicationRow.reference)
        )
        return tuple(self.session.scalars(query).all())

    def waiting(self) -> tuple[ApplicationRow, ...]:
        return self.in_state(ApplicationState.SUBMITTED)

    def for_carrier(self, usdot: int) -> tuple[ApplicationRow, ...]:
        query = (
            select(ApplicationRow)
            .where(ApplicationRow.usdot == usdot)
            .order_by(ApplicationRow.filed.desc())
        )
        return tuple(self.session.scalars(query).all())

    def filed_between(self, start: date, end: date) -> tuple[ApplicationRow, ...]:
        query = (
            select(ApplicationRow)
            .where(ApplicationRow.filed >= start, ApplicationRow.filed <= end)
            .order_by(ApplicationRow.filed, ApplicationRow.reference)
        )
        return tuple(self.session.scalars(query).all())


class PermitRepository:
    """Permits, by number and by the four questions the telephone asks."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def row(self, number: str) -> PermitRow:
        found = self.session.get(PermitRow, number)
        if found is None:
            raise NotOnFile(f"no permit {number}", number=number)
        return found

    def get(self, number: str) -> Permit:
        return row_to_permit(self.row(number))

    def add(self, permit: Permit) -> PermitRow:
        row = permit_to_row(permit)
        self.session.add(row)
        return row

    def save_state(self, permit: Permit) -> PermitRow:
        row = self.row(str(permit.number))
        row.state = permit.state
        row.void_reason = permit.void_reason
        row.superseded_by = None if permit.superseded_by is None else str(permit.superseded_by)
        return row

    def on_the_road(self, day: date) -> tuple[PermitRow, ...]:
        query = (
            select(PermitRow)
            .where(
                PermitRow.state == PermitState.ISSUED,
                PermitRow.valid_from <= day,
                PermitRow.valid_to >= day,
            )
            .order_by(PermitRow.number)
        )
        return tuple(self.session.scalars(query).all())

    def issued_between(self, start: date, end: date) -> tuple[PermitRow, ...]:
        query = (
            select(PermitRow)
            .where(PermitRow.issued >= start, PermitRow.issued <= end)
            .order_by(PermitRow.issued, PermitRow.number)
        )
        return tuple(self.session.scalars(query).all())

    def for_carrier(self, usdot: int) -> tuple[PermitRow, ...]:
        query = (
            select(PermitRow)
            .where(PermitRow.usdot == usdot)
            .order_by(PermitRow.issued.desc(), PermitRow.number.desc())
        )
        return tuple(self.session.scalars(query).all())

    def expiring(self, day: date, horizon: date) -> tuple[PermitRow, ...]:
        query = (
            select(PermitRow)
            .where(
                PermitRow.state == PermitState.ISSUED,
                PermitRow.valid_to >= day,
                PermitRow.valid_to <= horizon,
            )
            .order_by(PermitRow.valid_to, PermitRow.number)
        )
        return tuple(self.session.scalars(query).all())

    def touching_segment(self, ident: str) -> tuple[PermitRow, ...]:
        """Every permit whose route names a segment, for a closure notice.

        The route is a string, so this filters in Python after a like. That is
        acceptable because closures are rare and the office wants the answer
        rather than a schema change.
        """
        query = select(PermitRow).where(PermitRow.route.like(f"%{ident}%"))
        found = self.session.scalars(query).all()
        return tuple(one for one in found if ident in one.route_idents)
