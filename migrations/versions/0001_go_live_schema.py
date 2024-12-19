"""The schema as it stood on the day the office went live.

Written by autogenerate against the models and then read line by line, which
is the only way a migration is produced here.

One edit is made to every generated file and it is always the same one. The
custom column types are written back as the types they are stored in: a weight
is an integer, an enumeration is a string. A migration that imports application
code stops working the day somebody renames a class, and a migration that
stopped working is a database nobody can build from scratch.

Revision ID: 0001
Revises: nothing, this is the first one
Create Date: 2025-04-29 08:41:02.118350
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "carriers",
        sa.Column("usdot", sa.Integer(), autoincrement=False, nullable=False),
        sa.Column("name", sa.String(length=120), nullable=False),
        sa.Column("town", sa.String(length=120), nullable=False),
        sa.Column("state", sa.String(length=2), nullable=False),
        sa.Column("billing", sa.String(length=32), nullable=False),
        sa.CheckConstraint("length(state) = 2", name=op.f("ck_carriers_state_is_two_letters")),
        sa.CheckConstraint(
            "usdot > 0 and usdot < 100000000", name=op.f("ck_carriers_usdot_is_a_number")
        ),
        sa.PrimaryKeyConstraint("usdot", name=op.f("pk_carriers")),
    )
    with op.batch_alter_table("carriers", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_carriers_name"), ["name"], unique=False)

    op.create_table(
        "commodities",
        sa.Column("code", sa.String(length=32), nullable=False),
        sa.Column("description", sa.String(length=120), nullable=False),
        sa.Column("family", sa.String(length=32), nullable=False),
        sa.Column("divisible", sa.Boolean(), nullable=False),
        sa.Column("allowance", sa.String(length=120), nullable=True),
        sa.Column("tip_limit_degrees", sa.Integer(), nullable=True),
        sa.Column("high_centre", sa.Boolean(), nullable=False),
        sa.Column("retired", sa.Date(), nullable=True),
        sa.Column("changed_by", sa.String(length=120), nullable=False),
        sa.Column("changed_on", sa.Date(), nullable=True),
        sa.CheckConstraint("length(code) > 0", name=op.f("ck_commodities_code_not_empty")),
        sa.CheckConstraint(
            "tip_limit_degrees is null or (tip_limit_degrees between 1 and 45)",
            name=op.f("ck_commodities_tip_limit_is_an_angle"),
        ),
        sa.PrimaryKeyConstraint("code", name=op.f("pk_commodities")),
    )
    op.create_table(
        "condition_texts",
        sa.Column("code", sa.String(length=32), nullable=False),
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("category", sa.String(length=32), nullable=False),
        sa.Column("body", sa.Text(length=500), nullable=False),
        sa.Column("print_order", sa.Integer(), nullable=False),
        sa.Column("changed_by", sa.String(length=120), nullable=False),
        sa.CheckConstraint("length(body) > 0", name=op.f("ck_condition_texts_body_not_empty")),
        sa.CheckConstraint(
            "print_order >= 0", name=op.f("ck_condition_texts_print_order_not_negative")
        ),
        sa.PrimaryKeyConstraint("code", "effective", name=op.f("pk_condition_texts")),
    )
    op.create_table(
        "nodes",
        sa.Column("name", sa.String(length=32), nullable=False),
        sa.Column("town", sa.String(length=120), nullable=False),
        sa.PrimaryKeyConstraint("name", name=op.f("pk_nodes")),
    )
    op.create_table(
        "number_counters",
        sa.Column("year", sa.Integer(), nullable=False),
        sa.Column("series", sa.String(length=2), nullable=False),
        sa.Column("next_sequence", sa.Integer(), nullable=False),
        sa.CheckConstraint(
            "next_sequence >= 1", name=op.f("ck_number_counters_sequence_starts_at_one")
        ),
        sa.PrimaryKeyConstraint("year", "series", name=op.f("pk_number_counters")),
    )
    op.create_table(
        "reviews",
        sa.Column("reference", sa.String(length=32), nullable=False),
        sa.Column("opened", sa.Date(), nullable=False),
        sa.Column("state", sa.String(length=32), nullable=False),
        sa.Column("engineer", sa.String(length=120), nullable=True),
        sa.Column("structures", sa.String(length=500), nullable=False),
        sa.Column("conditions", sa.String(length=500), nullable=False),
        sa.PrimaryKeyConstraint("reference", name=op.f("pk_reviews")),
    )
    with op.batch_alter_table("reviews", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_reviews_opened"), ["opened"], unique=False)
        batch_op.create_index(batch_op.f("ix_reviews_state"), ["state"], unique=False)

    op.create_table(
        "applications",
        sa.Column("reference", sa.String(length=32), nullable=False),
        sa.Column("usdot", sa.Integer(), nullable=False),
        sa.Column("commodity", sa.String(length=32), nullable=False),
        sa.Column("origin", sa.String(length=32), nullable=False),
        sa.Column("destination", sa.String(length=32), nullable=False),
        sa.Column("travel_start", sa.Date(), nullable=False),
        sa.Column("travel_end", sa.Date(), nullable=False),
        sa.Column("filed", sa.Date(), nullable=False),
        sa.Column("state", sa.String(length=32), nullable=False),
        sa.Column("applicant_name", sa.String(length=120), nullable=False),
        sa.Column("applicant_phone", sa.String(length=32), nullable=False),
        sa.Column("applicant_email", sa.String(length=120), nullable=False),
        sa.Column("route", sa.String(length=500), nullable=False),
        sa.Column("notes", sa.String(length=500), nullable=False),
        sa.Column("width", sa.Integer(), nullable=True),
        sa.Column("height", sa.Integer(), nullable=True),
        sa.Column("length", sa.Integer(), nullable=True),
        sa.Column("front_overhang", sa.Integer(), nullable=True),
        sa.Column("rear_overhang", sa.Integer(), nullable=True),
        sa.CheckConstraint(
            "origin <> destination", name=op.f("ck_applications_trip_goes_somewhere")
        ),
        sa.CheckConstraint(
            "travel_end >= travel_start", name=op.f("ck_applications_span_ends_after_it_starts")
        ),
        sa.ForeignKeyConstraint(
            ["commodity"], ["commodities.code"], name=op.f("fk_applications_commodity_commodities")
        ),
        sa.ForeignKeyConstraint(
            ["usdot"], ["carriers.usdot"], name=op.f("fk_applications_usdot_carriers")
        ),
        sa.PrimaryKeyConstraint("reference", name=op.f("pk_applications")),
    )
    with op.batch_alter_table("applications", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_applications_filed"), ["filed"], unique=False)
        batch_op.create_index(batch_op.f("ix_applications_state"), ["state"], unique=False)
        batch_op.create_index(
            batch_op.f("ix_applications_travel_start"), ["travel_start"], unique=False
        )
        batch_op.create_index(batch_op.f("ix_applications_usdot"), ["usdot"], unique=False)

    op.create_table(
        "bonds",
        sa.Column("number", sa.String(length=32), nullable=False),
        sa.Column("usdot", sa.Integer(), nullable=False),
        sa.Column("surety", sa.String(length=120), nullable=False),
        sa.Column("amount", sa.Integer(), nullable=True),
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("expires", sa.Date(), nullable=False),
        sa.CheckConstraint("expires > effective", name=op.f("ck_bonds_expires_after_effective")),
        sa.ForeignKeyConstraint(
            ["usdot"], ["carriers.usdot"], name=op.f("fk_bonds_usdot_carriers")
        ),
        sa.PrimaryKeyConstraint("number", name=op.f("pk_bonds")),
    )
    with op.batch_alter_table("bonds", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_bonds_usdot"), ["usdot"], unique=False)

    op.create_table(
        "insurance_certificates",
        sa.Column("policy", sa.String(length=32), nullable=False),
        sa.Column("usdot", sa.Integer(), nullable=False),
        sa.Column("insurer", sa.String(length=120), nullable=False),
        sa.Column("liability", sa.Integer(), nullable=True),
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("expires", sa.Date(), nullable=False),
        sa.Column("filed", sa.Date(), nullable=True),
        sa.CheckConstraint(
            "expires > effective", name=op.f("ck_insurance_certificates_expires_after_effective")
        ),
        sa.ForeignKeyConstraint(
            ["usdot"], ["carriers.usdot"], name=op.f("fk_insurance_certificates_usdot_carriers")
        ),
        sa.PrimaryKeyConstraint("policy", name=op.f("pk_insurance_certificates")),
    )
    with op.batch_alter_table("insurance_certificates", schema=None) as batch_op:
        batch_op.create_index(
            batch_op.f("ix_insurance_certificates_usdot"), ["usdot"], unique=False
        )

    op.create_table(
        "review_moves",
        sa.Column("reference", sa.String(length=32), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("happened", sa.Date(), nullable=False),
        sa.Column("who", sa.String(length=120), nullable=False),
        sa.Column("state", sa.String(length=32), nullable=False),
        sa.Column("note", sa.String(length=500), nullable=False),
        sa.CheckConstraint("position >= 0", name=op.f("ck_review_moves_position_not_negative")),
        sa.ForeignKeyConstraint(
            ["reference"], ["reviews.reference"], name=op.f("fk_review_moves_reference_reviews")
        ),
        sa.PrimaryKeyConstraint("reference", "position", name=op.f("pk_review_moves")),
    )
    op.create_table(
        "segments",
        sa.Column("ident", sa.String(length=32), nullable=False),
        sa.Column("route_kind", sa.String(length=4), nullable=False),
        sa.Column("route_number", sa.Integer(), nullable=False),
        sa.Column("route_suffix", sa.String(length=2), nullable=False),
        sa.Column("start_node", sa.String(length=32), nullable=False),
        sa.Column("end_node", sa.String(length=32), nullable=False),
        sa.Column("length_hundredths", sa.Integer(), nullable=False),
        sa.Column("county", sa.String(length=120), nullable=False),
        sa.Column("town", sa.String(length=120), nullable=False),
        sa.Column("district", sa.Integer(), nullable=False),
        sa.Column("one_way", sa.Boolean(), nullable=False),
        sa.CheckConstraint("district between 1 and 9", name=op.f("ck_segments_district_in_range")),
        sa.CheckConstraint("length_hundredths > 0", name=op.f("ck_segments_length_positive")),
        sa.CheckConstraint("start_node <> end_node", name=op.f("ck_segments_ends_differ")),
        sa.ForeignKeyConstraint(
            ["end_node"], ["nodes.name"], name=op.f("fk_segments_end_node_nodes")
        ),
        sa.ForeignKeyConstraint(
            ["start_node"], ["nodes.name"], name=op.f("fk_segments_start_node_nodes")
        ),
        sa.PrimaryKeyConstraint("ident", name=op.f("pk_segments")),
    )
    with op.batch_alter_table("segments", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_segments_district"), ["district"], unique=False)
        batch_op.create_index(batch_op.f("ix_segments_end_node"), ["end_node"], unique=False)
        batch_op.create_index(batch_op.f("ix_segments_start_node"), ["start_node"], unique=False)

    op.create_table(
        "suspensions",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("usdot", sa.Integer(), nullable=False),
        sa.Column("reason", sa.String(length=500), nullable=False),
        sa.Column("from_day", sa.Date(), nullable=False),
        sa.Column("until", sa.Date(), nullable=True),
        sa.Column("ordered_by", sa.String(length=120), nullable=False),
        sa.CheckConstraint(
            "until is null or until >= from_day", name=op.f("ck_suspensions_lifts_after_it_starts")
        ),
        sa.ForeignKeyConstraint(
            ["usdot"], ["carriers.usdot"], name=op.f("fk_suspensions_usdot_carriers")
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_suspensions")),
    )
    with op.batch_alter_table("suspensions", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_suspensions_usdot"), ["usdot"], unique=False)

    op.create_table(
        "application_axles",
        sa.Column("reference", sa.String(length=32), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("kind", sa.String(length=32), nullable=False),
        sa.Column("weight", sa.Integer(), nullable=True),
        sa.Column("tires", sa.Integer(), nullable=False),
        sa.Column("tire_width", sa.Integer(), nullable=True),
        sa.Column("liftable", sa.Boolean(), nullable=False),
        sa.Column("spacing_before", sa.Integer(), nullable=True),
        sa.CheckConstraint(
            "position >= 0", name=op.f("ck_application_axles_position_not_negative")
        ),
        sa.CheckConstraint(
            "tires >= 2 and tires % 2 = 0", name=op.f("ck_application_axles_tires_are_an_even_pair")
        ),
        sa.ForeignKeyConstraint(
            ["reference"],
            ["applications.reference"],
            name=op.f("fk_application_axles_reference_applications"),
        ),
        sa.PrimaryKeyConstraint("reference", "position", name=op.f("pk_application_axles")),
    )
    op.create_table(
        "application_pieces",
        sa.Column("reference", sa.String(length=32), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("commodity", sa.String(length=32), nullable=False),
        sa.Column("description", sa.String(length=120), nullable=False),
        sa.Column("serial", sa.String(length=32), nullable=True),
        sa.ForeignKeyConstraint(
            ["commodity"],
            ["commodities.code"],
            name=op.f("fk_application_pieces_commodity_commodities"),
        ),
        sa.ForeignKeyConstraint(
            ["reference"],
            ["applications.reference"],
            name=op.f("fk_application_pieces_reference_applications"),
        ),
        sa.PrimaryKeyConstraint("reference", "position", name=op.f("pk_application_pieces")),
    )
    op.create_table(
        "application_units",
        sa.Column("reference", sa.String(length=32), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("kind", sa.String(length=32), nullable=False),
        sa.Column("axles", sa.Integer(), nullable=False),
        sa.Column("plate", sa.String(length=32), nullable=False),
        sa.Column("tare", sa.Integer(), nullable=True),
        sa.Column("make", sa.String(length=120), nullable=False),
        sa.Column("unit_number", sa.String(length=32), nullable=False),
        sa.CheckConstraint("axles >= 1", name=op.f("ck_application_units_a_unit_has_axles")),
        sa.ForeignKeyConstraint(
            ["reference"],
            ["applications.reference"],
            name=op.f("fk_application_units_reference_applications"),
        ),
        sa.PrimaryKeyConstraint("reference", "position", name=op.f("pk_application_units")),
    )
    op.create_table(
        "permits",
        sa.Column("number", sa.String(length=32), nullable=False),
        sa.Column("series", sa.String(length=2), nullable=False),
        sa.Column("application", sa.String(length=32), nullable=False),
        sa.Column("usdot", sa.Integer(), nullable=False),
        sa.Column("carrier_name", sa.String(length=120), nullable=False),
        sa.Column("issued", sa.Date(), nullable=False),
        sa.Column("valid_from", sa.Date(), nullable=False),
        sa.Column("valid_to", sa.Date(), nullable=False),
        sa.Column("origin", sa.String(length=32), nullable=False),
        sa.Column("destination", sa.String(length=32), nullable=False),
        sa.Column("route", sa.String(length=500), nullable=False),
        sa.Column("narrative", sa.Text(length=500), nullable=False),
        sa.Column("gross", sa.Integer(), nullable=True),
        sa.Column("width", sa.Integer(), nullable=True),
        sa.Column("height", sa.Integer(), nullable=True),
        sa.Column("length", sa.Integer(), nullable=True),
        sa.Column("front_overhang", sa.Integer(), nullable=True),
        sa.Column("rear_overhang", sa.Integer(), nullable=True),
        sa.Column("distance", sa.Integer(), nullable=True),
        sa.Column("state", sa.String(length=32), nullable=False),
        sa.Column("void_reason", sa.String(length=500), nullable=False),
        sa.Column("superseded_by", sa.String(length=32), nullable=True),
        sa.Column("supersedes", sa.String(length=32), nullable=True),
        sa.Column("schedule", sa.String(length=120), nullable=False),
        sa.CheckConstraint("length(route) > 0", name=op.f("ck_permits_route_not_empty")),
        sa.CheckConstraint("valid_from >= issued", name=op.f("ck_permits_not_valid_before_issue")),
        sa.CheckConstraint(
            "valid_to >= valid_from", name=op.f("ck_permits_span_ends_after_it_starts")
        ),
        sa.ForeignKeyConstraint(
            ["application"],
            ["applications.reference"],
            name=op.f("fk_permits_application_applications"),
        ),
        sa.ForeignKeyConstraint(
            ["usdot"], ["carriers.usdot"], name=op.f("fk_permits_usdot_carriers")
        ),
        sa.PrimaryKeyConstraint("number", name=op.f("pk_permits")),
    )
    with op.batch_alter_table("permits", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_permits_application"), ["application"], unique=False)
        batch_op.create_index(batch_op.f("ix_permits_issued"), ["issued"], unique=False)
        batch_op.create_index(batch_op.f("ix_permits_series"), ["series"], unique=False)
        batch_op.create_index(batch_op.f("ix_permits_state"), ["state"], unique=False)
        batch_op.create_index(batch_op.f("ix_permits_usdot"), ["usdot"], unique=False)
        batch_op.create_index(batch_op.f("ix_permits_valid_from"), ["valid_from"], unique=False)
        batch_op.create_index(batch_op.f("ix_permits_valid_to"), ["valid_to"], unique=False)

    op.create_table(
        "restrictions",
        sa.Column("ident", sa.String(length=32), nullable=False),
        sa.Column("segment_ident", sa.String(length=32), nullable=False),
        sa.Column("kind", sa.String(length=32), nullable=False),
        sa.Column("note", sa.String(length=500), nullable=False),
        sa.Column("height", sa.Integer(), nullable=True),
        sa.Column("width", sa.Integer(), nullable=True),
        sa.Column("weight", sa.Integer(), nullable=True),
        sa.Column("region", sa.String(length=120), nullable=True),
        sa.Column("curfew_days", sa.String(length=16), nullable=True),
        sa.Column("curfew_from", sa.Time(), nullable=True),
        sa.Column("curfew_to", sa.Time(), nullable=True),
        sa.Column("effective", sa.Date(), nullable=True),
        sa.Column("expires", sa.Date(), nullable=True),
        sa.CheckConstraint(
            "expires is null or effective is null or expires >= effective",
            name=op.f("ck_restrictions_lifts_after_it_starts"),
        ),
        sa.ForeignKeyConstraint(
            ["segment_ident"],
            ["segments.ident"],
            name=op.f("fk_restrictions_segment_ident_segments"),
        ),
        sa.PrimaryKeyConstraint("ident", name=op.f("pk_restrictions")),
    )
    with op.batch_alter_table("restrictions", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_restrictions_kind"), ["kind"], unique=False)
        batch_op.create_index(
            batch_op.f("ix_restrictions_segment_ident"), ["segment_ident"], unique=False
        )

    op.create_table(
        "structures",
        sa.Column("ident", sa.String(length=32), nullable=False),
        sa.Column("name", sa.String(length=120), nullable=False),
        sa.Column("segment_ident", sa.String(length=32), nullable=False),
        sa.Column("kind", sa.String(length=32), nullable=False),
        sa.Column("spans", sa.String(length=120), nullable=False),
        sa.Column("built", sa.Integer(), nullable=False),
        sa.Column("inspected", sa.Date(), nullable=True),
        sa.Column("inventory_rating", sa.Integer(), nullable=True),
        sa.Column("operating_rating", sa.Integer(), nullable=True),
        sa.Column("posted", sa.Integer(), nullable=True),
        sa.Column("clearance", sa.Integer(), nullable=True),
        sa.Column("deck_width", sa.Integer(), nullable=True),
        sa.CheckConstraint(
            "built = 0 or built between 1800 and 2200", name=op.f("ck_structures_built_is_a_year")
        ),
        sa.CheckConstraint(
            "inventory_rating is null or operating_rating is null or inventory_rating <= operating_rating",
            name=op.f("ck_structures_daily_rating_below_permit_rating"),
        ),
        sa.ForeignKeyConstraint(
            ["segment_ident"], ["segments.ident"], name=op.f("fk_structures_segment_ident_segments")
        ),
        sa.PrimaryKeyConstraint("ident", name=op.f("pk_structures")),
    )
    with op.batch_alter_table("structures", schema=None) as batch_op:
        batch_op.create_index(
            batch_op.f("ix_structures_segment_ident"), ["segment_ident"], unique=False
        )

    op.create_table(
        "permit_conditions",
        sa.Column("number", sa.String(length=32), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("code", sa.String(length=32), nullable=False),
        sa.Column("category", sa.String(length=32), nullable=False),
        sa.Column("body", sa.Text(length=500), nullable=False),
        sa.Column("print_order", sa.Integer(), nullable=False),
        sa.CheckConstraint("length(body) > 0", name=op.f("ck_permit_conditions_body_not_empty")),
        sa.ForeignKeyConstraint(
            ["number"], ["permits.number"], name=op.f("fk_permit_conditions_number_permits")
        ),
        sa.PrimaryKeyConstraint("number", "position", name=op.f("pk_permit_conditions")),
    )
    op.create_table(
        "permit_fees",
        sa.Column("number", sa.String(length=32), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("description", sa.String(length=120), nullable=False),
        sa.Column("amount", sa.Integer(), nullable=True),
        sa.ForeignKeyConstraint(
            ["number"], ["permits.number"], name=op.f("fk_permit_fees_number_permits")
        ),
        sa.PrimaryKeyConstraint("number", "position", name=op.f("pk_permit_fees")),
    )


def downgrade() -> None:
    op.drop_table("permit_fees")
    op.drop_table("permit_conditions")
    with op.batch_alter_table("structures", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_structures_segment_ident"))

    op.drop_table("structures")
    with op.batch_alter_table("restrictions", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_restrictions_segment_ident"))
        batch_op.drop_index(batch_op.f("ix_restrictions_kind"))

    op.drop_table("restrictions")
    with op.batch_alter_table("permits", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_permits_valid_to"))
        batch_op.drop_index(batch_op.f("ix_permits_valid_from"))
        batch_op.drop_index(batch_op.f("ix_permits_usdot"))
        batch_op.drop_index(batch_op.f("ix_permits_state"))
        batch_op.drop_index(batch_op.f("ix_permits_series"))
        batch_op.drop_index(batch_op.f("ix_permits_issued"))
        batch_op.drop_index(batch_op.f("ix_permits_application"))

    op.drop_table("permits")
    op.drop_table("application_units")
    op.drop_table("application_pieces")
    op.drop_table("application_axles")
    with op.batch_alter_table("suspensions", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_suspensions_usdot"))

    op.drop_table("suspensions")
    with op.batch_alter_table("segments", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_segments_start_node"))
        batch_op.drop_index(batch_op.f("ix_segments_end_node"))
        batch_op.drop_index(batch_op.f("ix_segments_district"))

    op.drop_table("segments")
    op.drop_table("review_moves")
    with op.batch_alter_table("insurance_certificates", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_insurance_certificates_usdot"))

    op.drop_table("insurance_certificates")
    with op.batch_alter_table("bonds", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_bonds_usdot"))

    op.drop_table("bonds")
    with op.batch_alter_table("applications", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_applications_usdot"))
        batch_op.drop_index(batch_op.f("ix_applications_travel_start"))
        batch_op.drop_index(batch_op.f("ix_applications_state"))
        batch_op.drop_index(batch_op.f("ix_applications_filed"))

    op.drop_table("applications")
    with op.batch_alter_table("reviews", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_reviews_state"))
        batch_op.drop_index(batch_op.f("ix_reviews_opened"))

    op.drop_table("reviews")
    op.drop_table("number_counters")
    op.drop_table("nodes")
    op.drop_table("condition_texts")
    op.drop_table("commodities")
    with op.batch_alter_table("carriers", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_carriers_name"))

    op.drop_table("carriers")
