#!/usr/bin/env node
// Regenerates db/seed/R__zzzzz_motorcycles_brakes_<brand(s)>_2026_09.sql from tools/brakes-research.json,
// reproducibly - one file per brand (or brand set) present in the current research batch, never a
// shared file whose content changes across runs.
// Gap-fills front_brake / rear_brake / abs_type on existing catalogue rows only. Usage: node <this> [--check]

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const args = process.argv.slice(2);
const CHECK_ONLY = args.includes('--check');
const option = (name, fallback) => {
    const i = args.indexOf(name);
    return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};

const JSON_PATH = path.join(REPO_ROOT, 'tools/brakes-research.json');
const SLUG_LIST = path.join(REPO_ROOT, 'tools/slugs_missing_brakes.txt');
// SQL_PATH is derived below from the brand(s) actually present in this research batch - see `brands`.

const COLUMN_LIMIT = 160;
const ABS_COLUMN_LIMIT = 80;
const SLUG_SHAPE = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const die = (message) => {
    console.error(`import-brakes: ${message}`);
    process.exit(1);
};

// --- inputs --------------------------------------------------------------------------------
// front_brake and rear_brake are always NULL together in this catalogue, so one target list
// covers both columns - unlike the suspension backfill, which needs a separate list per column.
const targets = new Set(
    fs.readFileSync(SLUG_LIST, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean),
);

const research = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
if (!Array.isArray(research)) die('brakes-research.json must be a flat array of {slug, front_brake, rear_brake, abs_type?, source}');

// --- validation ------------------------------------------------------------------------------
// Every research entry is staged, even one no longer in the target list: a slug can leave the
// worklist between the research pass and this run because another seed resolved it first, and the
// COALESCE guard on the final UPDATE makes that row a safe no-op rather than a reason to drop data
// tools/brakes-research.json already paid to collect. Anything actually wrong with an entry (bad
// slug shape, a duplicate, no brake value at all, no source) still aborts generation.
const seen = new Set();
const resolvedElsewhere = [];
for (const entry of research) {
    const { slug } = entry;
    if (!slug) die(`entry ${JSON.stringify(entry).slice(0, 80)} has no slug`);
    if (!SLUG_SHAPE.test(slug)) die(`slug "${slug}" is not a shape the public routing can use`);
    if (seen.has(slug)) die(`slug "${slug}" appears more than once`);
    seen.add(slug);
    if (!targets.has(slug)) resolvedElsewhere.push(slug);
    if (!entry.front_brake && !entry.rear_brake) die(`slug "${slug}" carries neither a front nor a rear value`);
    if (!entry.source) die(`slug "${slug}" has no source`);
}

// --- column fitting --------------------------------------------------------------------------
// Cut at a word boundary, never mid-word, and count characters rather than bytes.
const WIDTH_CUTS = { count: 0 };
function fitColumn(value, limit) {
    if (value == null || value === '') return null;
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

const quote = (value) => (value == null ? 'NULL' : `'${value.replace(/'/g, "''")}'`);

const rows = research
    .map((entry) => ({
        slug: entry.slug,
        front: fitColumn(entry.front_brake, COLUMN_LIMIT),
        rear: fitColumn(entry.rear_brake, COLUMN_LIMIT),
        absType: fitColumn(entry.abs_type, ABS_COLUMN_LIMIT),
        source: entry.source,
    }))
    .sort((a, b) => (a.slug < b.slug ? -1 : a.slug > b.slug ? 1 : 0));

// --- computed facts for the header -------------------------------------------------------------
const brandOf = (slug) => slug.split('-')[0];
const nameplateOf = (slug) => slug.replace(/-\d{4}$/, '');

const brands = [...new Set(rows.map((r) => brandOf(r.slug)))].sort();
const titleCase = (b) => b.charAt(0).toUpperCase() + b.slice(1);
const brandList = brands.map(titleCase).join(', ');

// One seed file per brand (or brand set): tools/brakes-research.json is replaced wholesale between
// research batches rather than accumulated, so a fixed shared filename would silently overwrite an
// already-applied, already-valid batch for a brand no longer present in this run. brandOf() already
// lower-cases (slugs are lower-case by construction), so no extra normalisation is needed here.
// --suffix handles a second research pass on a brand that already has a seed here (the file must stay
// distinct or the second run overwrites the first's already-applied rows) - same precedent as
// R__motorcycles_kawasaki_specs_research_2026_08.sql sitting beside R__motorcycles_kawasaki_specs_2026_08.sql.
const suffix = option('--suffix', null);
const brandToken = suffix ? `${brands.join('_')}_${suffix}` : brands.join('_');
const SQL_PATH = path.join(REPO_ROOT, `src/main/resources/db/seed/R__zzzzz_motorcycles_brakes_${brandToken}_2026_09.sql`);

const byNameplate = new Map();
for (const r of rows) {
    const key = nameplateOf(r.slug);
    if (!byNameplate.has(key)) byNameplate.set(key, []);
    byNameplate.get(key).push(r);
}
const splitNameplates = [...byNameplate.entries()]
    .filter(([, rs]) => rs.length > 1
        && (new Set(rs.map((r) => r.front)).size > 1 || new Set(rs.map((r) => r.rear)).size > 1 || new Set(rs.map((r) => r.absType)).size > 1))
    .map(([name]) => name)
    .sort();

const sources = [...new Set(rows.map((r) => r.source))].sort();
const sourceDomains = [...new Set(sources.flatMap((s) => (s.match(/[a-z0-9.-]+\.[a-z]{2,}/gi) || [])))].sort();
const frontCount = rows.filter((r) => r.front).length;
const rearCount = rows.filter((r) => r.rear).length;
const absCount = rows.filter((r) => r.absType).length;
const targetsForBatch = [...targets].filter((s) => brands.includes(brandOf(s))).length;

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

const resolvedProse = resolvedElsewhere.length === 0
    ? 'Every staged slug is still missing front_brake or rear_brake in the live catalogue.'
    : `${resolvedElsewhere.length} staged slug${resolvedElsewhere.length === 1 ? '' : 's'} `
      + `(${resolvedElsewhere.join(', ')}) already ${resolvedElsewhere.length === 1 ? 'has' : 'have'} both columns filled by another `
      + 'seed since this research was compiled; the COALESCE guard below makes writing it a safe no-op rather than a reason to drop the row.';

const header = `-- Motorcycle Comparison API - front_brake / rear_brake / abs_type backfill
--
-- Purpose: back-fill motorcycles.front_brake, motorcycles.rear_brake and motorcycles.abs_type for
-- catalogue rows still missing them. All three columns live directly on motorcycles (VARCHAR(160),
-- VARCHAR(160), VARCHAR(80) - see V1__initial_schema.sql), there is no child table involved.
--
-- Provenance: hand-curated from tools/brakes-research.json (web research, validated by
-- tools/validate-brakes-research.mjs), which only accepts a value carrying a real specification -
-- a diameter, an explicit disc/drum type, or an explicit "None" - and requires a source that owns a
-- diameter figure before accepting it. Regenerate this file with tools/import-brakes.mjs - every
-- count in this header is computed by that script, never typed.
-- Sources drawn on for this batch:
${wrap('--   ', sourceDomains.join(', '))}
--
${wrap('-- ', `Scope of this batch: ${brandList}. ${targetsForBatch} catalogue slugs of this brand were missing front_brake or rear_brake; ${rows.length} are staged below (${frontCount} carry a front value, ${rearCount} a rear, ${absCount} an abs_type).`)}
${wrap('-- ', resolvedProse)}
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. A slug with no corresponding motorcycles.slug is counted and left alone rather than
-- fuzzy-matched or inserted - never insert a catalogue row here.
--
-- Generation granularity: research was done per nameplate but written per exact slug, and split by
${wrap('-- ', `year range wherever the model actually changed - ${splitNameplates.length} of the ${byNameplate.size} nameplates in this batch carry more than one distinct value (front_brake, rear_brake or abs_type) across their model years. A single value was never stretched across a nameplate's whole life.`)}
--
-- Ordering, and why the filename is load-bearing: Flyway runs repeatable migrations in description
-- order. "zzzzz motorcycles brakes 2026 09" sorts after every other seed in this directory,
-- including "zzzz motorcycles suspension 2026 09" (the previous last-to-run file: 'zzzzz' > 'zzzz_'
-- because 'z' > '_'). That is deliberate and safe, mirroring the suspension backfill: the input list
-- is exactly the set of rows still missing front_brake or rear_brake after every existing seed, so
-- this file is pure gap-fill and cannot pre-empt any other import's claim on these columns.
--
${wrap('-- ', `${WIDTH_CUTS.count} values exceeded their column width and were cut at a word boundary rather than mid-word.`)}
--
-- Idempotent and repeatable: every write below is COALESCE(existing, staged), so re-running this
-- file changes nothing once it has been applied, and an admin edit or a richer earlier import
-- always wins over this one.`;

const body = `BEGIN;

CREATE TEMP TABLE tmp_motorcycle_brakes (
    row_no        bigint PRIMARY KEY,
    slug          varchar(160) NOT NULL,
    front         varchar(160),
    rear          varchar(160),
    abs_type      varchar(80),
    motorcycle_id bigint,
    CONSTRAINT uk_tmp_motorcycle_brakes_slug UNIQUE (slug),
    CONSTRAINT ck_tmp_motorcycle_brakes_any CHECK (front IS NOT NULL OR rear IS NOT NULL)
) ON COMMIT DROP;

INSERT INTO tmp_motorcycle_brakes (row_no, slug, front, rear, abs_type) VALUES
${rows.map((r, i) => `(${i + 1}, '${r.slug}', ${quote(r.front)}, ${quote(r.rear)}, ${quote(r.absType)})`).join(',\n')};

-- ---------------------------------------------------------------------------
-- Resolve every staged row against the live catalogue. An exact slug match only.
-- ---------------------------------------------------------------------------
UPDATE tmp_motorcycle_brakes t SET motorcycle_id = m.id FROM motorcycles m WHERE m.slug = t.slug;

-- Gap-fill only: an existing value (an admin edit, or an earlier import) always wins. Each column
-- carries its own COALESCE guard so a row missing only one of the three is never disturbed on the
-- other two.
UPDATE motorcycles m
SET front_brake = COALESCE(m.front_brake, t.front),
    rear_brake  = COALESCE(m.rear_brake,  t.rear),
    abs_type    = COALESCE(m.abs_type,    t.abs_type)
FROM tmp_motorcycle_brakes t
WHERE m.id = t.motorcycle_id;

-- ---------------------------------------------------------------------------
-- Defensive checks before commit, mirroring R__zzzz_motorcycles_suspension_2026_09.sql.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad        bigint;
    unresolved bigint;
BEGIN
    SELECT count(*) INTO bad
    FROM tmp_motorcycle_brakes
    WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle brakes backfill: % staged slugs are not a shape the public routing can use', bad;
    END IF;

    SELECT count(*) INTO bad
    FROM tmp_motorcycle_brakes
    WHERE length(front) > 160 OR length(rear) > 160 OR length(abs_type) > 80;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle brakes backfill: % staged rows exceed their column limit', bad;
    END IF;

    SELECT count(*) INTO unresolved FROM tmp_motorcycle_brakes WHERE motorcycle_id IS NULL;
    RAISE NOTICE 'Motorcycle brakes backfill: % of % staged rows matched a catalogue slug, % left unresolved',
        (SELECT count(*) FROM tmp_motorcycle_brakes WHERE motorcycle_id IS NOT NULL),
        (SELECT count(*) FROM tmp_motorcycle_brakes), unresolved;
END $$;

COMMIT;
`;

const sql = `${header}\n\n${body}`;

if (CHECK_ONLY) {
    console.log(`staged rows: ${rows.length} (${frontCount} front, ${rearCount} rear, ${absCount} abs_type) across ${brands.length} brand(s): ${brandList}`);
    console.log(`nameplates: ${byNameplate.size}, of which ${splitNameplates.length} split by model year`);
    console.log(`resolved elsewhere (stale vs worklist): ${resolvedElsewhere.length} ${resolvedElsewhere.join(', ')}`);
    console.log(`width cuts: ${WIDTH_CUTS.count}`);
    process.exit(0);
}

fs.writeFileSync(SQL_PATH, sql, 'utf8');
console.log(`import-brakes: wrote ${rows.length} rows (${frontCount} front, ${rearCount} rear, ${absCount} abs_type) for ${brandList} to ${path.relative(REPO_ROOT, SQL_PATH)}`);
console.log(`  nameplates ${byNameplate.size}, split by year ${splitNameplates.length}, resolved elsewhere ${resolvedElsewhere.length}, width cuts ${WIDTH_CUTS.count}`);
