#!/usr/bin/env node
// Regenerates db/seed/R__zzzz_motorcycles_engine_specs_2026_09.sql from tools/engine-specs-research.json,
// reproducibly. Gap-fills engine_specifications columns for catalogue rows still missing core specs. Usage: node <this> [--check]

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const args = process.argv.slice(2);
const option = (name, fallback) => {
    const i = args.indexOf(name);
    return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};
const CHECK_ONLY = args.includes('--check');

const JSON_PATH = path.resolve(option('--research', path.join(REPO_ROOT, 'tools/engine-specs-research.json')));
const TARGET_LIST = path.join(REPO_ROOT, 'tools/motorcycles-incomplete-core-engine-specs.txt');
const SQL_PATH = path.join(REPO_ROOT, 'src/main/resources/db/seed/R__zzzz_motorcycles_engine_specs_2026_09.sql');

const SLUG_SHAPE = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const die = (message) => {
    console.error(`import-engine-specs: ${message}`);
    process.exit(1);
};

// --- schema -----------------------------------------------------------------------------------
// Mirrors engine_specifications in V1__initial_schema.sql exactly. Every CHECK constraint on that
// table is a floor only (> 0 / >= 0) - there is no ceiling, so this generator does not invent one
// either. "int" fields are rejected if they are not a whole number; "num" fields accept decimals.
const KEY_FIELDS = ['displacement_cc', 'cylinders', 'max_power_hp', 'max_power_rpm', 'max_torque_nm', 'max_torque_rpm'];
const NUMERIC_COLUMNS = {
    displacement_cc: { kind: 'int', min: 0, minInclusive: false },
    cylinders: { kind: 'int', min: 0, minInclusive: false },
    valves_per_cylinder: { kind: 'int', min: 0, minInclusive: false },
    max_power_hp: { kind: 'num', min: 0, minInclusive: true },
    max_power_rpm: { kind: 'int', min: 0, minInclusive: false },
    max_torque_nm: { kind: 'num', min: 0, minInclusive: true },
    max_torque_rpm: { kind: 'int', min: 0, minInclusive: false },
    bore_mm: { kind: 'num', min: 0, minInclusive: true },
    stroke_mm: { kind: 'num', min: 0, minInclusive: true },
    gears: { kind: 'int', min: 0, minInclusive: false },
    top_speed_kph: { kind: 'int', min: 0, minInclusive: false },
    fuel_consumption_l_100km: { kind: 'num', min: 0, minInclusive: true },
};
const STRING_COLUMNS = {
    engine_type: 80,
    compression_ratio: 20,
    cooling_system: 40,
    fuel_system: 120,
    transmission_type: 60,
    final_drive: 40,
    emission_standard: 30,
};
// Declared in engine_specifications column order; used for the temp table shape and the SQL cast on
// each VALUES literal.
const ALL_COLUMNS = [
    'engine_type', 'displacement_cc', 'cylinders', 'valves_per_cylinder',
    'max_power_hp', 'max_power_rpm', 'max_torque_nm', 'max_torque_rpm',
    'compression_ratio', 'bore_mm', 'stroke_mm', 'cooling_system', 'fuel_system',
    'transmission_type', 'gears', 'final_drive', 'top_speed_kph', 'fuel_consumption_l_100km',
    'emission_standard',
];
const SQL_TYPE = {
    engine_type: 'varchar(80)', displacement_cc: 'integer', cylinders: 'integer',
    valves_per_cylinder: 'integer', max_power_hp: 'numeric(6,1)', max_power_rpm: 'integer',
    max_torque_nm: 'numeric(6,1)', max_torque_rpm: 'integer', compression_ratio: 'varchar(20)',
    bore_mm: 'numeric(6,2)', stroke_mm: 'numeric(6,2)', cooling_system: 'varchar(40)',
    fuel_system: 'varchar(120)', transmission_type: 'varchar(60)', gears: 'integer',
    final_drive: 'varchar(40)', top_speed_kph: 'integer', fuel_consumption_l_100km: 'numeric(5,2)',
    emission_standard: 'varchar(30)',
};

// --- inputs -------------------------------------------------------------------------------------
// The target list is the authority on what may be staged: it is exactly the set of catalogue slugs
// sql-pro found with an incomplete core (< 6 of the 6 key fields) as of this analysis. Staging
// anything outside it risks pre-empting a claim this file, running last, has no business making.
const targets = new Set(
    fs.readFileSync(TARGET_LIST, 'utf8')
        .split('\n')
        .map((l) => l.trim())
        .filter(Boolean)
        .map((l) => l.split('\t').pop()),
);

const research = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
if (!Array.isArray(research)) die('engine-specs-research.json must be a flat array of {slug, ...columns, source}');

// --- validation -----------------------------------------------------------------------------
// Every check below aborts rather than emitting a file. This seed runs last of all repeatables, so a
// slug that is not in the target list is one another import may already own - staging it here would
// let this file pre-empt a claim it has no business making.
const KNOWN_KEYS = new Set(['slug', 'source', ...ALL_COLUMNS]);
const seen = new Set();
const WIDTH_CUTS = { count: 0 };

function fitColumn(value, limit) {
    const text = String(value).trim();
    if ([...text].length <= limit) return text;
    const chars = [...text];
    let cut = chars.slice(0, limit).join('');
    const lastSpace = cut.lastIndexOf(' ');
    if (lastSpace > 0) cut = cut.slice(0, lastSpace);
    cut = cut.replace(/[\s,;/-]+$/, '');
    WIDTH_CUTS.count++;
    return cut;
}

const rows = [];
for (const entry of research) {
    const { slug } = entry;
    if (!slug) die(`entry ${JSON.stringify(entry).slice(0, 80)} has no slug`);
    if (!SLUG_SHAPE.test(slug)) die(`slug "${slug}" is not a shape the public routing can use`);
    if (seen.has(slug)) die(`slug "${slug}" appears more than once`);
    seen.add(slug);
    if (!targets.has(slug)) die(`slug "${slug}" is not in ${path.relative(REPO_ROOT, TARGET_LIST)}`);
    if (!entry.source) die(`slug "${slug}" has no source`);

    for (const key of Object.keys(entry)) {
        if (!KNOWN_KEYS.has(key)) die(`slug "${slug}" has unknown field "${key}"`);
    }

    const keyFieldCount = KEY_FIELDS.filter((f) => entry[f] !== undefined && entry[f] !== null).length;
    if (keyFieldCount < 3) die(`slug "${slug}" has only ${keyFieldCount} of the 6 key fields - floor is 3`);

    const row = { slug, source: entry.source };
    for (const [col, rule] of Object.entries(NUMERIC_COLUMNS)) {
        const value = entry[col];
        if (value === undefined || value === null) continue;
        if (typeof value !== 'number' || !Number.isFinite(value)) {
            die(`slug "${slug}" field "${col}" is not a finite number: ${JSON.stringify(value)}`);
        }
        if (rule.kind === 'int' && !Number.isInteger(value)) {
            die(`slug "${slug}" field "${col}" must be an integer, got ${value}`);
        }
        const violatesFloor = rule.minInclusive ? value < rule.min : value <= rule.min;
        if (violatesFloor) {
            die(`slug "${slug}" field "${col}" = ${value} violates ck_engine_specifications_${col}`);
        }
        row[col] = value;
    }
    for (const [col, limit] of Object.entries(STRING_COLUMNS)) {
        const value = entry[col];
        if (value === undefined || value === null || value === '') continue;
        row[col] = fitColumn(value, limit);
    }
    rows.push(row);
}

rows.sort((a, b) => (a.slug < b.slug ? -1 : a.slug > b.slug ? 1 : 0));
rows.forEach((r, i) => { r.row_no = i + 1; });

// Columns nobody in this batch filled get no COALESCE arm at all - a future batch that does fill one
// picks it up automatically without touching this generator.
const activeColumns = ALL_COLUMNS.filter((col) => rows.some((r) => r[col] !== undefined));

// --- computed facts for the header ---------------------------------------------------------
const brandOf = (slug) => slug.split('-')[0];
const brands = [...new Set(rows.map((r) => brandOf(r.slug)))].sort();
const stagedByBrand = new Map(brands.map((b) => [b, rows.filter((r) => brandOf(r.slug) === b).length]));
const targetsByBrand = new Map();
for (const slug of targets) {
    const b = brandOf(slug);
    targetsByBrand.set(b, (targetsByBrand.get(b) || 0) + 1);
}

const fieldFillCounts = new Map(activeColumns.map((c) => [c, rows.filter((r) => r[c] !== undefined).length]));

const sources = [...new Set(rows.map((r) => r.source))].sort();
const sourceDomains = [...new Set(sources.flatMap((s) => (s.match(/[a-z0-9.-]+\.[a-z]{2,}/gi) || [])))].sort();

const omittedCount = targets.size - rows.length;

const wrap = (prefix, text, width = 98) => {
    const out = [];
    let line = prefix;
    for (const word of text.split(/\s+/)) {
        if (line.length + 1 + word.length > width && line !== prefix) {
            out.push(line);
            line = `${prefix}${word}`;
        } else {
            line = line === prefix ? `${prefix}${word}` : `${line} ${word}`;
        }
    }
    if (line !== prefix) out.push(line);
    return out.join('\n');
};

const titleCase = (b) => b.charAt(0).toUpperCase() + b.slice(1);
const brandList = brands.map((b) => `${titleCase(b)} ${stagedByBrand.get(b)}/${targetsByBrand.get(b)}`).join(', ');
const fieldFillLine = activeColumns.map((c) => `${c} ${fieldFillCounts.get(c)}`).join(', ');

const header = `-- Motorcycle Comparison API - engine_specifications core-field backfill
--
-- Purpose: back-fill engine_specifications columns for catalogue rows sql-pro identified as
-- "core-incomplete" - missing at least one of displacement_cc, cylinders, max_power_hp,
-- max_power_rpm, max_torque_nm, max_torque_rpm. Every motorcycle already has an
-- engine_specifications row (1:1 via motorcycles.engine_specification_id, UNIQUE), so unlike the
-- suspension seed beside this one this is a column-level gap-fill inside an existing child row, not
-- a bare-columns-on-motorcycles fill. The defensive "give it an engine block if it somehow lacks
-- one" step below is inherited from R__motorcycles_displacement_cc_2026_09.sql for the same table -
-- it is not expected to fire against a catalogue where that seed has already run.
--
-- Provenance: researched by engine platform (one spec sheet covering every badge/trim sharing that
-- engine) via tools/engine-specs-research.json, gathered by a deep-research pass against
-- manufacturer press kits and official spec pages, corroborated against enthusiast references.
-- Regenerate this file with tools/import-engine-specs.mjs - every count in this header is computed
-- by that script, never typed. Sources drawn on for this batch:
${wrap('--   ', sourceDomains.join(', '))}
--
${wrap('-- ', `Scope of this batch (staged/gap-slugs by brand): ${brandList}.`)}
${wrap('-- ', `${targets.size} catalogue slugs were core-incomplete; ${rows.length} are staged below, ${omittedCount} deliberately left out.`)}
-- The omitted slugs are overwhelmingly motocross/enduro competition models (KTM EXC/SX, Yamaha YZ,
-- Kawasaki KX, Suzuki RM, Honda CRF-R, Beta, Sherco, Gas Gas) whose manufacturers do not publish
-- power or torque figures - displacement and bore/stroke alone cannot clear this file's floor of 3
-- of the 6 key fields. A NULL is preferable to an invented figure, the same policy the displacement
-- and suspension seeds already apply.
--
${wrap('-- ', `Field fill across the ${rows.length} staged rows: ${fieldFillLine}.`)}
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. The slugs staged here already carry the model year (motorcycles.slug is
-- slugify(brand + model + model_year)), so this join resolves for real.
--
-- Ordering, and why the filename is load-bearing: Flyway runs repeatable migrations in description
-- order. "zzzz motorcycles engine specs 2026 09" sorts after "zzzz motorcycles 1000ps specs 2026 09"
-- ('1' < 'e') so it runs after every per-brand seed and the 1000PS gap-fill and cannot pre-empt any
-- of their claims, and before "zzzz motorcycles suspension 2026 09" ('e' < 's') - though the two do
-- not compete for any column, so their relative order carries no correctness weight.
--
${wrap('-- ', `${WIDTH_CUTS.count} values exceeded their column width and were cut at a word boundary rather than mid-word.`)}
--
-- Idempotent and repeatable: every write below is COALESCE(existing, staged), so re-running this file
-- changes nothing once it has been applied. No CHECK constraint in this table has a ceiling, only a
-- floor (> 0 / >= 0), so values like a 225 Nm cruiser or a 106 mm big-bore twin are staged as-is.`;

const quote = (value) => (value == null ? 'NULL' : `'${String(value).replace(/'/g, "''")}'`);
const literal = (col, value) => {
    if (value === undefined) return 'NULL';
    if (STRING_COLUMNS[col] !== undefined) return quote(value);
    return String(value);
};

const tmpColumns = ALL_COLUMNS.map((c) => `    ${c.padEnd(24)} ${SQL_TYPE[c]}`).join(',\n');
const values = rows.map((r) => {
    const cols = ALL_COLUMNS.map((c) => literal(c, r[c])).join(', ');
    return `(${r.row_no}, '${r.slug}', ${cols})`;
});

const setClauses = activeColumns
    .map((c) => `    ${c.padEnd(24)} = COALESCE(e.${c}, t.${c})`)
    .join(',\n');

const body = `BEGIN;

CREATE TEMP TABLE tmp_motorcycle_engine_specs (
    row_no        bigint PRIMARY KEY,
    slug          varchar(160) NOT NULL,
${tmpColumns},
    motorcycle_id bigint,
    engine_id     bigint,
    CONSTRAINT uk_tmp_motorcycle_engine_specs_slug UNIQUE (slug)
) ON COMMIT DROP;

INSERT INTO tmp_motorcycle_engine_specs (row_no, slug, ${ALL_COLUMNS.join(', ')}) VALUES
${values.join(',\n')};

-- ---------------------------------------------------------------------------
-- Resolve every staged row against the live catalogue. An exact slug match only: a slug with no
-- corresponding motorcycles.slug is counted and left alone rather than fuzzy-matched or inserted -
-- never insert a catalogue row here.
-- ---------------------------------------------------------------------------
UPDATE tmp_motorcycle_engine_specs t
SET motorcycle_id = m.id,
    engine_id     = m.engine_specification_id
FROM motorcycles m
WHERE m.slug = t.slug;

-- A resolved motorcycle may not have an engine block yet. Give it one before the merge below, so a
-- real match is never silently dropped. Not expected to fire (see header), kept for parity with
-- R__motorcycles_displacement_cc_2026_09.sql against the same table.
UPDATE tmp_motorcycle_engine_specs
SET engine_id = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)
WHERE motorcycle_id IS NOT NULL
  AND engine_id IS NULL;

INSERT INTO engine_specifications (id)
SELECT t.engine_id
FROM tmp_motorcycle_engine_specs t
WHERE t.engine_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM engine_specifications e WHERE e.id = t.engine_id)
ORDER BY t.row_no;

UPDATE motorcycles m
SET engine_specification_id = t.engine_id
FROM tmp_motorcycle_engine_specs t
WHERE m.id = t.motorcycle_id AND m.engine_specification_id IS NULL AND t.engine_id IS NOT NULL;

-- Gap-fill only: an existing value (an admin edit, or an earlier import) always wins. Only columns
-- this batch actually staged get an arm - see activeColumns in the generator.
UPDATE engine_specifications e
SET
${setClauses}
FROM tmp_motorcycle_engine_specs t
WHERE e.id = t.engine_id;

-- ---------------------------------------------------------------------------
-- Defensive checks before commit, mirroring R__motorcycles_displacement_cc_2026_09.sql.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad        bigint;
    unresolved bigint;
BEGIN
    SELECT count(*) INTO bad
    FROM tmp_motorcycle_engine_specs
    WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle engine specs backfill: % staged slugs are not a shape the public routing can use', bad;
    END IF;

    SELECT count(*) INTO unresolved FROM tmp_motorcycle_engine_specs WHERE motorcycle_id IS NULL;
    RAISE NOTICE 'Motorcycle engine specs backfill: % of % staged rows matched a catalogue slug, % left unresolved',
        (SELECT count(*) FROM tmp_motorcycle_engine_specs WHERE motorcycle_id IS NOT NULL),
        (SELECT count(*) FROM tmp_motorcycle_engine_specs), unresolved;

    SELECT count(*) INTO bad
    FROM tmp_motorcycle_engine_specs t
    JOIN motorcycles m ON m.id = t.motorcycle_id
    WHERE t.engine_id IS NOT NULL AND m.engine_specification_id IS DISTINCT FROM t.engine_id;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle engine specs backfill: % rows have a mismatched engine block', bad;
    END IF;

    SELECT count(*) INTO bad
    FROM engine_specifications e
    JOIN tmp_motorcycle_engine_specs t ON t.engine_id = e.id
    WHERE e.displacement_cc IS NULL OR e.displacement_cc <= 0;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle engine specs backfill: % resolved engine blocks ended up with no positive displacement_cc', bad;
    END IF;
END $$;

COMMIT;
`;

const sql = `${header}\n\n${body}`;

if (CHECK_ONLY) {
    const current = fs.existsSync(SQL_PATH) ? fs.readFileSync(SQL_PATH, 'utf8') : '';
    const dataOf = (text) => (text.match(/^\(\d+, '.*\)[,;]$/gm) || []).join('\n');
    console.log(`staged rows: ${rows.length} across ${brands.length} brand(s): ${brandList}`);
    console.log(`active columns: ${activeColumns.join(', ')}`);
    console.log(`omitted: ${omittedCount}`);
    console.log(`width cuts: ${WIDTH_CUTS.count}`);
    console.log(`data rows identical to current file: ${dataOf(sql) === dataOf(current)}`);
    console.log(`whole file identical: ${sql === current}`);
    process.exit(0);
}

fs.writeFileSync(SQL_PATH, sql, 'utf8');
console.log(`import-engine-specs: wrote ${rows.length} rows for ${brandList} to ${path.relative(REPO_ROOT, SQL_PATH)}`);
console.log(`  active columns ${activeColumns.length}, omitted ${omittedCount}, width cuts ${WIDTH_CUTS.count}`);
