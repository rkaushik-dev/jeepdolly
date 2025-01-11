"""Who may ask this service for anything, and what they may ask for.

An api key is issued to a person, not to a company and not to a machine. When
a permit is written through this interface the office wants a name against it,
and a shared key means the name is a department, which is the same as no name.

The key itself is not stored. The table holds a hash, so a copy of the
database is not a copy of everybody's credentials, and a key that has been
lost cannot be read back out to whoever lost it. It is revoked and a new one
is issued.

A revoked key is kept. The permits written with it are still on file with the
name attached, and deleting the row would leave nothing to join them to.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from ..base import CODE_LENGTH, NAME_LENGTH, TEXT_LENGTH, Base

#: A sha256 in hexadecimal.
DIGEST_LENGTH = 64


class ApiKeyRow(Base):
    """One key, its owner, and what it is allowed to do."""

    __tablename__ = "api_keys"
    __table_args__ = (
        CheckConstraint("length(digest) = 64", name="digest_is_a_sha256"),
        CheckConstraint("length(person) > 0", name="person_not_empty"),
    )

    ident: Mapped[str] = mapped_column(String(CODE_LENGTH), primary_key=True)
    digest: Mapped[str] = mapped_column(String(DIGEST_LENGTH), unique=True, index=True)
    person: Mapped[str] = mapped_column(String(NAME_LENGTH))
    role: Mapped[str] = mapped_column(String(CODE_LENGTH), index=True)
    usdot: Mapped[int | None] = mapped_column(Integer, nullable=True)
    issued: Mapped[date] = mapped_column(Date)
    revoked: Mapped[date | None] = mapped_column(Date, nullable=True)
    note: Mapped[str] = mapped_column(String(TEXT_LENGTH), default="")

    def live_on(self, day: date) -> bool:
        if day < self.issued:
            return False
        return self.revoked is None or day < self.revoked

    def __repr__(self) -> str:
        return f"<ApiKeyRow {self.ident} {self.role}>"
