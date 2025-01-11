"""Keys for the interface, which arrived with the http surface.

Revision ID: 0002
Revises: 0001
Create Date: 2025-05-24 10:12:48.902117
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "api_keys",
        sa.Column("ident", sa.String(length=32), nullable=False),
        sa.Column("digest", sa.String(length=64), nullable=False),
        sa.Column("person", sa.String(length=120), nullable=False),
        sa.Column("role", sa.String(length=32), nullable=False),
        sa.Column("usdot", sa.Integer(), nullable=True),
        sa.Column("issued", sa.Date(), nullable=False),
        sa.Column("revoked", sa.Date(), nullable=True),
        sa.Column("note", sa.String(length=500), nullable=False),
        sa.CheckConstraint("length(digest) = 64", name=op.f("ck_api_keys_digest_is_a_sha256")),
        sa.CheckConstraint("length(person) > 0", name=op.f("ck_api_keys_person_not_empty")),
        sa.PrimaryKeyConstraint("ident", name=op.f("pk_api_keys")),
    )
    with op.batch_alter_table("api_keys", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_api_keys_digest"), ["digest"], unique=True)
        batch_op.create_index(batch_op.f("ix_api_keys_role"), ["role"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("api_keys", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_api_keys_role"))
        batch_op.drop_index(batch_op.f("ix_api_keys_digest"))

    op.drop_table("api_keys")
