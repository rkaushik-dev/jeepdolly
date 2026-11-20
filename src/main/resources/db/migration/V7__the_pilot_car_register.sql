-- The pilot car register, which the certification order made necessary.
--
-- Ported from Alembic revision 0007, every table, column, index and constraint under the name the
-- original gave it.

CREATE TABLE pilot_companies (
    ident VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    town VARCHAR(120) NOT NULL,
    telephone VARCHAR(32) NOT NULL,
    suspended BOOLEAN NOT NULL,
    note VARCHAR(500) NOT NULL,
    CONSTRAINT pk_pilot_companies PRIMARY KEY (ident)
);

CREATE INDEX ix_pilot_companies_name ON pilot_companies (name);

CREATE TABLE pilot_cards (
    card VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    company_ident VARCHAR(32) NOT NULL,
    issued DATE NOT NULL,
    expires DATE NOT NULL,
    endorsements VARCHAR(500) NOT NULL,
    suspended BOOLEAN NOT NULL,
    CONSTRAINT pk_pilot_cards PRIMARY KEY (card),
    CONSTRAINT fk_pilot_cards_company_ident_pilot_companies FOREIGN KEY (company_ident) REFERENCES pilot_companies (ident),
    CONSTRAINT ck_pilot_cards_expires_after_issued CHECK (expires > issued)
);

CREATE INDEX ix_pilot_cards_company_ident ON pilot_cards (company_ident);

CREATE INDEX ix_pilot_cards_expires ON pilot_cards (expires);

CREATE INDEX ix_pilot_cards_name ON pilot_cards (name);
