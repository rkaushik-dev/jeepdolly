"""Overhead lines, and the telephone call that has to happen before a lift.

A bridge is the thing everybody thinks of and it is not the thing that stops
tall loads. What stops them is a distribution line strung across a state road
at eighteen feet, owned by a company that is not this office and does not work
for it. The office cannot lift a line. It can only say which line is in the
way and who has to be rung.

Three things follow from that and they are the whole of this file.

A line is measured with daylight on top. A load that is exactly the height of
the line does not clear it: wire sags in the heat, the road crowns, and the
trailer bounces. The office wants six inches and does not negotiate it.

A line the owner can raise is a condition rather than a refusal, and it is a
condition with a name and a telephone number on it, because the hauler is the
one who rings. A line the owner cannot raise is a refusal and the answer is a
different route.

The notice is the part that catches people. A crew is not free tomorrow. Every
owner publishes how many working days it wants and the office reads that
against the day the application was filed, not the day the permit is written.
A hauler who files on Monday for a Wednesday move under a line wanting five
days is refused, and the refusal says so in days rather than in dates, because
that is the sentence he will repeat to his customer.

Working days, not days. A five day notice filed on a Thursday reaches the crew
the following Thursday, and an office that counted calendar days would promise
a lift on a Tuesday that nobody was coming to.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from ..domain.envelope import Envelope
from ..errors import Conflict, NotOnFile
from ..measure import Inches
from ..routing.itinerary import Itinerary

#: Daylight the office wants between the load and the wire.
SAFETY = Inches.of(0, 6)

#: Saturday and Sunday, which no line crew works without an emergency.
WEEKEND = (5, 6)


@dataclass(frozen=True)
class Owner:
    """Whoever owns the wire, and how much warning they want."""

    ident: str
    name: str
    telephone: str
    notice_days: int
    note: str = ""

    def __post_init__(self) -> None:
        if self.notice_days < 0:
            raise Conflict(f"{self.ident} cannot want negative notice")

    def notice_by(self, travel: date) -> date:
        """The last working day a request can reach them for that move."""
        standing = travel
        left = self.notice_days
        while left > 0:
            standing = standing - timedelta(days=1)
            if standing.weekday() not in WEEKEND:
                left -= 1
        return standing

    def in_time(self, filed: date, travel: date) -> bool:
        return filed <= self.notice_by(travel)

    def __str__(self) -> str:
        return f"{self.ident}  {self.name}  {self.telephone}  {self.notice_days} days notice"


@dataclass(frozen=True)
class Crossing:
    """One wire over one segment, measured, with who owns it."""

    ident: str
    segment: str
    place: str
    height: Inches
    owner: str
    lift_to: Inches | None = None
    note: str = ""

    def __post_init__(self) -> None:
        if self.height.value <= 0:
            raise Conflict(f"crossing {self.ident} has a measured height")
        if self.lift_to is not None and self.lift_to <= self.height:
            raise Conflict(f"crossing {self.ident} cannot be lifted to below where it hangs")

    @property
    def liftable(self) -> bool:
        return self.lift_to is not None

    def clears(self, envelope: Envelope) -> bool:
        """Whether this load goes under as the wire hangs today."""
        return envelope.height + SAFETY <= self.height

    def clears_lifted(self, envelope: Envelope) -> bool:
        """Whether it goes under once the owner has raised it."""
        if self.lift_to is None:
            return False
        return envelope.height + SAFETY <= self.lift_to

    def __str__(self) -> str:
        lift = f", lifts to {self.lift_to}" if self.lift_to else ", cannot be lifted"
        return f"{self.ident}  {self.segment} at {self.place}  {self.height}{lift}"


class LineBook:
    """Every crossing the office has measured, and every owner of one."""

    def __init__(self) -> None:
        self._owners: dict[str, Owner] = {}
        self._crossings: dict[str, Crossing] = {}

    def add_owner(self, owner: Owner) -> Owner:
        if owner.ident in self._owners:
            raise Conflict(f"line owner {owner.ident} is already on file")
        self._owners[owner.ident] = owner
        return owner

    def owner(self, ident: str) -> Owner:
        try:
            return self._owners[ident]
        except KeyError:
            raise NotOnFile(f"no line owner {ident}", ident=ident) from None

    def owners(self) -> tuple[Owner, ...]:
        return tuple(self._owners[one] for one in sorted(self._owners))

    def add(self, crossing: Crossing) -> Crossing:
        if crossing.ident in self._crossings:
            raise Conflict(f"crossing {crossing.ident} is already on file")
        self.owner(crossing.owner)
        self._crossings[crossing.ident] = crossing
        return crossing

    def get(self, ident: str) -> Crossing:
        try:
            return self._crossings[ident]
        except KeyError:
            raise NotOnFile(f"no crossing {ident}", ident=ident) from None

    def on_segment(self, segment: str) -> tuple[Crossing, ...]:
        found = [one for one in self._crossings.values() if one.segment == segment]
        return tuple(sorted(found, key=lambda one: one.ident))

    def crossings(self) -> tuple[Crossing, ...]:
        return tuple(self._crossings[one] for one in sorted(self._crossings))

    def lowest(self) -> Crossing | None:
        if not self._crossings:
            return None
        return min(self._crossings.values(), key=lambda one: (one.height.value, one.ident))

    def __len__(self) -> int:
        return len(self._crossings)


@dataclass(frozen=True)
class Lift:
    """One crossing a load needs raised, and who has to be rung about it."""

    crossing: Crossing
    owner: Owner
    notice_by: date
    in_time: bool

    @property
    def possible(self) -> bool:
        return self.crossing.liftable and self.in_time

    def __str__(self) -> str:
        return (
            f"{self.crossing.segment} at {self.crossing.place}: "
            f"ring {self.owner.name} on {self.owner.telephone} "
            f"by {self.notice_by.isoformat()}"
        )


@dataclass(frozen=True)
class Blocked:
    """One crossing nothing can be done about."""

    crossing: Crossing
    owner: Owner
    wanted: Inches
    late: bool = False

    def __str__(self) -> str:
        if self.late:
            return (
                f"{self.crossing.segment} at {self.crossing.place} wants "
                f"{self.owner.notice_days} working days notice to {self.owner.name}"
            )
        return (
            f"{self.crossing.segment} at {self.crossing.place} hangs at "
            f"{self.crossing.height} and this load wants {self.wanted}"
        )


@dataclass(frozen=True)
class Reading:
    """What one load meets overhead on one route."""

    lifts: tuple[Lift, ...]
    blocked: tuple[Blocked, ...]

    @property
    def clear(self) -> bool:
        return not self.blocked

    def refusals(self) -> tuple[str, ...]:
        return tuple(str(one) for one in self.blocked)

    def __str__(self) -> str:
        if self.blocked:
            return f"{len(self.blocked)} lines in the way"
        if self.lifts:
            return f"{len(self.lifts)} lifts to arrange"
        return "nothing overhead"


def wanted(envelope: Envelope) -> Inches:
    """The clearance this load wants, which is its height plus the daylight."""
    return envelope.height + SAFETY


def under(
    itinerary: Itinerary,
    envelope: Envelope,
    book: LineBook,
    filed: date,
    travel: date,
) -> Reading:
    """Every crossing on the route, sorted into lifts and refusals."""
    lifts: list[Lift] = []
    blocked: list[Blocked] = []
    for leg in itinerary:
        for crossing in book.on_segment(leg.segment.ident):
            if crossing.clears(envelope):
                continue
            owner = book.owner(crossing.owner)
            if not crossing.clears_lifted(envelope):
                blocked.append(Blocked(crossing, owner, wanted(envelope)))
                continue
            in_time = owner.in_time(filed, travel)
            if not in_time:
                blocked.append(Blocked(crossing, owner, wanted(envelope), late=True))
                continue
            lifts.append(Lift(crossing, owner, owner.notice_by(travel), in_time))
    return Reading(tuple(lifts), tuple(blocked))
