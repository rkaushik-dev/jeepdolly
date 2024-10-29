"""Banners, flags, lamps and beacons: what the load has to wear.

Marking is the cheap half of safety and the half that gets skipped, so the
permit spells it out and the enforcement people read it off the permit at the
roadside.

Four things and they stack rather than replace each other.

A banner reading OVERSIZE LOAD goes on the front and the rear of any load past
legal size. Seven feet by eighteen inches, black on yellow, and it comes off
when the load is legal again, because a banner on a legal load is a banner
people stop reading.

Flags go on the corners of anything that projects, and on the widest point of
anything over legal width. Eighteen inch red or fluorescent orange. Four of
them on a load that is both wide and long, and the count is on the permit
because it is the thing most often short.

Lamps replace flags after dark and in poor visibility. Not as well as, instead
of. A flag is invisible at night and the office would rather not have both, so
the permit says which applies to the trip it is written for.

A beacon goes on the power unit for any oversize load, on the highest point
where it can be seen from behind. Two of them for a superload, which is not
about visibility so much as about telling everybody that the thing coming is
the thing they were warned about.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..domain.envelope import Envelope
from ..measure import Inches

#: Anything past this width wears a banner and flags.
LEGAL_WIDTH = Inches.of(8, 6)

#: Anything past this length wears a banner.
LEGAL_LENGTH = Inches.of(75)

#: A projection past this gets its own flags.
FLAGGED_OVERHANG = Inches.of(4)


@dataclass(frozen=True)
class Marking:
    """What goes on the load for one trip."""

    banners: int
    flags: int
    lamps: int
    beacons: int
    reasons: tuple[str, ...] = ()

    @property
    def any(self) -> bool:
        return bool(self.banners or self.flags or self.lamps or self.beacons)

    @property
    def after_dark(self) -> bool:
        return bool(self.lamps)

    def __str__(self) -> str:
        if not self.any:
            return "no marking"
        parts = []
        if self.banners:
            parts.append(f"{self.banners} oversize load banners")
        if self.flags:
            parts.append(f"{self.flags} flags")
        if self.lamps:
            parts.append(f"{self.lamps} lamps")
        if self.beacons:
            parts.append(f"{self.beacons} beacons")
        return ", ".join(parts)


def marking_for(envelope: Envelope, at_night: bool = False, superload: bool = False) -> Marking:
    """Read the marking table for one load on one trip."""
    reasons: list[str] = []
    wide = envelope.width > LEGAL_WIDTH
    long = envelope.length > LEGAL_LENGTH
    banners = 2 if wide or long else 0
    if wide:
        reasons.append("over legal width")
    if long:
        reasons.append("over legal length")

    corners = 2 if wide else 0
    if envelope.front_overhang > FLAGGED_OVERHANG:
        corners += 2
        reasons.append("front overhang")
    if envelope.rear_overhang > FLAGGED_OVERHANG:
        corners += 2
        reasons.append("rear overhang")

    beacons = 0
    if banners:
        beacons = 2 if superload else 1
    if superload:
        reasons.append("superload")

    if at_night:
        return Marking(banners, 0, corners, beacons, tuple(reasons))
    return Marking(banners, corners, 0, beacons, tuple(reasons))


def conditions(marking: Marking) -> tuple[str, ...]:
    """The marking lines as they print on the permit face."""
    if not marking.any:
        return ()
    found = []
    if marking.banners:
        found.append(
            f"{marking.banners} OVERSIZE LOAD banners, one front and one rear, "
            "seven feet by eighteen inches"
        )
    if marking.flags:
        found.append(f"{marking.flags} eighteen inch red flags on the corners and widest points")
    if marking.lamps:
        found.append(f"{marking.lamps} amber lamps in place of flags, lit for the whole move")
    if marking.beacons:
        found.append(f"{marking.beacons} amber beacons on the power unit, visible from the rear")
    return tuple(found)
