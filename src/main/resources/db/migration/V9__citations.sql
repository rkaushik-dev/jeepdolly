-- Citations off the roadside, which the office had kept on paper.
--
-- Ported from Alembic revision 0009, every table, column, index and constraint under the name the
-- original gave it. Money is whole cents in BIGINT: citations.amount, citations.base.

CREATE TABLE citations (
    number VARCHAR(32) NOT NULL,
    written DATE NOT NULL,
    filed DATE NOT NULL,
    officer VARCHAR(120) NOT NULL,
    usdot INTEGER NOT NULL,
    carrier_name VARCHAR(120) NOT NULL,
    offence VARCHAR(32) NOT NULL,
    place VARCHAR(120) NOT NULL,
    permit VARCHAR(32),
    over INTEGER,
    base BIGINT,
    multiplier INTEGER NOT NULL,
    amount BIGINT,
    schedule DATE NOT NULL,
    state VARCHAR(32) NOT NULL,
    closing_reason VARCHAR(500) NOT NULL,
    note VARCHAR(500) NOT NULL,
    CONSTRAINT pk_citations PRIMARY KEY (number),
    CONSTRAINT fk_citations_permit_permits FOREIGN KEY (permit) REFERENCES permits (number),
    CONSTRAINT fk_citations_usdot_carriers FOREIGN KEY (usdot) REFERENCES carriers (usdot),
    CONSTRAINT ck_citations_filed_after_written CHECK (filed >= written),
    CONSTRAINT ck_citations_multiplier_in_range CHECK (multiplier between 1 and 3)
);

CREATE INDEX ix_citations_filed ON citations (filed);

CREATE INDEX ix_citations_offence ON citations (offence);

CREATE INDEX ix_citations_permit ON citations (permit);

CREATE INDEX ix_citations_state ON citations (state);

CREATE INDEX ix_citations_usdot ON citations (usdot);

CREATE INDEX ix_citations_written ON citations (written);
