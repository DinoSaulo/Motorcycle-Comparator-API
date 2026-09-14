// Black-box tests for tools/validate-tyres-research.mjs. Run: node --test "tools/tests/**/*.test.mjs"
// Mirrors validate-brakes-research.test.mjs, minus the abs_type rules the tyres validator has no column for.

import test from 'node:test';
import assert from 'node:assert/strict';

import { sandboxFor, STRONG_SOURCE, WEAK_SOURCE, tyreEntry } from './_harness.mjs';

const SCRIPT = 'validate-tyres-research.mjs';
const DEFAULT_TARGETS = ['honda-cg-160-2020', 'honda-cg-160-2021', 'kawasaki-z400-2023'];

/** A sandbox holding the validator, a worklist and a research file, ready to run. */
function setUp(t, research, targets = DEFAULT_TARGETS) {
    const box = sandboxFor(t, [SCRIPT]);
    box.writeSlugList('tools/slugs_missing_tyres.txt', targets);
    box.writeJson('tools/tyres-research.json', research);
    return box;
}

const runWith = (t, overrides, targets) => setUp(t, [tyreEntry(overrides)], targets).run(SCRIPT);

test('a valid entry passes and prints the derived coverage report', (t) => {
    const box = setUp(t, [tyreEntry({ front_tyre: '120/70ZR17', rear_tyre: '180/55ZR17', source: STRONG_SOURCE })]);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 0);
    assert.equal(result.stderr, '');
    assert.match(result.stdout, /tyres-research\.json is valid/);
    assert.match(result.stdout, /entries\s+1 \/ 3 catalogue slugs \(33\.3%\)/);
    assert.match(result.stdout, /columns\s+1 front, 1 rear/);
    assert.match(result.stdout, /brands\s+1: honda/);
    assert.match(result.stdout, /in-scope\s+1 \/ 2 slugs of the brands covered \(50\.0%\)/);
    assert.match(result.stdout, /sources\s+1 distinct domains: powersports\.honda\.com/);
});

// The three notations the validator deliberately accepts as genuine technical tokens.
for (const [label, front, rear] of [
    ['metric radial notation', '120/70ZR17', '180/55ZR17'],
    ['older bias-ply notation', '4.60-18', '3.25-19'],
    ['the kawasaki.com "x" separator variant', '2.50x14', '110/70x17'],
]) {
    test(`accepts ${label}`, (t) => {
        const result = runWith(t, { front_tyre: front, rear_tyre: rear });

        assert.equal(result.status, 0, result.stderr);
    });
}

test('accepts an explicit None for an axle with no tyre', (t) => {
    const result = runWith(t, { front_tyre: '120/70ZR17', rear_tyre: 'None' });

    assert.equal(result.status, 0, result.stderr);
});

test('counts a nameplate whose value changes across model years as a year split', (t) => {
    const box = setUp(t, [
        tyreEntry({ slug: 'honda-cg-160-2020', front_tyre: '80/100-18', rear_tyre: '90/90-18' }),
        tyreEntry({ slug: 'honda-cg-160-2021', front_tyre: '80/100-18', rear_tyre: '100/90-18' }),
    ]);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 0);
    assert.match(result.stdout, /nameplates\s+1, of which 1 span more than one year/);
    assert.match(result.stdout, /year splits\s+1 of those 1 carry more than one distinct value/);
});

test('an entry with no slug is reported and its other rules are skipped', (t) => {
    const box = setUp(t, [{ front_tyre: '120/70ZR17', rear_tyre: '180/55ZR17', source: STRONG_SOURCE }]);

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
    const box = setUp(t, [tyreEntry(), tyreEntry()]);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /slug "honda-cg-160-2020" appears more than once/);
});

test('rejects a slug that is not on the tyres worklist', (t) => {
    const result = runWith(t, { slug: 'kawasaki-z400-2023' }, ['honda-cg-160-2020']);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /slug "kawasaki-z400-2023" is not in tools\/slugs_missing_tyres\.txt/);
});

test('rejects an entry carrying neither a front nor a rear value', (t) => {
    const result = runWith(t, { front_tyre: null, rear_tyre: null });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /carries neither a front nor a rear value/);
});

test('rejects an entry with no source', (t) => {
    const result = runWith(t, { source: undefined });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /slug "honda-cg-160-2020" has no source/);
});

test('rejects a value with leading or trailing whitespace', (t) => {
    const result = runWith(t, { front_tyre: ' 120/70ZR17 ', rear_tyre: null });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /front_tyre has leading or trailing whitespace/);
});

test('accepts a value at exactly the 60 character limit and rejects one character more', (t) => {
    const atLimit = `120/70ZR17 ${'a'.repeat(49)}`;
    assert.equal(atLimit.length, 60);

    const ok = runWith(t, { front_tyre: atLimit, rear_tyre: null });
    const tooLong = runWith(t, { front_tyre: `${atLimit}a`, rear_tyre: null });

    assert.equal(ok.status, 0, ok.stderr);
    assert.equal(tooLong.status, 1);
    assert.match(tooLong.stderr, /front_tyre is 61 characters, over the 60 limit/);
});

test('rejects prose carrying no tyre-size code or explicit None', (t) => {
    const result = runWith(t, { front_tyre: 'Tubeless radial rubber by Pirelli', rear_tyre: null });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /carries no tyre-size code or explicit None/);
});

test('rejects marketing copy even when it happens to contain a size code', (t) => {
    const result = runWith(t, { front_tyre: 'Excellent 120/70ZR17 fitment', rear_tyre: null });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /reads as marketing prose/);
});

// A bare mm rim figure needs a source that owns it, exactly like a brake diameter.
test('rejects a bare mm figure whose source is not on the strong-source allowlist', (t) => {
    const result = runWith(t, { front_tyre: '120/70ZR17 (620 mm overall)', rear_tyre: null, source: WEAK_SOURCE });

    assert.equal(result.status, 1);
    assert.match(result.stderr, /states a bare mm figure but no source owns it/);
});

test('accepts the same bare mm figure from a strong source', (t) => {
    const result = runWith(t, { front_tyre: '120/70ZR17 (620 mm overall)', rear_tyre: null, source: 'https://honda.com/cg160' });

    assert.equal(result.status, 0, result.stderr);
});

test('accepts standard inch-rim size notation from a weak source', (t) => {
    const result = runWith(t, { front_tyre: '120/70ZR17', rear_tyre: '180/55ZR17', source: WEAK_SOURCE });

    assert.equal(result.status, 0, result.stderr);
});

test('--file validates an alternate shard instead of the default research file', (t) => {
    const box = setUp(t, [tyreEntry({ front_tyre: 'not a size at all', rear_tyre: null })]);
    box.writeJson('tools/shard.json', [tyreEntry({ slug: 'kawasaki-z400-2023', source: STRONG_SOURCE })]);

    const result = box.run(SCRIPT, ['--file', box.abs('tools/shard.json')]);

    assert.equal(result.status, 0);
    assert.match(result.stdout, /shard\.json is valid/);
    assert.doesNotMatch(result.stdout, /tyres-research\.json/);
});

test('--quiet suppresses the coverage report on success', (t) => {
    const box = setUp(t, [tyreEntry({ source: STRONG_SOURCE })]);

    const result = box.run(SCRIPT, ['--quiet']);

    assert.equal(result.status, 0);
    assert.equal(result.stdout, '');
});

test('--quiet still prints failures and still exits 1', (t) => {
    const box = setUp(t, [tyreEntry({ front_tyre: 'lovely rubber', rear_tyre: null })]);

    const result = box.run(SCRIPT, ['--quiet']);

    assert.equal(result.status, 1);
    assert.match(result.stderr, /1 problem\(s\)/);
    assert.equal(result.stdout, '');
});

test('lists every accumulated failure up to the 40-item cap and counts the rest', (t) => {
    const entries = Array.from({ length: 45 }, (_, i) => tyreEntry({ slug: `honda-model-${1000 + i}` }));
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

// The guard the brakes validator lacks: 0 in-scope targets prints "0.0%" here, "NaN%" there.
test('an empty research file passes and guards the in-scope percentage against division by zero', (t) => {
    const box = setUp(t, []);

    const result = box.run(SCRIPT);

    assert.equal(result.status, 0);
    assert.match(result.stdout, /in-scope\s+0 \/ 0 slugs of the brands covered \(0\.0%\)/);
});
