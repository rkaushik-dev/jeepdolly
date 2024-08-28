"""Carriers, and the three things that stop one getting a permit.

A permit is written to a carrier, not to a load and not to a driver. So before
anything is measured, the office asks whether this carrier may have a permit at
all today, and there are exactly three ways the answer is no.

The insurance certificate has lapsed. The office holds a certificate with an
effective date and an expiry, and a permit may not be issued for travel that
falls outside it. Note the shape of that rule: it is not enough for the
certificate to be good today, it has to be good on every day the permit is
valid for, which is why the check takes a span and not a date.

The surety bond has lapsed, for carriers that need one. A bond is only required
above a weight, so most carriers do not carry one and the absence of a bond is
not by itself a problem.

The carrier is suspended. That is a decision somebody made, with a reason and
a date it lifts, and it beats everything else. A suspended carrier with perfect
paper gets nothing.

A carrier record also holds the billing arrangement, because a permit issued on
account and a permit paid at the counter follow different paths later.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from enum import Enum

from ..errors import Conflict, Impossible, NotOnFile
from ..money import Dollars

#: Above this weight the state wants a bond behind the permit.
BOND_THRESHOLD_POUNDS = 120000


class Billing(Enum):
    """How this carrier pays for a permit."""

    COUNTER = "counter"
    ACCOUNT = "account"
    ESCROW = "escrow"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Insurance:
    """A certificate of liability insurance as the office holds it."""

    policy: str
    insurer: str
    liability: Dollars
    effective: date
    expires: date

    def __post_init__(self) -> None:
        if self.expires <= self.effective:
            raise Impossible(f"certificate {self.policy} expires before it takes effect")
        if self.liability.cents <= 0:
            raise Impossible(f"certificate {self.policy} carries no limit")

    def in_force(self, day: date) -> bool:
        return self.effective <= day <= self.expires

    def covers(self, start: date, end: date) -> bool:
        """Good for every day of a span, which is what a permit needs."""
        return self.in_force(start) and self.in_force(end)

    def __str__(self) -> str:
        return f"{self.policy} {self.insurer} to {self.expires.isoformat()}"


@dataclass(frozen=True)
class Bond:
    """A surety bond, held for the heavy end of the work."""

    number: str
    surety: str
    amount: Dollars
    effective: date
    expires: date

    def __post_init__(self) -> None:
        if self.expires <= self.effective:
            raise Impossible(f"bond {self.number} expires before it takes effect")

    def in_force(self, day: date) -> bool:
        return self.effective <= day <= self.expires

    def covers(self, start: date, end: date) -> bool:
        return self.in_force(start) and self.in_force(end)

    def __str__(self) -> str:
        return f"{self.number} {self.surety} {self.amount}"


@dataclass(frozen=True)
class Suspension:
    """A decision to stop writing permits for somebody, and why."""

    reason: str
    from_day: date
    until: date | None = None

    def in_force(self, day: date) -> bool:
        if day < self.from_day:
            return False
        return self.until is None or day <= self.until

    def __str__(self) -> str:
        ends = self.until.isoformat() if self.until else "further notice"
        return f"suspended {self.from_day.isoformat()} to {ends}: {self.reason}"


@dataclass
class Carrier:
    """One carrier, its paper, and its standing."""

    usdot: int
    name: str
    town: str
    state: str
    billing: Billing = Billing.COUNTER
    insurance: Insurance | None = None
    bond: Bond | None = None
    suspensions: list[Suspension] = field(default_factory=list)

    def __post_init__(self) -> None:
        if self.usdot <= 0 or self.usdot > 99999999:
            raise Impossible(f"{self.usdot} is not a usdot number")
        if not self.name.strip():
            raise Impossible("a carrier has a name")
        if len(self.state) != 2 or not self.state.isupper():
            raise Impossible(f"{self.state!r} is not a state")

    def suspend(self, reason: str, from_day: date, until: date | None = None) -> Suspension:
        if until is not None and until < from_day:
            raise Impossible("a suspension lifts after it starts")
        order = Suspension(reason, from_day, until)
        self.suspensions.append(order)
        return order

    def suspension_on(self, day: date) -> Suspension | None:
        for order in self.suspensions:
            if order.in_force(day):
                return order
        return None

    def needs_bond(self, gross_pounds: int) -> bool:
        return gross_pounds > BOND_THRESHOLD_POUNDS

    def standing(self, start: date, end: date, gross_pounds: int = 0) -> str | None:
        """What stops a permit over this span, or None if nothing does.

        The order matters. A suspension is reported ahead of lapsed paper,
        because a carrier that fixes its certificate is still suspended and
        being told one thing at a time wastes a week.
        """
        order = self.suspension_on(start)
        if order is not None:
            return str(order)
        if self.insurance is None:
            return "no certificate of insurance on file"
        if not self.insurance.covers(start, end):
            return f"insurance {self.insurance.policy} does not cover the whole trip"
        if self.needs_bond(gross_pounds):
            if self.bond is None:
                return f"no bond on file and the load is over {BOND_THRESHOLD_POUNDS:,} lb"
            if not self.bond.covers(start, end):
                return f"bond {self.bond.number} does not cover the whole trip"
        return None

    def in_good_standing(self, start: date, end: date, gross_pounds: int = 0) -> bool:
        return self.standing(start, end, gross_pounds) is None

    def __str__(self) -> str:
        return f"{self.name} ({self.usdot})"


class CarrierRegister:
    """Every carrier the office has ever written a permit for."""

    def __init__(self) -> None:
        self._by_usdot: dict[int, Carrier] = {}

    def add(self, carrier: Carrier) -> Carrier:
        if carrier.usdot in self._by_usdot:
            raise Conflict(f"usdot {carrier.usdot} is already registered", usdot=carrier.usdot)
        self._by_usdot[carrier.usdot] = carrier
        return carrier

    def get(self, usdot: int) -> Carrier:
        try:
            return self._by_usdot[usdot]
        except KeyError:
            raise NotOnFile(f"no carrier with usdot {usdot}", usdot=usdot) from None

    def search(self, text: str) -> tuple[Carrier, ...]:
        """Name search, the way the counter does it: any part, any case."""
        needle = text.strip().lower()
        if not needle:
            return ()
        found = [one for one in self._by_usdot.values() if needle in one.name.lower()]
        return tuple(sorted(found, key=lambda one: one.name))

    def suspended_on(self, day: date) -> tuple[Carrier, ...]:
        found = [one for one in self._by_usdot.values() if one.suspension_on(day)]
        return tuple(sorted(found, key=lambda one: one.name))

    def __len__(self) -> int:
        return len(self._by_usdot)

    def __contains__(self, usdot: object) -> bool:
        return usdot in self._by_usdot
