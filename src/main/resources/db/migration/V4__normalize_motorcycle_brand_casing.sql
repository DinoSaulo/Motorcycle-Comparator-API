-- Historical imports mixed "HONDA" and "Honda", splitting one manufacturer into two buckets. Only ALL CAPS rows are
-- touched: a deliberate style like "byCristo" is left alone, and the acronyms below keep their casing, not "Bmw".
UPDATE motorcycles
SET brand = initcap(lower(brand))
WHERE brand = UPPER(brand)
  AND brand <> ALL (ARRAY['BMW', 'KTM', 'TVS', 'BRP', 'SWM', 'FYM', 'MRX', 'MVK', 'SBM', 'NIU', 'UM']);

-- initcap() has no notion of a leading acronym inside an otherwise normal name, so "MV AGUSTA" and "CFMOTO" came out
-- as "Mv Agusta" / "Cfmoto" above instead of matching the casing already used by the rows seeded correctly.
UPDATE motorcycles SET brand = 'MV Agusta' WHERE brand = 'Mv Agusta';
UPDATE motorcycles SET brand = 'CFMoto' WHERE brand = 'Cfmoto';
