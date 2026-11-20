-- The pilot car cards a hauler names on the application.
--
-- Ported from Alembic revision 0008, every table, column, index and constraint under the name the
-- original gave it.

ALTER TABLE applications ADD COLUMN escort_cards VARCHAR(500) DEFAULT '' NOT NULL;
