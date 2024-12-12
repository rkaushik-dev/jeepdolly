"""The tables, split by the part of the office that owns them.

Import this package rather than the modules under it. A table that is not
imported is not on the metadata, and a table that is not on the metadata is
one ``create_all`` quietly does not build, which is a bad afternoon.
"""

from .applications import (
    ApplicationAxleRow,
    ApplicationPieceRow,
    ApplicationRow,
    ApplicationUnitRow,
)
from .carriers import BondRow, CarrierRow, InsuranceRow, SuspensionRow
from .network import NodeRow, RestrictionRow, SegmentRow
from .reference import CommodityRow, ConditionTextRow
from .structures import ReviewMoveRow, ReviewRow, StructureRow

__all__ = [
    "ApplicationAxleRow",
    "ApplicationPieceRow",
    "ApplicationRow",
    "ApplicationUnitRow",
    "BondRow",
    "CarrierRow",
    "CommodityRow",
    "ConditionTextRow",
    "InsuranceRow",
    "NodeRow",
    "RestrictionRow",
    "ReviewMoveRow",
    "ReviewRow",
    "SegmentRow",
    "StructureRow",
    "SuspensionRow",
]
