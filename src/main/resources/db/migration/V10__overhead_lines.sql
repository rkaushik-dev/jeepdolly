-- Overhead lines, off the survey the office finished this autumn.
--
-- Every constraint and index is named by the schema's convention: pk_, uq_, ix_, fk_ and ck_.

CREATE TABLE line_owners (
    ident VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    telephone VARCHAR(32) NOT NULL,
    notice_days INTEGER NOT NULL,
    note VARCHAR(500) NOT NULL,
    CONSTRAINT pk_line_owners PRIMARY KEY (ident),
    CONSTRAINT ck_line_owners_notice_is_not_negative CHECK (notice_days >= 0)
);

CREATE INDEX ix_line_owners_name ON line_owners (name);

CREATE TABLE line_crossings (
    ident VARCHAR(32) NOT NULL,
    segment_ident VARCHAR(32) NOT NULL,
    place VARCHAR(120) NOT NULL,
    height INTEGER,
    owner_ident VARCHAR(32) NOT NULL,
    lift_to INTEGER,
    measured DATE,
    note VARCHAR(500) NOT NULL,
    CONSTRAINT pk_line_crossings PRIMARY KEY (ident),
    CONSTRAINT fk_line_crossings_owner_ident_line_owners FOREIGN KEY (owner_ident) REFERENCES line_owners (ident),
    CONSTRAINT fk_line_crossings_segment_ident_segments FOREIGN KEY (segment_ident) REFERENCES segments (ident),
    CONSTRAINT ck_line_crossings_height_is_measured CHECK (height > 0),
    CONSTRAINT ck_line_crossings_lift_is_upwards CHECK (lift_to is null or lift_to > height)
);

CREATE INDEX ix_line_crossings_owner_ident ON line_crossings (owner_ident);

CREATE INDEX ix_line_crossings_segment_ident ON line_crossings (segment_ident);
