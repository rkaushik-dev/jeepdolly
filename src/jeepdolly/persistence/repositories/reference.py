"""The commodity table, read whole because it is small."""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from ...domain.commodity import CommodityBook
from ...errors import NotOnFile
from ..models.reference import CommodityRow


class CommodityRepository:
    """The commodity table, read whole because it is small and read often."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def book(self, on: date | None = None) -> CommodityBook:
        """Every live commodity, as the book the rules read."""
        book = CommodityBook()
        for row in self.session.scalars(select(CommodityRow).order_by(CommodityRow.code)):
            if on is not None and row.retired is not None and row.retired <= on:
                continue
            book.add(row.to_domain())
        return book

    def retire(self, code: str, on: date) -> CommodityRow:
        row = self.session.get(CommodityRow, code)
        if row is None:
            raise NotOnFile(f"no commodity {code}", code=code)
        row.retired = on
        return row
