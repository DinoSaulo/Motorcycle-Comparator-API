#!/usr/bin/env node
// Validates a brakes research file (or one research shard) against tools/slugs_missing_brakes.txt.
// Read-only: reports and exits non-zero on failure, never writes. Usage: node <this> [--file path] [--quiet]

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
const QUIET = args.includes('--quiet');

const JSON_PATH = path.resolve(option('--file', path.join(REPO_ROOT, 'tools/brakes-research.json')));
const SLUG_LIST = path.join(REPO_ROOT, 'tools/slugs_missing_brakes.txt');

const COLUMN_LIMIT = 160;
const ABS_COLUMN_LIMIT = 80;
const SLUG_SHAPE = /^[a-z0-9]+(-[a-z0-9]+)*$/;

// front_brake and rear_brake are always NULL together, so one target list covers both columns -
// unlike the suspension backfill, which needs a separate list per column.
const targets = new Set(fs.readFileSync(SLUG_LIST, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean));

const research = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
const failures = [];
const fail = (message) => failures.push(message);

if (!Array.isArray(research)) {
    console.error('validate-brakes: research file must be a flat array of {slug, front_brake, rear_brake, source}');
    process.exit(1);
}

// A value has to carry a real specification. A diameter, an explicit disc/drum type, or an explicit
// "None" for a machine with no brake on that axle - anything else is prose dressed up as data.
const TECHNICAL_TOKEN = /(\d+\s*mm|\bdiscs?\b|\bdrum\b|\bnone\b)/i;
// Marketing copy that reads like a spec but states nothing measurable.
const PROSE_SMELL = /^(excellent|great|advanced|good|powerful|modern|reliable|standard|otimos|ótimos|sistema de freio avan)/i;
// Anti-lock and combined braking belong in the abs_type column (VARCHAR(80), V1__initial_schema.sql:98),
// not inside the brake string - the dev seed keeps that separation and duplicating it would drift.
// Honda's "Linked Braking System" (LBS) on the ST1100/ST1300/VTX1800 and Yamaha's "Unified Brake
// System" (UBS) are the same category of thing as ABS/CBS under a different trade name.
const BRAKING_TECH = /\bABS\b|\bCBS\b|\bUBS\b|\bLBS\b|\blinked\s+brak(?:ing|e|es)?\b/i;

// Only these may carry a millimetre figure. Aggregator sites reprint diameters without attribution and
// the Honda pilot audit could not corroborate several of them, so a number needs a source that owns it.
const STRONG_SOURCES = [
    /\bhonda\.com(\.[a-z]{2})?\b/i, /\bsaladeimprensa\.honda\.com\.br\b/i, /\bhondanews\.com\b/i, /\bpowersports\.honda\.com\b/i,
    /\bkawasaki\.com(\.[a-z]{2})?\b/i, /\byamaha-motor\.com(\.[a-z]{2})?\b/i, /\bsuzukicycles\.com\b/i, /\bsuzuki\.com(\.[a-z]{2})?\b/i,
    /\bglobalsuzuki\.com\b/i, /\bktm\.com\b/i, /\bducati\.com\b/i, /\btriumphmotorcycles\.[a-z.]+\b/i, /\bpiaggio\.com\b/i, /\baprilia\.com\b/i,
    /\bbmw-motorrad\.[a-z.]+\b/i, /\bharley-davidson\.com\b/i, /\bpolaris\.com\b/i, /\bcan-am\.brp\.com\b/i, /\bbrp\.com\b/i,
    /\bautoevolution\.com\b/i, /\bmotorcyclenews\.com\b/i, /\bmotonline\.com\.br\b/i, /\bfichatecnica\.motosblog\.com\.br\b/i,
];
const DIAMETER = /\d+\s*mm/i;
const isStrong = (source) => STRONG_SOURCES.some((re) => re.test(source));

const countChars = (text) => [...text].length;

const seen = new Set();
for (const entry of research) {
    const { slug, front_brake: front, rear_brake: rear, source } = entry;
    const label = slug || JSON.stringify(entry).slice(0, 80);

    if (!slug) {
        fail(`entry ${label} has no slug`);
        continue;
    }
    if (!SLUG_SHAPE.test(slug)) fail(`slug "${slug}" is not a shape the public routing can use`);
    if (seen.has(slug)) fail(`slug "${slug}" appears more than once`);
    seen.add(slug);
    if (!targets.has(slug)) fail(`slug "${slug}" is not in tools/slugs_missing_brakes.txt`);
    if (!front && !rear) fail(`slug "${slug}" carries neither a front nor a rear value`);
    if (!source) fail(`slug "${slug}" has no source`);

    for (const [column, value] of [['front_brake', front], ['rear_brake', rear]]) {
        if (value == null || value === '') continue;
        if (typeof value !== 'string') {
            fail(`slug "${slug}" ${column} is not a string`);
            continue;
        }
        if (value.trim() !== value) fail(`slug "${slug}" ${column} has leading or trailing whitespace`);
        if (countChars(value) > COLUMN_LIMIT) fail(`slug "${slug}" ${column} is ${countChars(value)} characters, over the ${COLUMN_LIMIT} limit`);
        if (!TECHNICAL_TOKEN.test(value)) fail(`slug "${slug}" ${column} carries no diameter, disc/drum type or explicit None: ${JSON.stringify(value)}`);
        if (PROSE_SMELL.test(value.trim())) fail(`slug "${slug}" ${column} reads as marketing prose: ${JSON.stringify(value)}`);
        if (BRAKING_TECH.test(value)) fail(`slug "${slug}" ${column} mentions ABS or CBS, which belongs in abs_type: ${JSON.stringify(value)}`);
        if (DIAMETER.test(value) && source && !isStrong(source)) fail(`slug "${slug}" ${column} states a diameter but no source owns it: ${JSON.stringify(source)}`);
    }

    const absType = entry.abs_type;
    if (absType != null && absType !== '') {
        if (typeof absType !== 'string') fail(`slug "${slug}" abs_type is not a string`);
        else if (countChars(absType) > ABS_COLUMN_LIMIT) fail(`slug "${slug}" abs_type is ${countChars(absType)} characters, over the ${ABS_COLUMN_LIMIT} limit`);
        else if (!BRAKING_TECH.test(absType)) fail(`slug "${slug}" abs_type names no ABS or CBS system: ${JSON.stringify(absType)}`);
    }
}

if (failures.length > 0) {
    console.error(`validate-brakes: ${failures.length} problem(s) in ${path.relative(REPO_ROOT, JSON_PATH)}`);
    failures.slice(0, 40).forEach((f) => console.error(`  - ${f}`));
    if (failures.length > 40) console.error(`  ... and ${failures.length - 40} more`);
    process.exit(1);
}

if (QUIET) process.exit(0);

// --- coverage report -----------------------------------------------------------------------
// Everything below is derived, never typed, so the numbers cannot go stale against the data.
const brandOf = (slug) => slug.split('-')[0];
const nameplateOf = (slug) => slug.replace(/-\d{4}$/, '');

const brands = [...new Set(research.map((r) => brandOf(r.slug)))].sort();
const byNameplate = new Map();
for (const r of research) {
    const key = nameplateOf(r.slug);
    if (!byNameplate.has(key)) byNameplate.set(key, []);
    byNameplate.get(key).push(r);
}

// Nameplates whose value actually changes across model years: the evidence that research was split
// by generation rather than one figure stretched over a nameplate's whole life.
// abs_type counts here too: on Brazilian commuters the hardware often stays put and the model year
// change is precisely the arrival of CBS, so ignoring it would understate the research granularity.
const splitNameplates = [...byNameplate.entries()]
    .filter(([, rs]) => rs.length > 1
        && (new Set(rs.map((r) => r.front_brake)).size > 1 || new Set(rs.map((r) => r.rear_brake)).size > 1 || new Set(rs.map((r) => r.abs_type)).size > 1))
    .map(([name]) => name);

const multiYear = [...byNameplate.values()].filter((rs) => rs.length > 1).length;
const domains = [...new Set(research.flatMap((r) => (r.source.match(/[a-z0-9.-]+\.[a-z]{2,}/gi) || []).map((d) => d.toLowerCase())))].sort();
const frontCount = research.filter((r) => r.front_brake).length;
const rearCount = research.filter((r) => r.rear_brake).length;
const targetsForBrands = [...targets].filter((s) => brands.includes(brandOf(s))).length;

console.log(`validate-brakes: ${path.relative(REPO_ROOT, JSON_PATH)} is valid`);
console.log(`  entries      ${research.length} / ${targets.size} catalogue slugs (${(100 * research.length / targets.size).toFixed(1)}%)`);
console.log(`  columns      ${frontCount} front, ${rearCount} rear, ${research.filter((r) => r.abs_type).length} abs_type`);
console.log(`  brands       ${brands.length}: ${brands.join(', ')}`);
console.log(`  in-scope     ${research.length} / ${targetsForBrands} slugs of the brands covered (${(100 * research.length / targetsForBrands).toFixed(1)}%)`);
console.log(`  nameplates   ${byNameplate.size}, of which ${multiYear} span more than one year`);
console.log(`  year splits  ${splitNameplates.length} of those ${multiYear} carry more than one distinct value`);
console.log(`  sources      ${domains.length} distinct domains: ${domains.join(', ')}`);
