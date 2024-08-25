"""What is on the trailer, and whether it had to be.

The first question the counter asks is not how heavy. It is whether the load
could have been made legal by loading it differently. A permit is an exception
granted because there was no other way to move the thing, so a load that could
have gone in two trips at legal weight does not get one.

That is the divisibility test and it decides more applications than the
weight tables do. An excavator is one piece and cannot be cut in half. Forty
tons of gravel is forty tons of gravel and could have been two loads, so the
answer is no, and the hauler who argues that his customer wants it in one trip
is told the same thing every time.

There are exceptions, and they are exceptions in the law rather than
judgements at the counter, so they are on the commodity record. Round wood,
baled forage and a few others move under a standing allowance on designated
routes at a reduced overweight, because the legislature decided that the
alternative was another thousand trucks on the same roads. A commodity that
carries an allowance is still divisible. It just has somewhere else to go.

A commodity also carries the properties that matter later. Something that
cannot be tipped past a few degrees needs the route checked for superelevation.
Something with a high centre of gravity is refused a wind exemption. These are
flags on the record and the rules that read them live further along.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum

from ..errors import Conflict, Impossible, NotOnFile

#: The word that goes on the face of the permit, in the office's own shorthand.
DESCRIPTION_LENGTH = 60


class Family(Enum):
    """The broad kind of thing, which is how the fee schedule groups them."""

    MACHINERY = "machinery"
    STRUCTURAL = "structural"
    VESSEL = "vessel"
    BUILDING = "building"
    FOREST = "forest"
    AGRICULTURAL = "agricultural"
    BULK = "bulk"
    UTILITY = "utility"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Commodity:
    """One line of the commodity table.

    ``divisible`` says the load could have been split. ``allowance`` names the
    standing order that lets a divisible load move anyway, and it is only
    meaningful on a divisible commodity.
    """

    code: str
    description: str
    family: Family
    divisible: bool = False
    allowance: str | None = None
    tip_limit_degrees: int | None = None
    high_centre: bool = False

    def __post_init__(self) -> None:
        if not self.code or not self.code.isupper():
            raise Impossible(f"commodity code {self.code!r} is not a code")
        if len(self.description) > DESCRIPTION_LENGTH:
            raise Impossible(f"description of {self.code} will not fit on a permit")
        if self.allowance and not self.divisible:
            raise Impossible(f"{self.code} is not divisible and needs no allowance")
        if self.tip_limit_degrees is not None and not 1 <= self.tip_limit_degrees <= 45:
            raise Impossible(f"tip limit on {self.code} is not a plausible angle")

    @property
    def permittable(self) -> bool:
        """Whether an oversize or overweight permit can be written at all."""
        return not self.divisible or self.allowance is not None

    @property
    def restricted_to_allowance(self) -> bool:
        """A divisible load moving only because an allowance says it may."""
        return self.divisible and self.allowance is not None

    def __str__(self) -> str:
        return f"{self.code} {self.description}"


class CommodityBook:
    """The commodity table, which the counter reads from and nobody edits live."""

    def __init__(self) -> None:
        self._by_code: dict[str, Commodity] = {}

    def add(self, commodity: Commodity) -> Commodity:
        if commodity.code in self._by_code:
            raise Conflict(f"commodity {commodity.code} is already on file")
        self._by_code[commodity.code] = commodity
        return commodity

    def get(self, code: str) -> Commodity:
        try:
            return self._by_code[code]
        except KeyError:
            raise NotOnFile(f"no commodity {code}", code=code) from None

    def has(self, code: str) -> bool:
        return code in self._by_code

    def in_family(self, family: Family) -> tuple[Commodity, ...]:
        found = [item for item in self._by_code.values() if item.family is family]
        return tuple(sorted(found, key=lambda item: item.code))

    def under_allowance(self, allowance: str) -> tuple[Commodity, ...]:
        found = [item for item in self._by_code.values() if item.allowance == allowance]
        return tuple(sorted(found, key=lambda item: item.code))

    def codes(self) -> tuple[str, ...]:
        return tuple(sorted(self._by_code))

    def __len__(self) -> int:
        return len(self._by_code)

    def __contains__(self, code: object) -> bool:
        return code in self._by_code


@dataclass(frozen=True)
class Piece:
    """One thing on the trailer, because a permit may name several."""

    commodity: Commodity
    description: str
    serial: str | None = None
    tags: tuple[str, ...] = field(default_factory=tuple)

    def __post_init__(self) -> None:
        if not self.description.strip():
            raise Impossible("a piece is described or it is not on the permit")

    def __str__(self) -> str:
        if self.serial:
            return f"{self.description} ({self.serial})"
        return self.description


def standard_book() -> CommodityBook:
    """The table as it stands, which is the one the tests and the counter use."""
    book = CommodityBook()
    for commodity in (
        Commodity("EXCV", "excavator or shovel", Family.MACHINERY),
        Commodity("DOZR", "crawler dozer", Family.MACHINERY),
        Commodity("CRNE", "mobile crane", Family.MACHINERY, high_centre=True),
        Commodity("BEAM", "structural steel beam", Family.STRUCTURAL),
        Commodity("GIRD", "bridge girder", Family.STRUCTURAL, tip_limit_degrees=5),
        Commodity("TANK", "pressure vessel", Family.VESSEL, tip_limit_degrees=8),
        Commodity("BOAT", "boat on a cradle", Family.VESSEL, high_centre=True),
        Commodity("MODU", "modular building section", Family.BUILDING, high_centre=True),
        Commodity("HOUS", "manufactured home", Family.BUILDING, high_centre=True),
        Commodity("XFMR", "power transformer", Family.UTILITY, tip_limit_degrees=3),
        Commodity("POLE", "utility pole", Family.UTILITY),
        Commodity("WOOD", "round wood", Family.FOREST, divisible=True, allowance="forest products"),
        Commodity("CHIP", "wood chips", Family.FOREST, divisible=True),
        Commodity("HAYB", "baled forage", Family.AGRICULTURAL, divisible=True, allowance="forage"),
        Commodity("POTA", "potatoes in bulk", Family.AGRICULTURAL, divisible=True),
        Commodity("GRVL", "gravel or aggregate", Family.BULK, divisible=True),
        Commodity("LIQD", "liquid in bulk", Family.BULK, divisible=True, high_centre=True),
    ):
        book.add(commodity)
    return book
