-- The schema as it stood on the day the office went live.
--
-- Written by autogenerate against the models and then read line by line, which is the only way a
-- migration is produced here.
--
-- One edit is made to every generated file and it is always the same one. The custom column types
-- are written back as the types they are stored in: a weight is an integer, an enumeration is a
-- string. A migration that imports application code stops working the day somebody renames a
-- class, and a migration that stopped working is a database nobody can build from scratch.
--
-- Ported from Alembic revision 0001, every table, column, index and constraint under the name the
-- original gave it. Money is whole cents in BIGINT: bonds.amount,
-- insurance_certificates.liability, permit_fees.amount. The serial key is an identity column, as
-- SQLite's integer primary key was: suspensions.id.

CREATE TABLE carriers (
    usdot INTEGER NOT NULL,
    name VARCHAR(120) NOT NULL,
    town VARCHAR(120) NOT NULL,
    state VARCHAR(2) NOT NULL,
    billing VARCHAR(32) NOT NULL,
    CONSTRAINT pk_carriers PRIMARY KEY (usdot),
    CONSTRAINT ck_carriers_state_is_two_letters CHECK (length(state) = 2),
    CONSTRAINT ck_carriers_usdot_is_a_number CHECK (usdot > 0 and usdot < 100000000)
);

CREATE INDEX ix_carriers_name ON carriers (name);

CREATE TABLE commodities (
    code VARCHAR(32) NOT NULL,
    description VARCHAR(120) NOT NULL,
    family VARCHAR(32) NOT NULL,
    divisible BOOLEAN NOT NULL,
    allowance VARCHAR(120),
    tip_limit_degrees INTEGER,
    high_centre BOOLEAN NOT NULL,
    retired DATE,
    changed_by VARCHAR(120) NOT NULL,
    changed_on DATE,
    CONSTRAINT pk_commodities PRIMARY KEY (code),
    CONSTRAINT ck_commodities_code_not_empty CHECK (length(code) > 0),
    CONSTRAINT ck_commodities_tip_limit_is_an_angle CHECK (tip_limit_degrees is null or (tip_limit_degrees between 1 and 45))
);

CREATE TABLE condition_texts (
    code VARCHAR(32) NOT NULL,
    effective DATE NOT NULL,
    category VARCHAR(32) NOT NULL,
    body VARCHAR(500) NOT NULL,
    print_order INTEGER NOT NULL,
    changed_by VARCHAR(120) NOT NULL,
    CONSTRAINT pk_condition_texts PRIMARY KEY (code, effective),
    CONSTRAINT ck_condition_texts_body_not_empty CHECK (length(body) > 0),
    CONSTRAINT ck_condition_texts_print_order_not_negative CHECK (print_order >= 0)
);

CREATE TABLE nodes (
    name VARCHAR(32) NOT NULL,
    town VARCHAR(120) NOT NULL,
    CONSTRAINT pk_nodes PRIMARY KEY (name)
);

CREATE TABLE number_counters (
    year INTEGER NOT NULL,
    series VARCHAR(2) NOT NULL,
    next_sequence INTEGER NOT NULL,
    CONSTRAINT pk_number_counters PRIMARY KEY (year, series),
    CONSTRAINT ck_number_counters_sequence_starts_at_one CHECK (next_sequence >= 1)
);

CREATE TABLE reviews (
    reference VARCHAR(32) NOT NULL,
    opened DATE NOT NULL,
    state VARCHAR(32) NOT NULL,
    engineer VARCHAR(120),
    structures VARCHAR(500) NOT NULL,
    conditions VARCHAR(500) NOT NULL,
    CONSTRAINT pk_reviews PRIMARY KEY (reference)
);

CREATE INDEX ix_reviews_opened ON reviews (opened);

CREATE INDEX ix_reviews_state ON reviews (state);

CREATE TABLE applications (
    reference VARCHAR(32) NOT NULL,
    usdot INTEGER NOT NULL,
    commodity VARCHAR(32) NOT NULL,
    origin VARCHAR(32) NOT NULL,
    destination VARCHAR(32) NOT NULL,
    travel_start DATE NOT NULL,
    travel_end DATE NOT NULL,
    filed DATE NOT NULL,
    state VARCHAR(32) NOT NULL,
    applicant_name VARCHAR(120) NOT NULL,
    applicant_phone VARCHAR(32) NOT NULL,
    applicant_email VARCHAR(120) NOT NULL,
    route VARCHAR(500) NOT NULL,
    notes VARCHAR(500) NOT NULL,
    width INTEGER,
    height INTEGER,
    length INTEGER,
    front_overhang INTEGER,
    rear_overhang INTEGER,
    CONSTRAINT pk_applications PRIMARY KEY (reference),
    CONSTRAINT fk_applications_commodity_commodities FOREIGN KEY (commodity) REFERENCES commodities (code),
    CONSTRAINT fk_applications_usdot_carriers FOREIGN KEY (usdot) REFERENCES carriers (usdot),
    CONSTRAINT ck_applications_trip_goes_somewhere CHECK (origin <> destination),
    CONSTRAINT ck_applications_span_ends_after_it_starts CHECK (travel_end >= travel_start)
);

CREATE INDEX ix_applications_filed ON applications (filed);

CREATE INDEX ix_applications_state ON applications (state);

CREATE INDEX ix_applications_travel_start ON applications (travel_start);

CREATE INDEX ix_applications_usdot ON applications (usdot);

CREATE TABLE bonds (
    number VARCHAR(32) NOT NULL,
    usdot INTEGER NOT NULL,
    surety VARCHAR(120) NOT NULL,
    amount BIGINT,
    effective DATE NOT NULL,
    expires DATE NOT NULL,
    CONSTRAINT pk_bonds PRIMARY KEY (number),
    CONSTRAINT fk_bonds_usdot_carriers FOREIGN KEY (usdot) REFERENCES carriers (usdot),
    CONSTRAINT ck_bonds_expires_after_effective CHECK (expires > effective)
);

CREATE INDEX ix_bonds_usdot ON bonds (usdot);

CREATE TABLE insurance_certificates (
    policy VARCHAR(32) NOT NULL,
    usdot INTEGER NOT NULL,
    insurer VARCHAR(120) NOT NULL,
    liability BIGINT,
    effective DATE NOT NULL,
    expires DATE NOT NULL,
    filed DATE,
    CONSTRAINT pk_insurance_certificates PRIMARY KEY (policy),
    CONSTRAINT fk_insurance_certificates_usdot_carriers FOREIGN KEY (usdot) REFERENCES carriers (usdot),
    CONSTRAINT ck_insurance_certificates_expires_after_effective CHECK (expires > effective)
);

CREATE INDEX ix_insurance_certificates_usdot ON insurance_certificates (usdot);

CREATE TABLE review_moves (
    reference VARCHAR(32) NOT NULL,
    position INTEGER NOT NULL,
    happened DATE NOT NULL,
    who VARCHAR(120) NOT NULL,
    state VARCHAR(32) NOT NULL,
    note VARCHAR(500) NOT NULL,
    CONSTRAINT pk_review_moves PRIMARY KEY (reference, position),
    CONSTRAINT fk_review_moves_reference_reviews FOREIGN KEY (reference) REFERENCES reviews (reference),
    CONSTRAINT ck_review_moves_position_not_negative CHECK (position >= 0)
);

CREATE TABLE segments (
    ident VARCHAR(32) NOT NULL,
    route_kind VARCHAR(4) NOT NULL,
    route_number INTEGER NOT NULL,
    route_suffix VARCHAR(2) NOT NULL,
    start_node VARCHAR(32) NOT NULL,
    end_node VARCHAR(32) NOT NULL,
    length_hundredths INTEGER NOT NULL,
    county VARCHAR(120) NOT NULL,
    town VARCHAR(120) NOT NULL,
    district INTEGER NOT NULL,
    one_way BOOLEAN NOT NULL,
    CONSTRAINT pk_segments PRIMARY KEY (ident),
    CONSTRAINT fk_segments_end_node_nodes FOREIGN KEY (end_node) REFERENCES nodes (name),
    CONSTRAINT fk_segments_start_node_nodes FOREIGN KEY (start_node) REFERENCES nodes (name),
    CONSTRAINT ck_segments_district_in_range CHECK (district between 1 and 9),
    CONSTRAINT ck_segments_length_positive CHECK (length_hundredths > 0),
    CONSTRAINT ck_segments_ends_differ CHECK (start_node <> end_node)
);

CREATE INDEX ix_segments_district ON segments (district);

CREATE INDEX ix_segments_end_node ON segments (end_node);

CREATE INDEX ix_segments_start_node ON segments (start_node);

CREATE TABLE suspensions (
    id INTEGER GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    usdot INTEGER NOT NULL,
    reason VARCHAR(500) NOT NULL,
    from_day DATE NOT NULL,
    until DATE,
    ordered_by VARCHAR(120) NOT NULL,
    CONSTRAINT pk_suspensions PRIMARY KEY (id),
    CONSTRAINT fk_suspensions_usdot_carriers FOREIGN KEY (usdot) REFERENCES carriers (usdot),
    CONSTRAINT ck_suspensions_lifts_after_it_starts CHECK (until is null or until >= from_day)
);

CREATE INDEX ix_suspensions_usdot ON suspensions (usdot);

CREATE TABLE application_axles (
    reference VARCHAR(32) NOT NULL,
    position INTEGER NOT NULL,
    kind VARCHAR(32) NOT NULL,
    weight INTEGER,
    tires INTEGER NOT NULL,
    tire_width INTEGER,
    liftable BOOLEAN NOT NULL,
    spacing_before INTEGER,
    CONSTRAINT pk_application_axles PRIMARY KEY (reference, position),
    CONSTRAINT fk_application_axles_reference_applications FOREIGN KEY (reference) REFERENCES applications (reference),
    CONSTRAINT ck_application_axles_position_not_negative CHECK (position >= 0),
    CONSTRAINT ck_application_axles_tires_are_an_even_pair CHECK (tires >= 2 and tires % 2 = 0)
);

CREATE TABLE application_pieces (
    reference VARCHAR(32) NOT NULL,
    position INTEGER NOT NULL,
    commodity VARCHAR(32) NOT NULL,
    description VARCHAR(120) NOT NULL,
    serial VARCHAR(32),
    CONSTRAINT pk_application_pieces PRIMARY KEY (reference, position),
    CONSTRAINT fk_application_pieces_commodity_commodities FOREIGN KEY (commodity) REFERENCES commodities (code),
    CONSTRAINT fk_application_pieces_reference_applications FOREIGN KEY (reference) REFERENCES applications (reference)
);

CREATE TABLE application_units (
    reference VARCHAR(32) NOT NULL,
    position INTEGER NOT NULL,
    kind VARCHAR(32) NOT NULL,
    axles INTEGER NOT NULL,
    plate VARCHAR(32) NOT NULL,
    tare INTEGER,
    make VARCHAR(120) NOT NULL,
    unit_number VARCHAR(32) NOT NULL,
    CONSTRAINT pk_application_units PRIMARY KEY (reference, position),
    CONSTRAINT fk_application_units_reference_applications FOREIGN KEY (reference) REFERENCES applications (reference),
    CONSTRAINT ck_application_units_a_unit_has_axles CHECK (axles >= 1)
);

CREATE TABLE permits (
    number VARCHAR(32) NOT NULL,
    series VARCHAR(2) NOT NULL,
    application VARCHAR(32) NOT NULL,
    usdot INTEGER NOT NULL,
    carrier_name VARCHAR(120) NOT NULL,
    issued DATE NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    origin VARCHAR(32) NOT NULL,
    destination VARCHAR(32) NOT NULL,
    route VARCHAR(500) NOT NULL,
    narrative VARCHAR(500) NOT NULL,
    gross INTEGER,
    width INTEGER,
    height INTEGER,
    length INTEGER,
    front_overhang INTEGER,
    rear_overhang INTEGER,
    distance INTEGER,
    state VARCHAR(32) NOT NULL,
    void_reason VARCHAR(500) NOT NULL,
    superseded_by VARCHAR(32),
    supersedes VARCHAR(32),
    schedule VARCHAR(120) NOT NULL,
    CONSTRAINT pk_permits PRIMARY KEY (number),
    CONSTRAINT fk_permits_application_applications FOREIGN KEY (application) REFERENCES applications (reference),
    CONSTRAINT fk_permits_usdot_carriers FOREIGN KEY (usdot) REFERENCES carriers (usdot),
    CONSTRAINT ck_permits_route_not_empty CHECK (length(route) > 0),
    CONSTRAINT ck_permits_not_valid_before_issue CHECK (valid_from >= issued),
    CONSTRAINT ck_permits_span_ends_after_it_starts CHECK (valid_to >= valid_from)
);

CREATE INDEX ix_permits_application ON permits (application);

CREATE INDEX ix_permits_issued ON permits (issued);

CREATE INDEX ix_permits_series ON permits (series);

CREATE INDEX ix_permits_state ON permits (state);

CREATE INDEX ix_permits_usdot ON permits (usdot);

CREATE INDEX ix_permits_valid_from ON permits (valid_from);

CREATE INDEX ix_permits_valid_to ON permits (valid_to);

CREATE TABLE restrictions (
    ident VARCHAR(32) NOT NULL,
    segment_ident VARCHAR(32) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    note VARCHAR(500) NOT NULL,
    height INTEGER,
    width INTEGER,
    weight INTEGER,
    region VARCHAR(120),
    curfew_days VARCHAR(16),
    curfew_from TIME,
    curfew_to TIME,
    effective DATE,
    expires DATE,
    CONSTRAINT pk_restrictions PRIMARY KEY (ident),
    CONSTRAINT fk_restrictions_segment_ident_segments FOREIGN KEY (segment_ident) REFERENCES segments (ident),
    CONSTRAINT ck_restrictions_lifts_after_it_starts CHECK (expires is null or effective is null or expires >= effective)
);

CREATE INDEX ix_restrictions_kind ON restrictions (kind);

CREATE INDEX ix_restrictions_segment_ident ON restrictions (segment_ident);

CREATE TABLE structures (
    ident VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    segment_ident VARCHAR(32) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    spans VARCHAR(120) NOT NULL,
    built INTEGER NOT NULL,
    inspected DATE,
    inventory_rating INTEGER,
    operating_rating INTEGER,
    posted INTEGER,
    clearance INTEGER,
    deck_width INTEGER,
    CONSTRAINT pk_structures PRIMARY KEY (ident),
    CONSTRAINT fk_structures_segment_ident_segments FOREIGN KEY (segment_ident) REFERENCES segments (ident),
    CONSTRAINT ck_structures_built_is_a_year CHECK (built = 0 or built between 1800 and 2200),
    CONSTRAINT ck_structures_daily_rating_below_permit_rating CHECK (inventory_rating is null or operating_rating is null or inventory_rating <= operating_rating)
);

CREATE INDEX ix_structures_segment_ident ON structures (segment_ident);

CREATE TABLE permit_conditions (
    number VARCHAR(32) NOT NULL,
    position INTEGER NOT NULL,
    code VARCHAR(32) NOT NULL,
    category VARCHAR(32) NOT NULL,
    body VARCHAR(500) NOT NULL,
    print_order INTEGER NOT NULL,
    CONSTRAINT pk_permit_conditions PRIMARY KEY (number, position),
    CONSTRAINT fk_permit_conditions_number_permits FOREIGN KEY (number) REFERENCES permits (number),
    CONSTRAINT ck_permit_conditions_body_not_empty CHECK (length(body) > 0)
);

CREATE TABLE permit_fees (
    number VARCHAR(32) NOT NULL,
    position INTEGER NOT NULL,
    description VARCHAR(120) NOT NULL,
    amount BIGINT,
    CONSTRAINT pk_permit_fees PRIMARY KEY (number, position),
    CONSTRAINT fk_permit_fees_number_permits FOREIGN KEY (number) REFERENCES permits (number)
);
