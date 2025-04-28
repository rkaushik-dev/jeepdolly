"""Receipts, which are the other half of a permit.

Revision ID: 0006
Revises: 0005
Create Date: 2025-08-19 11:31:08.771324
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0006"
down_revision = "0005"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "receipts",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("permit", sa.String(length=32), nullable=False),
        sa.Column("taken", sa.Date(), nullable=False),
        sa.Column("method", sa.String(length=32), nullable=False),
        sa.Column("amount", sa.Integer(), nullable=True),
        sa.Column("taken_by", sa.String(length=120), nullable=False),
        sa.Column("reference", sa.String(length=32), nullable=False),
        sa.Column("note", sa.String(length=500), nullable=False),
        sa.CheckConstraint("amount <> 0", name=op.f("ck_receipts_amount_not_nothing")),
        sa.CheckConstraint("length(method) > 0", name=op.f("ck_receipts_method_not_empty")),
        sa.ForeignKeyConstraint(
            ["permit"], ["permits.number"], name=op.f("fk_receipts_permit_permits")
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_receipts")),
    )
    with op.batch_alter_table("receipts", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_receipts_method"), ["method"], unique=False)
        batch_op.create_index(batch_op.f("ix_receipts_permit"), ["permit"], unique=False)
        batch_op.create_index(batch_op.f("ix_receipts_taken"), ["taken"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("receipts", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_receipts_taken"))
        batch_op.drop_index(batch_op.f("ix_receipts_permit"))
        batch_op.drop_index(batch_op.f("ix_receipts_method"))

    op.drop_table("receipts")
