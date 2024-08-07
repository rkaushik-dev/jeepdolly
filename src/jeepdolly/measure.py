"""Whole units, because a permit is written in whole units.

A permit reads 14 ft 6 in and 132,400 lb. It never reads 14.5 ft, and that is
not a matter of taste. An inch of height is the difference between a load that
clears the Sebasticook overpass and a load that takes the deck off it, so an
inch is a thing this system can name and a fraction of one is not.

Three measures cover the system.

``Inches`` is a size on the envelope: width, height, length, overhang. Held as
whole inches. Whatever the hauler writes on the application, feet and inches or
a bare count of inches, parses back to the same integer.

``Pounds`` is a weight, on an axle or a group or the whole combination. Held as
whole pounds. Tons happen in conversation and never in storage.

``Miles`` is a distance along the network. Held in hundredths of a mile,
because the state road log is published in hundredths and a permit is priced
per mile off that log.

All three are frozen, ordered, and closed under the arithmetic the office
actually does. None of them will add itself to a bare integer: a width plus 6
is a question about what 6 means, and the answer belongs at the call site where
somebody knows.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

INCHES_PER_FOOT = 12
POUNDS_PER_TON = 2000
HUNDREDTHS_PER_MILE = 100

_FEET_INCHES = re.compile(
    r"""^\s*
    (?P<feet>\d+)
    \s*(?:'|ft\.?|feet)
    \s*
    (?:(?P<inches>\d+)\s*(?:"|in\.?|inches)?)?
    \s*$""",
    re.VERBOSE | re.IGNORECASE,
)
_DASHED = re.compile(r"^\s*(?P<feet>\d+)\s*-\s*(?P<inches>\d+)\s*$")
_BARE = re.compile(r"^\s*(?P<inches>\d+)\s*(?:\"|in\.?|inches)?\s*$", re.IGNORECASE)


@dataclass(frozen=True, order=True)
class Inches:
    """A size on the envelope, in whole inches."""

    value: int

    def __post_init__(self) -> None:
        if not isinstance(self.value, int) or isinstance(self.value, bool):
            raise TypeError(f"a size is a whole number of inches, not {self.value!r}")

    @classmethod
    def of(cls, feet: int, inches: int = 0) -> Inches:
        """Build from feet and inches. The inches part may run past eleven."""
        return cls(feet * INCHES_PER_FOOT + inches)

    @classmethod
    def parse(cls, text: str) -> Inches:
        """Read what a hauler wrote on the application.

        Applications arrive by fax as often as by form and the office has never
        managed to make anybody write one format. Every one of these means the
        same 174 inches, so every one of them reads back as 174.
        """
        for pattern in (_FEET_INCHES, _DASHED):
            found = pattern.match(text)
            if found:
                feet = int(found.group("feet"))
                part = found.group("inches")
                return cls.of(feet, int(part) if part else 0)
        found = _BARE.match(text)
        if found:
            return cls(int(found.group("inches")))
        raise ValueError(f"cannot read {text!r} as a size")

    @property
    def feet(self) -> int:
        """Whole feet, rounded toward zero."""
        return int(self.value / INCHES_PER_FOOT)

    @property
    def spare_inches(self) -> int:
        """What is left over the whole feet, carrying the sign."""
        return self.value - self.feet * INCHES_PER_FOOT

    def over(self, limit: Inches) -> Inches:
        """How far past ``limit`` this runs, never below zero."""
        return Inches(max(0, self.value - limit.value))

    def __add__(self, other: Inches) -> Inches:
        if not isinstance(other, Inches):
            return NotImplemented
        return Inches(self.value + other.value)

    def __sub__(self, other: Inches) -> Inches:
        if not isinstance(other, Inches):
            return NotImplemented
        return Inches(self.value - other.value)

    def __mul__(self, count: int) -> Inches:
        if not isinstance(count, int) or isinstance(count, bool):
            return NotImplemented
        return Inches(self.value * count)

    def __neg__(self) -> Inches:
        return Inches(-self.value)

    def __abs__(self) -> Inches:
        return Inches(abs(self.value))

    def __str__(self) -> str:
        sign = "-" if self.value < 0 else ""
        feet, spare = divmod(abs(self.value), INCHES_PER_FOOT)
        if spare == 0:
            return f"{sign}{feet} ft"
        if feet == 0:
            return f"{sign}{spare} in"
        return f"{sign}{feet} ft {spare} in"

    def __repr__(self) -> str:
        return f"Inches({self.value})"


@dataclass(frozen=True, order=True)
class Pounds:
    """A weight, in whole pounds."""

    value: int

    def __post_init__(self) -> None:
        if not isinstance(self.value, int) or isinstance(self.value, bool):
            raise TypeError(f"a weight is a whole number of pounds, not {self.value!r}")

    @classmethod
    def of_tons(cls, tons: int) -> Pounds:
        return cls(tons * POUNDS_PER_TON)

    @classmethod
    def parse(cls, text: str) -> Pounds:
        """Read a weight off an application, commas and unit and all."""
        cleaned = text.strip().lower().replace(",", "")
        for suffix in ("pounds", "lbs.", "lbs", "lb.", "lb", "#"):
            if cleaned.endswith(suffix):
                cleaned = cleaned[: -len(suffix)].strip()
                break
        else:
            for suffix in ("tons", "ton"):
                if cleaned.endswith(suffix):
                    tons = cleaned[: -len(suffix)].strip()
                    if not tons.isdigit():
                        raise ValueError(f"cannot read {text!r} as a weight")
                    return cls.of_tons(int(tons))
        if not cleaned.isdigit():
            raise ValueError(f"cannot read {text!r} as a weight")
        return cls(int(cleaned))

    def over(self, limit: Pounds) -> Pounds:
        """How far past ``limit`` this runs, never below zero."""
        return Pounds(max(0, self.value - limit.value))

    def __add__(self, other: Pounds) -> Pounds:
        if not isinstance(other, Pounds):
            return NotImplemented
        return Pounds(self.value + other.value)

    def __sub__(self, other: Pounds) -> Pounds:
        if not isinstance(other, Pounds):
            return NotImplemented
        return Pounds(self.value - other.value)

    def __mul__(self, count: int) -> Pounds:
        if not isinstance(count, int) or isinstance(count, bool):
            return NotImplemented
        return Pounds(self.value * count)

    def __neg__(self) -> Pounds:
        return Pounds(-self.value)

    def __str__(self) -> str:
        return f"{self.value:,} lb"

    def __repr__(self) -> str:
        return f"Pounds({self.value})"


@dataclass(frozen=True, order=True)
class Miles:
    """A distance along the network, in hundredths of a mile."""

    hundredths: int

    def __post_init__(self) -> None:
        if not isinstance(self.hundredths, int) or isinstance(self.hundredths, bool):
            raise TypeError(f"a distance is whole hundredths, not {self.hundredths!r}")

    @classmethod
    def of(cls, miles: int, hundredths: int = 0) -> Miles:
        return cls(miles * HUNDREDTHS_PER_MILE + hundredths)

    @classmethod
    def parse(cls, text: str) -> Miles:
        """Read 12.40 or 12 off the road log. Two places, never more."""
        cleaned = text.strip().lower().removesuffix("miles").removesuffix("mi").strip()
        if "." not in cleaned:
            if not cleaned.isdigit():
                raise ValueError(f"cannot read {text!r} as a distance")
            return cls.of(int(cleaned))
        whole, _, part = cleaned.partition(".")
        if not whole.isdigit() or not part.isdigit() or len(part) > 2:
            raise ValueError(f"cannot read {text!r} as a distance")
        return cls.of(int(whole), int(part.ljust(2, "0")))

    def rounded_up(self) -> int:
        """Whole miles, rounded up. A permit is priced on the mile above."""
        whole, part = divmod(self.hundredths, HUNDREDTHS_PER_MILE)
        return whole + (1 if part else 0)

    def __add__(self, other: Miles) -> Miles:
        if not isinstance(other, Miles):
            return NotImplemented
        return Miles(self.hundredths + other.hundredths)

    def __sub__(self, other: Miles) -> Miles:
        if not isinstance(other, Miles):
            return NotImplemented
        return Miles(self.hundredths - other.hundredths)

    def __str__(self) -> str:
        whole, part = divmod(self.hundredths, HUNDREDTHS_PER_MILE)
        return f"{whole}.{part:02d} mi"

    def __repr__(self) -> str:
        return f"Miles({self.hundredths})"


ZERO_INCHES = Inches(0)
ZERO_POUNDS = Pounds(0)
ZERO_MILES = Miles(0)
