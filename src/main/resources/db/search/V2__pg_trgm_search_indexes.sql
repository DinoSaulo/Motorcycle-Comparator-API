-- Free-text search support, kept in its own location (classpath:db/search) because
-- CREATE EXTENSION needs privileges some managed environments do not hand out.
--
-- Skip: drop this location from spring.flyway.locations. Pre-provision: have a DBA run
-- CREATE EXTENSION pg_trgm once, after which the statement below is a no-op.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- The q filter is lower(col) LIKE '%text%'. A leading wildcard makes any btree index
-- unusable, and trigram GIN is the only index type that can answer it.
CREATE INDEX idx_motorcycles_brand_trgm ON motorcycles USING GIN (lower(brand) gin_trgm_ops);

CREATE INDEX idx_motorcycles_model_trgm ON motorcycles USING GIN (lower(model) gin_trgm_ops);

-- Three separate indexes, not one concatenated column: the predicate is an OR of three
-- LIKEs, which the planner resolves as a BitmapOr over exactly these.
CREATE INDEX idx_motorcycles_slug_trgm ON motorcycles USING GIN (lower(slug) gin_trgm_ops);
