#!/usr/bin/env node
// One-off helper (not part of the permanent pipeline): splits a combined per-brand research batch
// (tools/research-batches/<brand>.json, carrying both brake and tyre fields from one research pass)
// into the two separate accumulator files tools/brakes-research.json and tools/tyres-research.json
// that import-brakes.mjs / import-tyres.mjs expect. Only entries whose slug is still on the live
// "missing" worklist for that column pair are kept - this project's validators hard-fail on any
// staged slug that isn't a genuine current target, and a combined batch researched per-nameplate
// legitimately returns brake values for rows whose brakes are already resolved by an earlier seed.
// Usage: node tools/_split-research-batch.mjs <brand-batch-file> [--brakes] [--tyres]
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const batchFile = args.find((a) => !a.startsWith('--'));
if (!batchFile) { console.error('usage: node tools/_split-research-batch.mjs <batch-file.json> [--brakes] [--tyres]'); process.exit(1); }
const doBrakes = args.includes('--brakes');
const doTyres = args.includes('--tyres');

const batch = JSON.parse(fs.readFileSync(path.resolve(REPO_ROOT, batchFile), 'utf8'));
const missingBrakes = new Set(fs.readFileSync(path.join(REPO_ROOT, 'tools/slugs_missing_brakes.txt'), 'utf8').split('\n').map((l) => l.trim()).filter(Boolean));
const missingTyres = new Set(fs.readFileSync(path.join(REPO_ROOT, 'tools/slugs_missing_tyres.txt'), 'utf8').split('\n').map((l) => l.trim()).filter(Boolean));

if (doBrakes) {
    const out = batch
        .filter((e) => missingBrakes.has(e.slug) && (e.front_brake || e.rear_brake))
        .map((e) => ({ slug: e.slug, front_brake: e.front_brake ?? null, rear_brake: e.rear_brake ?? null, abs_type: e.abs_type ?? null, source: e.source }));
    fs.writeFileSync(path.join(REPO_ROOT, 'tools/brakes-research.json'), JSON.stringify(out, null, 2) + '\n', 'utf8');
    console.log(`brakes: ${out.length} / ${batch.length} batch entries are genuine current brake targets -> tools/brakes-research.json`);
    const skipped = batch.filter((e) => (e.front_brake || e.rear_brake) && !missingBrakes.has(e.slug));
    if (skipped.length) console.log(`  (${skipped.length} entries carried a brake value for an already-resolved slug - left out, not lost: still in ${batchFile})`);
}
if (doTyres) {
    const out = batch
        .filter((e) => missingTyres.has(e.slug) && (e.front_tyre || e.rear_tyre))
        .map((e) => ({ slug: e.slug, front_tyre: e.front_tyre ?? null, rear_tyre: e.rear_tyre ?? null, source: e.source }));
    fs.writeFileSync(path.join(REPO_ROOT, 'tools/tyres-research.json'), JSON.stringify(out, null, 2) + '\n', 'utf8');
    console.log(`tyres: ${out.length} / ${batch.length} batch entries are genuine current tyre targets -> tools/tyres-research.json`);
}
