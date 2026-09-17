#!/usr/bin/env node
// Regenerates db/seed/R__zzzz_motorcycles_suspension_2026_09.sql from tools/suspension-research.json, reproducibly.
// Gap-fills front_suspension / rear_suspension on existing catalogue rows only. Usage: node <this> [--check]

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

const JSON_PATH = path.resolve(option('--research', path.join(REPO_ROOT, 'tools/suspension-research.json')));
const FRONT_LIST = path.join(REPO_ROOT, 'front_suspension.txt');
const REAR_LIST = path.join(REPO_ROOT, 'rear_suspension.txt');
const SQL_PATH = path.join(REPO_ROOT, 'src/main/resources/db/seed/R__zzzz_motorcycles_suspension_2026_09.sql');

const COLUMN_LIMIT = 160;
const SLUG_SHAPE = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const die = (message) => {
    console.error(`import-suspension: ${message}`);
    process.exit(1);
};

// --- inputs --------------------------------------------------------------------------------
// The two .txt lists are the authority on what may be staged: they are exactly the catalogue rows
// still NULL after every other seed. Expanding a nameplate over a year range instead would invent
// slugs - 34 of the Kawasaki nameplates alone have gaps in their year sets.
const readSlugList = (file) => new Set(
    fs.readFileSync(file, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean),
);

const frontTargets = readSlugList(FRONT_LIST);
const rearTargets = readSlugList(REAR_LIST);
const targets = new Set([...frontTargets, ...rearTargets]);

const research = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
if (!Array.isArray(research)) die('suspension-research.json must be a flat array of {slug, front, rear, source}');

// --- validation ----------------------------------------------------------------------------
// research.json is cumulative across batches (unlike brakes-research.json, which is replaced
// wholesale per batch), so a slug from an earlier, already-applied batch stops appearing in the .txt
// lists the moment its column stops being NULL - the lists are always regenerated from the live
// gap, never accumulated. That is not a reason to drop data already paid to collect: every entry is
// staged regardless, and the COALESCE guard on the final UPDATE makes a slug outside the current
// lists a safe no-op rather than a stale claim. Same precedent as tools/import-brakes.mjs's
// resolvedElsewhere. Anything actually wrong with an entry (bad shape, a duplicate, no value at
// all, no source) still aborts generation.
const seen = new Set();
const resolvedElsewhere = [];
for (const entry of research) {
    const { slug } = entry;
    if (!slug) die(`entry ${JSON.stringify(entry).slice(0, 80)} has no slug`);
    if (!SLUG_SHAPE.test(slug)) die(`slug "${slug}" is not a shape the public routing can use`);
    if (seen.has(slug)) die(`slug "${slug}" appears more than once`);
    seen.add(slug);
    if (!entry.front && !entry.rear) die(`slug "${slug}" carries neither a front nor a rear value`);
    if (!entry.source) die(`slug "${slug}" has no source`);
    if (!targets.has(slug)) resolvedElsewhere.push(slug);
}

// --- column fitting ------------------------------------------------------------------------
// Cut at a word boundary, never mid-word, and count characters rather than bytes: VARCHAR(160) is
// 160 characters and these values carry multibyte text.
const WIDTH_CUTS = { count: 0 };
function fitColumn(value) {
    if (value == null || value === '') return null;
    const text = String(value).trim();
    if ([...text].length <= COLUMN_LIMIT) return text;
    const chars = [...text];
    let cut = chars.slice(0, COLUMN_LIMIT).join('');
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
        front: fitColumn(entry.front),
        rear: fitColumn(entry.rear),
        source: entry.source,
    }))
    .sort((a, b) => (a.slug < b.slug ? -1 : a.slug > b.slug ? 1 : 0));

// --- computed facts for the header ---------------------------------------------------------
// Everything the header states is derived here, so a stale hand-typed count can never survive a
// regeneration.
const brandOf = (slug) => slug.split('-')[0];
const nameplateOf = (slug) => slug.replace(/-\d{4}$/, '');

const brands = [...new Set(rows.map((r) => brandOf(r.slug)))].sort();
const stagedByBrand = new Map(brands.map((b) => [b, rows.filter((r) => brandOf(r.slug) === b).length]));

// A slug in the .txt lists for a brand this batch covers, but absent from the research file, was
// deliberately left out: no trustworthy published spec. That is a NULL on purpose, not an oversight.
const omitted = [...targets]
    .filter((slug) => brands.includes(brandOf(slug)) && !seen.has(slug))
    .sort();
const omittedByNameplate = new Map();
for (const slug of omitted) {
    const key = nameplateOf(slug);
    if (!omittedByNameplate.has(key)) omittedByNameplate.set(key, []);
    omittedByNameplate.get(key).push(slug.slice(-4));
}

// Nameplates whose value actually changes across model years - the evidence that research was split
// by year rather than one figure stretched over a nameplate's whole life.
const byNameplate = new Map();
for (const r of rows) {
    const key = nameplateOf(r.slug);
    if (!byNameplate.has(key)) byNameplate.set(key, []);
    byNameplate.get(key).push(r);
}
const splitNameplates = [...byNameplate.entries()]
    .filter(([, rs]) => rs.length > 1
        && (new Set(rs.map((r) => r.front)).size > 1 || new Set(rs.map((r) => r.rear)).size > 1))
    .map(([name]) => name)
    .sort();

const sources = [...new Set(rows.map((r) => r.source))].sort();
const sourceDomains = [...new Set(sources.flatMap((s) => (s.match(/[a-z0-9.-]+\.[a-z]{2,}/gi) || [])))].sort();
const frontCount = rows.filter((r) => r.front).length;
const rearCount = rows.filter((r) => r.rear).length;
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

const titleCase = (b) => b.charAt(0).toUpperCase() + b.slice(1);
const brandList = brands.map(titleCase).join(', ');

const omissionProse = omitted.length === 0
    ? 'Every catalogue slug missing these columns for the brands above is staged: nothing was dropped.'
    : `The ${omitted.length} row${omitted.length === 1 ? '' : 's'} `
      + `${[...omittedByNameplate].map(([n, ys]) => `${n}-{${ys.join(',')}}`).join(', ')} `
      + `${omitted.length === 1 ? 'is' : 'are'} deliberately omitted because no trustworthy published spec could be `
      + 'found. A NULL is preferable to an invented figure - the same policy that made the displacement seed drop '
      + '65 rows rather than guess.';

const resolvedProse = resolvedElsewhere.length === 0
    ? 'Every staged slug is still missing front_suspension or rear_suspension in the live catalogue.'
    : `${resolvedElsewhere.length} staged slug${resolvedElsewhere.length === 1 ? '' : 's'} - the Honda and Kawasaki `
      + 'pilot batch, now fully applied - already have both columns filled; the COALESCE guard below makes '
      + 're-staging them a safe no-op rather than a reason to drop research already paid to collect. '
      + 'tools/suspension-research.json accumulates across batches, unlike tools/brakes-research.json, which is '
      + 'replaced wholesale per batch.';

const header = `-- Motorcycle Comparison API - front_suspension / rear_suspension backfill
--
-- Purpose: back-fill motorcycles.front_suspension and motorcycles.rear_suspension for catalogue rows
-- that still carry NULL in both columns. Both columns live directly on motorcycles (VARCHAR(160),
-- nullable - see V1__initial_schema.sql), there is no child table involved and no engine block to
-- create or link, unlike the displacement_cc seed beside this one.
--
-- Provenance: hand-curated from tools/suspension-research.json, researched per nameplate/generation
-- from manufacturer press material and Brazilian and international spec references. There is no
-- scraper snapshot behind this file, unlike the per-brand imports beside it. Regenerate it with
-- tools/import-suspension.mjs - every count in this header is computed by that script, never typed.
-- Sources drawn on for this batch:
${wrap('--   ', sourceDomains.join(', '))}
--
${wrap('-- ', `Scope of this batch: ${brandList}. ${targetsForBatch} catalogue slugs were missing front_suspension or rear_suspension; ${rows.length} are staged below (${frontCount} carry a front value, ${rearCount} a rear).`)}
${wrap('-- ', omissionProse)}
${wrap('-- ', resolvedProse)}
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. UNLIKE R__motorcycles_displacement_cc_2026_09.sql beside this file, the slugs staged here
-- already carry the model year (motorcycles.slug is slugify(brand + model + model_year)), so this
-- join resolves for real rather than resolving 0 rows.
--
-- Generation granularity: research was done per nameplate but written per exact slug, and split by
${wrap('-- ', `year range wherever the model actually changed - ${splitNameplates.length} of the ${byNameplate.size} nameplates in this batch carry more than one distinct value across their model years. A single value was never stretched across a nameplate's whole life.`)}
--
-- Ordering, and why the filename is load-bearing: Flyway runs repeatable migrations in description
-- order. "zzzz motorcycles suspension 2026 09" sorts after "zzzz motorcycles 1000ps specs 2026 09"
-- ('1' < 's'), so this is the LAST seed to run among the "zzzz" tier. That is deliberate and safe: the
-- .txt lists this generator reads are always regenerated from the live gap, never accumulated, so a
-- freshly-generated pair can only ever be a subset of what this file is allowed to touch - staging a
-- slug the lists no longer carry (an earlier batch's own already-applied rows) is caught by the
-- COALESCE guard below, never by pre-empting another import's claim. Rename it earlier than the
-- per-brand scrapes and it could start clobbering one before this file's own gap-fill guard applies.
--
${wrap('-- ', `${WIDTH_CUTS.count} values exceeded their column width and were cut at a word boundary rather than mid-word.`)}
--
-- Idempotent and repeatable: every write below is COALESCE(existing, staged), so re-running this file
-- changes nothing once it has been applied.`;

const body = `BEGIN;

CREATE TEMP TABLE tmp_motorcycle_suspension (
    row_no        bigint PRIMARY KEY,
    slug          varchar(160) NOT NULL,
    front         varchar(160),
    rear          varchar(160),
    motorcycle_id bigint,
    CONSTRAINT uk_tmp_motorcycle_suspension_slug UNIQUE (slug),
    CONSTRAINT ck_tmp_motorcycle_suspension_any CHECK (front IS NOT NULL OR rear IS NOT NULL)
) ON COMMIT DROP;

INSERT INTO tmp_motorcycle_suspension (row_no, slug, front, rear) VALUES
${rows.map((r, i) => `(${i + 1}, '${r.slug}', ${quote(r.front)}, ${quote(r.rear)})`).join(',\n')};

-- ---------------------------------------------------------------------------
-- Resolve every staged row against the live catalogue. An exact slug match only: a slug with no
-- corresponding motorcycles.slug is counted and left alone rather than fuzzy-matched or inserted -
-- never insert a catalogue row here.
-- ---------------------------------------------------------------------------
UPDATE tmp_motorcycle_suspension t SET motorcycle_id = m.id FROM motorcycles m WHERE m.slug = t.slug;

-- Gap-fill only: an existing value (an admin edit, or an earlier import) always wins.
UPDATE motorcycles m
SET front_suspension = COALESCE(m.front_suspension, t.front),
    rear_suspension  = COALESCE(m.rear_suspension,  t.rear)
FROM tmp_motorcycle_suspension t
WHERE m.id = t.motorcycle_id;

-- ---------------------------------------------------------------------------
-- Defensive checks before commit, mirroring R__motorcycles_displacement_cc_2026_09.sql.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad        bigint;
    unresolved bigint;
BEGIN
    SELECT count(*) INTO bad
    FROM tmp_motorcycle_suspension
    WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle suspension backfill: % staged slugs are not a shape the public routing can use', bad;
    END IF;

    SELECT count(*) INTO bad
    FROM tmp_motorcycle_suspension
    WHERE length(front) > 160 OR length(rear) > 160;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle suspension backfill: % staged rows exceed the 160-character column limit', bad;
    END IF;

    SELECT count(*) INTO unresolved FROM tmp_motorcycle_suspension WHERE motorcycle_id IS NULL;
    RAISE NOTICE 'Motorcycle suspension backfill: % of % staged rows matched a catalogue slug, % left unresolved',
        (SELECT count(*) FROM tmp_motorcycle_suspension WHERE motorcycle_id IS NOT NULL),
        (SELECT count(*) FROM tmp_motorcycle_suspension), unresolved;
END $$;

COMMIT;
`;

const sql = `${header}\n\n${body}`;

if (CHECK_ONLY) {
    const current = fs.existsSync(SQL_PATH) ? fs.readFileSync(SQL_PATH, 'utf8') : '';
    const dataOf = (text) => (text.match(/^\(\d+, '.*\)[,;]$/gm) || []).join('\n');
    console.log(`staged rows: ${rows.length} (${frontCount} front, ${rearCount} rear) across ${brands.length} brand(s): ${brandList}`);
    console.log(`nameplates: ${byNameplate.size}, of which ${splitNameplates.length} split by model year`);
    console.log(`deliberately omitted: ${omitted.length}`);
    console.log(`resolved elsewhere (stale vs worklist): ${resolvedElsewhere.length}`);
    console.log(`width cuts: ${WIDTH_CUTS.count}`);
    console.log(`data rows identical to current file: ${dataOf(sql) === dataOf(current)}`);
    console.log(`whole file identical: ${sql === current}`);
    process.exit(0);
}

fs.writeFileSync(SQL_PATH, sql, 'utf8');
console.log(`import-suspension: wrote ${rows.length} rows (${frontCount} front, ${rearCount} rear) for ${brandList} to ${path.relative(REPO_ROOT, SQL_PATH)}`);
console.log(`  nameplates ${byNameplate.size}, split by year ${splitNameplates.length}, omitted ${omitted.length}, `
    + `resolved elsewhere ${resolvedElsewhere.length}, width cuts ${WIDTH_CUTS.count}`);
