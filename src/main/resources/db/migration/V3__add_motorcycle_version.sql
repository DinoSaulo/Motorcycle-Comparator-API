-- Optimistic locking for the admin CRUD: without a version column two concurrent PUTs are last-write-wins, silently.
-- DEFAULT 0 stays in place after the backfill: the repeatable dev seed inserts column lists that never mention version.

ALTER TABLE motorcycles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN motorcycles.version IS 'JPA @Version counter; incremented by Hibernate on every update.';
