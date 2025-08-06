"""Turning everything that fired into the conditions a permit prints.

This is the join. Every other package produces findings in its own vocabulary:
obstructions, ratings, escorts, marking, travel limits. This one turns them all
into condition codes with facts, and hands the library a list to render.

The rule that keeps it honest is that nothing here decides anything. If the
escort table said two pilot cars, this writes two pilot cars. It does not think
about whether two is right for a load that narrow, because the escort table has
already thought about it and having the thinking in two places is how the two
places come to disagree.

A permit can only carry one of each code, so a route with two tight corners
on it prints one stop traffic condition naming the first. That is a known
shortcoming of collecting by code and it has not bitten yet, because a route
with two of them is a route the counter has already telephoned about.

There is one piece of judgement and it is about duplication. Several rules can
produce the same condition: a superload is daylight only, and so is anything
over twelve feet wide, and a permit that says daylight hours only twice reads
like a mistake. So conditions are collected by code and the first facts win,
which is why the general conditions are added last.
"""

from __future__ import annotations

from ..escorts.marking import Marking
from ..escorts.naming import Naming
from ..escorts.rules import Escorts
from ..routing.evaluate import RouteReading
from ..structures.analysis import Reading as StructureReading
from ..structures.rating import Verdict
from ..travel.windows import TravelLimits
from ..utilities.lines import Reading as LineReading
from .library import Condition, ConditionLibrary, sort_conditions

#: On every permit, whatever it is for.
ALWAYS = ("R-01", "Q-01", "G-01", "G-02", "G-03")


def base(code: str) -> str:
    """The library code behind a sheet key, dropping any tail after a slash."""
    return code.partition("/")[0]


class Sheet:
    """The conditions of one permit, collected by code with the first facts.

    A key is usually a library code and sometimes a code with a tail on it,
    written ``U-01/L-4``. The tail exists for the one condition that is
    printed more than once with different facts each time: three line lifts
    are three conditions naming three places, and collecting them by code
    alone would keep the first and quietly lose two.

    The tail is dropped when the library is asked for the wording. Nothing
    else in the system sees it.
    """

    def __init__(self, library: ConditionLibrary) -> None:
        self.library = library
        self._codes: list[str] = []
        self._facts: dict[str, dict[str, object]] = {}

    def add(self, code: str, **facts: object) -> None:
        if code in self._facts:
            return
        self._codes.append(code)
        self._facts[code] = dict(facts)

    def rendered(self) -> tuple[Condition, ...]:
        found = tuple(self.library.render(base(code), self._facts[code]) for code in self._codes)
        return sort_conditions(found)

    def codes(self) -> tuple[str, ...]:
        return tuple(self._codes)

    def __len__(self) -> int:
        return len(self._codes)


def assemble(
    library: ConditionLibrary,
    start: str,
    end: str,
    limits: TravelLimits,
    escorts: Escorts,
    marking: Marking,
    route: RouteReading | None = None,
    structures: StructureReading | None = None,
    liftable_axles: int = 0,
    naming: Naming | None = None,
    lines: LineReading | None = None,
) -> tuple[Condition, ...]:
    """Every condition this permit carries, in printing order."""
    sheet = Sheet(library)
    sheet.add("T-01", start=start, end=end)
    _travel(sheet, limits)
    if route is not None:
        _route(sheet, route)
    if structures is not None:
        _structures(sheet, structures)
    if lines is not None:
        _lines(sheet, lines)
    _escorts(sheet, escorts)
    if naming is not None and escorts.total:
        _naming(sheet, naming)
    _marking(sheet, marking)
    if liftable_axles:
        sheet.add("Q-02")
    for code in ALWAYS:
        sheet.add(code)
    return sheet.rendered()


def _travel(sheet: Sheet, limits: TravelLimits) -> None:
    if limits.daylight_only:
        sheet.add("T-02")
    if limits.no_weekend:
        sheet.add("T-03")
    if limits.wind_hold_mph:
        sheet.add("T-06", wind=limits.wind_hold_mph)
        sheet.add("T-07")


def _route(sheet: Sheet, route: RouteReading) -> None:
    for trouble in route.conditions:
        if trouble.kind == "posted season":
            sheet.add(
                "R-02",
                segment=trouble.segment,
                limit=trouble.allowed,
                order=trouble.note.rsplit(" ", 1)[-1],
            )
        elif trouble.kind == "curfew":
            sheet.add("T-05", segment=trouble.segment, curfew=trouble.allowed)
        elif trouble.kind == "tight corner":
            # Both of these, and in this order. The first is what the driver
            # has to do and the second is why, and an officer at the roadside
            # reads the first one on its own.
            sheet.add("R-05", segment=trouble.segment, place=trouble.place)
            sheet.add("R-06", place=trouble.place, allowed=trouble.allowed)


def _structures(sheet: Sheet, structures: StructureReading) -> None:
    for finding in structures.findings:
        if finding.verdict is not Verdict.CONDITIONAL:
            continue
        name = finding.structure.name
        sheet.add("S-01", structure=name)
        sheet.add("S-02", structure=name)
        sheet.add("S-03", structure=name)
        sheet.add("S-04", structure=name)


def _lines(sheet: Sheet, lines: LineReading) -> None:
    """One condition per lift, and one saying not to leave without them.

    This is the one place a condition is added more than once, and it works
    because each lift carries a different place in its facts. The sheet
    collects by code and would keep only the first, so the code is made
    unique per place before it is added. That is a small lie told to the
    sheet and it is the alternative to a permit that names one of three
    lifts.
    """
    for lift in lines.lifts:
        sheet.add(
            f"U-01/{lift.crossing.ident}",
            place=f"{lift.crossing.segment} at {lift.crossing.place}",
            owner=lift.owner.name,
            telephone=lift.owner.telephone,
        )
    if lines.lifts:
        sheet.add("U-02")


def _escorts(sheet: Sheet, escorts: Escorts) -> None:
    if escorts.front:
        sheet.add("E-01", count=escorts.front)
    if escorts.rear:
        sheet.add("E-02", count=escorts.rear)
    if escorts.height_pole:
        sheet.add("E-03")
    if escorts.police:
        sheet.add("E-04")


def _naming(sheet: Sheet, naming: Naming) -> None:
    """Who is piloting, or a line saying the office has not been told yet."""
    if naming.good:
        sheet.add("E-05", cards=naming.line())
    if not naming.complete:
        sheet.add("E-06")


def _marking(sheet: Sheet, marking: Marking) -> None:
    if marking.banners:
        sheet.add("M-01")
    if marking.flags:
        sheet.add("M-02", count=marking.flags)
    if marking.lamps:
        sheet.add("M-03", count=marking.lamps)
    if marking.beacons:
        sheet.add("M-04", count=marking.beacons)
