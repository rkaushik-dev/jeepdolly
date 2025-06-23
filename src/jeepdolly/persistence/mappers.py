"""Domain objects to rows and back, for the two that are not one to one.

Most tables carry their own ``of`` and ``to_domain`` because the mapping is a
rename. Applications and permits are not: an application is five tables and a
permit is three, and the assembly is worth having in one file where both
directions sit next to each other and can be read against each other.

The rule this file works to is that the round trip is exact. An application
written and read back is equal to the one that went in, and the tests hold that
directly rather than by checking fields one at a time. Anything that cannot
survive the round trip does not belong in the row: it belongs on a report.

Note what is not here. There is no lazy mapping, no proxy object and no row
that pretends to be a domain object. The domain objects know nothing about the
database and the rows know nothing about the rules, and this file is the only
thing that has heard of both.
"""

from __future__ import annotations

from ..conditions.library import Category, Condition
from ..domain.axle import Axle
from ..domain.combination import Combination, Unit
from ..domain.commodity import CommodityBook, Piece
from ..domain.envelope import Envelope
from ..domain.stream import AxleStream
from ..errors import Impossible
from ..fees.price import Line, Quote
from ..permits.application import Applicant, Application
from ..permits.numbering import PermitNumber
from ..permits.permit import Permit
from .models.applications import (
    ApplicationAxleRow,
    ApplicationPieceRow,
    ApplicationRow,
    ApplicationUnitRow,
)
from .models.permits import FeeLineRow, PermitConditionRow, PermitRow


def application_to_row(application: Application) -> ApplicationRow:
    """One application into the five rows that hold it."""
    envelope = application.combination.envelope
    row = ApplicationRow(
        reference=application.reference,
        usdot=application.usdot,
        commodity=application.commodity,
        origin=application.origin,
        destination=application.destination,
        travel_start=application.start,
        travel_end=application.end,
        filed=application.filed,
        state=application.state,
        applicant_name=application.applicant.name,
        applicant_phone=application.applicant.phone,
        applicant_email=application.applicant.email,
        route=",".join(application.route),
        escort_cards=",".join(application.escort_cards),
        notes=application.notes,
        width=envelope.width,
        height=envelope.height,
        length=envelope.length,
        front_overhang=envelope.front_overhang,
        rear_overhang=envelope.rear_overhang,
    )
    stream = application.combination.stream
    for position, axle in enumerate(stream.axles):
        row.axles.append(
            ApplicationAxleRow(
                position=position,
                kind=axle.kind,
                weight=axle.weight,
                tires=axle.tires,
                tire_width=axle.tire_width,
                liftable=axle.liftable,
                spacing_before=None if position == 0 else stream.spacings[position - 1],
            )
        )
    for position, unit in enumerate(application.combination.units):
        row.units.append(
            ApplicationUnitRow(
                position=position,
                kind=unit.kind,
                axles=unit.axles,
                plate=unit.plate,
                tare=unit.tare,
                make=unit.make,
                unit_number=unit.unit_number,
            )
        )
    for position, piece in enumerate(application.pieces):
        row.pieces.append(
            ApplicationPieceRow(
                position=position,
                commodity=piece.commodity.code,
                description=piece.description,
                serial=piece.serial,
            )
        )
    return row


def row_to_application(row: ApplicationRow, commodities: CommodityBook) -> Application:
    """The five rows back into one application."""
    if not row.axles:
        raise Impossible(f"application {row.reference} has no axles on file")
    axles = tuple(
        Axle(
            kind=one.kind,
            weight=one.weight,
            tires=one.tires,
            tire_width=one.tire_width,
            liftable=one.liftable,
        )
        for one in row.axles
    )
    spacings = tuple(one.spacing_before for one in row.axles[1:] if one.spacing_before is not None)
    stream = AxleStream(axles, spacings)
    units = tuple(
        Unit(
            kind=one.kind,
            axles=one.axles,
            plate=one.plate,
            tare=one.tare,
            make=one.make,
            unit_number=one.unit_number,
        )
        for one in row.units
    )
    envelope = Envelope(row.width, row.height, row.length, row.front_overhang, row.rear_overhang)
    pieces = tuple(
        Piece(commodities.get(one.commodity), one.description, one.serial) for one in row.pieces
    )
    return Application(
        reference=row.reference,
        usdot=row.usdot,
        commodity=row.commodity,
        pieces=pieces,
        combination=Combination(units, stream, envelope),
        origin=row.origin,
        destination=row.destination,
        start=row.travel_start,
        end=row.travel_end,
        applicant=Applicant(row.applicant_name, row.applicant_phone, row.applicant_email),
        filed=row.filed,
        route=row.route_idents,
        escort_cards=row.cards,
        state=row.state,
        notes=row.notes or "",
    )


def permit_to_row(permit: Permit) -> PermitRow:
    """One permit into the three rows that hold it."""
    row = PermitRow(
        number=str(permit.number),
        series=permit.number.series.value,
        application=permit.application,
        usdot=permit.usdot,
        carrier_name=permit.carrier_name,
        issued=permit.issued,
        valid_from=permit.start,
        valid_to=permit.end,
        origin=permit.origin,
        destination=permit.destination,
        route=",".join(permit.route),
        narrative=permit.narrative,
        gross=permit.gross,
        width=permit.envelope.width,
        height=permit.envelope.height,
        length=permit.envelope.length,
        front_overhang=permit.envelope.front_overhang,
        rear_overhang=permit.envelope.rear_overhang,
        distance=permit.distance,
        state=permit.state,
        void_reason=permit.void_reason,
        superseded_by=None if permit.superseded_by is None else str(permit.superseded_by),
        supersedes=None if permit.supersedes is None else str(permit.supersedes),
        schedule=permit.quote.schedule,
    )
    for position, condition in enumerate(permit.conditions):
        row.conditions.append(
            PermitConditionRow(
                position=position,
                code=condition.code,
                category=condition.category.value,
                body=condition.text,
                print_order=condition.order,
            )
        )
    for position, line in enumerate(permit.quote.lines):
        row.fees.append(
            FeeLineRow(position=position, description=line.description, amount=line.amount)
        )
    return row


def row_to_permit(row: PermitRow) -> Permit:
    """The three rows back into one permit."""
    conditions = tuple(
        Condition(one.code, Category(one.category), one.body, one.print_order)
        for one in row.conditions
    )
    quote = Quote(
        tuple(Line(one.description, one.amount) for one in row.fees),
        row.schedule or "",
    )
    return Permit(
        number=PermitNumber.parse(row.number),
        application=row.application,
        usdot=row.usdot,
        carrier_name=row.carrier_name,
        issued=row.issued,
        start=row.valid_from,
        end=row.valid_to,
        origin=row.origin,
        destination=row.destination,
        route=row.route_idents,
        narrative=row.narrative,
        gross=row.gross,
        envelope=Envelope(row.width, row.height, row.length, row.front_overhang, row.rear_overhang),
        distance=row.distance,
        conditions=conditions,
        quote=quote,
        state=row.state,
        void_reason=row.void_reason or "",
        superseded_by=None if row.superseded_by is None else PermitNumber.parse(row.superseded_by),
        supersedes=None if row.supersedes is None else PermitNumber.parse(row.supersedes),
    )
