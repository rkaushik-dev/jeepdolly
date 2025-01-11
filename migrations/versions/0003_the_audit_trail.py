"""The audit trail, which arrived once the interface had people behind it.

Revision ID: 0003
Revises: 0002
Create Date: 2025-05-26 16:20:41.556012
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "audit_trail",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("happened", sa.DateTime(), nullable=False),
        sa.Column("action", sa.String(length=32), nullable=False),
        sa.Column("actor", sa.String(length=120), nullable=False),
        sa.Column("kind", sa.String(length=32), nullable=False),
        sa.Column("reference", sa.String(length=32), nullable=False),
        sa.Column("detail", sa.String(length=500), nullable=False),
        sa.CheckConstraint("length(action) > 0", name=op.f("ck_audit_trail_action_not_empty")),
        sa.CheckConstraint("length(actor) > 0", name=op.f("ck_audit_trail_actor_not_empty")),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_audit_trail")),
    )
    with op.batch_alter_table("audit_trail", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_audit_trail_action"), ["action"], unique=False)
        batch_op.create_index(batch_op.f("ix_audit_trail_actor"), ["actor"], unique=False)
        batch_op.create_index(batch_op.f("ix_audit_trail_happened"), ["happened"], unique=False)
        batch_op.create_index(batch_op.f("ix_audit_trail_reference"), ["reference"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("audit_trail", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_audit_trail_reference"))
        batch_op.drop_index(batch_op.f("ix_audit_trail_happened"))
        batch_op.drop_index(batch_op.f("ix_audit_trail_actor"))
        batch_op.drop_index(batch_op.f("ix_audit_trail_action"))

    op.drop_table("audit_trail")
