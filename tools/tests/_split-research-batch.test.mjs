// Black-box tests for tools/_split-research-batch.mjs. Run: node --test "tools/tests/**/*.test.mjs"
// The script writes the two accumulator files relative to its own location, so each case runs a copy of
// it in a throwaway tree - the real tools/brakes-research.json is never overwritten by a test.

import test from 'node:test';
import assert from 'node:assert/strict';

import { sandboxFor, STRONG_SOURCE } from './_harness.mjs';

const SCRIPT = '_split-research-batch.mjs';
const BATCH = 'tools/research-batches/honda.json';

const COMBINED_BATCH = [
    // On both worklists, carrying both kinds of value.
    { slug: 'honda-cg-160-2020', front_brake: 'Single 240 mm disc', rear_brake: 'Drum', abs_type: 'CBS', front_tyre: '80/100-18', rear_tyre: '90/90-18', source: STRONG_SOURCE },
    // Tyres only: its brakes were resolved by an earlier seed, so it left the brakes worklist.
    { slug: 'honda-cg-160-2021', front_brake: 'Single 240 mm disc', rear_brake: 'Drum', front_tyre: '80/100-18', rear_tyre: null, source: STRONG_SOURCE },
    // On both worklists but the research pass found no value at all for either column pair.
    { slug: 'honda-xre-300-2022', front_brake: null, rear_brake: null, front_tyre: null, rear_tyre: null, source: STRONG_SOURCE },
];

/** A sandbox holding the splitter, both worklists and a combined per-brand batch. */
function setUp(t, batch = COMBINED_BATCH, { brakes, tyres } = {}) {
    const box = sandboxFor(t, [SCRIPT]);
    box.writeSlugList('tools/slugs_missing_brakes.txt', brakes ?? ['honda-cg-160-2020', 'honda-xre-300-2022']);
    box.writeSlugList('tools/slugs_missing_tyres.txt', tyres ?? ['honda-cg-160-2020', 'honda-cg-160-2021', 'honda-xre-300-2022']);
    box.writeJson(BATCH, batch);
    return box;
}

const readOut = (box, rel) => JSON.parse(box.read(rel));

test('--brakes keeps only slugs on the brakes worklist that carry a brake value', (t) => {
    const box = setUp(t);

    const result = box.run(SCRIPT, [BATCH, '--brakes']);

    assert.equal(result.status, 0);
    const out = readOut(box, 'tools/brakes-research.json');
    assert.deepEqual(out.map((e) => e.slug), ['honda-cg-160-2020']);
    assert.deepEqual(out[0], {
        slug: 'honda-cg-160-2020',
        front_brake: 'Single 240 mm disc',
        rear_brake: 'Drum',
        abs_type: 'CBS',
        source: STRONG_SOURCE,
    });
    assert.match(result.stdout, /brakes: 1 \/ 3 batch entries are genuine current brake targets/);
});

test('--brakes alone writes no tyres accumulator', (t) => {
    const box = setUp(t);

    box.run(SCRIPT, [BATCH, '--brakes']);

    assert.ok(box.exists('tools/brakes-research.json'));
    assert.ok(!box.exists('tools/tyres-research.json'));
});

test('--tyres keeps only slugs on the tyres worklist that carry a tyre value', (t) => {
    const box = setUp(t);

    const result = box.run(SCRIPT, [BATCH, '--tyres']);

    assert.equal(result.status, 0);
    const out = readOut(box, 'tools/tyres-research.json');
    assert.deepEqual(out.map((e) => e.slug), ['honda-cg-160-2020', 'honda-cg-160-2021']);
    assert.deepEqual(out[1], {
        slug: 'honda-cg-160-2021',
        front_tyre: '80/100-18',
        rear_tyre: null,
        source: STRONG_SOURCE,
    });
    assert.match(result.stdout, /tyres: 2 \/ 3 batch entries are genuine current tyre targets/);
});

test('--tyres alone writes no brakes accumulator', (t) => {
    const box = setUp(t);

    box.run(SCRIPT, [BATCH, '--tyres']);

    assert.ok(box.exists('tools/tyres-research.json'));
    assert.ok(!box.exists('tools/brakes-research.json'));
});

test('both flags together write both accumulators from one batch', (t) => {
    const box = setUp(t);

    const result = box.run(SCRIPT, [BATCH, '--brakes', '--tyres']);

    assert.equal(result.status, 0);
    assert.equal(readOut(box, 'tools/brakes-research.json').length, 1);
    assert.equal(readOut(box, 'tools/tyres-research.json').length, 2);
    assert.match(result.stdout, /brakes: 1 \/ 3/);
    assert.match(result.stdout, /tyres: 2 \/ 3/);
});

// An already-resolved slug is excluded from the accumulator but stays in the batch file it came from.
test('a brake value for an already-resolved slug is counted as skipped, not dropped from the batch', (t) => {
    const box = setUp(t);

    const result = box.run(SCRIPT, [BATCH, '--brakes']);

    assert.match(result.stdout, /1 entries carried a brake value for an already-resolved slug - left out, not lost/);
    assert.match(result.stdout, new RegExp(`still in ${BATCH.replace(/\//g, '\\/')}`));
    const batchOnDisk = readOut(box, BATCH);
    assert.equal(batchOnDisk.length, COMBINED_BATCH.length);
    assert.ok(batchOnDisk.some((e) => e.slug === 'honda-cg-160-2021' && e.front_brake === 'Single 240 mm disc'));
});

test('an entry with no value at all is excluded without being counted as skipped', (t) => {
    const box = setUp(t, [COMBINED_BATCH[2]]);

    const result = box.run(SCRIPT, [BATCH, '--brakes']);

    assert.equal(readOut(box, 'tools/brakes-research.json').length, 0);
    assert.match(result.stdout, /brakes: 0 \/ 1 batch entries/);
    assert.doesNotMatch(result.stdout, /already-resolved slug/);
});

test('normalises a missing optional field to an explicit null rather than omitting it', (t) => {
    const box = setUp(t, [{ slug: 'honda-cg-160-2020', front_brake: 'Drum', front_tyre: '80/100-18', source: STRONG_SOURCE }]);

    box.run(SCRIPT, [BATCH, '--brakes', '--tyres']);

    const brake = readOut(box, 'tools/brakes-research.json')[0];
    const tyre = readOut(box, 'tools/tyres-research.json')[0];
    assert.deepEqual(Object.keys(brake), ['slug', 'front_brake', 'rear_brake', 'abs_type', 'source']);
    assert.equal(brake.rear_brake, null);
    assert.equal(brake.abs_type, null);
    assert.equal(tyre.rear_tyre, null);
});

test('carries nothing over from a slug on neither worklist', (t) => {
    const box = setUp(t, COMBINED_BATCH, { brakes: [], tyres: [] });

    const result = box.run(SCRIPT, [BATCH, '--brakes', '--tyres']);

    assert.equal(result.status, 0);
    assert.equal(readOut(box, 'tools/brakes-research.json').length, 0);
    assert.equal(readOut(box, 'tools/tyres-research.json').length, 0);
    assert.match(result.stdout, /2 entries carried a brake value for an already-resolved slug/);
});

test('no batch-file argument is a usage error with exit 1', (t) => {
    const box = setUp(t);

    const result = box.run(SCRIPT, ['--brakes', '--tyres']);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /^usage: node tools\/_split-research-batch\.mjs <batch-file\.json> \[--brakes\] \[--tyres\]/);
    assert.ok(!box.exists('tools/brakes-research.json'));
    assert.ok(!box.exists('tools/tyres-research.json'));
});

test('a batch file with neither flag writes nothing and exits 0', (t) => {
    const box = setUp(t);

    const result = box.run(SCRIPT, [BATCH]);

    assert.equal(result.status, 0);
    assert.equal(result.stdout, '');
    assert.ok(!box.exists('tools/brakes-research.json'));
    assert.ok(!box.exists('tools/tyres-research.json'));
});

test('writes each accumulator as pretty-printed JSON ending in a newline', (t) => {
    const box = setUp(t);

    box.run(SCRIPT, [BATCH, '--brakes', '--tyres']);

    for (const rel of ['tools/brakes-research.json', 'tools/tyres-research.json']) {
        const text = box.read(rel);
        assert.ok(text.endsWith('\n'), `${rel} must end in a newline`);
        assert.match(text, /^\[\n {2}\{\n {4}"slug"/, `${rel} must be indented two spaces`);
    }
});
