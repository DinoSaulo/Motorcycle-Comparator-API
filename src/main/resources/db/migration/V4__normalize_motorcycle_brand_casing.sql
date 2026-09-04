-- Historical imports mixed "HONDA" and "Honda" for the same brand, so grouping or
-- filtering by brand silently split one manufacturer into two buckets.
--
-- Only rows currently stored in ALL CAPS are touched: anything already in a sensible
-- case (Title Case, or a deliberate style like "byCristo"/"LiveWire") is left alone,
-- and the recognized acronyms below keep their original casing instead of becoming
-- "Bmw" or "Ktm".
UPDATE motorcycles
SET brand = initcap(lower(brand))
WHERE brand = UPPER(brand)
  AND brand <> ALL (ARRAY['BMW', 'KTM', 'TVS', 'BRP', 'SWM', 'FYM', 'MRX', 'MVK', 'SBM', 'NIU', 'UM']);

-- initcap() has no notion of a leading acronym inside an otherwise normal name, so
-- "MV AGUSTA" and "CFMOTO" came out as "Mv Agusta" / "Cfmoto" above instead of matching
-- the casing already used by the handful of rows seeded correctly.
UPDATE motorcycles SET brand = 'MV Agusta' WHERE brand = 'Mv Agusta';
UPDATE motorcycles SET brand = 'CFMoto' WHERE brand = 'Cfmoto';
