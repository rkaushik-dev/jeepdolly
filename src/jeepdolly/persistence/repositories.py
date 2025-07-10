"""Repositories: the queries the office actually runs, named after the asking.

A repository here is not a generic collection with a filter method. It is the
list of questions one part of the office asks about one kind of record, each
with a name a clerk would recognise, and nothing else. That keeps the SQL in
one place per subject and it keeps the odd query honest: if a report needs
something strange, it gets a method with a name that says what it is for, and
the strangeness is visible in review rather than assembled in a service.

Every repository takes a session and holds no state. The unit of work owns the
session and the transaction, and a repository is a way of writing queries
against whichever session it was handed.

Reads hand back domain objects where there is one, and rows where the caller is
going to edit them. That split is deliberate: a caller with a domain object
cannot accidentally save half of it.
"""

from __future__ import annotations

from datetime import date, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..domain.carrier import Carrier
from ..domain.commodity import CommodityBook
from ..enforcement.citation import Citation, CitationState
from ..enforcement.penalty import REPEAT_DAYS
from ..errors import Conflict, NotOnFile
from ..escorts.register import Company, Operator
from ..permits.application import Application, ApplicationState
from ..permits.permit import Permit, PermitState
from .mappers import application_to_row, permit_to_row, row_to_application, row_to_permit
from .models.applications import ApplicationRow
from .models.carriers import CarrierRow
from .models.enforcement import CitationRow
from .models.escorts import PilotCardRow, PilotCompanyRow
from .models.network import RestrictionRow, SegmentRow
from .models.permits import PermitRow
from .models.reference import CommodityRow
from .models.structures import StructureRow


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


class CitationRepository:
    """Citations, by number, by carrier and by what is still owing.

    The prior count is the interesting method. It drives the repeat
    multiplier, and it counts only citations that stuck: a dismissed one and a
    voided one are not prior offences, because the carrier was found not to
    have committed them. A contested one that has not been heard yet does
    count, which is the office's reading of the statute and is the one thing
    in this file a lawyer has ever argued with.
    """

    def __init__(self, session: Session) -> None:
        self.session = session

    def row(self, number: str) -> CitationRow:
        found = self.session.get(CitationRow, number)
        if found is None:
            raise NotOnFile(f"no citation {number}", number=number)
        return found

    def get(self, number: str) -> Citation:
        return self.row(number).to_domain()

    def add(self, citation: Citation, schedule: date) -> CitationRow:
        if self.session.get(CitationRow, citation.number) is not None:
            raise Conflict(f"citation {citation.number} is already on file")
        row = CitationRow.of(citation, schedule)
        self.session.add(row)
        return row

    def save_state(self, citation: Citation, reason: str = "") -> CitationRow:
        """Write the state back, and the reason when the state is a closing one."""
        row = self.row(citation.number)
        row.state = citation.state
        if reason:
            row.closing_reason = reason
        return row

    def for_carrier(self, usdot: int) -> tuple[CitationRow, ...]:
        query = (
            select(CitationRow)
            .where(CitationRow.usdot == usdot)
            .order_by(CitationRow.written.desc(), CitationRow.number)
        )
        return tuple(self.session.scalars(query).all())

    def owing(self) -> tuple[CitationRow, ...]:
        """Everything still expecting money, oldest first, which is the chase list."""
        query = (
            select(CitationRow)
            .where(
                CitationRow.state.in_(
                    [CitationState.WRITTEN, CitationState.CONTESTED, CitationState.UPHELD]
                )
            )
            .order_by(CitationRow.written, CitationRow.number)
        )
        return tuple(self.session.scalars(query).all())

    def written_between(self, start: date, end: date) -> tuple[CitationRow, ...]:
        query = (
            select(CitationRow)
            .where(CitationRow.written >= start, CitationRow.written <= end)
            .order_by(CitationRow.written, CitationRow.number)
        )
        return tuple(self.session.scalars(query).all())

    def priors(self, usdot: int, before: date, within_days: int = REPEAT_DAYS) -> int:
        """How many citations stuck against this carrier in the window.

        The window ends the day before the one asked about, so a citation
        written today is never a prior offence against itself. Two written on
        the same day are both first offences, which is generous and is what
        the office has always done: the officer wrote them a minute apart and
        the carrier had no chance to mend anything in between.
        """
        opened = before - timedelta(days=within_days)
        query = select(CitationRow).where(
            CitationRow.usdot == usdot,
            CitationRow.written >= opened,
            CitationRow.written < before,
            CitationRow.state.notin_([CitationState.DISMISSED, CitationState.VOID]),
        )
        return len(self.session.scalars(query).all())

    def count(self) -> int:
        return len(self.session.scalars(select(CitationRow)).all())


class PilotRepository:
    """Pilot car companies and the cards the office has issued.

    A card is never edited except to be suspended or lifted. A renewal is a
    new card, which is why there is no method here that moves an expiry date.
    """

    def __init__(self, session: Session) -> None:
        self.session = session

    def company_row(self, ident: str) -> PilotCompanyRow:
        found = self.session.get(PilotCompanyRow, ident)
        if found is None:
            raise NotOnFile(f"no pilot car company {ident}", ident=ident)
        return found

    def card_row(self, card: str) -> PilotCardRow:
        found = self.session.get(PilotCardRow, card)
        if found is None:
            raise NotOnFile(f"no pilot car card {card}", card=card)
        return found

    def companies(self) -> tuple[PilotCompanyRow, ...]:
        query = select(PilotCompanyRow).order_by(PilotCompanyRow.ident)
        return tuple(self.session.scalars(query).all())

    def cards(self, company: str | None = None) -> tuple[PilotCardRow, ...]:
        query = select(PilotCardRow).order_by(PilotCardRow.card)
        if company is not None:
            query = query.where(PilotCardRow.company_ident == company)
        return tuple(self.session.scalars(query).all())

    def add_company(self, company: Company) -> PilotCompanyRow:
        if self.session.get(PilotCompanyRow, company.ident) is not None:
            raise Conflict(f"pilot car company {company.ident} is already on file")
        row = PilotCompanyRow.of(company)
        self.session.add(row)
        return row

    def certify(self, operator: Operator) -> PilotCardRow:
        """Issue a card, refusing one whose company is not on file."""
        if self.session.get(PilotCardRow, operator.card) is not None:
            raise Conflict(f"pilot car card {operator.card} is already on file")
        self.company_row(operator.company)
        row = PilotCardRow.of(operator)
        self.session.add(row)
        return row

    def suspend_company(self, ident: str, suspended: bool = True) -> PilotCompanyRow:
        row = self.company_row(ident)
        row.suspended = suspended
        return row

    def suspend_card(self, card: str, suspended: bool = True) -> PilotCardRow:
        row = self.card_row(card)
        row.suspended = suspended
        return row

    def expiring(self, start: date, end: date) -> tuple[PilotCardRow, ...]:
        """Cards running out between two days, soonest first."""
        query = (
            select(PilotCardRow)
            .where(
                PilotCardRow.suspended.is_(False),
                PilotCardRow.expires >= start,
                PilotCardRow.expires <= end,
            )
            .order_by(PilotCardRow.expires, PilotCardRow.card)
        )
        return tuple(self.session.scalars(query).all())


class CommodityRepository:
    """The commodity table, read whole because it is small and read often."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def book(self, on: date | None = None) -> CommodityBook:
        """Every live commodity, as the book the rules read."""
        book = CommodityBook()
        for row in self.session.scalars(select(CommodityRow).order_by(CommodityRow.code)):
            if on is not None and row.retired is not None and row.retired <= on:
                continue
            book.add(row.to_domain())
        return book

    def retire(self, code: str, on: date) -> CommodityRow:
        row = self.session.get(CommodityRow, code)
        if row is None:
            raise NotOnFile(f"no commodity {code}", code=code)
        row.retired = on
        return row


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


class NetworkRepository:
    """The road log and what is wrong with it."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def segment(self, ident: str) -> SegmentRow:
        found = self.session.get(SegmentRow, ident)
        if found is None:
            raise NotOnFile(f"no segment {ident}", segment=ident)
        return found

    def segments(self) -> tuple[SegmentRow, ...]:
        return tuple(self.session.scalars(select(SegmentRow).order_by(SegmentRow.ident)).all())

    def in_district(self, district: int) -> tuple[SegmentRow, ...]:
        query = select(SegmentRow).where(SegmentRow.district == district)
        return tuple(self.session.scalars(query.order_by(SegmentRow.ident)).all())

    def restrictions_on(self, ident: str, day: date) -> tuple[RestrictionRow, ...]:
        query = select(RestrictionRow).where(RestrictionRow.segment_ident == ident)
        found = self.session.scalars(query).all()
        return tuple(one for one in found if _in_force(one, day))

    def structure(self, ident: str) -> StructureRow:
        found = self.session.get(StructureRow, ident)
        if found is None:
            raise NotOnFile(f"no structure {ident}", structure=ident)
        return found

    def structures_on(self, ident: str) -> tuple[StructureRow, ...]:
        query = (
            select(StructureRow)
            .where(StructureRow.segment_ident == ident)
            .order_by(StructureRow.ident)
        )
        return tuple(self.session.scalars(query).all())

    def posted_structures(self) -> tuple[StructureRow, ...]:
        query = (
            select(StructureRow)
            .where(StructureRow.posted.is_not(None))
            .order_by(StructureRow.ident)
        )
        return tuple(self.session.scalars(query).all())


def _in_force(restriction: RestrictionRow, day: date) -> bool:
    if restriction.effective is not None and day < restriction.effective:
        return False
    return not (restriction.expires is not None and day > restriction.expires)
