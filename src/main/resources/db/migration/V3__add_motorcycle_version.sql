-- Optimistic locking for the admin CRUD: without a version column two concurrent PUTs
-- last-write-wins and the first admin's edit disappears with no error anywhere.
--
-- DEFAULT 0 stays in place after the backfill: the repeatable dev seed inserts explicit
-- column lists that do not mention version, and so would any other raw import.

ALTER TABLE motorcycles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN motorcycles.version IS 'JPA @Version counter; incremented by Hibernate on every update.';
