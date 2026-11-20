-- The reprint register, which was a pad by the printer until now.
--
-- Every constraint and index is named by the schema's convention: pk_, uq_, ix_, fk_ and ck_.

CREATE TABLE reprints (
    permit VARCHAR(32) NOT NULL,
    copy INTEGER NOT NULL,
    issued_on DATE NOT NULL,
    reason VARCHAR(32) NOT NULL,
    asked_by VARCHAR(120) NOT NULL,
    issued_by VARCHAR(120) NOT NULL,
    note VARCHAR(500) NOT NULL,
    CONSTRAINT pk_reprints PRIMARY KEY (permit, copy),
    CONSTRAINT fk_reprints_permit_permits FOREIGN KEY (permit) REFERENCES permits (number),
    CONSTRAINT ck_reprints_the_original_is_not_a_reprint CHECK (copy > 1)
);

CREATE INDEX ix_reprints_issued_on ON reprints (issued_on);

CREATE INDEX ix_reprints_reason ON reprints (reason);
