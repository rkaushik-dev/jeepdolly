-- The published tables, which used to live in the code.
--
-- Ported from Alembic revision 0005, every table, column, index and constraint under the name the
-- original gave it. Money is whole cents in BIGINT: fee_base.amount, fee_per_mile.amount,
-- fee_schedules.amendment, fee_schedules.annual, fee_schedules.minimum, fee_schedules.review,
-- fee_weight_steps.per_block.

CREATE TABLE fee_base (
    effective DATE NOT NULL,
    kind VARCHAR(32) NOT NULL,
    amount BIGINT,
    CONSTRAINT pk_fee_base PRIMARY KEY (effective, kind)
);

CREATE TABLE fee_per_mile (
    effective DATE NOT NULL,
    highway_class VARCHAR(32) NOT NULL,
    amount BIGINT,
    CONSTRAINT pk_fee_per_mile PRIMARY KEY (effective, highway_class)
);

CREATE TABLE fee_schedules (
    effective DATE NOT NULL,
    review BIGINT,
    amendment BIGINT,
    minimum BIGINT,
    annual BIGINT,
    CONSTRAINT pk_fee_schedules PRIMARY KEY (effective)
);

CREATE TABLE fee_weight_steps (
    effective DATE NOT NULL,
    position INTEGER NOT NULL,
    up_to INTEGER,
    per_block BIGINT,
    note VARCHAR(120) NOT NULL,
    CONSTRAINT pk_fee_weight_steps PRIMARY KEY (effective, position),
    CONSTRAINT ck_fee_weight_steps_position_not_negative CHECK (position >= 0)
);

CREATE TABLE size_limits (
    highway_class VARCHAR(32) NOT NULL,
    effective DATE NOT NULL,
    legal_width INTEGER,
    legal_height INTEGER,
    legal_length INTEGER,
    legal_front INTEGER,
    legal_rear INTEGER,
    routine_width INTEGER,
    routine_height INTEGER,
    routine_length INTEGER,
    routine_front INTEGER,
    routine_rear INTEGER,
    CONSTRAINT pk_size_limits PRIMARY KEY (highway_class, effective)
);

CREATE TABLE statutory_limits (
    highway_class VARCHAR(32) NOT NULL,
    effective DATE NOT NULL,
    single INTEGER,
    tandem INTEGER,
    tridem INTEGER,
    quad INTEGER,
    beyond INTEGER,
    gross INTEGER,
    tire_rate INTEGER NOT NULL,
    CONSTRAINT pk_statutory_limits PRIMARY KEY (highway_class, effective),
    CONSTRAINT ck_statutory_limits_tandem_above_single CHECK (tandem > single),
    CONSTRAINT ck_statutory_limits_tire_rate_positive CHECK (tire_rate > 0)
);
