"""Citations off the roadside, which the office had kept on paper.

Revision ID: 0009
Revises: 0008
Create Date: 2025-10-06 10:22:17.550913
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0009"
down_revision = "0008"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "citations",
        sa.Column("number", sa.String(length=32), nullable=False),
        sa.Column("written", sa.Date(), nullable=False),
        sa.Column("filed", sa.Date(), nullable=False),
        sa.Column("officer", sa.String(length=120), nullable=False),
        sa.Column("usdot", sa.Integer(), nullable=False),
        sa.Column("carrier_name", sa.String(length=120), nullable=False),
        sa.Column("offence", sa.String(length=32), nullable=False),
        sa.Column("place", sa.String(length=120), nullable=False),
        sa.Column("permit", sa.String(length=32), nullable=True),
        sa.Column("over", sa.Integer(), nullable=True),
        sa.Column("base", sa.Integer(), nullable=True),
        sa.Column("multiplier", sa.Integer(), nullable=False),
        sa.Column("amount", sa.Integer(), nullable=True),
        sa.Column("schedule", sa.Date(), nullable=False),
        sa.Column("state", sa.String(length=32), nullable=False),
        sa.Column("closing_reason", sa.String(length=500), nullable=False),
        sa.Column("note", sa.String(length=500), nullable=False),
        sa.CheckConstraint("filed >= written", name=op.f("ck_citations_filed_after_written")),
        sa.CheckConstraint(
            "multiplier between 1 and 3", name=op.f("ck_citations_multiplier_in_range")
        ),
        sa.ForeignKeyConstraint(
            ["permit"], ["permits.number"], name=op.f("fk_citations_permit_permits")
        ),
        sa.ForeignKeyConstraint(
            ["usdot"], ["carriers.usdot"], name=op.f("fk_citations_usdot_carriers")
        ),
        sa.PrimaryKeyConstraint("number", name=op.f("pk_citations")),
    )
    with op.batch_alter_table("citations", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_citations_filed"), ["filed"], unique=False)
        batch_op.create_index(batch_op.f("ix_citations_offence"), ["offence"], unique=False)
        batch_op.create_index(batch_op.f("ix_citations_permit"), ["permit"], unique=False)
        batch_op.create_index(batch_op.f("ix_citations_state"), ["state"], unique=False)
        batch_op.create_index(batch_op.f("ix_citations_usdot"), ["usdot"], unique=False)
        batch_op.create_index(batch_op.f("ix_citations_written"), ["written"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("citations", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_citations_written"))
        batch_op.drop_index(batch_op.f("ix_citations_usdot"))
        batch_op.drop_index(batch_op.f("ix_citations_state"))
        batch_op.drop_index(batch_op.f("ix_citations_permit"))
        batch_op.drop_index(batch_op.f("ix_citations_offence"))
        batch_op.drop_index(batch_op.f("ix_citations_filed"))

    op.drop_table("citations")
