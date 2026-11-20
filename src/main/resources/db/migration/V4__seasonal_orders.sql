-- Seasonal orders, which used to be typed in by hand every spring.
--
-- Ported from Alembic revision 0004, every table, column, index and constraint under the name the
-- original gave it.

CREATE TABLE season_orders (
    number VARCHAR(32) NOT NULL,
    region VARCHAR(120) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    starts DATE NOT NULL,
    ends DATE NOT NULL,
    share INTEGER NOT NULL,
    signed_by VARCHAR(120) NOT NULL,
    note VARCHAR(500) NOT NULL,
    CONSTRAINT pk_season_orders PRIMARY KEY (number),
    CONSTRAINT ck_season_orders_ends_after_it_starts CHECK (ends >= starts),
    CONSTRAINT ck_season_orders_share_is_a_percent CHECK (share between 1 and 200)
);

CREATE INDEX ix_season_orders_region ON season_orders (region);

CREATE INDEX ix_season_orders_region_starts ON season_orders (region, starts);
