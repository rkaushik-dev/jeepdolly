"""The pilot car register, which the certification order made necessary.

Revision ID: 0007
Revises: 0006
Create Date: 2025-09-15 09:12:44.106882
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0007"
down_revision = "0006"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "pilot_companies",
        sa.Column("ident", sa.String(length=32), nullable=False),
        sa.Column("name", sa.String(length=120), nullable=False),
        sa.Column("town", sa.String(length=120), nullable=False),
        sa.Column("telephone", sa.String(length=32), nullable=False),
        sa.Column("suspended", sa.Boolean(), nullable=False),
        sa.Column("note", sa.String(length=500), nullable=False),
        sa.PrimaryKeyConstraint("ident", name=op.f("pk_pilot_companies")),
    )
    with op.batch_alter_table("pilot_companies", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_pilot_companies_name"), ["name"], unique=False)

    op.create_table(
        "pilot_cards",
        sa.Column("card", sa.String(length=32), nullable=False),
        sa.Column("name", sa.String(length=120), nullable=False),
        sa.Column("company_ident", sa.String(length=32), nullable=False),
        sa.Column("issued", sa.Date(), nullable=False),
        sa.Column("expires", sa.Date(), nullable=False),
        sa.Column("endorsements", sa.String(length=500), nullable=False),
        sa.Column("suspended", sa.Boolean(), nullable=False),
        sa.CheckConstraint("expires > issued", name=op.f("ck_pilot_cards_expires_after_issued")),
        sa.ForeignKeyConstraint(
            ["company_ident"],
            ["pilot_companies.ident"],
            name=op.f("fk_pilot_cards_company_ident_pilot_companies"),
        ),
        sa.PrimaryKeyConstraint("card", name=op.f("pk_pilot_cards")),
    )
    with op.batch_alter_table("pilot_cards", schema=None) as batch_op:
        batch_op.create_index(
            batch_op.f("ix_pilot_cards_company_ident"), ["company_ident"], unique=False
        )
        batch_op.create_index(batch_op.f("ix_pilot_cards_expires"), ["expires"], unique=False)
        batch_op.create_index(batch_op.f("ix_pilot_cards_name"), ["name"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("pilot_cards", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_pilot_cards_name"))
        batch_op.drop_index(batch_op.f("ix_pilot_cards_expires"))
        batch_op.drop_index(batch_op.f("ix_pilot_cards_company_ident"))

    op.drop_table("pilot_cards")

    with op.batch_alter_table("pilot_companies", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_pilot_companies_name"))

    op.drop_table("pilot_companies")
