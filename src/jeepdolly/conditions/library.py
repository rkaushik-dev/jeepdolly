"""The condition library: every sentence that can print on a permit.

A permit condition is a legal instrument. It is what an enforcement officer
reads at the roadside and it is what a court reads afterwards, so the office
holds the words in one place, gives each one a code, and never writes one
freehand at the counter.

Every condition has a code, and the code is what everything else refers to.
The rules that fire produce codes and facts. Nothing upstream of this file
holds a sentence, and nothing downstream of it invents one.

Conditions print grouped by category and ordered within the group, because a
driver reads them in the cab and wants the ones about today near the top. The
order is on the record rather than in the printing code, so a change to the
running order is a change to a table.

A condition may take facts. The text carries named slots and the caller hands
in a dictionary. A missing slot is a refusal rather than a blank, because a
permit that reads cross the bridge at is worse than no permit at all.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any

from ..errors import Conflict, Impossible, NotOnFile


class Category(Enum):
    """Which part of the permit face a condition prints under."""

    TRAVEL = "travel"
    ROUTE = "route"
    STRUCTURE = "structure"
    ESCORT = "escort"
    MARKING = "marking"
    EQUIPMENT = "equipment"
    GENERAL = "general"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class ConditionText:
    """One sentence, its code, and where it prints."""

    code: str
    category: Category
    text: str
    order: int = 50

    def __post_init__(self) -> None:
        if not self.code.strip():
            raise Impossible("a condition carries a code")
        if not self.text.strip():
            raise Impossible(f"condition {self.code} has no words in it")

    def render(self, facts: dict[str, Any] | None = None) -> str:
        """Fill the slots, refusing rather than printing a gap."""
        try:
            return self.text.format(**(facts or {}))
        except KeyError as missing:
            raise Impossible(
                f"condition {self.code} wants {missing.args[0]} and was not given it",
                code=self.code,
                wants=missing.args[0],
            ) from None

    def __str__(self) -> str:
        return f"{self.code} ({self.category})"


@dataclass(frozen=True)
class Condition:
    """A rendered condition, ready to print."""

    code: str
    category: Category
    text: str
    order: int

    def __str__(self) -> str:
        return self.text


class ConditionLibrary:
    """Every condition the office can print."""

    def __init__(self) -> None:
        self._by_code: dict[str, ConditionText] = {}

    def add(self, condition: ConditionText) -> ConditionText:
        if condition.code in self._by_code:
            raise Conflict(f"condition {condition.code} is already in the library")
        self._by_code[condition.code] = condition
        return condition

    def get(self, code: str) -> ConditionText:
        try:
            return self._by_code[code]
        except KeyError:
            raise NotOnFile(f"no condition {code}", code=code) from None

    def render(self, code: str, facts: dict[str, Any] | None = None) -> Condition:
        found = self.get(code)
        return Condition(found.code, found.category, found.render(facts), found.order)

    def of_category(self, category: Category) -> tuple[ConditionText, ...]:
        found = [one for one in self._by_code.values() if one.category is category]
        return tuple(sorted(found, key=lambda one: (one.order, one.code)))

    def codes(self) -> tuple[str, ...]:
        return tuple(sorted(self._by_code))

    def __len__(self) -> int:
        return len(self._by_code)

    def __contains__(self, code: object) -> bool:
        return code in self._by_code


def sort_conditions(conditions: tuple[Condition, ...]) -> tuple[Condition, ...]:
    """Group by category in printing order, then by the order on the record."""
    printing = list(Category)
    return tuple(
        sorted(conditions, key=lambda one: (printing.index(one.category), one.order, one.code))
    )


STANDARD_TEXTS = [
    ("T-01", Category.TRAVEL, "Valid from {start} to {end} inclusive.", 10),
    ("T-02", Category.TRAVEL, "Daylight hours only, half an hour either side.", 20),
    ("T-03", Category.TRAVEL, "No movement Saturday, Sunday or a legal holiday.", 30),
    ("T-04", Category.TRAVEL, "No movement during the {holiday} travel ban.", 40),
    ("T-05", Category.TRAVEL, "No movement on {segment} between {curfew}.", 50),
    ("T-06", Category.TRAVEL, "Stop and park when the wind reaches {wind} mph.", 60),
    ("T-07", Category.TRAVEL, "Stop when visibility falls below five hundred feet.", 70),
    ("R-01", Category.ROUTE, "Route as written. Any deviation voids this permit.", 10),
    ("R-02", Category.ROUTE, "{segment} is posted at {limit} while order {order} runs.", 20),
    ("R-03", Category.ROUTE, "Do not enter {segment}. Use {detour}.", 30),
    ("R-04", Category.ROUTE, "Obey the posted clearance of {clearance} at {structure}.", 40),
    ("R-05", Category.ROUTE, "Stop traffic both ways to turn onto {segment} at {place}.", 50),
    ("R-06", Category.ROUTE, "The turn at {place} allows {allowed}. Walk it first.", 60),
    ("U-01", Category.ROUTE, "Line lift at {place}. Ring {owner} on {telephone}.", 70),
    ("U-02", Category.ROUTE, "Do not move until every line lift is confirmed.", 80),
    ("S-01", Category.STRUCTURE, "Cross {structure} alone with no other vehicle on it.", 10),
    ("S-02", Category.STRUCTURE, "Cross {structure} at walking pace.", 20),
    ("S-03", Category.STRUCTURE, "Cross {structure} centred on the roadway.", 30),
    ("S-04", Category.STRUCTURE, "Do not stop on {structure} for any reason.", 40),
    ("E-01", Category.ESCORT, "{count} certified pilot car ahead of the load.", 10),
    ("E-02", Category.ESCORT, "{count} certified pilot car behind the load.", 20),
    ("E-03", Category.ESCORT, "Front pilot car carries a height pole set one inch high.", 30),
    ("E-04", Category.ESCORT, "Police escort, arranged by the carrier.", 40),
    ("E-05", Category.ESCORT, "Pilot cars for this move: {cards}.", 50),
    ("E-06", Category.ESCORT, "Tell the office who is piloting before the load moves.", 60),
    ("M-01", Category.MARKING, "OVERSIZE LOAD banners front and rear.", 10),
    ("M-02", Category.MARKING, "{count} eighteen inch red flags on corners and widest points.", 20),
    ("M-03", Category.MARKING, "{count} amber lamps in place of flags.", 30),
    ("M-04", Category.MARKING, "{count} amber beacons on the power unit.", 40),
    ("Q-01", Category.EQUIPMENT, "Axle spacings as declared. Any change voids this permit.", 10),
    ("Q-02", Category.EQUIPMENT, "All liftable axles down and carrying for the whole move.", 20),
    ("Q-03", Category.EQUIPMENT, "Tires as declared, no fewer and no narrower.", 30),
    ("G-01", Category.GENERAL, "Carry this permit in the power unit and produce it on demand.", 10),
    ("G-02", Category.GENERAL, "The carrier is liable for damage to any structure.", 20),
    ("G-03", Category.GENERAL, "This permit does not waive any other law or local order.", 30),
]


def standard_library() -> ConditionLibrary:
    """The library as it stands, which is what the printer reads."""
    library = ConditionLibrary()
    for code, category, text, order in STANDARD_TEXTS:
        library.add(ConditionText(code, category, text, order))
    return library
