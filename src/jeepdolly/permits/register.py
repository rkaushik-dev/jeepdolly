"""The permit register: every permit ever issued, and the questions asked of it.

The register answers four questions and they are the four the telephone asks.

What is permit 25-P-004471. By number, and it has to work for a number read
badly off a fax, so the number is parsed rather than matched as a string.

What has this carrier got out. By usdot, newest first, because the caller is
asking about the one they just took out.

What is on the road today. By day, which is the question the enforcement people
ask and the one the office is slowest at on paper.

What is about to expire. Which nobody asks, and which the office needs anyway,
because a permit that expires with the load halfway to Presque Isle is a
telephone call at four on a Friday.

Nothing is ever removed. A void permit stays, an expired permit stays, and a
superseded permit stays with its chain intact. The register grows forever and
that is the point of it.
"""

from __future__ import annotations

from datetime import date, timedelta

from ..errors import Conflict, NotOnFile
from .numbering import PermitNumber, Series
from .permit import Permit, PermitState


class PermitRegister:
    """Every permit the office has issued."""

    def __init__(self) -> None:
        self._by_number: dict[str, Permit] = {}
        self._by_usdot: dict[int, list[str]] = {}

    def file(self, permit: Permit) -> Permit:
        key = str(permit.number)
        if key in self._by_number:
            raise Conflict(f"permit {key} is already on file", number=key)
        self._by_number[key] = permit
        self._by_usdot.setdefault(permit.usdot, []).append(key)
        return permit

    def get(self, number: str | PermitNumber) -> Permit:
        key = str(number if isinstance(number, PermitNumber) else PermitNumber.parse(number))
        try:
            return self._by_number[key]
        except KeyError:
            raise NotOnFile(f"no permit {key}", number=key) from None

    def has(self, number: str) -> bool:
        try:
            self.get(number)
        except NotOnFile:
            return False
        return True

    def for_carrier(self, usdot: int) -> tuple[Permit, ...]:
        """Newest first, because that is the one being asked about."""
        keys = self._by_usdot.get(usdot, [])
        found = [self._by_number[key] for key in keys]
        return tuple(sorted(found, key=lambda one: (one.issued, str(one.number)), reverse=True))

    def on_the_road(self, day: date) -> tuple[Permit, ...]:
        found = [one for one in self._by_number.values() if one.valid_on(day)]
        return tuple(sorted(found, key=lambda one: str(one.number)))

    def issued_on(self, day: date) -> tuple[Permit, ...]:
        found = [one for one in self._by_number.values() if one.issued == day]
        return tuple(sorted(found, key=lambda one: str(one.number)))

    def expiring(self, day: date, within: int = 2) -> tuple[Permit, ...]:
        """Live permits whose last day falls inside the next few days."""
        horizon = day + timedelta(days=within)
        found = [
            one
            for one in self._by_number.values()
            if one.state is PermitState.ISSUED and day <= one.end <= horizon
        ]
        return tuple(sorted(found, key=lambda one: (one.end, str(one.number))))

    def in_state(self, state: PermitState) -> tuple[Permit, ...]:
        found = [one for one in self._by_number.values() if one.state is state]
        return tuple(sorted(found, key=lambda one: str(one.number)))

    def of_series(self, series: Series) -> tuple[Permit, ...]:
        found = [one for one in self._by_number.values() if one.number.series is series]
        return tuple(sorted(found, key=lambda one: str(one.number)))

    def chain(self, number: str | PermitNumber) -> tuple[Permit, ...]:
        """A permit and everything that replaced it, in order."""
        found = [self.get(number)]
        while found[-1].superseded_by is not None:
            following = found[-1].superseded_by
            if following is None or not self.has(str(following)):
                break
            found.append(self.get(following))
        return tuple(found)

    def void_all_for(self, usdot: int, on: date, reason: str) -> tuple[Permit, ...]:
        """Void every live permit a carrier holds, which suspension does."""
        voided = []
        for permit in self.for_carrier(usdot):
            if permit.state is PermitState.ISSUED and not permit.expired_on(on):
                permit.void(on, reason)
                voided.append(permit)
        return tuple(voided)

    def __len__(self) -> int:
        return len(self._by_number)

    def __contains__(self, number: object) -> bool:
        return str(number) in self._by_number
