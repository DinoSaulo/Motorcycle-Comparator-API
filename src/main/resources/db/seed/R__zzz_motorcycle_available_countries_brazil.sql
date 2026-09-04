-- Every motorcycle currently in the catalogue was sourced from a market actually sold in Brazil (the FIPE
-- snapshot and every brand import gap-fill it), so this backfills all of them as available in "BR".
--
-- Flyway runs repeatable migrations in description order, so the file name alone decides when this runs.
-- The triple "zzz" is load-bearing: it must sort after every motorcycle-inserting seed, including
-- "zz_motorcycles_specs_gapfill" (the last of those, by the same rule) - "zzz" > "zz " under a plain
-- byte compare, so this always runs last and sees every row every other seed has inserted.
--
-- ON CONFLICT DO NOTHING makes the insert idempotent: a rerun after a new brand seed is added still only
-- adds the rows that are actually missing, and never touches a country an admin has since changed via the API.
INSERT INTO motorcycle_available_countries (motorcycle_id, country_code)
SELECT id, 'BR' FROM motorcycles
ON CONFLICT (motorcycle_id, country_code) DO NOTHING;
