"""Seasonal orders, which used to be typed in by hand every spring.

Revision ID: 0004
Revises: 0003
Create Date: 2025-07-08 09:14:22.104553
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "season_orders",
        sa.Column("number", sa.String(length=32), nullable=False),
        sa.Column("region", sa.String(length=120), nullable=False),
        sa.Column("kind", sa.String(length=32), nullable=False),
        sa.Column("starts", sa.Date(), nullable=False),
        sa.Column("ends", sa.Date(), nullable=False),
        sa.Column("share", sa.Integer(), nullable=False),
        sa.Column("signed_by", sa.String(length=120), nullable=False),
        sa.Column("note", sa.String(length=500), nullable=False),
        sa.CheckConstraint("ends >= starts", name=op.f("ck_season_orders_ends_after_it_starts")),
        sa.CheckConstraint(
            "share between 1 and 200", name=op.f("ck_season_orders_share_is_a_percent")
        ),
        sa.PrimaryKeyConstraint("number", name=op.f("pk_season_orders")),
    )
    with op.batch_alter_table("season_orders", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_season_orders_region"), ["region"], unique=False)
        batch_op.create_index("ix_season_orders_region_starts", ["region", "starts"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("season_orders", schema=None) as batch_op:
        batch_op.drop_index("ix_season_orders_region_starts")
        batch_op.drop_index(batch_op.f("ix_season_orders_region"))

    op.drop_table("season_orders")
