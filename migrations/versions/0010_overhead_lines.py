"""Overhead lines, off the survey the office finished this autumn.

Revision ID: 0010
Revises: 0009
Create Date: 2025-10-28 08:41:03.229116
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0010"
down_revision = "0009"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "line_owners",
        sa.Column("ident", sa.String(length=32), nullable=False),
        sa.Column("name", sa.String(length=120), nullable=False),
        sa.Column("telephone", sa.String(length=32), nullable=False),
        sa.Column("notice_days", sa.Integer(), nullable=False),
        sa.Column("note", sa.String(length=500), nullable=False),
        sa.CheckConstraint("notice_days >= 0", name=op.f("ck_line_owners_notice_is_not_negative")),
        sa.PrimaryKeyConstraint("ident", name=op.f("pk_line_owners")),
    )
    with op.batch_alter_table("line_owners", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_line_owners_name"), ["name"], unique=False)

    op.create_table(
        "line_crossings",
        sa.Column("ident", sa.String(length=32), nullable=False),
        sa.Column("segment_ident", sa.String(length=32), nullable=False),
        sa.Column("place", sa.String(length=120), nullable=False),
        sa.Column("height", sa.Integer(), nullable=True),
        sa.Column("owner_ident", sa.String(length=32), nullable=False),
        sa.Column("lift_to", sa.Integer(), nullable=True),
        sa.Column("measured", sa.Date(), nullable=True),
        sa.Column("note", sa.String(length=500), nullable=False),
        sa.CheckConstraint("height > 0", name=op.f("ck_line_crossings_height_is_measured")),
        sa.CheckConstraint(
            "lift_to is null or lift_to > height",
            name=op.f("ck_line_crossings_lift_is_upwards"),
        ),
        sa.ForeignKeyConstraint(
            ["owner_ident"],
            ["line_owners.ident"],
            name=op.f("fk_line_crossings_owner_ident_line_owners"),
        ),
        sa.ForeignKeyConstraint(
            ["segment_ident"],
            ["segments.ident"],
            name=op.f("fk_line_crossings_segment_ident_segments"),
        ),
        sa.PrimaryKeyConstraint("ident", name=op.f("pk_line_crossings")),
    )
    with op.batch_alter_table("line_crossings", schema=None) as batch_op:
        batch_op.create_index(
            batch_op.f("ix_line_crossings_owner_ident"), ["owner_ident"], unique=False
        )
        batch_op.create_index(
            batch_op.f("ix_line_crossings_segment_ident"), ["segment_ident"], unique=False
        )


def downgrade() -> None:
    with op.batch_alter_table("line_crossings", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_line_crossings_segment_ident"))
        batch_op.drop_index(batch_op.f("ix_line_crossings_owner_ident"))

    op.drop_table("line_crossings")

    with op.batch_alter_table("line_owners", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_line_owners_name"))

    op.drop_table("line_owners")
