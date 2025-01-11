"""The audit trail: what was done, by whom, to which document, and when.

One table, append only, and the only table in the schema with no update path
of any kind. Nothing in this system has a method that edits a trail row and
nothing ever should.

The shape is deliberately narrow. An action is a dotted word from a fixed
vocabulary, not a sentence, because the trail is grepped and counted and a
sentence cannot be either. The detail is free text and is the only place a
person writes anything.

There is no foreign key to the document. That is not an oversight. The trail
outlives the things it describes: an application that was withdrawn and
purged, a carrier record merged into another, a permit from a schema three
migrations ago. A trail row that could be deleted by a cascade is not a trail.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import CheckConstraint, DateTime, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from ..base import CODE_LENGTH, NAME_LENGTH, TEXT_LENGTH, Base


class AuditRow(Base):
    """One thing that happened, written once and never touched again."""

    __tablename__ = "audit_trail"
    __table_args__ = (
        CheckConstraint("length(action) > 0", name="action_not_empty"),
        CheckConstraint("length(actor) > 0", name="actor_not_empty"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    happened: Mapped[datetime] = mapped_column(DateTime, index=True)
    action: Mapped[str] = mapped_column(String(CODE_LENGTH), index=True)
    actor: Mapped[str] = mapped_column(String(NAME_LENGTH), index=True)
    kind: Mapped[str] = mapped_column(String(CODE_LENGTH))
    reference: Mapped[str] = mapped_column(String(CODE_LENGTH), index=True)
    detail: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")

    def __repr__(self) -> str:
        return f"<AuditRow {self.action} {self.kind}:{self.reference}>"
