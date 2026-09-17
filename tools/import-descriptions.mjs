#!/usr/bin/env node
// Regenerates db/seed/R__zzzzzzz_motorcycles_description_2026_09.sql from
// tools/description-research.json and tools/description-derived.json, reproducibly - one shared
// file, not one per brand: this is a single whole-catalogue back-fill, not a batch that accumulates
// brand by brand like tools/import-brakes.mjs.
// Gap-fills motorcycles.description on existing catalogue rows only. Usage: node <this> [--check]

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const args = process.argv.slice(2);
const CHECK_ONLY = args.includes('--check');

const RESEARCH_PATH = path.join(REPO_ROOT, 'tools/description-research.json');
const DERIVED_PATH = path.join(REPO_ROOT, 'tools/description-derived.json');
const SLUG_LIST = path.join(REPO_ROOT, 'tools/slugs_missing_description.txt');
const SQL_PATH = path.join(REPO_ROOT, 'src/main/resources/db/seed/R__zzzzzzz_motorcycles_description_2026_09.sql');

const COLUMN_LIMIT = 2000;
const SLUG_SHAPE = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const die = (message) => {
    console.error(`import-descriptions: ${message}`);
    process.exit(1);
};

// --- inputs --------------------------------------------------------------------------------
// Every slug this migration must reach - the full set the pipeline's first step measured missing.
const targets = new Set(
    fs.readFileSync(SLUG_LIST, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean),
);

const research = JSON.parse(fs.readFileSync(RESEARCH_PATH, 'utf8'));
const derived = JSON.parse(fs.readFileSync(DERIVED_PATH, 'utf8'));
if (!Array.isArray(research)) die('description-research.json must be a flat array of {slug, description, source}');
if (!Array.isArray(derived)) die('description-derived.json must be a flat array of {slug, description, source}');

// --- validation of each input file on its own -------------------------------------------------
function validateEntries(entries, label) {
    const seen = new Set();
    for (const entry of entries) {
        const { slug } = entry;
        if (!slug) die(`${label}: entry ${JSON.stringify(entry).slice(0, 80)} has no slug`);
        if (!SLUG_SHAPE.test(slug)) die(`${label}: slug "${slug}" is not a shape the public routing can use`);
        if (seen.has(slug)) die(`${label}: slug "${slug}" appears more than once`);
        seen.add(slug);
        if (!entry.description || !entry.description.trim()) die(`${label}: slug "${slug}" has no description`);
        if (!entry.source) die(`${label}: slug "${slug}" has no source`);
    }
    return seen;
}

const researchSlugs = validateEntries(research, 'description-research.json');
const derivedSlugs = validateEntries(derived, 'description-derived.json');

// --- merge: research wins over derived, since it is real investigation rather than a spec-sheet
// paraphrase; every slug not covered by research falls back to its derived entry ------------------
const researchBySlug = new Map(research.map((e) => [e.slug, e]));
const derivedBySlug = new Map(derived.map((e) => [e.slug, e]));

const merged = [];
for (const slug of targets) {
    const entry = researchBySlug.get(slug) ?? derivedBySlug.get(slug);
    if (!entry) die(`slug "${slug}" from slugs_missing_description.txt has no entry in either input file`);
    merged.push({ slug, description: entry.description.trim(), source: entry.source });
}

// The merge must reproduce the target list exactly: same slugs, no duplicate, no leftover from
// either input file that the target list does not call for.
if (merged.length !== targets.size) die(`merged ${merged.length} rows but the target list has ${targets.size} slugs`);
const researchNotTargeted = [...researchSlugs].filter((s) => !targets.has(s));
if (researchNotTargeted.length > 0) {
    die(`${researchNotTargeted.length} description-research.json slugs are not in slugs_missing_description.txt: `
        + researchNotTargeted.slice(0, 5).join(', '));
}
const derivedNotTargeted = [...derivedSlugs].filter((s) => !targets.has(s));
if (derivedNotTargeted.length > 0) {
    die(`${derivedNotTargeted.length} description-derived.json slugs are not in slugs_missing_description.txt: `
        + derivedNotTargeted.slice(0, 5).join(', '));
}
const targetsNotDerived = [...targets].filter((s) => !derivedSlugs.has(s));
if (targetsNotDerived.length > 0) {
    die(`${targetsNotDerived.length} target slugs have no description-derived.json fallback: `
        + targetsNotDerived.slice(0, 5).join(', '));
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

const rows = merged
    .map((entry) => ({
        slug: entry.slug,
        description: fitColumn(entry.description, COLUMN_LIMIT),
        source: entry.source,
        fromResearch: researchBySlug.has(entry.slug),
    }))
    .sort((a, b) => (a.slug < b.slug ? -1 : a.slug > b.slug ? 1 : 0));

// --- computed facts for the header -------------------------------------------------------------
const researchCount = rows.filter((r) => r.fromResearch).length;
const derivedCount = rows.length - researchCount;
const researchSources = [...new Set(rows.filter((r) => r.fromResearch).map((r) => r.source))].sort();
const researchSourceDomains = [...new Set(researchSources.flatMap((s) => (s.match(/[a-z0-9.-]+\.[a-z]{2,}/gi) || [])))].sort();

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

const header = `-- Motorcycle Comparison API - description backfill
--
-- Purpose: back-fill motorcycles.description (VARCHAR(2000), see V1__initial_schema.sql) for the
-- ${targets.size} catalogue rows tools/slugs_missing_description.txt found with none, out of the
-- 13029 rows the catalogue carries at this point in the migration history.
--
-- Provenance: two-tier merge computed by tools/import-descriptions.mjs, never hand-typed here.
${wrap('-- ', `Tier 1, ${researchCount} rows: tools/description-research.json, real web investigation across seven brands (Vespa/Piaggio, Livewire, Energica, Zero and others) - wins whenever a slug is covered by it.`)}
${wrap('-- ', `Tier 2, ${derivedCount} rows: tools/description-derived.json, a weaker prose paraphrase of each row's own stored specifications (tools/generate-description-from-specs.mjs), used only for the slugs tier 1 does not cover.`)}
-- Every slug in tools/slugs_missing_description.txt is covered by exactly one of the two tiers;
-- description-derived.json alone already carries one entry per target slug, tier 1 included, so the
-- merge is a straight override rather than a coverage gap-fill between the two files.
-- Sources drawn on for the tier-1 rows:
${wrap('--   ', researchSourceDomains.join(', '))}
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. A slug with no corresponding motorcycles.slug is counted and left alone rather than
-- fuzzy-matched or inserted - never insert a catalogue row here.
--
-- Ordering, and why the filename is load-bearing: Flyway runs repeatable migrations in description
-- order. "zzzzzzz motorcycles description 2026 09" sorts after every other seed in this directory,
-- including "zzzzzz motorcycles tyres suzuki 2026 09" (the previous last-to-run file: 'zzzzzzz' >
-- 'zzzzzz ' because a seventh 'z' beats a space). No other current seed writes the description
-- column, so this ordering carries no correctness weight of its own - it is here purely for
-- consistency with "every dedicated backfill runs after every other seed".
--
${wrap('-- ', `${WIDTH_CUTS.count} values exceeded the 2000-character column width and were cut at a word boundary rather than mid-word.`)}
--
-- Idempotent and repeatable: the UPDATE below only fires WHERE description IS NULL OR
-- btrim(description) = '', so re-running this file changes nothing once it has been applied, and an
-- admin edit or a description written by any future import always wins over this one.`;

const body = `BEGIN;

CREATE TEMP TABLE tmp_motorcycle_descriptions (
    row_no        bigint PRIMARY KEY,
    slug          varchar(160) NOT NULL,
    description   varchar(2000) NOT NULL,
    motorcycle_id bigint,
    CONSTRAINT uk_tmp_motorcycle_descriptions_slug UNIQUE (slug)
) ON COMMIT DROP;

INSERT INTO tmp_motorcycle_descriptions (row_no, slug, description) VALUES
${rows.map((r, i) => `(${i + 1}, '${r.slug}', ${quote(r.description)})`).join(',\n')};

-- ---------------------------------------------------------------------------
-- Resolve every staged row against the live catalogue. An exact slug match only.
-- ---------------------------------------------------------------------------
UPDATE tmp_motorcycle_descriptions t SET motorcycle_id = m.id FROM motorcycles m WHERE m.slug = t.slug;

-- Gap-fill only: only write where there is nothing there yet, so an existing value (an admin edit,
-- or a future import) always wins.
UPDATE motorcycles m
SET description = t.description
FROM tmp_motorcycle_descriptions t
WHERE m.id = t.motorcycle_id
  AND (m.description IS NULL OR btrim(m.description) = '');

-- ---------------------------------------------------------------------------
-- Defensive checks before commit, mirroring R__zzzzz_motorcycles_brakes_honda_2026_09.sql.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad        bigint;
    unresolved bigint;
BEGIN
    SELECT count(*) INTO bad
    FROM tmp_motorcycle_descriptions
    WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle description backfill: % staged slugs are not a shape the public routing can use', bad;
    END IF;

    SELECT count(*) INTO bad
    FROM tmp_motorcycle_descriptions
    WHERE length(description) > 2000;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle description backfill: % staged rows exceed the column limit', bad;
    END IF;

    SELECT count(*) INTO unresolved FROM tmp_motorcycle_descriptions WHERE motorcycle_id IS NULL;
    RAISE NOTICE 'Motorcycle description backfill: % of % staged rows matched a catalogue slug, % left unresolved',
        (SELECT count(*) FROM tmp_motorcycle_descriptions WHERE motorcycle_id IS NOT NULL),
        (SELECT count(*) FROM tmp_motorcycle_descriptions), unresolved;
END $$;

COMMIT;
`;

const sql = `${header}\n\n${body}`;

if (CHECK_ONLY) {
    console.log(`staged rows: ${rows.length} (${researchCount} from research, ${derivedCount} derived-from-specs)`);
    console.log(`width cuts: ${WIDTH_CUTS.count}`);
    process.exit(0);
}

fs.writeFileSync(SQL_PATH, sql, 'utf8');
console.log(`import-descriptions: wrote ${rows.length} rows (${researchCount} from research, ${derivedCount} derived-from-specs) to ${path.relative(REPO_ROOT, SQL_PATH)}`);
console.log(`  width cuts ${WIDTH_CUTS.count}`);
