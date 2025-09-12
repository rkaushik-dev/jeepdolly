"""Writing to the trail, and the vocabulary of actions that may be written.

Every action is one of the words in ``ACTIONS`` and a caller that invents one
is refused. That looks fussy and it is the whole value of the table: a trail
where half the rows say ``permit.issued`` and half say ``issued permit`` is a
trail nobody can count.

The trail is written inside the caller's transaction. If the work rolls back,
so does the record of it, which is right: the trail says what happened, and
something that was rolled back did not happen.

Reading is by document or by person, both of which are the questions actually
asked. Nobody has ever asked this office for the whole trail.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..errors import Impossible
from ..persistence.models.audit import AuditRow

#: Every action the trail knows about. Adding one is a deliberate act.
ACTIONS = frozenset(
    {
        "application.filed",
        "application.submitted",
        "application.withdrawn",
        "application.refused",
        "application.pilots_named",
        "permit.issued",
        "permit.voided",
        "permit.reprinted",
        "permit.amended",
        "review.opened",
        "review.assigned",
        "review.cleared",
        "review.refused",
        "carrier.registered",
        "carrier.suspended",
        "carrier.restored",
        "road.closed",
        "road.opened",
        "structure.posted",
        "tariff.filed",
        "pilot.company_filed",
        "pilot.company_suspended",
        "pilot.company_restored",
        "pilot.card_issued",
        "pilot.card_suspended",
        "pilot.card_restored",
        "citation.filed",
        "citation.contested",
        "citation.upheld",
        "citation.dismissed",
        "citation.paid",
        "citation.voided",
        "payment.taken",
        "key.issued",
        "key.revoked",
    }
)


def record(
    session: Session,
    action: str,
    actor: str,
    kind: str,
    reference: str,
    at: datetime | None = None,
    detail: str = "",
) -> AuditRow:
    """Write one line on the trail, in the caller's transaction."""
    if action not in ACTIONS:
        raise Impossible(f"{action!r} is not an action the trail knows", action=action)
    if not actor.strip():
        raise Impossible("something did this and the trail says who")
    row = AuditRow(
        happened=at or datetime.now(),
        action=action,
        actor=actor,
        kind=kind,
        reference=reference,
        detail=detail,
    )
    session.add(row)
    return row


def for_document(session: Session, kind: str, reference: str) -> tuple[AuditRow, ...]:
    """Everything that has happened to one document, oldest first."""
    query = (
        select(AuditRow)
        .where(AuditRow.kind == kind, AuditRow.reference == reference)
        .order_by(AuditRow.happened, AuditRow.id)
    )
    return tuple(session.scalars(query).all())


def by_person(session: Session, actor: str) -> tuple[AuditRow, ...]:
    """Everything one person has done, newest first."""
    query = (
        select(AuditRow)
        .where(AuditRow.actor == actor)
        .order_by(AuditRow.happened.desc(), AuditRow.id.desc())
    )
    return tuple(session.scalars(query).all())


def of_action(session: Session, action: str) -> tuple[AuditRow, ...]:
    """Every time one thing was done, for the monthly count."""
    query = select(AuditRow).where(AuditRow.action == action).order_by(AuditRow.happened)
    return tuple(session.scalars(query).all())


def between(session: Session, start: datetime, end: datetime) -> tuple[AuditRow, ...]:
    """The trail for a span, which is what an auditor asks for."""
    if end < start:
        raise Impossible("a span of the trail ends after it starts")
    query = (
        select(AuditRow)
        .where(AuditRow.happened >= start, AuditRow.happened <= end)
        .order_by(AuditRow.happened, AuditRow.id)
    )
    return tuple(session.scalars(query).all())
