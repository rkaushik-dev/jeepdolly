"""The office: every book a decision reads, wired together once.

This used to live in the same file as the deciding, which meant that anything
wanting the type had to import the module that runs the whole procedure. The
office is a bag of books and nothing else, so it sits on its own and the
things that need one no longer drag the procedure in with it.
"""

from __future__ import annotations

from dataclasses import dataclass

from ..conditions.library import ConditionLibrary
from ..domain.carrier import CarrierRegister
from ..domain.commodity import CommodityBook
from ..domain.network import Network
from ..domain.restriction import RestrictionBook
from ..enforcement.standing import EnforcementBook
from ..escorts.register import EscortRegister
from ..fees.schedule import ScheduleBook
from ..limits.size import SizeBook
from ..limits.statutory import StatutoryBook
from ..routing.turns import TurnBook
from ..seasons import SeasonBook
from ..structures.inventory import StructureBook
from ..travelcalendar import PermitCalendar
from .numbering import Numbering


@dataclass
class Office:
    """Every book the decision reads, wired together once."""

    network: Network
    restrictions: RestrictionBook
    seasons: SeasonBook
    statutes: StatutoryBook
    sizes: SizeBook
    commodities: CommodityBook
    carriers: CarrierRegister
    structures: StructureBook
    turns: TurnBook
    escorts: EscortRegister
    enforcement: EnforcementBook
    schedules: ScheduleBook
    library: ConditionLibrary
    numbering: Numbering

    def calendar(self, year: int) -> PermitCalendar:
        return PermitCalendar(year)
