"""The pilot car cards a hauler names on the application.

Revision ID: 0008
Revises: 0007
Create Date: 2025-09-19 14:03:51.442097
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0008"
down_revision = "0007"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Filled with the empty string rather than left null, because every reader
    # of this column splits it and a null would be a special case in each of
    # them. The existing applications named nobody, which is what empty means.
    with op.batch_alter_table("applications", schema=None) as batch_op:
        batch_op.add_column(
            sa.Column("escort_cards", sa.String(length=500), nullable=False, server_default="")
        )


def downgrade() -> None:
    with op.batch_alter_table("applications", schema=None) as batch_op:
        batch_op.drop_column("escort_cards")
