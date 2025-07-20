"""Where a carrier stands on money it owes the office, and when that stops it.

A carrier with an unpaid citation still gets permits. That surprises people
and it is deliberate: a ticket written on Tuesday is not settled by Thursday
whatever anybody intends, and an office that stopped writing permits over an
unpaid ticket would stop the state's freight over its own paperwork.

What stops a carrier is a citation that is past due. Past due means the
contest window has closed, the grace period after it has run, and the money
has still not arrived. At that point the carrier has had seven weeks and has
neither paid nor contested, and the office holds new permits until it does.

Two things are deliberately not a hold.

A contested citation is never past due, however old. The carrier said no in
writing and the office has not answered, and holding permits over a dispute
the office has not got round to hearing would be the office punishing its own
backlog.

An amount does not matter. A hundred dollars past due holds a carrier exactly
as five thousand does, because the hold is about a carrier that has stopped
answering letters rather than about the money.

A hold is lifted by paying, and it is lifted the same day. There is no waiting
period and no supervisor's signature, because the whole point is to get the
carrier to ring the office, and a carrier that has rung and paid should be
loading that afternoon.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from ..money import Dollars

#: Days after the contest window closes before an unpaid citation holds.
GRACE_DAYS = 30


@dataclass(frozen=True)
class Owed:
    """One unpaid citation, with the day it stops being merely late."""

    number: str
    written: date
    amount: Dollars
    due: date
    contested: bool = False

    def past_due_on(self, day: date) -> bool:
        """Whether this one holds the carrier on that day."""
        return not self.contested and day > self.due

    def __str__(self) -> str:
        mark = " (contested)" if self.contested else ""
        return f"{self.number}  {self.amount}  due {self.due.isoformat()}{mark}"


def due_day(written: date, contest_days: int, grace: int = GRACE_DAYS) -> date:
    """When an unpaid citation stops being late and starts being a hold."""
    return written + timedelta(days=contest_days + grace)


@dataclass(frozen=True)
class Account:
    """What one carrier owes, and whether any of it holds them."""

    usdot: int
    unpaid: tuple[Owed, ...] = ()

    @property
    def owing(self) -> Dollars:
        found = Dollars.of(0)
        for one in self.unpaid:
            found = found + one.amount
        return found

    def past_due(self, day: date) -> tuple[Owed, ...]:
        return tuple(one for one in self.unpaid if one.past_due_on(day))

    def hold(self, day: date) -> str | None:
        """The refusal line, or nothing when the carrier is clear to load."""
        found = self.past_due(day)
        if not found:
            return None
        oldest = min(found, key=lambda one: one.due)
        return (
            f"citation {oldest.number} has been due since {oldest.due.isoformat()} "
            f"and {len(found)} unpaid citations hold this carrier"
        )

    def __str__(self) -> str:
        if not self.unpaid:
            return f"{self.usdot} owes nothing"
        return f"{self.usdot} owes {self.owing} on {len(self.unpaid)} citations"


class EnforcementBook:
    """Every carrier that owes the office something, read as at one day."""

    def __init__(self) -> None:
        self._accounts: dict[int, Account] = {}

    def add(self, account: Account) -> Account:
        self._accounts[account.usdot] = account
        return account

    def account(self, usdot: int) -> Account:
        """One carrier, which is an empty account when nothing is on file.

        An empty account rather than a refusal. A carrier with no citations is
        the ordinary case and making every caller handle a missing one would
        put the same two lines in six places.
        """
        return self._accounts.get(usdot, Account(usdot))

    def hold(self, usdot: int, day: date) -> str | None:
        return self.account(usdot).hold(day)

    def held(self, day: date) -> tuple[Account, ...]:
        """Every carrier the office is holding, worst first."""
        found = [one for one in self._accounts.values() if one.hold(day)]
        return tuple(sorted(found, key=lambda one: (-len(one.past_due(day)), one.usdot)))

    def __len__(self) -> int:
        return len(self._accounts)

    def __contains__(self, usdot: object) -> bool:
        return usdot in self._accounts
