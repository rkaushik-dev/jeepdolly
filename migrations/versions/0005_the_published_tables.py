"""The published tables, which used to live in the code.

Revision ID: 0005
Revises: 0004
Create Date: 2025-07-19 14:02:11.884201
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "fee_base",
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("kind", sa.String(length=32), nullable=False),
        sa.Column("amount", sa.Integer(), nullable=True),
        sa.PrimaryKeyConstraint("effective", "kind", name=op.f("pk_fee_base")),
    )
    op.create_table(
        "fee_per_mile",
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("highway_class", sa.String(length=32), nullable=False),
        sa.Column("amount", sa.Integer(), nullable=True),
        sa.PrimaryKeyConstraint("effective", "highway_class", name=op.f("pk_fee_per_mile")),
    )
    op.create_table(
        "fee_schedules",
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("review", sa.Integer(), nullable=True),
        sa.Column("amendment", sa.Integer(), nullable=True),
        sa.Column("minimum", sa.Integer(), nullable=True),
        sa.Column("annual", sa.Integer(), nullable=True),
        sa.PrimaryKeyConstraint("effective", name=op.f("pk_fee_schedules")),
    )
    op.create_table(
        "fee_weight_steps",
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("up_to", sa.Integer(), nullable=True),
        sa.Column("per_block", sa.Integer(), nullable=True),
        sa.Column("note", sa.String(length=120), nullable=False),
        sa.CheckConstraint("position >= 0", name=op.f("ck_fee_weight_steps_position_not_negative")),
        sa.PrimaryKeyConstraint("effective", "position", name=op.f("pk_fee_weight_steps")),
    )
    op.create_table(
        "size_limits",
        sa.Column("highway_class", sa.String(length=32), nullable=False),
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("legal_width", sa.Integer(), nullable=True),
        sa.Column("legal_height", sa.Integer(), nullable=True),
        sa.Column("legal_length", sa.Integer(), nullable=True),
        sa.Column("legal_front", sa.Integer(), nullable=True),
        sa.Column("legal_rear", sa.Integer(), nullable=True),
        sa.Column("routine_width", sa.Integer(), nullable=True),
        sa.Column("routine_height", sa.Integer(), nullable=True),
        sa.Column("routine_length", sa.Integer(), nullable=True),
        sa.Column("routine_front", sa.Integer(), nullable=True),
        sa.Column("routine_rear", sa.Integer(), nullable=True),
        sa.PrimaryKeyConstraint("highway_class", "effective", name=op.f("pk_size_limits")),
    )
    op.create_table(
        "statutory_limits",
        sa.Column("highway_class", sa.String(length=32), nullable=False),
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("single", sa.Integer(), nullable=True),
        sa.Column("tandem", sa.Integer(), nullable=True),
        sa.Column("tridem", sa.Integer(), nullable=True),
        sa.Column("quad", sa.Integer(), nullable=True),
        sa.Column("beyond", sa.Integer(), nullable=True),
        sa.Column("gross", sa.Integer(), nullable=True),
        sa.Column("tire_rate", sa.Integer(), nullable=False),
        sa.CheckConstraint("tandem > single", name=op.f("ck_statutory_limits_tandem_above_single")),
        sa.CheckConstraint("tire_rate > 0", name=op.f("ck_statutory_limits_tire_rate_positive")),
        sa.PrimaryKeyConstraint("highway_class", "effective", name=op.f("pk_statutory_limits")),
    )


def downgrade() -> None:
    op.drop_table("statutory_limits")
    op.drop_table("size_limits")
    op.drop_table("fee_weight_steps")
    op.drop_table("fee_schedules")
    op.drop_table("fee_per_mile")
    op.drop_table("fee_base")
