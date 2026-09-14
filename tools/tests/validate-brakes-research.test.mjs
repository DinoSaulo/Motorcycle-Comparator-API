// Black-box tests for tools/validate-brakes-research.mjs. Run: node --test "tools/tests/**/*.test.mjs"
// Every case runs a copy of the script in a throwaway tree, so the real research files are never touched.

import test from 'node:test';
import assert from 'node:assert/strict';

import { sandboxFor, STRONG_SOURCE, WEAK_SOURCE, brakeEntry } from './_harness.mjs';

const SCRIPT = 'validate-brakes-research.mjs';
const DEFAULT_TARGETS = ['honda-cg-160-2020', 'honda-cg-160-2021', 'honda-xre-300-2022'];

/** A sandbox holding the validator, a worklist and a research file, ready to run. */
function setUp(t, research, targets = DEFAULT_TARGETS) {
    const box = sandboxFor(t, [SCRIPT]);
    box.writeSlugList('tools/slugs_missing_brakes.txt', targets);
    box.writeJson('tools/brakes-research.json', research);
    return box;
}

/** Runs the validator over one entry and returns the result, for the many single-rule cases. */
const runWith = (t, overrides, targets) => setUp(t, [brakeEntry(overrides)], targets).run(SCRIPT);

test('a valid entry passes and prints the derived coverage report', (t) => {
    const box = setUp(t, [
        brakeEntry({ slug: 'honda-cg-160-2020', front_brake: 'Single 240 mm disc', rear_brake: 'Drum', abs_type: 'CBS', source: STRONG_SOURCE }),
    ]);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 0);
    assert.equal(result.stderr, '');
    assert.match(result.stdout, /brakes-research\.json is valid/);
    assert.match(result.stdout, /entries\s+1 \/ 3 catalogue slugs \(33\.3%\)/);
    assert.match(result.stdout, /columns\s+1 front, 1 rear, 1 abs_type/);
    assert.match(result.stdout, /brands\s+1: honda/);
    assert.match(result.stdout, /in-scope\s+1 \/ 3 slugs of the brands covered \(33\.3%\)/);
    assert.match(result.stdout, /sources\s+1 distinct domains: powersports\.honda\.com/);
});

test('counts a nameplate whose value changes across model years as a year split', (t) => {
    const box = setUp(t, [
        brakeEntry({ slug: 'honda-cg-160-2020', front_brake: 'Drum', rear_brake: 'Drum', source: STRONG_SOURCE }),
        brakeEntry({ slug: 'honda-cg-160-2021', front_brake: 'Single disc', rear_brake: 'Drum', source: STRONG_SOURCE }),
    ]);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 0);
    assert.match(result.stdout, /nameplates\s+1, of which 1 span more than one year/);
    assert.match(result.stdout, /year splits\s+1 of those 1 carry more than one distinct value/);
});

test('an entry with no slug is reported and its other rules are skipped', (t) => {
    const box = setUp(t, [{ front_brake: 'Drum', rear_brake: 'Drum', source: STRONG_SOURCE }]);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /1 problem\(s\)/);
    assert.match(result.stderr, /has no slug/);
});

for (const [label, slug] of [
    ['uppercase', 'Honda-CG-160-2020'],
    ['underscores', 'honda_cg_160'],
    ['a leading hyphen', '-honda-cg-160'],
]) {
    test(`rejects a slug with ${label}`, (t) => {
        const result = runWith(t, { slug });

        assert.equal(result.status, 1);
        assert.match(result.stderr, new RegExp(`slug "${slug}" is not a shape the public routing can use`));
    });
}

test('rejects the same slug appearing twice in one file', (t) => {
    const box = setUp(t, [brakeEntry(), brakeEntry()]);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /slug "honda-cg-160-2020" appears more than once/);
});

test('rejects a slug that is not on the brakes worklist', (t) => {
    const result = runWith(t, { slug: 'honda-xre-300-2022' }, ['honda-cg-160-2020']);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /slug "honda-xre-300-2022" is not in tools\/slugs_missing_brakes\.txt/);
});

test('rejects an entry carrying neither a front nor a rear value', (t) => {
    const result = runWith(t, { front_brake: null, rear_brake: null });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /carries neither a front nor a rear value/);
});

test('rejects an entry with no source', (t) => {
    const result = runWith(t, { source: undefined });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /slug "honda-cg-160-2020" has no source/);
});

test('rejects a value with leading or trailing whitespace', (t) => {
    const result = runWith(t, { front_brake: ' Single disc ', rear_brake: null });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /front_brake has leading or trailing whitespace/);
});

test('accepts a value at exactly the 160 character limit and rejects one character more', (t) => {
    const atLimit = `Single disc ${'a'.repeat(148)}`;
    assert.equal(atLimit.length, 160);

    const ok = runWith(t, { front_brake: atLimit, rear_brake: null });
    const tooLong = runWith(t, { front_brake: `${atLimit}a`, rear_brake: null });

    assert.equal(ok.status, 0);
    assert.equal(tooLong.status, 1);
    assert.match(tooLong.stderr, /front_brake is 161 characters, over the 160 limit/);
});

test('rejects prose carrying no diameter, disc/drum type or explicit None', (t) => {
    const result = runWith(t, { front_brake: 'Front and rear stoppers by Nissin', rear_brake: null });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /carries no diameter, disc\/drum type or explicit None/);
});

test('rejects marketing copy even when it happens to contain a technical token', (t) => {
    const result = runWith(t, { front_brake: 'Excellent disc setup', rear_brake: null });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /reads as marketing prose/);
});

test('accepts an explicit None for an axle with no brake', (t) => {
    const result = runWith(t, { front_brake: 'Single disc', rear_brake: 'None' });

    assert.equal(result.status, 0);
});

// ABS/CBS/UBS/LBS live in abs_type, never inside the brake string.
for (const [label, value] of [
    ['ABS', 'Single disc with ABS'],
    ['CBS', 'Drum with CBS'],
    ['UBS', 'Drum, UBS equipped'],
    ['LBS', 'Single disc, LBS'],
    ['spelled-out linked braking', 'Single disc with linked braking'],
]) {
    test(`rejects a brake string mentioning ${label}`, (t) => {
        const result = runWith(t, { front_brake: value, rear_brake: null });

        assert.equal(result.status, 1);
        assert.match(result.stderr, /which belongs in abs_type/);
    });
}

test('rejects an abs_type that names no ABS or CBS system', (t) => {
    const result = runWith(t, { abs_type: 'Advanced braking hardware' });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /abs_type names no ABS or CBS system/);
});

test('rejects an abs_type over the 80 character limit', (t) => {
    const result = runWith(t, { abs_type: `Dual-channel ABS ${'a'.repeat(64)}` });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /abs_type is 81 characters, over the 80 limit/);
});

test('accepts an abs_type at exactly the 80 character limit', (t) => {
    const result = runWith(t, { abs_type: `Dual-channel ABS ${'a'.repeat(63)}` });

    assert.equal(result.status, 0);
});

// A millimetre figure needs a source that owns it - aggregators reprint diameters without attribution.
test('rejects a diameter whose source is not on the strong-source allowlist', (t) => {
    const result = runWith(t, { front_brake: 'Single 240 mm disc', rear_brake: null, source: WEAK_SOURCE });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /states a diameter but no source owns it/);
});

test('accepts the same diameter from a strong source', (t) => {
    const result = runWith(t, { front_brake: 'Single 240 mm disc', rear_brake: null, source: 'https://honda.com/cg160' });

    assert.equal(result.status, 0);
});

test('accepts a disc/drum value with no diameter from a weak source', (t) => {
    const result = runWith(t, { front_brake: 'Single disc', rear_brake: 'Drum', source: WEAK_SOURCE });

    assert.equal(result.status, 0);
});

test('--file validates an alternate shard instead of the default research file', (t) => {
    const box = setUp(t, [brakeEntry({ front_brake: 'not a spec at all', rear_brake: null })]);
    box.writeJson('tools/shard.json', [brakeEntry({ slug: 'honda-xre-300-2022', source: STRONG_SOURCE })]);

    const result = box.run(SCRIPT, ['--file', box.abs('tools/shard.json')]);

    assert.equal(result.status, 0);
    assert.match(result.stdout, /shard\.json is valid/);
    assert.doesNotMatch(result.stdout, /brakes-research\.json/);
});

test('--quiet suppresses the coverage report on success', (t) => {
    const box = setUp(t, [brakeEntry({ source: STRONG_SOURCE })]);

    const result = box.run(SCRIPT, ['--quiet']);

    assert.equal(result.status, 0);
    assert.equal(result.stdout, '');
});

test('--quiet still prints failures and still exits 1', (t) => {
    const box = setUp(t, [brakeEntry({ front_brake: 'gorgeous stoppers', rear_brake: null })]);

    const result = box.run(SCRIPT, ['--quiet']);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /1 problem\(s\)/);
    assert.equal(result.stdout, '');
});

test('lists every accumulated failure up to the 40-item cap and counts the rest', (t) => {
    const entries = Array.from({ length: 45 }, (_, i) => brakeEntry({ slug: `honda-model-${1000 + i}` }));
    const box = setUp(t, entries, []);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /45 problem\(s\)/);
    assert.equal(result.stderr.split('\n').filter((l) => l.startsWith('  - ')).length, 40);
    assert.match(result.stderr, /\.\.\. and 5 more/);
});

test('a research file that is not a flat array dies with a clear message', (t) => {
    const box = setUp(t, { slug: 'honda-cg-160-2020' });

    const result = box.run(SCRIPT);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /research file must be a flat array/);
});

// DIVERGENCE FROM validate-tyres-research.mjs, documented rather than asserted as correct:
// the tyres validator guards its in-scope percentage with `targetsForBrands > 0 ? ... : '0.0'`;
// this one divides unguarded, so an empty research file prints "(NaN%)". Reported to the owner.
test('an empty research file passes but prints NaN% for in-scope coverage (unguarded division)', (t) => {
    const box = setUp(t, []);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 0);
    assert.match(result.stdout, /in-scope\s+0 \/ 0 slugs of the brands covered \(NaN%\)/);
});
