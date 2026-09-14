// Black-box tests for tools/import-brakes.mjs. Run: node --test "tools/tests/**/*.test.mjs"
// The script writes into src/main/resources/db/seed relative to its own location, so every case runs a
// copy of it inside a throwaway tree - no test ever writes a seed file into the real repo.

import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';

import { sandboxFor, STRONG_SOURCE, insertRows, stagedRows, headerProse } from './_harness.mjs';

const SCRIPT = 'import-brakes.mjs';
const SEED_DIR = 'src/main/resources/db/seed';
const COLUMN_LIMIT = 160;

const seedPath = (token) => `${SEED_DIR}/R__zzzzz_motorcycles_brakes_${token}_2026_09.sql`;

/** A sandbox holding the importer, a worklist and a research batch, ready to run. */
function setUp(t, research, targets) {
    const box = sandboxFor(t, [SCRIPT]);
    box.writeSlugList('tools/slugs_missing_brakes.txt', targets ?? research.map((e) => e.slug).filter(Boolean));
    box.writeJson('tools/brakes-research.json', research);
    return box;
}

/** N distinct words of exactly five characters, so the column-fitting maths stays readable. */
const fiveCharWords = (count) => Array.from({ length: count }, (_, i) => `w${String(i).padStart(4, '0')}`);

const HONDA_BATCH = [
    { slug: 'honda-xre-300-2022', front_brake: "Single 256 mm disc, Nissin's own caliper", rear_brake: 'Single 220 mm disc', abs_type: 'Dual-channel ABS', source: STRONG_SOURCE },
    { slug: 'honda-cg-160-2020', front_brake: 'Single 240 mm disc', rear_brake: null, abs_type: null, source: STRONG_SOURCE },
    { slug: 'honda-cg-160-2021', front_brake: 'Single 240 mm disc', rear_brake: 'Drum', abs_type: 'CBS', source: STRONG_SOURCE },
];

test('--check prints the staged-row summary, writes no file and exits 0', (t) => {
    const box = setUp(t, HONDA_BATCH);

    const result = box.run(SCRIPT, ['--check']);

    assert.equal(result.status, 0);
    assert.deepEqual(box.seedFiles(), []);
    assert.match(result.stdout, /staged rows: 3 \(3 front, 2 rear, 2 abs_type\) across 1 brand\(s\): Honda/);
    assert.match(result.stdout, /nameplates: 2, of which 1 split by model year/);
    assert.match(result.stdout, /resolved elsewhere \(stale vs worklist\): 0/);
    assert.match(result.stdout, /width cuts: 0/);
});

test('writes the seed at the brand-derived path and reports what it wrote', (t) => {
    const box = setUp(t, HONDA_BATCH);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 0);
    assert.deepEqual(box.seedFiles(), ['R__zzzzz_motorcycles_brakes_honda_2026_09.sql']);
    assert.match(result.stdout, /wrote 3 rows \(3 front, 2 rear, 2 abs_type\) for Honda to/);
    assert.match(result.stdout, /R__zzzzz_motorcycles_brakes_honda_2026_09\.sql/);
});

test('the generated seed carries the header prose and the BEGIN/COMMIT wrapper', (t) => {
    const box = setUp(t, HONDA_BATCH);
    box.run(SCRIPT);

    const sql = box.read(seedPath('honda'));

    assert.match(sql, /^-- Motorcycle Comparison API - front_brake \/ rear_brake \/ abs_type backfill\n/);
    assert.match(sql, /Provenance: hand-curated from tools\/brakes-research\.json/);
    assert.match(sql, /Idempotent and repeatable: every write below is COALESCE\(existing, staged\)/);
    assert.match(sql, /why the filename is load-bearing/);
    assert.match(sql, /\nBEGIN;\n/);
    assert.ok(sql.endsWith('COMMIT;\n'));
});

test('stages exactly one INSERT row per research entry, sorted by slug ascending', (t) => {
    const box = setUp(t, HONDA_BATCH);
    box.run(SCRIPT);

    const rows = stagedRows(box.read(seedPath('honda')));

    assert.equal(rows.length, HONDA_BATCH.length);
    assert.deepEqual(rows.map((r) => r[1]), ['honda-cg-160-2020', 'honda-cg-160-2021', 'honda-xre-300-2022']);
    assert.deepEqual(rows.map((r) => r[0]), ['1', '2', '3']);
});

test('doubles a single quote inside a value and passes an absent value through as NULL', (t) => {
    const box = setUp(t, HONDA_BATCH);
    box.run(SCRIPT);
    const sql = box.read(seedPath('honda'));

    const raw = insertRows(sql);
    const rows = stagedRows(sql);

    assert.match(raw[2], /'Single 256 mm disc, Nissin''s own caliper'/);
    assert.equal(rows[2][2], "Single 256 mm disc, Nissin's own caliper");
    // honda-cg-160-2020 has no rear brake and no abs_type: unquoted NULL, not the string "NULL".
    assert.match(raw[0], /^\(1, 'honda-cg-160-2020', 'Single 240 mm disc', NULL, NULL\)$/);
    assert.equal(rows[0][3], null);
    assert.equal(rows[0][4], null);
});

test('guards every backfilled column with its own COALESCE gap-fill', (t) => {
    const box = setUp(t, HONDA_BATCH);
    box.run(SCRIPT);

    const sql = box.read(seedPath('honda'));

    assert.match(sql, /SET front_brake = COALESCE\(m\.front_brake, t\.front\),/);
    assert.match(sql, /rear_brake\s+= COALESCE\(m\.rear_brake,\s+t\.rear\),/);
    assert.match(sql, /abs_type\s+= COALESCE\(m\.abs_type,\s+t\.abs_type\)/);
});

test('a batch spanning two brands writes one file whose token joins both brand names', (t) => {
    const box = setUp(t, [
        { slug: 'kawasaki-z400-2023', front_brake: 'Single disc', rear_brake: 'Single disc', source: STRONG_SOURCE },
        { slug: 'honda-cg-160-2020', front_brake: 'Drum', rear_brake: null, source: STRONG_SOURCE },
    ]);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 0);
    assert.deepEqual(box.seedFiles(), ['R__zzzzz_motorcycles_brakes_honda_kawasaki_2026_09.sql']);
    assert.match(result.stdout, /for Honda, Kawasaki to/);
});

// Regression for the shared-output-path defect: a per-brand batch must never overwrite another brand's
// already-applied seed, so two single-brand runs have to leave two distinct files behind.
test('two single-brand runs never collide on the same output path', (t) => {
    const box = setUp(t, HONDA_BATCH, ['honda-xre-300-2022', 'honda-cg-160-2020', 'honda-cg-160-2021', 'kawasaki-z400-2023']);
    box.run(SCRIPT);
    const hondaSql = box.read(seedPath('honda'));

    box.writeJson('tools/brakes-research.json', [
        { slug: 'kawasaki-z400-2023', front_brake: 'Dual 310 mm discs', rear_brake: 'Single 220 mm disc', source: STRONG_SOURCE },
    ]);
    const second = box.run(SCRIPT);

    assert.equal(second.status, 0);
    assert.deepEqual(box.seedFiles(), [
        'R__zzzzz_motorcycles_brakes_honda_2026_09.sql',
        'R__zzzzz_motorcycles_brakes_kawasaki_2026_09.sql',
    ]);
    assert.equal(box.read(seedPath('honda')), hondaSql);
    assert.match(box.read(seedPath('kawasaki')), /'kawasaki-z400-2023'/);
});

// Second research pass on a brand that already has a seed: --suffix keeps the file distinct.
test('--suffix writes a differently named file for the same brand instead of overwriting', (t) => {
    const box = setUp(t, HONDA_BATCH);
    box.run(SCRIPT);
    const firstSql = box.read(seedPath('honda'));

    box.writeJson('tools/brakes-research.json', [
        { slug: 'honda-cg-160-2020', front_brake: 'Revised single 240 mm disc', rear_brake: 'Drum', source: STRONG_SOURCE },
    ]);
    const second = box.run(SCRIPT, ['--suffix', 'residual']);

    assert.equal(second.status, 0);
    assert.deepEqual(box.seedFiles(), [
        'R__zzzzz_motorcycles_brakes_honda_2026_09.sql',
        'R__zzzzz_motorcycles_brakes_honda_residual_2026_09.sql',
    ]);
    assert.equal(box.read(seedPath('honda')), firstSql, 'the first batch must survive the second run untouched');
    assert.match(box.read(seedPath('honda_residual')), /'Revised single 240 mm disc'/);
});

test('a value under the column limit is passed through unchanged', (t) => {
    const value = `Single 240 mm disc ${'a'.repeat(COLUMN_LIMIT - 19)}`;
    assert.equal(value.length, COLUMN_LIMIT);
    const box = setUp(t, [{ slug: 'honda-cg-160-2020', front_brake: value, rear_brake: null, source: STRONG_SOURCE }]);

    const result = box.run(SCRIPT);

    assert.match(result.stdout, /width cuts 0/);
    assert.equal(stagedRows(box.read(seedPath('honda')))[0][2], value);
});

test('a value over the column limit is cut at the last word boundary, never mid-word', (t) => {
    const head = fiveCharWords(25).join(' ');
    assert.equal(head.length, 149);
    const value = `${head}, supplementary detail that runs well past the one hundred and sixty character column limit`;
    const box = setUp(t, [{ slug: 'honda-cg-160-2020', front_brake: value, rear_brake: null, source: STRONG_SOURCE }]);

    const result = box.run(SCRIPT);
    const fitted = stagedRows(box.read(seedPath('honda')))[0][2];

    assert.match(result.stdout, /width cuts 1/);
    assert.equal(fitted, head);
    assert.ok(fitted.length <= COLUMN_LIMIT, `cut is ${fitted.length} characters`);
    assert.ok(value.startsWith(fitted), 'the cut value must be a prefix of the original');
    assert.doesNotMatch(fitted, /[\s,;/-]$/, 'trailing punctuation and space are stripped');
    assert.ok(/[\s,;/-]/.test(value[fitted.length]), 'the character after the cut is a separator, so no word was split');
});

test('a value over the limit with no word boundary at all is cut at exactly the limit', (t) => {
    const value = 'd'.repeat(200);
    const box = setUp(t, [{ slug: 'honda-cg-160-2020', front_brake: value, rear_brake: null, source: STRONG_SOURCE }]);

    box.run(SCRIPT);

    assert.equal(stagedRows(box.read(seedPath('honda')))[0][2].length, COLUMN_LIMIT);
});

test('fits abs_type against its own narrower 80 character limit', (t) => {
    const head = fiveCharWords(11).join(' ');
    assert.equal(head.length, 65);
    const box = setUp(t, [{
        slug: 'honda-cg-160-2020',
        front_brake: 'Single disc',
        rear_brake: 'Drum',
        abs_type: `${head}, dual-channel ABS across both wheels`,
        source: STRONG_SOURCE,
    }]);

    box.run(SCRIPT);

    const absType = stagedRows(box.read(seedPath('honda')))[0][4];
    assert.ok(absType.length <= 80, `abs_type is ${absType.length} characters`);
    assert.doesNotMatch(absType, /[\s,;/-]$/);
});

test('a slug no longer on the worklist is still staged and reported as resolved elsewhere', (t) => {
    const box = setUp(t, HONDA_BATCH, ['honda-cg-160-2020', 'honda-cg-160-2021']);

    const result = box.run(SCRIPT);
    const rows = stagedRows(box.read(seedPath('honda')));

    assert.equal(result.status, 0);
    assert.equal(rows.length, 3);
    assert.ok(rows.some((r) => r[1] === 'honda-xre-300-2022'));
    assert.match(result.stdout, /resolved elsewhere 1/);
    assert.match(headerProse(box.read(seedPath('honda'))), /1 staged slug \(honda-xre-300-2022\) already has both columns filled by another seed/);
});

test('detects a nameplate split when two model years carry different values', (t) => {
    const box = setUp(t, [
        { slug: 'honda-cg-160-2020', front_brake: 'Drum', rear_brake: 'Drum', source: STRONG_SOURCE },
        { slug: 'honda-cg-160-2021', front_brake: 'Single disc', rear_brake: 'Drum', source: STRONG_SOURCE },
    ]);

    const result = box.run(SCRIPT, ['--check']);

    assert.match(result.stdout, /nameplates: 1, of which 1 split by model year/);
});

test('does not call it a split when two model years carry the same values', (t) => {
    const box = setUp(t, [
        { slug: 'honda-cg-160-2020', front_brake: 'Drum', rear_brake: 'Drum', source: STRONG_SOURCE },
        { slug: 'honda-cg-160-2021', front_brake: 'Drum', rear_brake: 'Drum', source: STRONG_SOURCE },
    ]);

    const result = box.run(SCRIPT, ['--check']);

    assert.match(result.stdout, /nameplates: 1, of which 0 split by model year/);
});

test('an abs_type that changes across model years counts as a split on its own', (t) => {
    const box = setUp(t, [
        { slug: 'honda-cg-160-2020', front_brake: 'Drum', rear_brake: 'Drum', source: STRONG_SOURCE },
        { slug: 'honda-cg-160-2021', front_brake: 'Drum', rear_brake: 'Drum', abs_type: 'CBS', source: STRONG_SOURCE },
    ]);

    const result = box.run(SCRIPT, ['--check']);

    assert.match(result.stdout, /nameplates: 1, of which 1 split by model year/);
});

// --- generation-aborting validation -----------------------------------------------------------

const ABORT_CASES = [
    ['an entry with no slug', [{ front_brake: 'Drum', rear_brake: null, source: STRONG_SOURCE }], /has no slug/],
    ['a badly shaped slug', [{ slug: 'Honda-CG-160', front_brake: 'Drum', source: STRONG_SOURCE }], /is not a shape the public routing can use/],
    ['a duplicate slug', [
        { slug: 'honda-cg-160-2020', front_brake: 'Drum', source: STRONG_SOURCE },
        { slug: 'honda-cg-160-2020', front_brake: 'Drum', source: STRONG_SOURCE },
    ], /appears more than once/],
    ['neither a front nor a rear value', [{ slug: 'honda-cg-160-2020', front_brake: null, rear_brake: null, source: STRONG_SOURCE }], /carries neither a front nor a rear value/],
    ['no source', [{ slug: 'honda-cg-160-2020', front_brake: 'Drum' }], /has no source/],
];

for (const [label, research, expected] of ABORT_CASES) {
    test(`aborts with no file written on ${label}`, (t) => {
        const box = setUp(t, research, ['honda-cg-160-2020']);

        const result = box.run(SCRIPT);

        assert.equal(result.status, 1);
        assert.match(result.stderr, expected);
        assert.match(result.stderr, /^import-brakes: /);
        assert.deepEqual(box.seedFiles(), []);
    });
}

test('a research file that is not a flat array dies with a clear message', (t) => {
    const box = setUp(t, { slug: 'honda-cg-160-2020' }, ['honda-cg-160-2020']);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /brakes-research\.json must be a flat array/);
    assert.deepEqual(box.seedFiles(), []);
});

test('writes only inside the sandbox seed directory', (t) => {
    const box = setUp(t, HONDA_BATCH);

    box.run(SCRIPT);

    assert.ok(box.exists(seedPath('honda')));
    assert.equal(path.basename(box.seedFiles()[0]), 'R__zzzzz_motorcycles_brakes_honda_2026_09.sql');
});
