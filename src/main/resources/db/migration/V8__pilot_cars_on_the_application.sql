-- The pilot car cards a hauler names on the application.
--
-- Every constraint and index is named by the schema's convention: pk_, uq_, ix_, fk_ and ck_.

ALTER TABLE applications ADD COLUMN escort_cards VARCHAR(500) DEFAULT '' NOT NULL;
