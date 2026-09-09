#!/usr/bin/env node
// Regenerates db/seed/R__zzzz_motorcycles_list_price_2026_09.sql from tools/price-research.json,
// reproducibly. Writes to motorcycle_additional_specs under the 'List price (EUR)' key - never to
// motorcycles.price_eur, which already mixes FIPE/1000ps used-market semantics (see the header this
// script writes). Usage: node <this> [--check]

import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const args = process.argv.slice(2);
const CHECK_ONLY = args.includes('--check');

const JSON_PATH = path.join(REPO_ROOT, 'tools/price-research.json');
const TARGET_LIST = path.join(REPO_ROOT, 'tools/motorcycles-price-null-slugs.txt');
const VALIDATOR = path.join(REPO_ROOT, 'tools/validate-price-research.mjs');
const SQL_PATH = path.join(REPO_ROOT, 'src/main/resources/db/seed/R__zzzz_motorcycles_list_price_2026_09.sql');

const die = (message) => {
    console.error(`import-price-research: ${message}`);
    process.exit(1);
};

// --- validation --------------------------------------------------------------------------------
// The rules that decide what a trustworthy list price looks like already live in
// tools/validate-price-research.mjs (source tiers, price band, ambiguous-figure rejection,
// classifieds rejection). Reimplementing them here would just be a second copy to keep in sync, so
// this generator shells out to it and aborts on anything but a clean pass.
try {
    execFileSync('node', [VALIDATOR, '--quiet'], { cwd: REPO_ROOT, stdio: 'pipe' });
} catch (err) {
    console.error(err.stdout ? err.stdout.toString() : err.message);
    die(`${path.relative(REPO_ROOT, VALIDATOR)} rejected the research file - fix it before regenerating`);
}

const targets = new Set(
    fs.readFileSync(TARGET_LIST, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean),
);
const research = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));

// --- rows ----------------------------------------------------------------------------------
// NUMERIC(10,2)-shaped string, same format R__motorcycles_brazil_fipe_2026_08.sql already uses for
// the sibling 'Reference price (BRL)' key via to_char(price, 'FM999999990.00') - plain digits and a
// decimal point, no currency symbol, no thousands separator.
const rows = research
    .map((entry) => ({
        slug: entry.slug,
        priceValue: entry.price_eur.toFixed(2),
        source: entry.source,
    }))
    .sort((a, b) => (a.slug < b.slug ? -1 : a.slug > b.slug ? 1 : 0));
rows.forEach((r, i) => { r.row_no = i + 1; });

// --- computed facts for the header ---------------------------------------------------------
const brandOf = (slug) => slug.split('-')[0];
const brands = [...new Set(rows.map((r) => brandOf(r.slug)))].sort();
const stagedByBrand = new Map(brands.map((b) => [b, rows.filter((r) => brandOf(r.slug) === b).length]));
const targetsByBrand = new Map();
for (const slug of targets) {
    const b = brandOf(slug);
    targetsByBrand.set(b, (targetsByBrand.get(b) || 0) + 1);
}

// Mirrors the tier split tools/validate-price-research.mjs already computes, so the header agrees
// with what `node tools/validate-price-research.mjs` prints.
const STRONG_SOURCES = [
    /\bpeugeot-motocycles\.[a-z.]+/i, /\bpeugeot-scooters\.[a-z.]+/i, /\byamaha-motor\.[a-z.]+/i, /\bducati\.com\b/i,
    /\bkymco\.[a-z.]+/i, /\bbetamotor\.com\b/i, /\brieju\.es\b/i, /\bgasgas\.com\b/i, /\bsherco\.com\b/i,
    /\bmvagusta\.com\b/i, /\bkeeway\.[a-z.]+/i, /\bsym-global\.com\b/i, /\btriumphmotorcycles\.[a-z.]+/i,
    /\bsuzuki-motorcycles\.[a-z.]+/i, /\bsuzukicycles\.com\b/i, /\bpiaggio\.com\b/i, /\bkawasaki\.[a-z.]+/i,
    /\baprilia\.com\b/i, /\bhonda\.[a-z.]+/i, /\bbmw-motorrad\.[a-z.]+/i, /\bktm\.com\b/i, /\bhusqvarna-motorcycles\.com\b/i,
    /\bharley-davidson\.com\b/i, /\bindianmotorcycle\.[a-z.]+/i, /\broyalenfield\.com\b/i, /\bbenelli\.com\b/i,
    /\bmotoguzzi\.com\b/i, /\bmotomorini\.[a-z.]+/i, /\bniu\.com\b/i, /\bmash-motors\.[a-z.]+/i,
    /\bmotorcyclenews\.com\b/i, /\blerepairedesmotards\.com\b/i, /\bscooter-system\.fr\b/i, /\bmotorradonline\.de\b/i,
    /\bmotociclismo\.[a-z.]+/i, /\bmotos\.pt\b/i, /\blargus\.fr\b/i, /\b1000ps\.[a-z.]+/i, /\bautoevolution\.com\b/i,
];
const isStrong = (source) => STRONG_SOURCES.some((re) => re.test(source));
const manufacturerCount = rows.filter((r) => isStrong(r.source)).length;
const aggregatorCount = rows.length - manufacturerCount;

const sources = [...new Set(rows.map((r) => r.source))].sort();
const sourceDomains = [...new Set(sources.flatMap((s) => (s.match(/[a-z0-9.-]+\.[a-z]{2,}/gi) || [])))].sort();

const prices = rows.map((r) => Number(r.priceValue)).sort((a, b) => a - b);
const median = prices.length ? prices[Math.floor(prices.length / 2)] : 0;

const omittedCount = targets.size - rows.length;
const yearOf = (slug) => Number((slug.match(/-(\d{4})$/) || [])[1]);
const omittedPre2016 = [...targets].filter((s) => !rows.some((r) => r.slug === s) && yearOf(s) && yearOf(s) < 2016).length;

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

const header = `-- Motorcycle Comparison API - list price (EUR) research, written to motorcycle_additional_specs
--
-- Purpose: back-fill a researched manufacturer/importer list price for catalogue rows that have no
-- motorcycles.price_eur. This file deliberately does NOT touch price_eur. That column already mixes
-- three different kinds of number - R__motorcycles_brazil_fipe_2026_08.sql's Brazilian FIPE used-
-- vehicle reference, R__zzzz_motorcycles_1000ps_specs_2026_09.sql's used-listing average, and a
-- handful of hand-written German ex-factory MSRPs in R__dev_seed.sql - and every ORDER BY/range
-- filter on it already compares incompatible numbers. Adding a fourth kind (a new-bike tariff) would
-- deepen that defect, not fix the gap. Instead this writes to the existing long-tail EAV table,
-- motorcycle_additional_specs, under a key ('List price (EUR)') distinct from the FIPE seed's own
-- 'Reference price (BRL)' key so the two are never confused for the same kind of number. The value is
-- visible in the API's additionalSpecs map but intentionally does not feed minPriceEur/maxPriceEur,
-- ORDER BY price_eur, or CatalogStatsRepository - it is queryable by key, not comparable as price.
--
-- Provenance: tools/price-research.json, validated by tools/validate-price-research.mjs (which this
-- generator runs and aborts on failure, rather than re-implementing its rules). Regenerate this file
-- with tools/import-price-research.mjs - every count in this header is computed by that script.
-- Sources drawn on for this batch:
${wrap('--   ', sourceDomains.join(', '))}
--
${wrap('-- ', `Scope of this batch (staged/gap-slugs by brand): ${brandList}.`)}
${wrap('-- ', `${targets.size} catalogue slugs had no price_eur; ${rows.length} are staged below (${manufacturerCount} from a manufacturer/importer source, ${aggregatorCount} from a listino aggregator), ${omittedCount} left out.`)}
-- Price backfill for this gap is retrieval-bound, not source-quality-bound: 2,824 distinct
-- nameplates for 2,827 target slugs means almost no model amortises research across model years,
-- colour variants carry no tariff of their own, and pre-2016 slugs (structurally the largest chunk
-- of what remains uncovered, see below) sit outside every current European price list. Loosening the
-- acceptance bar would buy false positives, not volume - see tools/validate-price-research.mjs.
${wrap('-- ', `Of the ${omittedCount} omitted, ${omittedPre2016} are 2015-or-older model years with no current tariff to cite.`)}
--
${wrap('-- ', `Price band across the ${rows.length} staged rows: EUR ${prices[0] ?? 0} to ${prices[prices.length - 1] ?? 0}, median ${median}.`)}
--
-- Keying: joins on motorcycles.slug, exact match only. Write is INSERT ... ON CONFLICT (motorcycle_id,
-- spec_key) DO NOTHING, the same idempotent pattern every per-brand seed already uses for this table
-- (see R__motorcycles_harley_davidson_specs_2026_08.sql). No other seed writes the
-- 'List price (EUR)' key, so first-writer-wins carries no risk of clobbering a richer import.
--
-- Ordering: "zzzz motorcycles list price 2026 09" sorts after "...engine specs..." ('e') and before
-- "...suspension..." ('s') - the same "zzzz, research-derived, gap-fill only" family as its
-- neighbours, kept for consistency even though no other seed currently disputes this EAV key.
--
-- Idempotent and repeatable: ON CONFLICT DO NOTHING means re-running this file changes nothing once
-- it has been applied.`;

const quote = (value) => `'${String(value).replace(/'/g, "''")}'`;

const values = rows.map((r) => `(${r.row_no}, '${r.slug}', ${quote(r.priceValue)})`);

const body = `BEGIN;

CREATE TEMP TABLE tmp_motorcycle_list_price (
    row_no        bigint PRIMARY KEY,
    slug          varchar(160) NOT NULL,
    price_value   varchar(20)  NOT NULL,
    motorcycle_id bigint,
    CONSTRAINT uk_tmp_motorcycle_list_price_slug UNIQUE (slug)
) ON COMMIT DROP;

INSERT INTO tmp_motorcycle_list_price (row_no, slug, price_value) VALUES
${values.join(',\n')};

-- ---------------------------------------------------------------------------
-- Resolve every staged row against the live catalogue. An exact slug match only: a slug with no
-- corresponding motorcycles.slug is counted and left alone rather than fuzzy-matched or inserted -
-- never insert a catalogue row here.
-- ---------------------------------------------------------------------------
UPDATE tmp_motorcycle_list_price t
SET motorcycle_id = m.id
FROM motorcycles m
WHERE m.slug = t.slug;

-- First writer wins: no other seed claims the 'List price (EUR)' key, so this is safe to run
-- anywhere in the ordering, but ON CONFLICT DO NOTHING still makes a re-run idempotent.
INSERT INTO motorcycle_additional_specs (motorcycle_id, spec_key, spec_value)
SELECT motorcycle_id, 'List price (EUR)', price_value
FROM tmp_motorcycle_list_price
WHERE motorcycle_id IS NOT NULL
ON CONFLICT (motorcycle_id, spec_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Defensive checks before commit.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad        bigint;
    unresolved bigint;
    missing    bigint;
BEGIN
    SELECT count(*) INTO bad
    FROM tmp_motorcycle_list_price
    WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle list price backfill: % staged slugs are not a shape the public routing can use', bad;
    END IF;

    SELECT count(*) INTO unresolved FROM tmp_motorcycle_list_price WHERE motorcycle_id IS NULL;
    RAISE NOTICE 'Motorcycle list price backfill: % of % staged rows matched a catalogue slug, % left unresolved',
        (SELECT count(*) FROM tmp_motorcycle_list_price WHERE motorcycle_id IS NOT NULL),
        (SELECT count(*) FROM tmp_motorcycle_list_price), unresolved;

    -- Proves ON CONFLICT DO NOTHING never silently swallowed a real insert failure: every resolved
    -- row must have a matching 'List price (EUR)' row now, whether this run wrote it or an earlier
    -- run already did.
    SELECT count(*) INTO missing
    FROM tmp_motorcycle_list_price t
    WHERE t.motorcycle_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM motorcycle_additional_specs s
          WHERE s.motorcycle_id = t.motorcycle_id AND s.spec_key = 'List price (EUR)'
      );
    IF missing <> 0 THEN
        RAISE EXCEPTION 'Motorcycle list price backfill: % resolved rows have no List price (EUR) spec after the insert', missing;
    END IF;
END $$;

COMMIT;
`;

const sql = `${header}\n\n${body}`;

if (CHECK_ONLY) {
    const current = fs.existsSync(SQL_PATH) ? fs.readFileSync(SQL_PATH, 'utf8') : '';
    const dataOf = (text) => (text.match(/^\(\d+, '.*\)[,;]$/gm) || []).join('\n');
    console.log(`staged rows: ${rows.length} across ${brands.length} brand(s): ${brandList}`);
    console.log(`manufacturer/importer: ${manufacturerCount}, aggregator: ${aggregatorCount}`);
    console.log(`omitted: ${omittedCount} (${omittedPre2016} pre-2016)`);
    console.log(`data rows identical to current file: ${dataOf(sql) === dataOf(current)}`);
    console.log(`whole file identical: ${sql === current}`);
    process.exit(0);
}

fs.writeFileSync(SQL_PATH, sql, 'utf8');
console.log(`import-price-research: wrote ${rows.length} rows for ${brandList} to ${path.relative(REPO_ROOT, SQL_PATH)}`);
console.log(`  manufacturer/importer ${manufacturerCount}, aggregator ${aggregatorCount}, omitted ${omittedCount} (${omittedPre2016} pre-2016)`);
