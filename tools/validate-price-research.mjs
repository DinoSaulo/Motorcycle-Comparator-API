#!/usr/bin/env node
// Validates a price research file (or one research shard) against tools/motorcycles-price-null-slugs.txt.
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

const JSON_PATH = path.resolve(option('--file', path.join(REPO_ROOT, 'tools/price-research.json')));
const SLUG_LIST = path.join(REPO_ROOT, 'tools/motorcycles-price-null-slugs.txt');

const SLUG_SHAPE = /^[a-z0-9]+(-[a-z0-9]+)*$/;
// NUMERIC(10,2) with ck_motorcycles_price_eur (V1__initial_schema.sql:90,116) allows far more, but a
// list price outside this band is a parse error, not a motorcycle.
const PRICE_MIN = 300;
const PRICE_MAX = 999999.99;
// European tariffs print EUR 4.199,00. Reading that as 4.199 passes every other check, so the floor
// above is the guard that actually catches a mis-parsed thousands separator.
const OLDEST_SUPPORTED_YEAR = 2016;

const targets = new Set(fs.readFileSync(SLUG_LIST, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean));

const research = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
const failures = [];
const warnings = [];
const fail = (message) => failures.push(message);
const warn = (message) => warnings.push(message);

if (!Array.isArray(research)) {
    console.error('validate-price: research file must be a flat array of {slug, price_eur, source}');
    process.exit(1);
}

// Second-hand listing sites reprint whatever a seller typed. A used asking price is not an MSRP and
// must never reach a column the catalogue sorts and range-filters on.
const CLASSIFIEDS = [
    /\bolx\.[a-z.]+/i, /\bebay\.[a-z.]+/i, /\bvinted\.[a-z.]+/i, /\bleboncoin\.fr\b/i, /\bmobile\.de\b/i,
    /\bautoscout24\.[a-z.]+/i, /\bmarktplaats\.nl\b/i, /\bleparking-moto\.[a-z.]+/i, /\bstandvirtual\.com\b/i,
    /\bwallapop\.com\b/i, /\bmilanuncios\.com\b/i, /\bcustojusto\.pt\b/i, /\bbikeexchange\.[a-z.]+/i,
];

// Only these own a price figure: the manufacturer, its national importer, or a title that prints the
// published tariff. Anything else reprints a number it cannot stand behind (memory: brake diameters).
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
    // National importers that publish their own tariff, found in round 5. Same standing as a
    // manufacturer site: the importer sets the price it prints.
    /\bqjmotoritaly\.com\b/i, /\bswm-motorcycles\.[a-z.]+/i, /\bvogeitaly\.it\b/i,
];

// Specialist press and listino aggregators reprint a published tariff without owning it. They are
// accepted here by explicit decision to reach volume, but counted separately so the mix stays visible.
const AGGREGATOR_SOURCES = [
    /\binsella\.it\b/i, /\bmotorbox\.com\b/i, /\bdueruote\.it\b/i, /\binmoto\.it\b/i, /\bmoto\.it\b/i,
    /\bomnimoto\.it\b/i, /\bmotoplanete\.com\b/i, /\bmoto-station\.com\b/i, /\bmotoblouz\.com\b/i,
    /\bmotofichas\.com\b/i, /\bmotorbikemag\.es\b/i,
];

// A tariff that says "from" prices a trim this row is not. The spec rejects the figure outright, so
// the marker has to be caught in the one free-text field an entry carries.
const AMBIGUOUS = /(\bfrom\b|\ba partir\b|\ba partire da\b|\bab \d|\bdesde\b|\bentre\b|\bsob consulta\b|\bon request\b|\bapprox|~)/i;
const DISCONTINUED = /\bdiscontinued\s+\d{4}\b/i;

const isClassifieds = (source) => CLASSIFIEDS.some((re) => re.test(source));
const isStrong = (source) => STRONG_SOURCES.some((re) => re.test(source));
const isAggregator = (source) => AGGREGATOR_SOURCES.some((re) => re.test(source));
const yearOf = (slug) => Number((slug.match(/-(\d{4})$/) || [])[1]);
const tiers = { manufacturer: 0, aggregator: 0, unrecognised: 0 };

const seen = new Set();
for (const entry of research) {
    const { slug, price_eur: price, source } = entry;
    const label = slug || JSON.stringify(entry).slice(0, 80);

    if (!slug) {
        fail(`entry ${label} has no slug`);
        continue;
    }
    if (!SLUG_SHAPE.test(slug)) fail(`slug "${slug}" is not a shape the public routing can use`);
    if (seen.has(slug)) fail(`slug "${slug}" appears more than once`);
    seen.add(slug);
    if (!targets.has(slug)) fail(`slug "${slug}" is not in tools/motorcycles-price-null-slugs.txt`);

    if (typeof price !== 'number' || !Number.isFinite(price)) {
        fail(`slug "${slug}" price_eur is not a number: ${JSON.stringify(price)}`);
    } else {
        if (price < PRICE_MIN) fail(`slug "${slug}" price_eur ${price} is below ${PRICE_MIN}, which reads as a mis-parsed thousands separator`);
        if (price > PRICE_MAX) fail(`slug "${slug}" price_eur ${price} is over the ${PRICE_MAX} ceiling`);
        if (Math.round(price * 100) !== price * 100) fail(`slug "${slug}" price_eur ${price} has more precision than NUMERIC(10,2) stores`);
    }

    if (!source || typeof source !== 'string' || !source.trim()) {
        fail(`slug "${slug}" has no source`);
        continue;
    }
    if (source.trim() !== source) fail(`slug "${slug}" source has leading or trailing whitespace`);
    if (isClassifieds(source)) fail(`slug "${slug}" cites a second-hand listing site: ${JSON.stringify(source)}`);
    if (AMBIGUOUS.test(source)) fail(`slug "${slug}" source records an open-ended or approximate figure: ${JSON.stringify(source)}`);

    // largus.fr carries forward the last recorded tariff under later millesimes. The 'relevé' (tariff date)
    // must be within the model year or the year before, otherwise the price is stale. Extract both and check the gap.
    if (/largus\.fr/i.test(source)) {
        const millesime = yearOf(slug);
        const releveMatch = source.match(/millesime\s+(\d{4}).*relevé\s+\d{2}\/\d{2}\/(\d{4})/i);
        if (releveMatch) {
            const releveYear = Number(releveMatch[2]);
            const gap = Number(releveMatch[1]) - releveYear;
            if (gap > 1) {
                fail(`slug "${slug}" cites largus.fr with a tariff dated ${releveYear} (${gap} years before millesime ${releveMatch[1]}), which likely carries a stale price`);
            }
        }
    }

    if (isStrong(source)) tiers.manufacturer++;
    else if (isAggregator(source)) tiers.aggregator++;
    else {
        tiers.unrecognised++;
        warn(`slug "${slug}" cites a source in neither tier: ${JSON.stringify(source)}`);
    }

    const year = yearOf(slug);
    if (year && year < OLDEST_SUPPORTED_YEAR && !DISCONTINUED.test(source)) {
        warn(`slug "${slug}" is a ${year} model with no "(discontinued YYYY)" marker on its source`);
    }
}

if (failures.length > 0) {
    console.error(`validate-price: ${failures.length} problem(s) in ${path.relative(REPO_ROOT, JSON_PATH)}`);
    failures.slice(0, 40).forEach((f) => console.error(`  - ${f}`));
    if (failures.length > 40) console.error(`  ... and ${failures.length - 40} more`);
    process.exit(1);
}

if (QUIET) process.exit(0);

// --- coverage report -----------------------------------------------------------------------
// Everything below is derived, never typed, so the numbers cannot go stale against the data.
const brandOf = (slug) => slug.split('-')[0];

const brands = [...new Set(research.map((r) => brandOf(r.slug)))].sort();
const domains = [...new Set(research.flatMap((r) => (r.source.match(/[a-z0-9.-]+\.[a-z]{2,}/gi) || []).map((d) => d.toLowerCase())))].sort();
const targetsForBrands = [...targets].filter((s) => brands.includes(brandOf(s))).length;
const discontinued = research.filter((r) => DISCONTINUED.test(r.source)).length;
const prices = research.map((r) => r.price_eur).sort((a, b) => a - b);
const median = prices.length ? prices[Math.floor(prices.length / 2)] : 0;

console.log(`validate-price: ${path.relative(REPO_ROOT, JSON_PATH)} is valid`);
console.log(`  entries      ${research.length} / ${targets.size} catalogue slugs (${(100 * research.length / targets.size).toFixed(1)}%)`);
console.log(`  brands       ${brands.length}: ${brands.join(', ')}`);
console.log(`  in-scope     ${research.length} / ${targetsForBrands} slugs of the brands covered (${(100 * research.length / targetsForBrands).toFixed(1)}%)`);
console.log(`  price band   EUR ${prices[0] ?? 0} to ${prices[prices.length - 1] ?? 0}, median ${median}`);
console.log(`  evidence     ${tiers.manufacturer} manufacturer/importer, ${tiers.aggregator} listino aggregator, ${tiers.unrecognised} unrecognised`);
console.log(`  discontinued ${discontinued} entries carry a "(discontinued YYYY)" marker`);
console.log(`  sources      ${domains.length} distinct domains: ${domains.join(', ')}`);
if (warnings.length > 0) {
    console.log(`  warnings     ${warnings.length}`);
    warnings.slice(0, 10).forEach((w) => console.log(`    - ${w}`));
    if (warnings.length > 10) console.log(`    ... and ${warnings.length - 10} more`);
}
