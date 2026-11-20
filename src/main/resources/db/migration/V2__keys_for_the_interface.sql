-- Keys for the interface, which arrived with the http surface.
--
-- Every constraint and index is named by the schema's convention: pk_, uq_, ix_, fk_ and ck_.

CREATE TABLE api_keys (
    ident VARCHAR(32) NOT NULL,
    digest VARCHAR(64) NOT NULL,
    person VARCHAR(120) NOT NULL,
    role VARCHAR(32) NOT NULL,
    usdot INTEGER,
    issued DATE NOT NULL,
    revoked DATE,
    note VARCHAR(500) NOT NULL,
    CONSTRAINT pk_api_keys PRIMARY KEY (ident),
    CONSTRAINT ck_api_keys_digest_is_a_sha256 CHECK (length(digest) = 64),
    CONSTRAINT ck_api_keys_person_not_empty CHECK (length(person) > 0)
);

CREATE UNIQUE INDEX ix_api_keys_digest ON api_keys (digest);

CREATE INDEX ix_api_keys_role ON api_keys (role);
