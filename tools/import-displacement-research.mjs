// Builds R__motorcycles_displacement_cc_2026_09.sql by resolving every slug in cilindradas.txt (each
// ending in its model year) against tools/motorcycle-displacement-research.json (keyed by nameplate,
// i.e. the same slug with the trailing year stripped). Guards derive from the data: a slug that
// cannot be resolved safely fails the generator, not Flyway hours later.
// Usage: node tools/import-displacement-research.mjs [research.json] [--out <path>]

import { readFileSync, writeFileSync } from 'node:fs';
import { argv } from 'node:process';

const SLUGS_PATH = 'cilindradas.txt';
const SRC = argv[2] && !argv[2].startsWith('--') ? argv[2] : 'tools/motorcycle-displacement-research.json';
const outFlag = argv.indexOf('--out');
const OUT = outFlag !== -1
    ? argv[outFlag + 1]
    : 'src/main/resources/db/seed/R__motorcycles_displacement_cc_2026_09.sql';

// Trailing model year, with an optional "-2" (etc.) slug disambiguator immediately before it - that
// suffix distinguishes two catalogue entries that would otherwise collide, it is not a second year.
const NAMEPLATE_RE = /-((?:19|20)\d{2})(-\d+)?$/;
const SLUG_SHAPE_RE = /^[a-z0-9]+(-[a-z0-9]+)*$/;

// The four provenance buckets this import can prove from the research JSON's own "source" field.
// Anything else staged is web research done specifically for this import - there is no fifth bucket
// to fall into, so it is the default rather than another named case.
const SOURCE_BUCKET = {
    'db/seed R__motorcycles_displacement_cc_2026_09 curated set, keyed by nameplate': 'prevCurated',
    'FIPE model descriptor, which states the displacement itself': 'fipeDescriptor',
    'db/seed per-brand manufacturer scrape, another model year of this nameplate': 'brandScrape',
    'db/seed catalogue scrape (1000PS / cross-brand gap-fill), another model year': 'catalogueScrape',
};

const research = JSON.parse(readFileSync(SRC, 'utf8'));
const slugs = readFileSync(SLUGS_PATH, 'utf8')
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);

const nameplateOf = (slug) => {
    const m = slug.match(NAMEPLATE_RE);
    if (!m) throw new Error(`slug "${slug}" has no trailing model year (expected .../-NNNN[-N])`);
    return { nameplate: slug.slice(0, m.index), year: Number(m[1]) };
};

// --- resolve every slug -----------------------------------------------------------------------
const seenSlugs = new Set();
const staged = [];
const skippedByReason = new Map();
const skippedNameplates = new Set();
const nameplatesStaged = new Set();
const allNameplates = new Set();

for (const slug of slugs) {
    if (!SLUG_SHAPE_RE.test(slug)) {
        throw new Error(`slug "${slug}" is not shaped like slugify() output (^[a-z0-9]+(-[a-z0-9]+)*$)`);
    }
    if (seenSlugs.has(slug)) {
        throw new Error(`duplicate slug in ${SLUGS_PATH}: ${slug}`);
    }
    seenSlugs.add(slug);

    const { nameplate, year } = nameplateOf(slug);
    allNameplates.add(nameplate);

    const rec = research[nameplate];
    if (!rec) {
        throw new Error(`nameplate "${nameplate}" (from slug "${slug}") is missing from ${SRC}`);
    }

    let value = rec.displacement_cc;
    if (rec.by_year && Object.prototype.hasOwnProperty.call(rec.by_year, String(year))) {
        value = rec.by_year[String(year)];
    }

    if (value === null || value === undefined) {
        const reason = rec.reason || 'unspecified';
        skippedByReason.set(reason, (skippedByReason.get(reason) || 0) + 1);
        skippedNameplates.add(nameplate);
        continue;
    }

    if (typeof value !== 'number' || !Number.isFinite(value)) {
        throw new Error(`slug "${slug}" (nameplate "${nameplate}") resolves to a non-numeric displacement: ${value}`);
    }
    // Round half-up; every value in the research JSON is an integer today, but a future entry
    // (e.g. a by_year override) is not guaranteed to be, and the column is INTEGER.
    value = Math.round(value);
    if (!Number.isInteger(value) || value <= 0) {
        throw new Error(`slug "${slug}" (nameplate "${nameplate}") resolves to a non-positive displacement: ${value}`);
    }

    nameplatesStaged.add(nameplate);
    staged.push({
        slug,
        displacement_cc: value,
        confidence: rec.confidence || 'unspecified',
        source: rec.source || '',
    });
}

staged.sort((a, b) => (a.slug < b.slug ? -1 : a.slug > b.slug ? 1 : 0));

// --- stats for the header and stdout ----------------------------------------------------------
const confidenceCounts = {};
for (const r of staged) confidenceCounts[r.confidence] = (confidenceCounts[r.confidence] || 0) + 1;

const sourceCounts = { prevCurated: 0, fipeDescriptor: 0, brandScrape: 0, catalogueScrape: 0, webResearch: 0 };
for (const r of staged) sourceCounts[SOURCE_BUCKET[r.source] || 'webResearch']++;

let skippedTotal = 0;
for (const n of skippedByReason.values()) skippedTotal += n;

// --- SQL ---------------------------------------------------------------------------------------
const values = staged.map((r, i) => `(${i + 1}, '${r.slug.replace(/'/g, "''")}', ${r.displacement_cc})`);

const confidenceLine = Object.entries(confidenceCounts)
    .sort((a, b) => b[1] - a[1])
    .map(([k, v]) => `${v} ${k}`)
    .join(', ');

const skippedReasonLine = [...skippedByReason.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([k, v]) => `${v} ${k}`)
    .join(', ') || '(none)';

const sql = `-- Motorcycle Comparison API - displacement_cc gap-fill, researched by model year
-- Generated by tools/import-displacement-research.mjs. Do not edit by hand; edit the generator or
-- tools/motorcycle-displacement-research.json (the versioned research artefact) and re-run it, or
-- the next run will silently revert whatever was changed here.
--
-- WHAT CHANGED SINCE THE PREVIOUS REVISION
-- The previous revision of this file was keyed by bare nameplate ("honda-cbx-150-aero") and resolved
-- 0 of its 651 staged rows, because motorcycles.slug always carries a trailing model year and a bare
-- nameplate cannot join against it. That premise does not survive into this revision. cilindradas.txt
-- lists ${slugs.length} slugs, one per line, and every one already ends in a model year - the exact
-- shape slugify(brand + model + model_year) produces and the same shape
-- R__motorcycles_brazil_fipe_2026_08.sql derives for every catalogue row - so the join below
-- (WHERE m.slug = t.slug) resolves for real instead of reporting a permanent RAISE NOTICE.
--
-- SOURCE AND METHOD
-- The ${slugs.length} slugs collapse to ${allNameplates.size} distinct nameplates once the trailing
-- model year (and, where present, the "-N" slug disambiguator immediately before it - a catalogue
-- collision, not a second year) is stripped. Each nameplate was researched once in
-- tools/motorcycle-displacement-research.json, the versioned artefact this generator reads, and the
-- resulting figure is applied to every model year that nameplate covers in cilindradas.txt: an
-- engine can change across a nameplate's production run, but a figure sourced against the nameplate
-- is exactly as trustworthy as the FIPE-derived slug it is being applied to, model year for model
-- year, and no per-year guessing happens here beyond an explicit by_year override in the research
-- record, honoured where present.
--
-- ${staged.length} of the ${slugs.length} slugs are staged below. The other ${skippedTotal} slugs
-- (${skippedNameplates.size} nameplates) are excluded, each for the same recorded reason:
-- ${skippedReasonLine}. An electric motor has no swept volume, so there is no displacement_cc to
-- publish, and ck_engine_specifications_displacement_cc (CHECK > 0) would reject the 0 an electric
-- row would otherwise carry.
--
-- EVIDENCE GRADING
-- Of the ${staged.length} staged rows, ${sourceCounts.brandScrape} come from this repository's own
-- per-brand manufacturer scrapes (another model year of the same nameplate already carried a figure)
-- and ${sourceCounts.catalogueScrape} from its catalogue scrapes (1000PS / cross-brand gap-fill,
-- likewise another model year of the same nameplate); ${sourceCounts.fipeDescriptor} come from a FIPE
-- model descriptor that states the displacement itself; ${sourceCounts.prevCurated} are carried over
-- from the previous revision's curated set; the remaining ${sourceCounts.webResearch} come from web
-- research done specifically for this import. Confidence, as recorded per nameplate in the research
-- JSON: ${confidenceLine} staged rows. Neither confidence nor source nor notes is written to the
-- catalogue below - this table has no provenance column, so they stay in the JSON artefact.
--
-- A MODEL-NAME BADGE IS NOT A DISPLACEMENT
-- This import corrects a number of cases where an earlier one took a badge number for a swept
-- volume. The Vespa 946 Emporio Armani is staged at 155 - 946 is the model name, not a displacement,
-- and the machine is a 155 cc scooter. Can-Am "1000R" machines are a 976 cc Rotax. The Kymco Agility
-- 16+ 200i is 163 cc. The Honda XR 300L Tornado is 293.5 cc. byCristo Star trikes run a VW 1.6
-- (1600 cc), not the 200 cc an earlier import staged. Harley-Davidson "115th"/"120th" anniversary
-- numbers are anniversaries, not displacements: the 2025 Fat Boy Icon is a Milwaukee-Eight 117
-- (1923 cc), not the 1449 cc Twin Cam 88B the 2001-2006 FLSTFI actually carried and an earlier import
-- staged for it. The Triumph Rocket III is 2294 cc and the Rocket 3 R/GT 2458 cc, both of which FIPE's
-- own model descriptor rounds to the nearest hundred in its slug ("...-2300cc-...", "...-2500cc-...").
--
-- ONE KNOWN DISAGREEMENT, RECORDED HONESTLY
-- R__motorcycles_honda_specs_2026_08.sql carries 186 cc for the XRE 190 model-years, while
-- honda.com.br itself publishes 184.4 cc for that engine. This file stages 184 for both
-- honda-xre-190-flex and honda-xre-190-adventure-flex. It does not retroactively correct the Honda
-- seed's already-migrated rows: every write below is COALESCE(existing, imported), which only ever
-- fills a NULL. In a database where the Honda seed has already run, displacement_cc for those engine
-- rows is already 186, not NULL, so this file's COALESCE has nothing to fill there. The 184 staged
-- here only reaches the catalogue where displacement_cc is still unset when this file runs - a fresh
-- database applying every repeatable migration in filename order for the first time, where this
-- file's "d" sorts ahead of the Honda seed's "h" and claims the column first (see ORDERING below).
--
-- ORDERING
-- Flyway runs repeatable migrations in description order, and this import only gap-fills, so it must
-- run after the FIPE seed that creates the rows it fills and before every seed whose COALESCE could
-- otherwise claim displacement_cc first. "motorcycles displacement cc" sorts after
-- "motorcycles brazil fipe" ('d' > 'b') and before every per-brand spec seed (BMW, Harley-Davidson,
-- Honda, Kawasaki, Royal Enfield, Triumph, Yamaha) and both "zz" gap-fill files, so this file claims
-- the column first under COALESCE. That ordering is load-bearing, not incidental: it is what lets
-- this file's corrected figures (see above) reach the catalogue ahead of the defects they correct.
--
-- GAP-FILL ONLY
-- This file only ever touches engine_specifications.displacement_cc. It never creates a catalogue
-- row and never sets any other engine, dimension or motorcycle column. The join is on
-- motorcycles.slug, exact match only (WHERE m.slug = t.slug), nothing fuzzy and no prefix matching.
-- Every write is COALESCE(existing, imported), so an admin edit or a richer import always wins, and a
-- slug that fails to resolve against the live catalogue is counted and left alone rather than
-- fuzzy-matched or inserted. Repeatable and idempotent: re-running this file changes nothing once it
-- has been applied.

BEGIN;

CREATE TEMP TABLE tmp_motorcycle_displacement (
    row_no          bigint PRIMARY KEY,
    slug            varchar(160) NOT NULL,
    displacement_cc integer NOT NULL,
    motorcycle_id   bigint,
    engine_id       bigint,
    CONSTRAINT uk_tmp_motorcycle_displacement_slug UNIQUE (slug),
    CONSTRAINT ck_tmp_motorcycle_displacement_cc CHECK (displacement_cc > 0)
) ON COMMIT DROP;

INSERT INTO tmp_motorcycle_displacement (row_no, slug, displacement_cc) VALUES
${values.join(',\n')}
;

-- ---------------------------------------------------------------------------
-- Resolve every staged row against the live catalogue. An exact match only: a slug with no
-- corresponding motorcycles.slug is counted and left alone rather than fuzzy-matched or inserted -
-- see the header for why prefix/year-stripped matching was rejected.
-- ---------------------------------------------------------------------------
UPDATE tmp_motorcycle_displacement t
SET motorcycle_id = m.id,
    engine_id     = m.engine_specification_id
FROM motorcycles m
WHERE m.slug = t.slug;

-- A resolved motorcycle may not have an engine block yet (e.g. a FIPE-only row with no spec import
-- applied). Give it one before the merge below, so a real match is never silently dropped.
UPDATE tmp_motorcycle_displacement
SET engine_id = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)
WHERE motorcycle_id IS NOT NULL
  AND engine_id IS NULL;

INSERT INTO engine_specifications (id)
SELECT t.engine_id
FROM tmp_motorcycle_displacement t
WHERE t.engine_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM engine_specifications e WHERE e.id = t.engine_id)
ORDER BY t.row_no;

UPDATE motorcycles m
SET engine_specification_id = t.engine_id
FROM tmp_motorcycle_displacement t
WHERE m.id = t.motorcycle_id AND m.engine_specification_id IS NULL AND t.engine_id IS NOT NULL;

-- Gap-fill only: an existing displacement_cc (an admin edit, or a richer earlier import) always wins.
UPDATE engine_specifications e
SET displacement_cc = COALESCE(e.displacement_cc, t.displacement_cc)
FROM tmp_motorcycle_displacement t
WHERE e.id = t.engine_id;

-- ---------------------------------------------------------------------------
-- Defensive checks before commit, mirroring R__zz_motorcycles_specs_gapfill.sql: an unresolved slug is
-- reported, never an exception, because this file is expected to resolve 0 rows until motorcycles.slug
-- is normalised to carry a model year (see header).
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad        bigint;
    unresolved bigint;
BEGIN
    SELECT count(*) INTO bad
    FROM tmp_motorcycle_displacement
    WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle displacement backfill: % staged slugs are not a shape the public routing can use', bad;
    END IF;

    SELECT count(*) INTO unresolved FROM tmp_motorcycle_displacement WHERE motorcycle_id IS NULL;
    RAISE NOTICE 'Motorcycle displacement backfill: % of % staged rows matched a catalogue slug, % left unresolved',
        (SELECT count(*) FROM tmp_motorcycle_displacement WHERE motorcycle_id IS NOT NULL),
        (SELECT count(*) FROM tmp_motorcycle_displacement), unresolved;

    SELECT count(*) INTO bad
    FROM tmp_motorcycle_displacement t
    JOIN motorcycles m ON m.id = t.motorcycle_id
    WHERE t.engine_id IS NOT NULL AND m.engine_specification_id IS DISTINCT FROM t.engine_id;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle displacement backfill: % rows have a mismatched engine block', bad;
    END IF;

    SELECT count(*) INTO bad
    FROM engine_specifications e
    JOIN tmp_motorcycle_displacement t ON t.engine_id = e.id
    WHERE e.displacement_cc IS NULL OR e.displacement_cc <= 0;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle displacement backfill: % resolved engine blocks ended up with no positive displacement_cc', bad;
    END IF;
END $$;

COMMIT;
`;

writeFileSync(OUT, sql);

console.log(`slugs read:        ${slugs.length}`);
console.log(`nameplates:        ${allNameplates.size}`);
console.log(`slugs staged:      ${staged.length}`);
console.log(`slugs skipped:     ${skippedTotal} (${skippedReasonLine})`);
console.log(`confidence:        ${confidenceLine}`);
console.log(`source breakdown:  prevCurated=${sourceCounts.prevCurated} fipeDescriptor=${sourceCounts.fipeDescriptor} `
    + `brandScrape=${sourceCounts.brandScrape} catalogueScrape=${sourceCounts.catalogueScrape} webResearch=${sourceCounts.webResearch}`);
console.log(`written:           ${OUT}`);
