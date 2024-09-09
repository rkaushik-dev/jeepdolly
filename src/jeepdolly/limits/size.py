"""Legal size, the routine permit envelope, and where review starts.

Three boxes matter and they nest.

The legal box is what moves with no permit. Eight foot six wide, thirteen six
high on the state system, seventy five feet of combination. A load inside it is
not this office's business at all.

The routine box is what a clerk may issue over the counter without asking
anybody. It is generous, because most of the work is a wide excavator or a long
beam and there is no sense sending those to an engineer. A load inside the
routine box and past the legal box is an ordinary permit, priced off a table,
issued while the hauler waits.

Past the routine box, a person has to look at it. That is a review, and the
review is what the superload process is built around. The office's word for
it is a study, and the study takes a fortnight in the summer.

Height is the awkward one. There is no routine height in the way there is a
routine width, because height is not about the load, it is about what the load
goes under, and that is a route question. So the height entry in the routine
box is the height above which nothing can be routine on any road, and anything
past legal height still gets its clearances walked.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from ..domain.envelope import Envelope, Excess
from ..domain.highway import HighwayClass
from ..errors import Impossible, NotOnFile
from ..measure import Inches


class SizeClass(Enum):
    """Which of the three boxes a load falls in."""

    LEGAL = "legal"
    ROUTINE = "routine"
    REVIEW = "review"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class SizeTable:
    """The legal box and the routine box for one class of road."""

    highway_class: HighwayClass
    legal: Envelope
    routine: Envelope

    def __post_init__(self) -> None:
        if self.routine.width < self.legal.width:
            raise Impossible("the routine box is not narrower than the legal one")
        if self.routine.height < self.legal.height:
            raise Impossible("the routine box is not lower than the legal one")
        if self.routine.length < self.legal.length:
            raise Impossible("the routine box is not shorter than the legal one")

    def over_legal(self, load: Envelope) -> Excess:
        return load.excess_over(self.legal)

    def over_routine(self, load: Envelope) -> Excess:
        return load.excess_over(self.routine)

    def classify(self, load: Envelope) -> SizeClass:
        """Which box the load falls in, read outward from the legal one."""
        if not self.over_legal(load).any:
            return SizeClass.LEGAL
        if not self.over_routine(load).any:
            return SizeClass.ROUTINE
        return SizeClass.REVIEW

    def reasons_for_review(self, load: Envelope) -> tuple[str, ...]:
        """Which measures put this load past the counter, in form order."""
        return self.over_routine(load).over()

    def __str__(self) -> str:
        return f"{self.highway_class} size table"


class SizeBook:
    """One size table for each class of road."""

    def __init__(self) -> None:
        self._tables: dict[HighwayClass, SizeTable] = {}

    def file(self, table: SizeTable) -> SizeTable:
        self._tables[table.highway_class] = table
        return table

    def on(self, highway_class: HighwayClass) -> SizeTable:
        try:
            return self._tables[highway_class]
        except KeyError:
            raise NotOnFile(
                f"no size table for {highway_class}", highway_class=str(highway_class)
            ) from None

    def tightest(self, classes: tuple[HighwayClass, ...]) -> SizeTable:
        """The table a trip is held to when it crosses several classes.

        A trip is held to the narrowest legal box it touches, which is not
        always the same table as the lowest one, so this picks per measure and
        hands back a table with the tightest of each.
        """
        if not classes:
            raise Impossible("a trip runs over at least one class of road")
        tables = [self.on(one) for one in classes]
        return SizeTable(
            highway_class=min(tables, key=lambda table: table.legal.width.value).highway_class,
            legal=_tightest_box(tuple(table.legal for table in tables)),
            routine=_tightest_box(tuple(table.routine for table in tables)),
        )

    def __len__(self) -> int:
        return len(self._tables)


def _tightest_box(boxes: tuple[Envelope, ...]) -> Envelope:
    return Envelope(
        width=min(box.width for box in boxes),
        height=min(box.height for box in boxes),
        length=min(box.length for box in boxes),
        front_overhang=min(box.front_overhang for box in boxes),
        rear_overhang=min(box.rear_overhang for box in boxes),
    )


def _box(width: str, height: str, length: str, front: str, rear: str) -> Envelope:
    return Envelope(
        Inches.parse(width),
        Inches.parse(height),
        Inches.parse(length),
        Inches.parse(front),
        Inches.parse(rear),
    )


def standard_book() -> SizeBook:
    """The size tables as they stand."""
    book = SizeBook()
    book.file(
        SizeTable(
            HighwayClass.INTERSTATE,
            _box("8'6", "13'6", "75'", "3'", "4'"),
            _box("16'", "16'", "120'", "10'", "15'"),
        )
    )
    book.file(
        SizeTable(
            HighwayClass.ARTERIAL,
            _box("8'6", "13'6", "75'", "3'", "4'"),
            _box("16'", "15'6", "120'", "10'", "15'"),
        )
    )
    book.file(
        SizeTable(
            HighwayClass.STATE_AID,
            _box("8'6", "13'6", "68'", "3'", "4'"),
            _box("14'", "14'6", "100'", "8'", "12'"),
        )
    )
    book.file(
        SizeTable(
            HighwayClass.TOWN_WAY,
            _box("8'6", "13'6", "68'", "3'", "4'"),
            _box("12'", "14'", "80'", "6'", "10'"),
        )
    )
    return book
