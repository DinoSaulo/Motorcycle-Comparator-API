// Builds R__motorcycles_kawasaki_specs_research_2026_08.sql from the engine-spec research set beside this file: not a
// scrape, so that set is the artefact. Guards derive from each row. Usage: node <this> [research.json] [--out <path>]

import { readFileSync, writeFileSync } from 'node:fs';
import { argv } from 'node:process';

const SRC = argv[2] && !argv[2].startsWith('--') ? argv[2] : 'tools/kawasaki-engine-research.json';
const outFlag = argv.indexOf('--out');
const OUT = outFlag !== -1
    ? argv[outFlag + 1]
    : 'src/main/resources/db/seed/R__motorcycles_kawasaki_specs_research_2026_08.sql';

// Column widths from V1__initial_schema.sql. A value longer than its column does not fail at
// generation time, it fails hours later inside Flyway, so it is refused here instead.
const WIDTH = {
    engine_type: 80,
    compression_ratio: 20,
    cooling_system: 40,
    fuel_system: 120,
    transmission_type: 60,
    final_drive: 40,
    emission_standard: 30,
};

// Errata. Each entry is a published factory figure the researched row contradicts, and each is provable from the row
// itself: displacement, stroke and cylinder count determine bore, so a pair that misses it has an arithmetic error.
const ERRATA = [
    {
        // 748 cc over 4 cylinders at a 50.9 mm stroke needs a 68.39 mm bore; the source says 63.4,
        // which is 68.4 with the digits transposed and yields 643 cc - 14% under the stated figure.
        match: (slug, r) => /^kawasaki-z-750-/.test(slug) && r.bore_mm === 63.4,
        apply: (r) => { r.bore_mm = 68.4; },
        why: 'Z 750: bore 63.4 -> 68.4 mm (63.4 gives 643 cc, not the 748 cc the row states)',
    },
    {
        // First-generation Z 1000 is 953 cc from 77.2 x 50.9 mm; the source's 77.0 x 51.8 computes to 965 cc. 51.8 mm is
        // the Ninja 400 stroke, so this is a carry-over rather than rounding; both figures go back to the published pair.
        match: (slug, r) => /^kawasaki-z-1000-/.test(slug) && r.displacement_cc === 953
            && r.bore_mm === 77 && r.stroke_mm === 51.8,
        apply: (r) => { r.bore_mm = 77.2; r.stroke_mm = 50.9; },
        why: 'Z 1000 (953 cc): bore/stroke 77.0 x 51.8 -> 77.2 x 50.9 mm (77.0 x 51.8 gives 965 cc)',
    },
];

const NUMERIC = new Set([
    'displacement_cc', 'cylinders', 'valves_per_cylinder', 'max_power_hp', 'max_power_rpm',
    'max_torque_nm', 'max_torque_rpm', 'bore_mm', 'stroke_mm', 'gears', 'top_speed_kph',
    'fuel_consumption_l_100km',
]);

const COLUMNS = [
    'engine_type', 'displacement_cc', 'cylinders', 'valves_per_cylinder', 'max_power_hp',
    'max_power_rpm', 'max_torque_nm', 'max_torque_rpm', 'compression_ratio', 'bore_mm', 'stroke_mm',
    'cooling_system', 'fuel_system', 'transmission_type', 'gears', 'final_drive', 'top_speed_kph',
    'fuel_consumption_l_100km', 'emission_standard',
];

const raw = JSON.parse(readFileSync(SRC, 'utf8'));
const log = [];
const dropped = [];

// --- errata, then the checks ----------------------------------------------------------------
let erratumCount = 0;
for (const [slug, row] of Object.entries(raw)) {
    for (const e of ERRATA) {
        if (e.match(slug, row)) { e.apply(row); erratumCount++; }
    }
}
for (const e of ERRATA) {
    const n = Object.entries(raw).filter(([s, r]) => e.match(s, r)).length;
    if (n !== 0) throw new Error(`erratum did not take on ${n} rows: ${e.why}`);
}

// The bore/stroke/cylinder/displacement quartet has one algebraic relation, so it checks itself. 1% sits outside published
// rounding and still catches a transposed digit. A failing pair loses bore and stroke, never the displacement.
const GEOMETRY_TOLERANCE_PCT = 1.0;
let geometryDropped = 0;
for (const [slug, r] of Object.entries(raw)) {
    if (!(r.bore_mm && r.stroke_mm && r.cylinders && r.displacement_cc)) continue;
    const calc = Math.PI / 4 * r.bore_mm ** 2 * r.stroke_mm * r.cylinders / 1000;
    const dev = Math.abs(calc - r.displacement_cc) / r.displacement_cc * 100;
    if (dev > GEOMETRY_TOLERANCE_PCT) {
        dropped.push(`${slug}: bore/stroke ${r.bore_mm}x${r.stroke_mm} over ${r.cylinders} cyl gives `
            + `${calc.toFixed(0)} cc against a stated ${r.displacement_cc} cc (${dev.toFixed(1)}%)`);
        r.bore_mm = null;
        r.stroke_mm = null;
        geometryDropped++;
    }
}

// Bounds mirror the CHECK constraints in V1__initial_schema.sql plus physical plausibility. A value
// outside them is dropped rather than stored; the seed is meant to fill gaps, not to widen them.
const BOUNDS = {
    displacement_cc: [30, 2500],
    cylinders: [1, 6],
    valves_per_cylinder: [2, 5],
    max_power_hp: [1, 350],
    max_power_rpm: [1500, 16000],
    max_torque_nm: [1, 250],
    max_torque_rpm: [1000, 14000],
    bore_mm: [30, 120],
    stroke_mm: [25, 130],
    gears: [1, 7],
    top_speed_kph: [40, 340],
    fuel_consumption_l_100km: [1.5, 15],
};
let boundsDropped = 0;
for (const [slug, r] of Object.entries(raw)) {
    for (const [col, [lo, hi]] of Object.entries(BOUNDS)) {
        const v = r[col];
        if (v === null || v === undefined) continue;
        if (typeof v !== 'number' || !Number.isFinite(v) || v < lo || v > hi) {
            dropped.push(`${slug}: ${col} = ${v} is outside ${lo}..${hi}`);
            r[col] = null;
            boundsDropped++;
        }
    }
}

// Peak torque below peak power is how a combustion engine behaves. The reverse is a swapped pair,
// and a swapped pair renders as a plausible number in the comparison table, so it is refused.
let rpmDropped = 0;
for (const [slug, r] of Object.entries(raw)) {
    if (r.max_power_rpm && r.max_torque_rpm && r.max_torque_rpm > r.max_power_rpm) {
        dropped.push(`${slug}: torque peak ${r.max_torque_rpm} rpm sits above power peak ${r.max_power_rpm} rpm`);
        r.max_torque_rpm = null;
        rpmDropped++;
    }
}

let widthDropped = 0;
for (const [slug, r] of Object.entries(raw)) {
    for (const [col, max] of Object.entries(WIDTH)) {
        const v = r[col];
        if (typeof v === 'string' && v.length > max) {
            dropped.push(`${slug}: ${col} is ${v.length} chars, column holds ${max}`);
            r[col] = null;
            widthDropped++;
        }
    }
}

// --- SQL ------------------------------------------------------------------------------------
const lit = (v, col) => {
    if (v === null || v === undefined || v === '') return 'NULL';
    if (NUMERIC.has(col)) {
        if (typeof v !== 'number' || !Number.isFinite(v)) return 'NULL';
        return String(v);
    }
    return `'${String(v).replace(/'/g, "''")}'`;
};

const slugs = Object.keys(raw).sort();
const values = slugs.map((s, i) =>
    `(${i + 1}, '${s.replace(/'/g, "''")}', ${COLUMNS.map((c) => lit(raw[s][c], c)).join(', ')})`);

const filled = (col) => slugs.filter((s) => raw[s][col] !== null && raw[s][col] !== undefined && raw[s][col] !== '').length;
const fillReport = COLUMNS.map((c) => `--   ${c.padEnd(26)} ${String(filled(c)).padStart(3)} of ${slugs.length}`).join('\n');

const erratumNotes = ERRATA.map((e) => `--   ${e.why}`).join('\n');
const droppedNotes = dropped.length
    ? dropped.map((d) => `--   ${d}`).join('\n')
    : '--   (none)';

const sql = `-- Motorcycle Comparison API - Kawasaki engine specifications, researched set
-- Generated by tools/import-kawasaki-engine-research.mjs. Do not edit by hand; edit the
-- generator and re-run it, or the next run will silently revert whatever was changed here.
--
-- WHAT THIS IS, AND WHAT IT IS NOT
-- The sibling brand seeds in this folder are scrapes: each figure was parsed out of one named page
-- for that exact model year. This one is not. It fills the engine block for ${slugs.length} Kawasaki
-- model-years the FIPE snapshot created without one, and its figures were researched per engine
-- family - manufacturer specification sheets where those exist, press and road-test material where
-- they do not - and then applied to every model year that shares the family. That is a weaker claim
-- than the other files in this folder make, and it is recorded here because the difference is not
-- visible once the numbers are rows in a table.
--
-- Two columns deserve the warning most. top_speed_kph and fuel_consumption_l_100km are almost never
-- published by the manufacturer; they come from magazine testing, they vary with rider, load and
-- conditions, and they should be read as indicative. The bore, stroke, displacement, cylinder count
-- and compression figures are on much firmer ground: they are physical properties of the engine,
-- they are published, and the check below proves them against each other.
--
-- ORDERING
-- Flyway runs repeatable migrations in description order, and this import only gap-fills, so it has
-- to run after the FIPE seed that creates the rows it fills. "motorcycles kawasaki specs research
-- 2026 08" sorts after "motorcycles brazil fipe 2026 08" (k > b) and after
-- "motorcycles kawasaki specs 2026 08" ('2' < 'r'), which is the order this file needs: the scraped
-- Kawasaki seed is the better-sourced of the two, so it goes first and its values win.
--
-- GAP-FILL ONLY
-- Every write below is COALESCE(existing, imported), so a value already in the catalogue is never
-- overwritten - not by this file, and not on a re-run. This file also creates no motorcycles. All
-- ${slugs.length} slugs were present in the catalogue when it was generated, and a slug that is
-- missing at run time is skipped rather than inserted, because a row built from an engine block
-- alone would have no brand, model or year worth serving.
--
-- CHECKS APPLIED AT GENERATION
-- Nothing here is estimated or interpolated. A field the research could not establish is NULL.
-- Beyond that, four guards ran, and each drops a value rather than the row:
--   geometry     bore, stroke, cylinders and displacement have one algebraic relation, so the
--                quartet checks itself. Deviation over 1% drops bore and stroke and keeps
--                displacement. (${geometryDropped} value${geometryDropped === 1 ? '' : 's'} dropped)
--   bounds       each figure must sit inside a physically plausible range and inside the CHECK
--                constraints in V1__initial_schema.sql. (${boundsDropped} dropped)
--   rpm order    peak torque must not sit above peak power in the rev range, which would mean a
--                swapped pair. (${rpmDropped} dropped)
--   width        a string must fit its column, or it fails inside Flyway rather than here.
--                (${widthDropped} dropped)
--
-- ERRATA, APPLIED BEFORE THE CHECKS
${erratumNotes}
--
-- VALUES DROPPED BY THE CHECKS
${droppedNotes}
--
-- COVERAGE OF THE ${slugs.length} ROWS
${fillReport}

BEGIN;

CREATE TEMP TABLE tmp_kawasaki_engine_research (
    row_no                   INTEGER PRIMARY KEY,
    slug                     TEXT NOT NULL,
    engine_type              VARCHAR(80),
    displacement_cc          INTEGER,
    cylinders                INTEGER,
    valves_per_cylinder      INTEGER,
    max_power_hp             NUMERIC(6, 1),
    max_power_rpm            INTEGER,
    max_torque_nm            NUMERIC(6, 1),
    max_torque_rpm           INTEGER,
    compression_ratio        VARCHAR(20),
    bore_mm                  NUMERIC(6, 2),
    stroke_mm                NUMERIC(6, 2),
    cooling_system           VARCHAR(40),
    fuel_system              VARCHAR(120),
    transmission_type        VARCHAR(60),
    gears                    INTEGER,
    final_drive              VARCHAR(40),
    top_speed_kph            INTEGER,
    fuel_consumption_l_100km NUMERIC(5, 2),
    emission_standard        VARCHAR(30),
    motorcycle_id            BIGINT,
    engine_id                BIGINT
) ON COMMIT DROP;

INSERT INTO tmp_kawasaki_engine_research (
    row_no, slug, ${COLUMNS.join(', ')}
) VALUES
${values.join(',\n')};

-- Join on slug. The FIPE seed derives the same slug from the same descriptor, so this is an
-- equality join rather than a fuzzy name match.
UPDATE tmp_kawasaki_engine_research s
SET motorcycle_id = m.id,
    engine_id     = m.engine_specification_id
FROM motorcycles m
WHERE m.slug = s.slug;

DO $$
BEGIN
    IF pg_get_serial_sequence('engine_specifications', 'id') IS NULL THEN
        RAISE EXCEPTION 'engine_specifications has no serial/identity sequence';
    END IF;
END $$;

-- A catalogue row seeded from FIPE may carry no engine block at all. Give those one before the
-- gap-fill runs, or their figures would be dropped on the floor.
UPDATE tmp_kawasaki_engine_research
SET engine_id = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)
WHERE motorcycle_id IS NOT NULL AND engine_id IS NULL;

INSERT INTO engine_specifications (id)
SELECT s.engine_id
FROM tmp_kawasaki_engine_research s
WHERE s.engine_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM engine_specifications e WHERE e.id = s.engine_id)
ORDER BY s.row_no;

UPDATE motorcycles m
SET engine_specification_id = s.engine_id
FROM tmp_kawasaki_engine_research s
WHERE m.id = s.motorcycle_id AND m.engine_specification_id IS NULL;

-- COALESCE in every column: whatever the catalogue already holds wins, so this is a gap-fill and
-- re-running the seed changes nothing the second time.
UPDATE engine_specifications e
SET engine_type              = COALESCE(e.engine_type, s.engine_type),
    displacement_cc          = COALESCE(e.displacement_cc, s.displacement_cc),
    cylinders                = COALESCE(e.cylinders, s.cylinders),
    valves_per_cylinder      = COALESCE(e.valves_per_cylinder, s.valves_per_cylinder),
    max_power_hp             = COALESCE(e.max_power_hp, s.max_power_hp),
    max_power_rpm            = COALESCE(e.max_power_rpm, s.max_power_rpm),
    max_torque_nm            = COALESCE(e.max_torque_nm, s.max_torque_nm),
    max_torque_rpm           = COALESCE(e.max_torque_rpm, s.max_torque_rpm),
    compression_ratio        = COALESCE(e.compression_ratio, s.compression_ratio),
    bore_mm                  = COALESCE(e.bore_mm, s.bore_mm),
    stroke_mm                = COALESCE(e.stroke_mm, s.stroke_mm),
    cooling_system           = COALESCE(e.cooling_system, s.cooling_system),
    fuel_system              = COALESCE(e.fuel_system, s.fuel_system),
    transmission_type        = COALESCE(e.transmission_type, s.transmission_type),
    gears                    = COALESCE(e.gears, s.gears),
    final_drive              = COALESCE(e.final_drive, s.final_drive),
    top_speed_kph            = COALESCE(e.top_speed_kph, s.top_speed_kph),
    fuel_consumption_l_100km = COALESCE(e.fuel_consumption_l_100km, s.fuel_consumption_l_100km),
    emission_standard        = COALESCE(e.emission_standard, s.emission_standard)
FROM tmp_kawasaki_engine_research s
WHERE e.id = s.engine_id;

-- The one place this file overrules a value the catalogue already held, and it is allowed to only
-- because the row convicts itself. bore, stroke, cylinders and displacement have one algebraic
-- relation, so a stored pair that misses its own displacement by more than 2% is arithmetically
-- wrong whatever its provenance - and an imported pair that lands within 2% is arithmetically
-- right. Where those two conditions hold at once, the imported pair replaces the stored one.
--
-- The ZZR 1200 is why this exists. The scraped seed publishes 78.0 x 59.0 mm against its own
-- 1164 cc, which computes to 1128 cc; the figures are 79.0 x 59.4 mm, rounded to whole millimetres
-- by the source until the error compounded past 3%. Nothing is hardcoded here: no slug is named,
-- the rule tests the arithmetic, and a re-run on corrected upstream data simply matches nothing.
UPDATE engine_specifications e
SET bore_mm   = s.bore_mm,
    stroke_mm = s.stroke_mm
FROM tmp_kawasaki_engine_research s
WHERE e.id = s.engine_id
  AND s.bore_mm IS NOT NULL AND s.stroke_mm IS NOT NULL
  AND e.cylinders IS NOT NULL AND e.displacement_cc IS NOT NULL
  AND e.bore_mm IS NOT NULL AND e.stroke_mm IS NOT NULL
  -- what is stored contradicts the displacement stored beside it
  AND abs(pi() / 4 * e.bore_mm * e.bore_mm * e.stroke_mm * e.cylinders / 1000.0 - e.displacement_cc)
      > 0.02 * e.displacement_cc
  -- and what this import carries does not
  AND abs(pi() / 4 * s.bore_mm * s.bore_mm * s.stroke_mm * e.cylinders / 1000.0 - e.displacement_cc)
      <= 0.02 * e.displacement_cc;

-- ---------------------------------------------------------------------------
-- Post-conditions. Each one fails the migration rather than leaving the catalogue in a state the
-- API would serve as fact.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad INTEGER;
    unmatched INTEGER;
    offenders TEXT;
BEGIN
    SELECT count(*) INTO unmatched FROM tmp_kawasaki_engine_research WHERE motorcycle_id IS NULL;
    IF unmatched <> 0 THEN
        -- Not fatal: this seed creates no motorcycles, so an unmatched slug means the catalogue
        -- moved under it. Worth saying out loud, because the row's figures went nowhere.
        RAISE NOTICE 'Kawasaki engine research: % of % slugs are not in the catalogue and were skipped',
            unmatched, (SELECT count(*) FROM tmp_kawasaki_engine_research);
    END IF;

    -- Every matched row must now have an engine block; a row that does not means the link above
    -- failed and the figures were written to an engine nothing points at.
    SELECT count(*) INTO bad
    FROM tmp_kawasaki_engine_research s
    JOIN motorcycles m ON m.id = s.motorcycle_id
    WHERE m.engine_specification_id IS NULL;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Kawasaki engine research: % matched rows still have no engine block', bad;
    END IF;

    -- The geometry relation is checked again here, against what is actually in the table rather
    -- than against what this file tried to write. That distinction matters: every column above is a
    -- COALESCE, so the stored quartet can be a mixture - a displacement the catalogue already had
    -- beside a bore and stroke this file supplied. Neither source is wrong on its own and both pass
    -- their own checks; only the merged row is impossible. This is the only place that can catch it.
    -- The message names the rows, because a bare count sends the next reader back to the database.
    SELECT count(*), string_agg(slug, ', ' ORDER BY slug) INTO bad, offenders
    FROM (
        SELECT m.slug
        FROM engine_specifications e
        JOIN motorcycles m ON m.engine_specification_id = e.id
        JOIN tmp_kawasaki_engine_research s ON s.motorcycle_id = m.id
        WHERE e.bore_mm IS NOT NULL AND e.stroke_mm IS NOT NULL
          AND e.cylinders IS NOT NULL AND e.displacement_cc IS NOT NULL
          AND abs(pi() / 4 * e.bore_mm * e.bore_mm * e.stroke_mm * e.cylinders / 1000.0 - e.displacement_cc)
              > 0.02 * e.displacement_cc
    ) q;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Kawasaki engine research: % engine rows have bore/stroke that contradict displacement: %',
            bad, offenders;
    END IF;

    SELECT count(*) INTO bad
    FROM engine_specifications e
    JOIN motorcycles m ON m.engine_specification_id = e.id
    JOIN tmp_kawasaki_engine_research s ON s.motorcycle_id = m.id
    WHERE e.max_power_rpm IS NOT NULL AND e.max_torque_rpm IS NOT NULL
      AND e.max_torque_rpm > e.max_power_rpm;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Kawasaki engine research: % engine rows peak torque above peak power', bad;
    END IF;
END $$;

COMMIT;
`;

writeFileSync(OUT, sql);

console.log(`rows:              ${slugs.length}`);
console.log(`errata applied:    ${erratumCount}`);
console.log(`geometry dropped:  ${geometryDropped}`);
console.log(`bounds dropped:    ${boundsDropped}`);
console.log(`rpm-order dropped: ${rpmDropped}`);
console.log(`width dropped:     ${widthDropped}`);
console.log(`written:           ${OUT}`);
