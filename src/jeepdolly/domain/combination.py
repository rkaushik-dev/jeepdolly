"""The combination: the equipment, in order, and the axles it puts down.

An application describes equipment the way a hauler talks about it. A day cab,
a two axle jeep, a nine axle lowboy, a booster. Every one of those is a unit
with a plate and an owner, and the office needs the plate for the permit face
and the axle count to check the stream against.

The combination is where those two descriptions are made to agree. If a hauler
writes a nine axle trailer and then lists eight axles in the stream, the office
has a load that will not be where the permit says it is, and that is refused
here rather than three checks later where the message would be arithmetic.

Order matters and it is the order down the road. The power unit is first, and
a jeep goes between the power unit and the trailer, which is the thing people
get backwards on the form. A booster goes on the back.

Tare is the empty weight of the equipment. Payload is what is left when tare
comes off the gross. The office does not care much about payload except when
it does: a hauler who declares a payload that does not match the piece on the
trailer has usually misread the crane chart, and the counter catches it here.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from ..errors import Impossible
from ..measure import Pounds
from .envelope import Envelope
from .stream import AxleStream


class UnitKind(Enum):
    """A piece of equipment, by what it does in the string."""

    TRACTOR = "tractor"
    TRUCK = "truck"
    LOWBOY = "lowboy"
    RGN = "removable gooseneck"
    FLATBED = "flatbed"
    STEP = "step deck"
    JEEP = "jeep dolly"
    BOOSTER = "booster"

    @property
    def powered(self) -> bool:
        return self in (UnitKind.TRACTOR, UnitKind.TRUCK)

    @property
    def carries_load(self) -> bool:
        return self in (UnitKind.LOWBOY, UnitKind.RGN, UnitKind.FLATBED, UnitKind.STEP)

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Unit:
    """One piece of equipment on the permit face."""

    kind: UnitKind
    axles: int
    plate: str
    tare: Pounds
    make: str = ""
    unit_number: str = ""

    def __post_init__(self) -> None:
        if self.axles < 1:
            raise Impossible(f"a {self.kind} has axles under it")
        if not self.plate.strip():
            raise Impossible(f"a {self.kind} on a permit carries a plate")
        if self.tare.value < 0:
            raise Impossible("equipment does not weigh less than nothing")

    def __str__(self) -> str:
        label = self.unit_number or self.plate
        return f"{self.kind} {label} ({self.axles} axle)"


@dataclass(frozen=True)
class Combination:
    """The whole string, front to back, with the axles it puts on the road."""

    units: tuple[Unit, ...]
    stream: AxleStream
    envelope: Envelope

    def __post_init__(self) -> None:
        if not self.units:
            raise Impossible("a combination has equipment in it")
        if not self.units[0].kind.powered:
            raise Impossible("a combination starts with something that pulls")
        if sum(1 for unit in self.units if unit.kind.powered) > 1:
            raise Impossible("a combination is pulled by one power unit")
        if not any(unit.kind.carries_load for unit in self.units):
            raise Impossible("a combination has something to put the load on")
        declared = sum(unit.axles for unit in self.units)
        if declared != self.stream.count:
            raise Impossible(
                f"the equipment lists {declared} axles and the stream holds {self.stream.count}",
                declared=declared,
                stream=self.stream.count,
            )
        self._check_order()

    def _check_order(self) -> None:
        seen_deck = False
        for unit in self.units[1:]:
            if unit.kind.carries_load:
                seen_deck = True
            elif unit.kind is UnitKind.JEEP and seen_deck:
                raise Impossible("a jeep goes ahead of the trailer, not behind it")
            elif unit.kind is UnitKind.BOOSTER and not seen_deck:
                raise Impossible("a booster goes behind the trailer, not ahead of it")

    @property
    def power_unit(self) -> Unit:
        return self.units[0]

    @property
    def deck(self) -> Unit:
        return next(unit for unit in self.units if unit.kind.carries_load)

    @property
    def jeeps(self) -> tuple[Unit, ...]:
        return tuple(unit for unit in self.units if unit.kind is UnitKind.JEEP)

    @property
    def boosters(self) -> tuple[Unit, ...]:
        return tuple(unit for unit in self.units if unit.kind is UnitKind.BOOSTER)

    @property
    def axle_count(self) -> int:
        return self.stream.count

    @property
    def gross(self) -> Pounds:
        return self.stream.gross

    @property
    def tare(self) -> Pounds:
        running = Pounds(0)
        for unit in self.units:
            running = running + unit.tare
        return running

    @property
    def payload(self) -> Pounds:
        """What the equipment is carrying, which the crane chart has to match."""
        return self.gross - self.tare

    def describe(self) -> str:
        """The string as a hauler would say it out loud."""
        return ", ".join(str(unit.kind) for unit in self.units)

    def plates(self) -> tuple[str, ...]:
        return tuple(unit.plate for unit in self.units)

    def __str__(self) -> str:
        return f"{self.describe()}, {self.axle_count} axles, {self.gross}"
