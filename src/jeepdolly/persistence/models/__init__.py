"""The tables, split by the part of the office that owns them.

Import this package rather than the modules under it. A table that is not
imported is not on the metadata, and a table that is not on the metadata is
one ``create_all`` quietly does not build, which is a bad afternoon.
"""

from .access import ApiKeyRow
from .applications import (
    ApplicationAxleRow,
    ApplicationPieceRow,
    ApplicationRow,
    ApplicationUnitRow,
)
from .audit import AuditRow
from .carriers import BondRow, CarrierRow, InsuranceRow, SuspensionRow
from .enforcement import CitationRow
from .escorts import PilotCardRow, PilotCompanyRow
from .network import NodeRow, RestrictionRow, SegmentRow
from .permits import CounterRow, FeeLineRow, PermitConditionRow, PermitRow
from .receipts import ReceiptRow
from .reference import CommodityRow, ConditionTextRow
from .seasons import SeasonOrderRow
from .structures import ReviewMoveRow, ReviewRow, StructureRow
from .tariff import (
    BaseFeeRow,
    MileFeeRow,
    ScheduleRow,
    SizeRow,
    StatutoryRow,
    WeightStepRow,
)

__all__ = [
    "ApiKeyRow",
    "ApplicationAxleRow",
    "ApplicationPieceRow",
    "ApplicationRow",
    "ApplicationUnitRow",
    "AuditRow",
    "BaseFeeRow",
    "BondRow",
    "CarrierRow",
    "CitationRow",
    "CommodityRow",
    "ConditionTextRow",
    "CounterRow",
    "FeeLineRow",
    "InsuranceRow",
    "MileFeeRow",
    "NodeRow",
    "PermitConditionRow",
    "PilotCardRow",
    "PilotCompanyRow",
    "PermitRow",
    "ReceiptRow",
    "RestrictionRow",
    "ReviewMoveRow",
    "ReviewRow",
    "SeasonOrderRow",
    "ScheduleRow",
    "SegmentRow",
    "SizeRow",
    "StatutoryRow",
    "StructureRow",
    "SuspensionRow",
    "WeightStepRow",
]
