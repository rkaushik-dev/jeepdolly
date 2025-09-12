"""The reprint register, which was a pad by the printer until now.

Revision ID: 0011
Revises: 0010
Create Date: 2025-11-17 13:55:26.008411
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0011"
down_revision = "0010"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "reprints",
        sa.Column("permit", sa.String(length=32), nullable=False),
        sa.Column("copy", sa.Integer(), nullable=False),
        sa.Column("issued_on", sa.Date(), nullable=False),
        sa.Column("reason", sa.String(length=32), nullable=False),
        sa.Column("asked_by", sa.String(length=120), nullable=False),
        sa.Column("issued_by", sa.String(length=120), nullable=False),
        sa.Column("note", sa.String(length=500), nullable=False),
        sa.CheckConstraint("copy > 1", name=op.f("ck_reprints_the_original_is_not_a_reprint")),
        sa.ForeignKeyConstraint(
            ["permit"], ["permits.number"], name=op.f("fk_reprints_permit_permits")
        ),
        sa.PrimaryKeyConstraint("permit", "copy", name=op.f("pk_reprints")),
    )
    with op.batch_alter_table("reprints", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_reprints_issued_on"), ["issued_on"], unique=False)
        batch_op.create_index(batch_op.f("ix_reprints_reason"), ["reason"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("reprints", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_reprints_reason"))
        batch_op.drop_index(batch_op.f("ix_reprints_issued_on"))

    op.drop_table("reprints")
