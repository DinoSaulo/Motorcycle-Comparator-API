#!/usr/bin/env node
// Level-2 safety net for the description backfill: derives a short factual sentence or two for
// EVERY row in tools/motorcycles_missing_description.json straight from the fields already present
// on that row (brand, model, year, category, engine, dimensions/weight) - no internet, no invented
// facts. Writes tools/description-derived.json, one entry per input slug, every run.
// Usage: node tools/generate-description-from-specs.mjs

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const INPUT_PATH = path.join(REPO_ROOT, 'tools/motorcycles_missing_description.json');
const OUTPUT_PATH = path.join(REPO_ROOT, 'tools/description-derived.json');

const die = (message) => {
    console.error(`generate-description-from-specs: ${message}`);
    process.exit(1);
};

const source = JSON.parse(fs.readFileSync(INPUT_PATH, 'utf8'));
if (!Array.isArray(source)) die('motorcycles_missing_description.json must be a flat array');

// --- category vocabulary --------------------------------------------------------------------
// The 9 category values are a closed enum in the DB; ELECTRIC gets its own phrasing (motor, not
// engine) and everything unrecognised falls back to the generic noun rather than guessing.
const CATEGORY_PHRASES = {
    OFF_ROAD: 'off-road motorcycle',
    SCOOTER: 'scooter',
    NAKED: 'naked motorcycle',
    ADVENTURE: 'adventure motorcycle',
    SUPERMOTO: 'supermoto motorcycle',
    TOURING: 'touring motorcycle',
    SPORT: 'sport motorcycle',
    CRUISER: 'cruiser motorcycle',
    ELECTRIC: 'electric motorcycle',
};

const CYLINDER_WORDS = {
    1: 'single-cylinder', 2: 'twin-cylinder', 3: 'three-cylinder',
    4: 'four-cylinder', 5: 'five-cylinder', 6: 'six-cylinder',
};

// --- deterministic phrasing variation --------------------------------------------------------
// Same slug always picks the same template, so re-runs are reproducible, but the 11k descriptions
// aren't all identically worded.
function hashCode(str) {
    let h = 0;
    for (let i = 0; i < str.length; i++) h = (Math.imul(h, 31) + str.charCodeAt(i)) >>> 0;
    return h;
}

// Every CATEGORY_PHRASES value is known up front, so the article is picked once per phrase rather
// than with a generic (and unreliable) vowel-sound heuristic. Only needed where the phrase directly
// follows the article - "a 2011 off-road motorcycle" (year in between) never needs "an".
function withArticle(cat) {
    return /^(off-road|adventure|electric)\b/.test(cat) ? `an ${cat}` : `a ${cat}`;
}

const IDENTITY_TEMPLATES = [
    (brand, model, year, cat) => `The ${brand} ${model} is a ${year} ${cat}.`,
    (brand, model, year, cat) => `The ${year} ${brand} ${model} is ${withArticle(cat)}.`,
    (brand, model, year, cat) => `For ${year}, the ${brand} ${model} is classified as ${withArticle(cat)}.`,
];
const ENGINE_VERBS = ['is powered by', 'is fitted with', 'uses'];

// --- field normalisation ----------------------------------------------------------------------
// The upstream scrape is inconsistent (casing, language, typos - see scraper-source-defects in
// memory), so cooling_system and engine_type are read by keyword, never by exact string match.
function normalizeCooling(raw) {
    if (!raw) return null;
    const s = raw.toLowerCase();
    const hasLiquid = /liquid|l[ií]quid/.test(s);
    const hasAir = /\bair\b|\bar\b/.test(s);
    const hasOil = /oil|[oó]leo/.test(s);
    if (hasAir && hasOil) return 'air/oil-cooled';
    if (hasLiquid) return 'liquid-cooled';
    if (hasAir) return 'air-cooled';
    if (hasOil) return 'oil-cooled';
    return null;
}

function cylinderWord(n, engineTypeRaw) {
    if (n === 2 && engineTypeRaw && /^V,/.test(engineTypeRaw)) return 'V-twin';
    if (n === 2 && engineTypeRaw && /^Boxer/i.test(engineTypeRaw)) return 'boxer-twin';
    if (n === 4 && engineTypeRaw && /^V,/.test(engineTypeRaw)) return 'V4';
    return CYLINDER_WORDS[n] || `${n}-cylinder`;
}

function strokeType(engineTypeRaw) {
    if (!engineTypeRaw) return null;
    if (/2-Stroke/i.test(engineTypeRaw)) return 'two-stroke';
    if (/4-Stroke/i.test(engineTypeRaw)) return 'four-stroke';
    return null;
}

// Trims a float to at most 2 decimals and drops a trailing ".00"/".50" -> "50" style zero tail.
function fmtNum(n) {
    if (typeof n !== 'number' || !Number.isFinite(n)) return null;
    return n.toFixed(2).replace(/\.?0+$/, '');
}

// --- sentence builders -------------------------------------------------------------------------
function buildEngineDescriptor(rec, isElectric) {
    const parts = [];
    const disp = typeof rec.displacement_cc === 'number' ? `${fmtNum(rec.displacement_cc)}cc` : null;
    const cooling = normalizeCooling(rec.cooling_system);
    const cyl = !isElectric && typeof rec.cylinders === 'number' ? cylinderWord(rec.cylinders, rec.engine_type) : null;
    const stroke = !isElectric ? strokeType(rec.engine_type) : null;
    if (disp) parts.push(disp);
    if (cooling) parts.push(cooling);
    if (cyl) parts.push(cyl);
    if (stroke) parts.push(stroke);
    return parts.length ? parts.join(' ') : null;
}

function buildOutputClause(rec) {
    const powerHp = typeof rec.max_power_hp === 'number' ? fmtNum(rec.max_power_hp) : null;
    const powerRpm = typeof rec.max_power_rpm === 'number' ? fmtNum(rec.max_power_rpm) : null;
    const torqueNm = typeof rec.max_torque_nm === 'number' ? fmtNum(rec.max_torque_nm) : null;
    const torqueRpm = typeof rec.max_torque_rpm === 'number' ? fmtNum(rec.max_torque_rpm) : null;
    const powerStr = powerHp ? `${powerHp} hp${powerRpm ? ` at ${powerRpm} rpm` : ''}` : null;
    const torqueStr = torqueNm ? `${torqueNm} Nm${torqueRpm ? ` at ${torqueRpm} rpm` : ''}` : null;
    if (powerStr && torqueStr) return `${powerStr} and ${torqueStr} of torque`;
    return powerStr || (torqueStr ? `${torqueStr} of torque` : null);
}

function buildEngineSentence(rec, hash, isElectric) {
    const descriptor = buildEngineDescriptor(rec, isElectric);
    const output = buildOutputClause(rec);
    if (!descriptor && !output) return null;
    const unit = isElectric ? 'electric motor' : 'engine';
    const verb = ENGINE_VERBS[hash % ENGINE_VERBS.length];
    // descriptor usually opens with a displacement figure ("125cc ..."), but a bare cooling word
    // (air-cooled/oil-cooled, no known displacement) needs "an", not "a".
    const article = /^(air-cooled|oil-cooled|air\/oil-cooled)\b/.test(descriptor || '') ? 'an' : 'a';
    if (descriptor && output) return `It ${verb} ${article} ${descriptor} ${unit}, producing ${output}.`;
    if (descriptor) return `It ${verb} ${article} ${descriptor} ${unit}.`;
    return `The ${unit} produces ${output}.`;
}

// Weight/seat height/fuel capacity only - the "dimensões/peso" the task scopes this to. kerb weight
// is preferred over dry weight when both exist, since it is the more commonly compared figure.
function buildDimensionsSentence(rec) {
    const clauses = [];
    const hasKerb = typeof rec.kerb_weight_kg === 'number';
    const hasDry = typeof rec.dry_weight_kg === 'number';
    if (hasKerb) clauses.push(`a kerb weight of ${fmtNum(rec.kerb_weight_kg)} kg`);
    else if (hasDry) clauses.push(`a dry weight of ${fmtNum(rec.dry_weight_kg)} kg`);
    if (typeof rec.seat_height_mm === 'number') clauses.push(`a seat height of ${fmtNum(rec.seat_height_mm)} mm`);
    if (typeof rec.fuel_capacity_l === 'number') clauses.push(`a ${fmtNum(rec.fuel_capacity_l)} L fuel tank`);
    if (!clauses.length) return null;
    let joined;
    if (clauses.length === 1) joined = clauses[0];
    else if (clauses.length === 2) joined = `${clauses[0]} and ${clauses[1]}`;
    else joined = `${clauses.slice(0, -1).join(', ')}, and ${clauses[clauses.length - 1]}`;
    return `It has ${joined}.`;
}

function buildDescription(rec) {
    // Some catalogue rows carry stray double spaces in brand/model (e.g. a trailing "(UTV)" tag);
    // collapsed here since it's whitespace, not a fact, and left alone everywhere else.
    const brand = rec.brand.trim().replace(/\s+/g, ' ');
    const model = rec.model.trim().replace(/\s+/g, ' ');
    const catPhrase = CATEGORY_PHRASES[rec.category] || 'motorcycle';
    const isElectric = rec.category === 'ELECTRIC';
    const hash = hashCode(rec.slug);
    const identity = IDENTITY_TEMPLATES[hash % IDENTITY_TEMPLATES.length](brand, model, rec.year, catPhrase);
    const engineSentence = buildEngineSentence(rec, hash, isElectric);
    const dimensionsSentence = buildDimensionsSentence(rec);
    return [identity, engineSentence, dimensionsSentence].filter(Boolean).join(' ');
}

// --- run -----------------------------------------------------------------------------------
const seenSlugs = new Set();
const derived = source.map((rec) => {
    if (!rec.slug || !rec.brand || !rec.model || !rec.year || !rec.category) {
        die(`slug "${rec.slug}" is missing a core field (brand/model/year/category)`);
    }
    if (seenSlugs.has(rec.slug)) die(`slug "${rec.slug}" appears more than once in the input`);
    seenSlugs.add(rec.slug);
    const description = buildDescription(rec);
    if (!description) die(`slug "${rec.slug}" produced an empty description`);
    if (description.length > 2000) die(`slug "${rec.slug}" produced a description over 2000 chars`);
    return { slug: rec.slug, description, source: 'derived-from-specs' };
});

if (derived.length !== source.length) die(`expected ${source.length} entries, built ${derived.length}`);

fs.writeFileSync(OUTPUT_PATH, JSON.stringify(derived, null, 2) + '\n', 'utf8');
console.log(`generate-description-from-specs: wrote ${derived.length} entries to ${path.relative(REPO_ROOT, OUTPUT_PATH)}`);
