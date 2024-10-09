"""What is wrong with a piece of road, hanging off the segment it is wrong on.

The road log changes twice a year. Restrictions change every week, so they are
kept apart from it and joined by segment ident.

Six kinds cover everything the office writes down.

A clearance is a height, under a bridge or a sign gantry or a set of wires. It
is the most common reason a route is refused and the one with the least room to
argue: the number is measured, and the load is measured, and one is bigger.

A width restriction is a narrow structure, usually an old bridge with a curb
either side, sometimes a village street with buildings on it.

A weight restriction is a posted structure. The number is the whole vehicle
weight the structure will carry, and it is posted because an engineer rated it
and did not like the answer.

A seasonal restriction is a road that goes soft in the spring. It does not
carry a number of its own: it carries the region the thaw order names, and the
number comes off whichever order is in force.

A curfew is a time of day, on days of the week. Rush hour through a village,
school buses on a route with no shoulder.

A closure is a road that is not there this month. Bridge work, a culvert, a
washout. It carries dates and a detour note, and no load of any size gets past
it.

Everything here answers ``applies_on`` for a date and nothing here decides
anything about a load. The deciding is done by the evaluator, which knows about
loads, and keeping it out of this file is what lets the same restriction be
read by the router, the fee schedule and the printed conditions.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, time
from enum import Enum

from ..errors import Conflict, Impossible
from ..measure import Inches, Pounds


class RestrictionKind(Enum):
    """What sort of trouble this is."""

    CLEARANCE = "clearance"
    WIDTH = "width"
    WEIGHT = "weight"
    SEASONAL = "seasonal"
    CURFEW = "curfew"
    CLOSURE = "closure"

    @property
    def stops_everything(self) -> bool:
        return self is RestrictionKind.CLOSURE

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Curfew:
    """Days of the week and a time of day, as the order is written."""

    days: frozenset[int]
    start: time
    end: time

    def __post_init__(self) -> None:
        if not self.days:
            raise Impossible("a curfew names the days it runs on")
        if any(day not in range(7) for day in self.days):
            raise Impossible("a curfew names days of the week")
        if self.start >= self.end:
            raise Impossible("a curfew ends after it starts")

    def covers(self, when: date, clock: time) -> bool:
        return when.weekday() in self.days and self.start <= clock <= self.end

    def __str__(self) -> str:
        names = "".join("MTWTFSS"[day] for day in sorted(self.days))
        return f"{names} {self.start:%H:%M} to {self.end:%H:%M}"


@dataclass(frozen=True)
class Restriction:
    """One thing wrong with one segment, for a while or for good."""

    ident: str
    segment: str
    kind: RestrictionKind
    note: str
    height: Inches | None = None
    width: Inches | None = None
    weight: Pounds | None = None
    region: str | None = None
    curfew: Curfew | None = None
    effective: date | None = None
    expires: date | None = None

    def __post_init__(self) -> None:
        needed = {
            RestrictionKind.CLEARANCE: self.height,
            RestrictionKind.WIDTH: self.width,
            RestrictionKind.WEIGHT: self.weight,
            RestrictionKind.SEASONAL: self.region,
            RestrictionKind.CURFEW: self.curfew,
        }.get(self.kind, "closure needs nothing")
        if needed is None:
            raise Impossible(f"a {self.kind} restriction carries a {self.kind} to compare against")
        if self.effective and self.expires and self.expires < self.effective:
            raise Impossible(f"restriction {self.ident} lifts before it takes effect")

    def applies_on(self, day: date) -> bool:
        if self.effective and day < self.effective:
            return False
        return not (self.expires and day > self.expires)

    def permanent(self) -> bool:
        return self.effective is None and self.expires is None

    def __str__(self) -> str:
        limit = self.height or self.width or self.weight or self.curfew or self.region or ""
        joined = f" {limit}" if limit != "" else ""
        return f"{self.segment} {self.kind}{joined}: {self.note}"


class RestrictionBook:
    """Every restriction on file, asked for by segment and by day."""

    def __init__(self) -> None:
        self._by_ident: dict[str, Restriction] = {}
        self._by_segment: dict[str, list[str]] = {}

    def add(self, restriction: Restriction) -> Restriction:
        if restriction.ident in self._by_ident:
            raise Conflict(f"restriction {restriction.ident} is already on file")
        self._by_ident[restriction.ident] = restriction
        self._by_segment.setdefault(restriction.segment, []).append(restriction.ident)
        return restriction

    def lift(self, ident: str) -> Restriction:
        """Take one off the book, which happens when the work finishes early."""
        if ident not in self._by_ident:
            raise Conflict(f"no restriction {ident} to lift")
        restriction = self._by_ident.pop(ident)
        self._by_segment[restriction.segment].remove(ident)
        return restriction

    def on_segment(self, segment: str, day: date) -> tuple[Restriction, ...]:
        idents = self._by_segment.get(segment, [])
        found = [self._by_ident[one] for one in idents]
        return tuple(one for one in found if one.applies_on(day))

    def of_kind(self, kind: RestrictionKind, day: date) -> tuple[Restriction, ...]:
        found = [one for one in self._by_ident.values() if one.kind is kind and one.applies_on(day)]
        return tuple(sorted(found, key=lambda one: one.ident))

    def closures(self, day: date) -> tuple[Restriction, ...]:
        return self.of_kind(RestrictionKind.CLOSURE, day)

    def segments_restricted(self, day: date) -> tuple[str, ...]:
        found = {one.segment for one in self._by_ident.values() if one.applies_on(day)}
        return tuple(sorted(found))

    def __len__(self) -> int:
        return len(self._by_ident)
