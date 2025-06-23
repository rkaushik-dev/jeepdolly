"""The bodies that go over the wire, and the one rule about measures.

Every measure crosses as a string. A width is ``"14 ft 6 in"``, a weight is
``"132,400 lb"``, a fee is ``"$188.72"``. Not a number, not a number with a
unit field beside it, a string that reads the way it reads on the permit.

That is a decision worth defending because it looks wrong at first. The reason
is that the client of this interface is a carrier's own software, written by
whoever they could get, and the number that comes back is going onto a screen
in front of a driver. A JSON number invites the client to do arithmetic on it,
and the arithmetic they do will be in feet with a decimal point, and the answer
they show the driver will be a foot different from the permit.

Incoming is the same in reverse. The parsers already accept everything the
office receives on paper, so the interface accepts it too, and one hauler's
software sending ``14'6"`` and another sending ``174`` both work.
"""

from __future__ import annotations

from datetime import date

from pydantic import BaseModel, Field

from ..conditions.library import Condition
from ..domain.axle import Axle, AxleKind
from ..domain.combination import Combination, Unit, UnitKind
from ..domain.commodity import CommodityBook, Piece
from ..domain.envelope import Envelope
from ..domain.stream import AxleStream
from ..errors import Impossible
from ..fees.price import Quote
from ..measure import Inches, Pounds
from ..permits.application import Applicant, Application
from ..permits.permit import Permit


class EnvelopeIn(BaseModel):
    """The box, as the hauler measured it."""

    width: str
    height: str
    length: str
    front_overhang: str = "0"
    rear_overhang: str = "0"

    def to_domain(self) -> Envelope:
        return Envelope.read(
            self.width, self.height, self.length, self.front_overhang, self.rear_overhang
        )


class AxleIn(BaseModel):
    """One axle, with the gap to the axle in front of it."""

    kind: str
    weight: str
    tires: int = 4
    tire_width: str = "11"
    liftable: bool = False
    spacing_before: str | None = None

    def to_domain(self) -> Axle:
        return Axle(
            kind=AxleKind(self.kind),
            weight=Pounds.parse(self.weight),
            tires=self.tires,
            tire_width=Inches.parse(self.tire_width),
            liftable=self.liftable,
        )


class UnitIn(BaseModel):
    """One piece of equipment in the string."""

    kind: str
    axles: int
    plate: str
    tare: str
    make: str = ""
    unit_number: str = ""

    def to_domain(self) -> Unit:
        return Unit(
            kind=UnitKind(self.kind),
            axles=self.axles,
            plate=self.plate,
            tare=Pounds.parse(self.tare),
            make=self.make,
            unit_number=self.unit_number,
        )


class PieceIn(BaseModel):
    """One thing on the trailer."""

    commodity: str
    description: str
    serial: str | None = None


class ApplicantIn(BaseModel):
    """Who to ring about it."""

    name: str
    phone: str
    email: str = ""

    def to_domain(self) -> Applicant:
        return Applicant(self.name, self.phone, self.email)


class ApplicationIn(BaseModel):
    """Everything a hauler sends to ask for a permit."""

    usdot: int
    commodity: str
    origin: str
    destination: str
    start: date
    end: date
    applicant: ApplicantIn
    envelope: EnvelopeIn
    axles: list[AxleIn] = Field(min_length=1)
    units: list[UnitIn] = Field(min_length=1)
    pieces: list[PieceIn] = Field(min_length=1)
    route: list[str] = Field(default_factory=list)
    escort_cards: list[str] = Field(default_factory=list)
    notes: str = ""

    def to_domain(self, reference: str, filed: date, commodities: CommodityBook) -> Application:
        spacings = []
        for position, axle in enumerate(self.axles):
            if position == 0:
                continue
            if axle.spacing_before is None:
                raise Impossible(f"axle {position + 1} does not say how far behind it stands")
            spacings.append(Inches.parse(axle.spacing_before))
        stream = AxleStream(
            tuple(one.to_domain() for one in self.axles),
            tuple(spacings),
        )
        combination = Combination(
            tuple(one.to_domain() for one in self.units), stream, self.envelope.to_domain()
        )
        pieces = tuple(
            Piece(commodities.get(one.commodity), one.description, one.serial)
            for one in self.pieces
        )
        return Application(
            reference=reference,
            usdot=self.usdot,
            commodity=self.commodity,
            pieces=pieces,
            combination=combination,
            origin=self.origin,
            destination=self.destination,
            start=self.start,
            end=self.end,
            applicant=self.applicant.to_domain(),
            filed=filed,
            route=tuple(self.route),
            escort_cards=tuple(self.escort_cards),
            notes=self.notes,
        )


class ConditionOut(BaseModel):
    """One condition as it prints."""

    code: str
    category: str
    text: str

    @classmethod
    def of(cls, condition: Condition) -> ConditionOut:
        return cls(code=condition.code, category=condition.category.value, text=condition.text)


class FeeLineOut(BaseModel):
    """One line of what it cost."""

    description: str
    amount: str


class QuoteOut(BaseModel):
    """What it cost, line by line."""

    lines: list[FeeLineOut]
    total: str
    schedule: str

    @classmethod
    def of(cls, quote: Quote) -> QuoteOut:
        return cls(
            lines=[
                FeeLineOut(description=one.description, amount=str(one.amount))
                for one in quote.lines
            ],
            total=str(quote.total),
            schedule=quote.schedule,
        )


class ApplicationOut(BaseModel):
    """An application as the office holds it."""

    reference: str
    usdot: int
    state: str
    origin: str
    destination: str
    start: date
    end: date
    gross: str
    width: str
    height: str
    length: str
    escort_cards: list[str] = Field(default_factory=list)

    @classmethod
    def of(cls, application: Application) -> ApplicationOut:
        envelope = application.combination.envelope
        return cls(
            reference=application.reference,
            usdot=application.usdot,
            state=application.state.value,
            origin=application.origin,
            destination=application.destination,
            start=application.start,
            end=application.end,
            gross=str(application.gross),
            width=str(envelope.width),
            height=str(envelope.height),
            length=str(envelope.length),
            escort_cards=list(application.escort_cards),
        )


class PermitOut(BaseModel):
    """A permit as it is printed and as it is read at the roadside."""

    number: str
    application: str
    usdot: int
    carrier: str
    state: str
    issued: date
    start: date
    end: date
    origin: str
    destination: str
    route: list[str]
    narrative: str
    gross: str
    distance: str
    conditions: list[ConditionOut]
    fees: QuoteOut

    @classmethod
    def of(cls, permit: Permit) -> PermitOut:
        return cls(
            number=str(permit.number),
            application=permit.application,
            usdot=permit.usdot,
            carrier=permit.carrier_name,
            state=permit.state.value,
            issued=permit.issued,
            start=permit.start,
            end=permit.end,
            origin=permit.origin,
            destination=permit.destination,
            route=list(permit.route),
            narrative=permit.narrative,
            gross=str(permit.gross),
            distance=str(permit.distance),
            conditions=[ConditionOut.of(one) for one in permit.conditions],
            fees=QuoteOut.of(permit.quote),
        )


class AssessmentOut(BaseModel):
    """What the office worked out, whether or not it can be permitted."""

    reference: str
    permittable: bool
    kind: str
    refusals: list[str]
    route: list[str]
    distance: str | None = None
    conditions: list[ConditionOut] = Field(default_factory=list)
    fees: QuoteOut | None = None
