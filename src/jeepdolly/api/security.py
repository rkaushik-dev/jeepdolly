"""Api keys, the four roles, and what each of them may do.

A key arrives in the ``X-Permit-Key`` header. It is hashed and looked up. If
there is no live row for that hash the answer is 401 and nothing else, with no
hint about whether the key was wrong or revoked.

Four roles and they are a ladder, which is unusual enough to say out loud.
Most permission systems are a matrix and this one is not, because the office
is eleven people and a matrix would be eleven columns of yes.

``reader`` may look at anything. Enforcement people and the state police sit
here, and so does a carrier looking at its own permits.

``clerk`` may file and submit applications and issue ordinary permits.

``engineer`` may do all of that and clear or refuse a study.

``supervisor`` may do all of that and void a permit, suspend a carrier, and
edit the road log.

A carrier key carries its usdot number, and a route that reads permits will
only show that carrier its own. That is the one piece of this that is not a
ladder and it is checked by the route rather than here, because it is about
the row and not about the person.
"""

from __future__ import annotations

import hashlib
import secrets
from collections.abc import Callable
from dataclasses import dataclass
from datetime import date
from enum import Enum

from fastapi import Depends, Header, HTTPException
from sqlalchemy import select

from ..persistence.models.access import ApiKeyRow
from ..persistence.unitofwork import UnitOfWork
from .deps import unit_of_work

HEADER = "X-Permit-Key"

#: Keys are issued with this many bytes of randomness.
KEY_BYTES = 24


class Role(str, Enum):
    """What a key may do, as a ladder."""

    READER = "reader"
    CLERK = "clerk"
    ENGINEER = "engineer"
    SUPERVISOR = "supervisor"

    @property
    def rank(self) -> int:
        return LADDER.index(self)

    def reaches(self, wanted: Role) -> bool:
        return self.rank >= wanted.rank

    def __str__(self) -> str:
        return self.value


LADDER = [Role.READER, Role.CLERK, Role.ENGINEER, Role.SUPERVISOR]


@dataclass(frozen=True)
class Caller:
    """Who is asking, once the key has been read."""

    ident: str
    person: str
    role: Role
    usdot: int | None = None

    @property
    def is_carrier(self) -> bool:
        return self.usdot is not None

    def may(self, wanted: Role) -> bool:
        return self.role.reaches(wanted)

    def __str__(self) -> str:
        return f"{self.person} ({self.role})"


def digest_of(key: str) -> str:
    """The hash the table holds, which is all it ever holds."""
    return hashlib.sha256(key.encode("utf-8")).hexdigest()


def new_key() -> str:
    """A fresh key, handed out once and never stored."""
    return secrets.token_urlsafe(KEY_BYTES)


def issue_key(
    work: UnitOfWork,
    ident: str,
    person: str,
    role: Role,
    on: date,
    usdot: int | None = None,
    note: str = "",
) -> tuple[str, ApiKeyRow]:
    """Make a key, store its hash, and hand the key back exactly once."""
    key = new_key()
    row = ApiKeyRow(
        ident=ident,
        digest=digest_of(key),
        person=person,
        role=role.value,
        usdot=usdot,
        issued=on,
        note=note,
    )
    session = work.session
    if session is None:  # pragma: no cover - guarded by the unit of work
        raise RuntimeError("the unit of work is closed")
    session.add(row)
    return key, row


def caller_for(work: UnitOfWork, key: str | None, on: date) -> Caller:
    """Read the key into a caller, or refuse."""
    if not key:
        raise HTTPException(status_code=401, detail="a permit key is required")
    session = work.session
    if session is None:  # pragma: no cover - guarded by the unit of work
        raise RuntimeError("the unit of work is closed")
    found = session.scalars(
        select(ApiKeyRow).where(ApiKeyRow.digest == digest_of(key))
    ).one_or_none()
    if found is None or not found.live_on(on):
        raise HTTPException(status_code=401, detail="that key is not in use")
    return Caller(found.ident, found.person, Role(found.role), found.usdot)


def caller(
    work: UnitOfWork = Depends(unit_of_work),
    permit_key: str | None = Header(default=None, alias=HEADER),
) -> Caller:
    """The caller behind this request."""
    return caller_for(work, permit_key, date.today())


def needs(wanted: Role) -> Callable[..., Caller]:
    """A dependency that refuses anybody below a rung of the ladder."""

    def guard(who: Caller = Depends(caller)) -> Caller:
        if not who.may(wanted):
            raise HTTPException(
                status_code=403, detail=f"this needs a {wanted}, and you are a {who.role}"
            )
        return who

    return guard
