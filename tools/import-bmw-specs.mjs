#!/usr/bin/env node
// Regenerates src/main/resources/db/seed/R__motorcycles_specs_bmw_2026_08.sql from the
// zontes-scraper BMW snapshot, and materialises the images that seed points at.
//
// Two outputs, one run, because they have to agree: the SQL stores an image URL whose file name
// is derived from the motorcycle slug, and the copy step writes exactly those names. Re-running
// on another machine reproduces both byte-for-byte.
//
//   node tools/import-bmw-specs.mjs [--source <dir>] [--sql-only] [--images-only]
//
// --source defaults to ../zontes-scraper relative to the repository root.
//
// Nothing here estimates. A figure the sources never published stays NULL, and a parsed figure
// outside the plausible range for its column is dropped rather than stored.

import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const option = (name, fallback) => {
    const i = args.indexOf(name);
    return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};

const SOURCE_DIR = path.resolve(option('--source', path.join(REPO_ROOT, '..', 'zontes-scraper')));
const JSON_PATH = path.join(SOURCE_DIR, 'bmw_motos.json');
// Named "specs_bmw" and not "bmw_specs" like its siblings, and the difference is load-bearing.
// Flyway orders repeatable migrations by description (MigrationInfoImpl.compareTo: "Two repeatable
// migrations: sort by description"), so a file called R__motorcycles_bmw_specs_* would run BEFORE
// R__motorcycles_brazil_fipe_* - "bmw" sorts before "brazil". Every other brand import gets the
// right order by luck, because h/r/y all sort after b. This one has to be spelled differently to
// earn it. See the generated header for what running first would have cost.
const SQL_PATH = path.join(REPO_ROOT, 'src/main/resources/db/seed/R__motorcycles_specs_bmw_2026_08.sql');
const IMAGE_DIR = path.join(REPO_ROOT, 'uploads/motorcycles');
const IMAGE_URL_PREFIX = '/api/v1/images/motorcycles/';

const doSql = !flag('--images-only');
const doImages = !flag('--sql-only');

// --- number parsing -----------------------------------------------------------------------
// The sources mix Portuguese and English conventions in the same file ("17,5 kgf.m" next to
// "107 Nm", "7.750 RPM" next to "7500 rpm"), so a separator is read from its own shape:
// exactly three trailing digits means a thousands group, anything else means a decimal point.
function toNumber(token) {
    if (token == null) return null;
    let t = String(token).trim();
    if (!t) return null;
    if (/^\d{1,3}([.,]\d{3})+$/.test(t)) return Number(t.replace(/[.,]/g, ''));
    const dot = t.lastIndexOf('.');
    const comma = t.lastIndexOf(',');
    if (dot >= 0 && comma >= 0) {
        const dec = Math.max(dot, comma);
        t = t.slice(0, dec).replace(/[.,]/g, '') + '.' + t.slice(dec + 1).replace(/[.,]/g, '');
    } else if (dot >= 0 || comma >= 0) {
        const dec = Math.max(dot, comma);
        const after = t.length - dec - 1;
        t = after === 3 ? t.replace(/[.,]/g, '') : t.slice(0, dec) + '.' + t.slice(dec + 1);
    }
    const n = Number(t);
    return Number.isFinite(n) ? n : null;
}

// A source string that spaces out its digits is garbled, not SI-formatted. The repair below only
// joins a digit to a following group of exactly three, and anything still spaced is refused by
// NUMBER_START rather than read as its first fragment — a plausibility bound cannot catch a wrong
// figure that happens to land inside the range.
const MALFORMED = { count: 0 };
const repairDigitGroups = (text) => String(text).replace(/(\d) (\d{3})(?!\d)/g, '$1$2');

// "810 - 830 mm" is an adjustable measurement published as its range. Collapse it to the low end:
// that is the figure the bike stands at as delivered. Measurements only, never power or rpm.
const collapseRanges = (text) => text.replace(/(\d[\d.,]*)\s*[-–—]\s*\d[\d.,]*(\s*)(?=[a-z°])/gi, '$1$2');

/** A number may not begin immediately after a digit, or after a digit and a space. */
const NUMBER_START = '(?<!\\d)(?<!\\d\\s)';

function countMalformed(text) {
    if (/\d\s+\d/.test(text)) MALFORMED.count++;
    return null;
}

const MM_PER_INCH = 25.4;

/**
 * First number carrying one of the given units. These are metric sheets, so `inches` is a last
 * resort that fires only where the metric figure is unreadable — in this snapshot that is the one
 * row whose length reads "2324 m / 91.5 in", with the unit itself mistyped. Metric always wins
 * where both are printed, so "2212 mm / 87.1 in" reads 2212 and never 2212.2. `bare` allows a
 * unitless value as a last resort.
 */
function measure(rawText, unitPattern, { bare = false, inches = false } = {}) {
    if (!rawText) return null;
    const text = collapseRanges(repairDigitGroups(rawText));
    const withUnit = new RegExp(`${NUMBER_START}(\\d[\\d.,]*)\\s*(?:${unitPattern})`, 'i').exec(text);
    if (withUnit) return toNumber(withUnit[1]);
    if (inches) {
        const imperial = new RegExp(`${NUMBER_START}(\\d[\\d.,]*)\\s*(?:in\\b|inch|")`, 'i').exec(text);
        const value = imperial ? toNumber(imperial[1]) : null;
        if (value != null) {
            CONVERTED.count++;
            return value * MM_PER_INCH;
        }
    }
    if (!bare) return countMalformed(text);
    // The bare read has to refuse a garbled string too. NUMBER_START stops the unit-anchored read
    // from taking a fragment, but without this the unitless fallback would walk straight past that
    // guard and take the first one: "11 72 cc" is 1172 cc, and reading it as 11 is worse than
    // reading nothing, because 11 is a number a plausibility bound might well have let through.
    if (/\d\s+\d/.test(text)) {
        MALFORMED.count++;
        return null;
    }
    const any = new RegExp(`${NUMBER_START}(\\d[\\d.,]*)`).exec(text);
    return any ? toNumber(any[1]) : countMalformed(text);
}

const CC_PER_CUBIC_INCH = 16.387064;
const CUBIC_INCHES = { count: 0 };

/**
 * Displacement in cc, from the imperial figure where the metric one is unreadable. 10 rows here
 * publish "11 72 cc / 71.5 cu in": the extractor has split 1172 into two fragments that no repair
 * can safely rejoin, but the cubic-inch figure beside it is intact and independent of that split,
 * so it is converted rather than leaving the whole row's displacement NULL. Same principle as the
 * inch fallback on the dimension fields - a published figure in the other unit, never an estimate.
 */
function displacementOf(text) {
    const cc = measure(text, 'cc|cm³|cm3|ccm', { bare: true });
    if (cc != null) return cc;
    const cubic = unit(text, 'cub?\\.?\\s*in\\b|cubic\\s*inch(?:es)?');
    if (cubic == null) return null;
    CUBIC_INCHES.count++;
    return cubic * CC_PER_CUBIC_INCH;
}

const round = (n, dp) => (n == null ? null : Math.round(n * 10 ** dp) / 10 ** dp);

/** Out-of-range means the source string was misread or the source itself is wrong; either way, drop it. */
function bounded(value, min, max, dropped, label) {
    if (value == null) return null;
    if (value < min || value > max) {
        dropped[label] = (dropped[label] || 0) + 1;
        return null;
    }
    return value;
}

/** Same unit-anchored read as `measure`, kept in one place so every parser shares the digit guards. */
const unit = (rawText, unitPattern) => {
    if (!rawText) return null;
    const text = repairDigitGroups(rawText);
    const m = new RegExp(`${NUMBER_START}(\\d[\\d.,]*)\\s*(?:${unitPattern})`, 'i').exec(text);
    return m ? toNumber(m[1]) : null;
};

function rpmOf(text, dropped, label) {
    const value = unit(text, 'rpm');
    return value == null ? null : bounded(Math.round(value), 500, 20000, dropped, label);
}

// --- text cleaning ------------------------------------------------------------------------
const TRUNCATED = { count: 0 };
const REPAIRED = { count: 0 };
const CONVERTED = { count: 0 };

/**
 * The extractor that produced this snapshot occasionally emits a word with a space after its first
 * letter. Only the four forms it actually damages are repaired, and each is repaired to a word that
 * exists: a blanket "single letter followed by a word" rule would corrupt the legitimate text this
 * snapshot also carries — "BMS-K with overrun fuel cut off", "Optional M forged aluminum wheels",
 * "by mean of a hydraulic handwheel" are all correct as printed.
 */
const SPLIT_WORDS = [
    [/\bF our\b/g, 'Four'],
    [/\bE lectric\b/g, 'Electric'],
    [/\bt hrottle\b/g, 'throttle'],
    [/\bC hill\b/g, 'Chill'],
];

/** Collapses whitespace, drops trailing punctuation the scraper carried over from the source table. */
function clean(text, maxLength) {
    if (text == null) return null;
    const before = String(text);
    let t = before;
    for (const [pattern, replacement] of SPLIT_WORDS) t = t.replace(pattern, replacement);
    if (t !== before) REPAIRED.count++;
    t = t.replace(/\s+/g, ' ').trim().replace(/[,;]+$/, '');
    if (!t) return null;
    if (t.length > maxLength) {
        const cut = t.lastIndexOf(' ', maxLength);
        t = (cut > maxLength * 0.6 ? t.slice(0, cut) : t.slice(0, maxLength)).replace(/[\s,;.-]+$/, '');
        TRUNCATED.count++;
    }
    return t || null;
}

// --- per-field extraction -----------------------------------------------------------------
const WORD_CYLINDERS = { single: 1, mono: 1, one: 1, two: 2, twin: 2, three: 3, triple: 3, four: 4, six: 6 };
const PT_CYLINDERS = { um: 1, uma: 1, dois: 2, duas: 2, três: 3, tres: 3, quatro: 4, seis: 6 };

/** Returns the count the prose states and the exact phrase it was read from, so it can be rewritten. */
function cylindersOf(engineText, explicit) {
    if (explicit != null) return { count: explicit, phrase: null };
    if (!engineText) return { count: null, phrase: null };
    // Adjacency to "cylinder"/"cilindro" is required. "Four stroke, two cylinder horizontally
    // opposed Boxer" is a twin: the "Four" belongs to the stroke count and must not be read, and
    // the 4 in "4 valves per cylinder" must not be read either.
    const en = /\b(single|mono|one|two|twin|three|triple|four|six|\d)[\s-]*cylinders?\b/i.exec(engineText);
    if (en) return { count: WORD_CYLINDERS[en[1].toLowerCase()] ?? (Number(en[1]) || null), phrase: en[0] };
    const pt = /\b(um|uma|dois|duas|tr[êe]s|quatro|seis|\d)\s*cilindros?\b/i.exec(engineText);
    if (pt) return { count: PT_CYLINDERS[pt[1].toLowerCase()] ?? (Number(pt[1]) || null), phrase: pt[0] };
    const mono = /\bmonocil\w*/i.exec(engineText);
    if (mono) return { count: 1, phrase: mono[0] };
    const bi = /\bbicil\w*/i.exec(engineText);
    if (bi) return { count: 2, phrase: bi[0] };
    // A Boxer is a flat twin and nothing else, so it names the count without ever writing it.
    const boxer = /\bboxer\b/i.exec(engineText);
    if (boxer) return { count: 2, phrase: boxer[0] };
    const v = /\bV[\s-]?(\d)\b/i.exec(engineText);
    if (v) return { count: Number(v[1]), phrase: v[0] };
    const twin = /\btwin\b/i.exec(engineText);
    return twin ? { count: 2, phrase: twin[0] } : { count: null, phrase: null };
}

// Bore, stroke and displacement are printed on the same sheet, so their product decides the cylinder
// count whenever the prose on that sheet disagrees with it. This range spans singles, flat twins,
// parallel twins, inline fours and the K 1600's inline six, so the arithmetic is the only check
// that holds across all of them.
const CORRECTED = { count: 0 };

function cylindersFromSweptVolume(displacementCc, boreMm, strokeMm) {
    if (!displacementCc || !boreMm || !strokeMm) return null;
    const perCylinder = (Math.PI / 4) * boreMm * boreMm * strokeMm / 1000;
    if (!(perCylinder > 0)) return null;
    const exact = displacementCc / perCylinder;
    const rounded = Math.round(exact);
    if (rounded < 1 || rounded > 8) return null;
    // 6% absorbs a rounded bore or stroke without ever letting 1.5 cylinders round into a verdict.
    return Math.abs(exact - rounded) / rounded <= 0.06 ? rounded : null;
}

const LAYOUT_NAME = { 1: 'Single cylinder', 2: 'Twin cylinder', 3: 'Three cylinder', 4: 'Four cylinder', 6: 'Six cylinder' };

/**
 * Rewrites the layout phrase the cylinder parser read, and only that phrase, so a description whose
 * count the swept volume has just overruled does not go on contradicting the column beside it.
 */
function relabelLayout(engineText, phrase, count) {
    const name = LAYOUT_NAME[count];
    if (!engineText || !name || !phrase) return engineText;
    const at = engineText.indexOf(phrase);
    if (at < 0) return engineText;
    const before = engineText.slice(0, at).replace(/\b(?:parallel|inline|in[\s-]?line|transverse|horizontally opposed|v)[\s-]*$/i, '');
    return before + name + engineText.slice(at + phrase.length);
}

function valvesPerCylinderOf(engineText, cylinders) {
    if (!engineText) return null;
    const perCylinder = /(\d+)\s*(?:valves?\s*per\s*cylinder|válvulas?\s*por\s*cilindro)/i.exec(engineText);
    if (perCylinder) return Number(perCylinder[1]);
    const total = /(\d+)\s*(?:valves?|válvulas?)\b/i.exec(engineText);
    if (total && cylinders) {
        const n = Number(total[1]);
        if (n % cylinders === 0) return n / cylinders;
    }
    return null;
}

function powerOf(text, dropped) {
    if (!text) return [null, null];
    const rpm = rpmOf(text, dropped, 'max_power_rpm');
    // hp/bhp/cv/PS are all read as horsepower, matching how the sources use them interchangeably.
    // Where a row publishes both ("72.9 kW / 100 hp @ 7500 rpm") the horsepower figure is the
    // published one and the kW conversion below never runs. The crank figure is always printed
    // first, so the parenthesised "(70.8 kW 94.9 hp @ 7400 rpm at rear wheel)" some rows append
    // is never the one read.
    const hp = unit(text, 'hp|bhp|cv|ps\\b');
    if (hp != null) return [bounded(round(hp, 1), 0.5, 350, dropped, 'max_power_hp'), rpm];
    const kw = unit(text, 'kw\\b');
    if (kw != null) return [bounded(round(kw * 1.34102, 1), 0.5, 350, dropped, 'max_power_hp'), rpm];
    return [null, rpm];
}

function torqueOf(text, dropped) {
    if (!text) return [null, null];
    const rpm = rpmOf(text, dropped, 'max_torque_rpm');
    // Nm wins wherever the source printed it, however far along the string it sits, so
    // "107 Nm / 10.9 kgf-m / 78.9 ft lb @ 5500 rpm" never falls through to the pound-feet figure.
    const nm = unit(text, 'n[\\s.-]?m\\b');
    if (nm != null) return [bounded(round(nm, 1), 1, 300, dropped, 'max_torque_nm'), rpm];
    const kgf = unit(text, 'kgf?[\\s.·-]?m\\b');
    if (kgf != null) return [bounded(round(kgf * 9.80665, 1), 1, 300, dropped, 'max_torque_nm'), rpm];
    const lbft = unit(text, 'lb[\\s.-]?ft|ft[\\s.-]?lb');
    if (lbft != null) return [bounded(round(lbft * 1.35582, 1), 1, 300, dropped, 'max_torque_nm'), rpm];
    return [null, rpm];
}

function topSpeedOf(text, dropped) {
    if (!text) return null;
    const kph = unit(text, 'km\\s*/?\\s*h');
    if (kph != null) return bounded(Math.round(kph), 20, 400, dropped, 'top_speed_kph');
    const mph = unit(text, 'mph');
    if (mph != null) return bounded(Math.round(mph * 1.60934), 20, 400, dropped, 'top_speed_kph');
    return null;
}

/** l/100km from the explicit l/100km and km/l forms only: a bare "mpg" is ambiguous UK vs US. */
function consumptionOf(extra, dropped) {
    for (const key of ['Consumption Average', 'Consumption average', 'Fuel Consumption', 'Average Consumption']) {
        const raw = extra[key];
        if (!raw) continue;
        const per100 = unit(raw, 'l\\s*/\\s*100');
        if (per100 != null) return bounded(round(per100, 2), 0.5, 30, dropped, 'fuel_consumption_l_100km');
        const kmPerLitre = unit(raw, 'km\\s*/\\s*(?:lit(?:re|ro)?s?|l)\\b');
        if (kmPerLitre != null && kmPerLitre > 0) {
            return bounded(round(100 / kmPerLitre, 2), 0.5, 30, dropped, 'fuel_consumption_l_100km');
        }
    }
    return null;
}

/** The snapshot files the ABS description under two labels depending on which sheet supplied it. */
const absTypeOf = (extra) => extra['ABS'] || extra['ABS System'] || null;

function compressionOf(text) {
    if (!text) return null;
    // "13.3:1", "9,0 : 1" and the typo "11.3;1" all describe the same thing; store one shape.
    const m = /(\d[\d.,]*)\s*[:;.]\s*1\b/.exec(text);
    if (m) {
        const v = toNumber(m[1]);
        if (v != null && v >= 4 && v <= 20) return `${round(v, 1)}:1`;
    }
    return clean(text, 20);
}

/**
 * BMW never prints a bare "Euro 5" here: the standard is named inside a sentence about the catalytic
 * converter ("Closed-loop 3-way catalytic converter, EU5"), and on some rows only under Exhaust.
 * Only the standard itself is stored. A row that describes the converter without naming a standard
 * ("Fully-controlled three-way catalytic converter") yields NULL rather than a truncated sentence:
 * emission_standard is a filter facet, and a device description is not a standard.
 */
function emissionOf(extra) {
    for (const key of ['Emission', 'Emissions', 'Emission control', 'Exhaust management', 'Exhaust']) {
        const raw = extra[key];
        if (!raw) continue;
        const m = /\bEUR?O?[\s-]?(\d)\b/i.exec(String(raw));
        if (m) return `Euro ${m[1]}`;
    }
    return null;
}

// Category is only ever used by the insert path below, which is dead for this snapshot: all 200
// rows already exist in the catalogue and their category is never overwritten. It is derived
// properly anyway, so a re-scrape that adds a model lands it in the right segment rather than in a
// blanket default. The source's own label wins where it published one; otherwise the designation
// decides, in this order, because BMW's suffixes overlap ("GS" before "S", "RR" before "R").
const CATEGORY_BY_SOURCE_LABEL = {
    adventure: 'ADVENTURE', supersport: 'SPORT', sport: 'SPORT', touring: 'TOURING',
    naked: 'NAKED', roadster: 'NAKED', enduro: 'OFF_ROAD', supermoto: 'SUPERMOTO', cruiser: 'CRUISER',
};

const CATEGORY_BY_DESIGNATION = [
    [/\bX\s?Moto\b/i, 'SUPERMOTO'],
    [/\bX\s?(Challenge|Country)\b|\b450\s?X\b/i, 'OFF_ROAD'],
    [/\bGS\b|\bDakar\b|\bSert[ãa]o\b|\bFunduro\b/i, 'ADVENTURE'],
    [/^F 650$/i, 'ADVENTURE'], // the Funduro, sold here under the bare designation
    [/\bRR\b/i, 'SPORT'],
    [/\b(GTL|GT|LT|RT|CL)\b/i, 'TOURING'],
    [/\b(RS|S)\b/i, 'SPORT'],
    [/\bC\b|\bAVANTGARD\b|\bClassic\b|\bIndependent\b/i, 'CRUISER'],
];

function categoryOf(model, label) {
    const fromLabel = CATEGORY_BY_SOURCE_LABEL[String(label || '').trim().toLowerCase()];
    if (fromLabel) return fromLabel;
    const name = String(model || '');
    for (const [pattern, category] of CATEGORY_BY_DESIGNATION) if (pattern.test(name)) return category;
    return 'NAKED';
}

// --- long-tail specs ----------------------------------------------------------------------
// Deliberately a curated subset, and deliberately the same key names the Honda, Yamaha, Royal
// Enfield and Harley-Davidson imports use, so a side-by-side comparison of a BMW and a Honda lines
// its long-tail rows up instead of doubling them. The scraper's own bookkeeping is dropped: it is
// not a rider-facing fact.
const TOP_LEVEL_SPEC_KEYS = [
    'Pressão do Pneu Dianteiro', 'Pressão do Pneu Traseiro', 'Iluminação', 'Painel', 'Marcha Lenta',
    'Sistema de Partida', 'Sistema de Chave de Ignição', 'Modos de Condução', 'Rodas', 'Tomada USB',
    'Bateria de Gel', 'Ajuste do Para-brisas', 'Embreagem',
];

const EXTRA_SPEC_KEYS = {
    'Pressão do Pneu Dianteiro (com garupa)': 'Pressão do Pneu Dianteiro (com garupa)',
    'Pressão do Pneu Traseiro (com garupa)': 'Pressão do Pneu Traseiro (com garupa)',
    'Óleo Recomendado': 'Óleo Recomendado',
    'Capacidade de Óleo': 'Capacidade de Óleo',
    'Engine Oil': 'Capacidade de Óleo',
    'Anos de Fabricação': 'Anos de Fabricação',
    Production: 'Anos de Fabricação',
    Lubrication: 'Lubrificação',
    Battery: 'Bateria',
    'Electrical System — Battery': 'Bateria',
    Alternator: 'Saída do Alternador',
    'Charging System': 'Saída do Alternador',
    'Electrical System — Alternator': 'Saída do Alternador',
    'Spark Plug': 'Vela de Ignição',
    'Spark Plugs': 'Vela de Ignição',
    Exhaust: 'Escape',
    // Trail and Castor are the same measurement under two of the source's names and never appear on
    // the same row (52 rows carry one, 55 the other, 0 both), so they share a key rather than
    // splitting one fact across two rows of the comparison table.
    Trail: 'Trail',
    Castor: 'Trail',
    // Rake and Steering Head Angle are NOT merged. Both are angles, but the source measures rake
    // from the vertical (26.2°) and the steering head angle from the horizontal (66.4°). They never
    // co-occur either, so merging would silently mix two conventions under one label.
    Rake: 'Ângulo de Cáster',
    'Fork Angle': 'Ângulo de Cáster',
    'Steering Head Angle': 'Ângulo da Coluna de Direção',
    'Steering Angle': 'Ângulo de Esterço',
    'Gear Ratio': 'Relações de Engrenagem',
    'Gear Ratios': 'Relações de Engrenagem',
    'Gear ratios': 'Relações de Engrenagem',
    'Final Drive Ratio': 'Relação Final',
    'Rear Wheel Ratio': 'Relação Final',
    'Secondary Ratio': 'Relação Final',
    'Rrimary Drive': 'Acionamento Primário', // the source's own typo for "Primary Drive"
    'Primary Drive': 'Acionamento Primário',
    'Primary Ratio': 'Acionamento Primário',
    Reserve: 'Reserva do Tanque',
    'Tank Reserve': 'Reserva do Tanque',
    'Reserve Tank': 'Reserva do Tanque',
    'Tank Range': 'Autonomia',
    'Average Tank Distance': 'Autonomia',
    'Power-to-weight ratio': 'Relação Peso-Potência',
    'Max Power Rear Tyre': 'Potência na Roda Traseira',
    'Max Power Rear Wheel': 'Potência na Roda Traseira',
    'Permitted Total Weight': 'Peso Bruto Total',
    'Throttle Valve Diameter': 'Diâmetro do Corpo de Borboleta',
    'Engine Control': 'Gerenciamento do Motor',
    // BMW's own "Fuel" is a fuel GRADE ("Unleaded super, octane number 95 (RON)"). The FIPE seed
    // already stores a key called 'Fuel' meaning the fuel TYPE (Gasolina/Álcool), so this one is
    // named apart rather than colliding with it under ON CONFLICT DO NOTHING.
    Fuel: 'Combustível Recomendado',
    // Road-test figures, published by motorcyclespecs on a minority of rows. The source writes the
    // same measurement under several spellings; each is normalised to one label so the rows stack.
    'Standing 0 - 100 km/h': 'Aceleração 0-100 km/h',
    'Standing 0 -100 km/h': 'Aceleração 0-100 km/h',
    'Standing 0 - 100km': 'Aceleração 0-100 km/h',
    'Standing 0 - 100km/h': 'Aceleração 0-100 km/h',
    'Acceleration 0 - 100mk/h / 62 mph': 'Aceleração 0-100 km/h',
    'Standing 0 - 140 km/h': 'Aceleração 0-140 km/h',
    'Standing 0 - 140km': 'Aceleração 0-140 km/h',
    'Standing 0 - 150 km/h': 'Aceleração 0-150 km/h',
    'Standing 0 -150 km/h': 'Aceleração 0-150 km/h',
    'Standing 0 - 180km': 'Aceleração 0-180 km/h',
    'Standing 0 - 200 km/h': 'Aceleração 0-200 km/h',
    'Standing 0 -200 km/h': 'Aceleração 0-200 km/h',
    'Standing 0 - 200km': 'Aceleração 0-200 km/h',
    // "0 - 2000 km/h" is the source's own typo: the values it carries (11.8 sec) sit alongside the
    // 0-200 figures (10.7 sec) on comparable bikes, and no row publishes both.
    'Standing 0 - 2000 km/h': 'Aceleração 0-200 km/h',
    'Standing 0 - 1000m': 'Standing 1000 m',
    'Standing 0 - 1000 m': 'Standing 1000 m',
    'Acceleration 60 - 100 km/h': 'Retomada 60-100 km/h',
    'Acceleration 60-100 km/h': 'Retomada 60-100 km/h',
    'Acceleration 60 - 140 km/h': 'Retomada 60-140 km/h',
    'Acceleration 60-140 km/h': 'Retomada 60-140 km/h',
    'Acceleration 100 - 140 km/h': 'Retomada 100-140 km/h',
    'Acceleration 100-140 km/h': 'Retomada 100-140 km/h',
    'Acceleration 140 - 180 km/h': 'Retomada 140-180 km/h',
    'Acceleration 140-180 km/h': 'Retomada 140-180 km/h',
    'Braking 60 km/h - 0': 'Frenagem 60-0 km/h',
    'Braking 60 km/h- 0': 'Frenagem 60-0 km/h',
    'Braking 100 km/h - 0': 'Frenagem 100-0 km/h',
    'Braking 100 - 0 km/h': 'Frenagem 100-0 km/h',
    'Braking 60 - 0 / 100 - 0': 'Frenagem 60-0 / 100-0 km/h',
};

function longTailSpecs(moto) {
    const specs = new Map();
    // Portuguese source labels are written first and never overwritten, so the Brazilian sheet wins
    // over the global one whenever both published the same fact.
    for (const key of TOP_LEVEL_SPEC_KEYS) {
        const value = clean(moto[key], 500);
        if (value) specs.set(key, value);
    }
    for (const [sourceKey, targetKey] of Object.entries(EXTRA_SPEC_KEYS)) {
        if (specs.has(targetKey)) continue;
        const value = clean((moto.outros_dados || {})[sourceKey], 500);
        if (value) specs.set(targetKey, value);
    }
    return specs;
}

// --- image naming -------------------------------------------------------------------------
// A UUID v5 over the slug: the file name the seed stores has to be reproducible on any machine,
// and FileStorageServiceImpl only reads names matching UUID + jpg/png/webp.
const UUID_NAMESPACE = Buffer.from('6ba7b8119dad11d180b400c04fd430c8', 'hex');

function uuidV5(name) {
    const digest = createHash('sha1').update(Buffer.concat([UUID_NAMESPACE, Buffer.from(name, 'utf8')])).digest();
    const bytes = Buffer.from(digest.subarray(0, 16));
    bytes[6] = (bytes[6] & 0x0f) | 0x50;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = bytes.toString('hex');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * One stored copy per motorcycle, even where several model-years share a source photo. The app
 * treats the file as owned by the row — deleting a motorcycle or clearing its image deletes the
 * file — so a shared name would blank the other rows' images.
 */
function imageFor(moto) {
    for (const relative of moto.imagens_locais || []) {
        const absolute = path.join(SOURCE_DIR, relative);
        if (!fs.existsSync(absolute)) continue; // the scraper records a gallery it did not finish downloading
        const extension = path.extname(absolute).toLowerCase();
        if (!['.jpg', '.png', '.webp'].includes(extension)) continue;
        return { source: absolute, fileName: `${uuidV5(moto.id)}${extension}` };
    }
    return null;
}

// --- build ----------------------------------------------------------------------------------
const snapshot = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
const dropped = {};
const rows = [];

for (const moto of snapshot.motos) {
    const extra = moto.outros_dados || {};

    const displacement = bounded(Math.round(displacementOf(moto['Cilindrada']) ?? NaN) || null, 30, 2500, dropped, 'displacement_cc');
    const bore = bounded(round(measure(moto['Diâmentro do Cilindro'], 'mm', { bare: true }), 2), 20, 150, dropped, 'bore_mm');
    const stroke = bounded(round(measure(moto['Curso'], 'mm', { bare: true }), 2), 20, 150, dropped, 'stroke_mm');

    // The prose is read first, then checked against the sheet's own bore, stroke and displacement.
    // Where the two disagree the arithmetic wins and the prose is relabelled to match it.
    const stated = cylindersOf(moto['Motor'], toNumber(extra['[Motor] Cilindros']));
    const swept = cylindersFromSweptVolume(displacement, bore, stroke);
    const contradicted = swept != null && stated.count != null && swept !== stated.count;
    if (contradicted) CORRECTED.count++;
    const engineText = contradicted ? relabelLayout(moto['Motor'], stated.phrase, swept) : moto['Motor'];
    const cylinders = bounded(swept ?? stated.count, 1, 8, dropped, 'cylinders');

    const [powerHp, powerRpm] = powerOf(moto['Potência Máxima'], dropped);
    const [torqueNm, torqueRpm] = torqueOf(moto['Torque Máximo'], dropped);

    const kerb = bounded(round(measure(moto['Peso em Ordem de Marcha'], 'kg'), 1), 20, 500, dropped, 'kerb_weight_kg');
    let dry = bounded(round(measure(moto['Peso Seco'] || extra['Dry Wight'], 'kg'), 1), 20, 500, dropped, 'dry_weight_kg');
    // ck_dimensions_dry_weight_below_kerb: a source pair the wrong way round would abort the import.
    if (dry != null && kerb != null && dry > kerb) {
        dropped.dry_weight_above_kerb = (dropped.dry_weight_above_kerb || 0) + 1;
        dry = null;
    }

    // "6-speed gearbox" is how these sheets write it, so the separator has to admit a hyphen; a
    // whitespace-only rule reads the gear count off "5 Speed" and misses every BMW written the
    // other way, which is most of them.
    const gearsText = moto['Transmissão'];
    const gearsMatch = gearsText && /(\d+)[\s-]*(?:speed|velocidades|marchas)/i.exec(gearsText);

    rows.push({
        slug: moto.id,
        brand: 'BMW',
        model: clean(moto.modelo, 120),
        modelYear: moto.ano,
        category: categoryOf(moto.modelo, extra['Categoria']),

        frame_type: clean(moto['Chassis'], 120),
        front_suspension: clean(moto['Suspensão Dianteira'], 160),
        rear_suspension: clean(moto['Suspensão Traseira'], 160),
        front_brake: clean(moto['Freio dianteiro'], 160),
        rear_brake: clean(moto['Freio traseiro'], 160),
        abs_type: clean(absTypeOf(extra), 80),
        front_tyre: clean(moto['Pneu Dianteiro'], 60),
        rear_tyre: clean(moto['Pneu Traseiro'], 60),

        engine_type: clean(engineText, 80),
        displacement_cc: displacement,
        cylinders,
        valves_per_cylinder: bounded(valvesPerCylinderOf(engineText, cylinders), 1, 8, dropped, 'valves_per_cylinder'),
        max_power_hp: powerHp,
        max_power_rpm: powerRpm,
        max_torque_nm: torqueNm,
        max_torque_rpm: torqueRpm,
        compression_ratio: compressionOf(moto['Taxa de Compressão']),
        bore_mm: bore,
        stroke_mm: stroke,
        cooling_system: clean(moto['Refrigeração'], 40),
        fuel_system: clean(moto['Alimentação'], 120),
        transmission_type: clean(gearsText, 60),
        gears: bounded(gearsMatch ? Number(gearsMatch[1]) : null, 1, 8, dropped, 'gears'),
        final_drive: clean(extra['Final Drive'] || extra['Final drive'] || extra['Drive'], 40),
        top_speed_kph: topSpeedOf(moto['Velocidade Máxima'], dropped),
        fuel_consumption_l_100km: consumptionOf(extra, dropped),
        emission_standard: emissionOf(extra),

        length_mm: bounded(Math.round(measure(moto['Comprimento'], 'mm', { bare: true, inches: true }) ?? NaN) || null, 1200, 3000, dropped, 'length_mm'),
        width_mm: bounded(Math.round(measure(moto['Largura'], 'mm', { bare: true, inches: true }) ?? NaN) || null, 400, 1500, dropped, 'width_mm'),
        height_mm: bounded(Math.round(measure(moto['Altura'], 'mm', { bare: true, inches: true }) ?? NaN) || null, 700, 1800, dropped, 'height_mm'),
        wheelbase_mm: bounded(Math.round(measure(moto['Distância Entre Eixos'], 'mm', { bare: true, inches: true }) ?? NaN) || null, 800, 2000, dropped, 'wheelbase_mm'),
        seat_height_mm: bounded(Math.round(measure(moto['Altura do Assento'], 'mm', { bare: true, inches: true }) ?? NaN) || null, 400, 1100, dropped, 'seat_height_mm'),
        ground_clearance_mm: bounded(Math.round(measure(moto['Distância do Solo'], 'mm', { bare: true, inches: true }) ?? NaN) || null, 50, 400, dropped, 'ground_clearance_mm'),
        kerb_weight_kg: kerb,
        dry_weight_kg: dry,
        // Litres only. "22 L / 5.8 US gal" must never fall through to the gallon figure. The sheets
        // spell the unit four ways - "L", "Litres", "Liters", "litros" - and the American spelling
        // is not a rounding error to leave out: 28 rows publish the tank only as "24 Liters".
        fuel_capacity_l: bounded(round(measure(moto['Capacidade do Tanque'], 'l\\b|lt\\b|litros?|litres?|liters?'), 1), 1, 50, dropped, 'fuel_capacity_l'),

        image: imageFor(moto),
        specs: longTailSpecs(moto),
    });
}

// --- image materialisation ------------------------------------------------------------------
if (doImages) {
    fs.mkdirSync(IMAGE_DIR, { recursive: true });
    let copied = 0;
    let bytes = 0;
    for (const row of rows) {
        if (!row.image) continue;
        const target = path.join(IMAGE_DIR, row.image.fileName);
        // Idempotent: an unchanged file is left alone so a re-run does not churn the upload directory.
        const source = fs.statSync(row.image.source);
        if (fs.existsSync(target) && fs.statSync(target).size === source.size) continue;
        fs.copyFileSync(row.image.source, target);
        copied++;
        bytes += source.size;
    }
    console.log(`images: ${rows.filter((r) => r.image).length} rows carry one, ${copied} written to ${IMAGE_DIR} (${(bytes / 1048576).toFixed(1)} MB)`);
}

if (!doSql) process.exit(0);

// --- SQL emission ---------------------------------------------------------------------------
const q = (value) => (value == null ? 'NULL' : `'${String(value).replace(/'/g, "''")}'`);
const n = (value) => (value == null ? 'NULL' : String(value));

const IMPORT_COLUMNS = [
    'row_no', 'slug', 'is_new', 'brand', 'model', 'model_year', 'category', 'image_url',
    'frame_type', 'front_suspension', 'rear_suspension', 'front_brake', 'rear_brake', 'abs_type',
    'front_tyre', 'rear_tyre', 'engine_type', 'displacement_cc', 'cylinders', 'valves_per_cylinder',
    'max_power_hp', 'max_power_rpm', 'max_torque_nm', 'max_torque_rpm', 'compression_ratio', 'bore_mm',
    'stroke_mm', 'cooling_system', 'fuel_system', 'transmission_type', 'gears', 'final_drive',
    'top_speed_kph', 'fuel_consumption_l_100km', 'emission_standard', 'length_mm', 'width_mm',
    'height_mm', 'wheelbase_mm', 'seat_height_mm', 'ground_clearance_mm', 'kerb_weight_kg',
    'dry_weight_kg', 'fuel_capacity_l',
];

const valuesFor = (row, rowNo) => [
    rowNo, q(row.slug), 'FALSE', q(row.brand), q(row.model), n(row.modelYear), q(row.category),
    q(row.image ? IMAGE_URL_PREFIX + row.image.fileName : null),
    q(row.frame_type), q(row.front_suspension), q(row.rear_suspension), q(row.front_brake),
    q(row.rear_brake), q(row.abs_type), q(row.front_tyre), q(row.rear_tyre), q(row.engine_type),
    n(row.displacement_cc), n(row.cylinders), n(row.valves_per_cylinder), n(row.max_power_hp),
    n(row.max_power_rpm), n(row.max_torque_nm), n(row.max_torque_rpm), q(row.compression_ratio),
    n(row.bore_mm), n(row.stroke_mm), q(row.cooling_system), q(row.fuel_system),
    q(row.transmission_type), n(row.gears), q(row.final_drive), n(row.top_speed_kph),
    n(row.fuel_consumption_l_100km), q(row.emission_standard), n(row.length_mm), n(row.width_mm),
    n(row.height_mm), n(row.wheelbase_mm), n(row.seat_height_mm), n(row.ground_clearance_mm),
    n(row.kerb_weight_kg), n(row.dry_weight_kg), n(row.fuel_capacity_l),
].join(', ');

const SPEC_COLUMNS = [
    'frame_type', 'front_suspension', 'rear_suspension', 'front_brake', 'rear_brake', 'abs_type',
    'front_tyre', 'rear_tyre', 'engine_type', 'displacement_cc', 'cylinders', 'valves_per_cylinder',
    'max_power_hp', 'max_power_rpm', 'max_torque_nm', 'max_torque_rpm', 'compression_ratio',
    'bore_mm', 'stroke_mm', 'cooling_system', 'fuel_system', 'transmission_type', 'gears',
    'final_drive', 'top_speed_kph', 'fuel_consumption_l_100km', 'emission_standard', 'length_mm',
    'width_mm', 'height_mm', 'wheelbase_mm', 'seat_height_mm', 'ground_clearance_mm',
    'kerb_weight_kg', 'dry_weight_kg', 'fuel_capacity_l',
];

// Rows the sources published nothing usable for are not emitted at all: an all-NULL row would add
// bytes to this file and change nothing in the database.
const emitted = rows.filter((row) => SPEC_COLUMNS.some((c) => row[c] != null) || row.specs.size > 0 || row.image);
const populated = Object.fromEntries(SPEC_COLUMNS.map((c) => [c, emitted.filter((r) => r[c] != null).length]));
const specRowCount = emitted.reduce((sum, row) => sum + row.specs.size, 0);
const withImage = emitted.filter((r) => r.image).length;
const distinctImages = new Set(emitted.filter((r) => r.image).map((r) => r.image.source)).size;
const withDimensions = emitted.filter((r) => ['length_mm', 'width_mm', 'height_mm', 'wheelbase_mm', 'seat_height_mm',
    'ground_clearance_mm', 'kerb_weight_kg', 'dry_weight_kg', 'fuel_capacity_l'].some((c) => r[c] != null)).length;
const withoutImage = emitted.filter((r) => !r.image).map((r) => r.slug);
const skipped = rows.filter((row) => !emitted.includes(row)).map((r) => r.slug);
const grades = snapshot.resumo;

const out = [];
const w = (line = '') => out.push(line);

w('-- Motorcycle Comparison API - BMW technical specifications');
w(`-- Generated from zontes-scraper/bmw_motos.json (scraped ${snapshot.extraido_em}) by`);
w('-- tools/import-bmw-specs.mjs.');
w('--');
w('-- Sources, as recorded by the scraper:');
for (const [name, description] of Object.entries(snapshot.fontes)) w(`--   ${name}: ${description}`);
w('--');
w('-- The file name reads "specs_bmw" where the Harley, Honda, Royal Enfield and Yamaha imports read');
w('-- "<brand>_specs", and the difference is load-bearing rather than a slip. Flyway runs repeatable');
w('-- migrations in description order (MigrationInfoImpl.compareTo: "Two repeatable migrations: sort by');
w('-- description"), and "motorcycles bmw specs ..." sorts before "motorcycles brazil fipe ...", because');
w('-- "bmw" < "brazil". The other four brands clear the FIPE seed by luck - h, r and y all sort after b.');
w('-- Running before it would not fail: this import would find no catalogue row for any of its slugs,');
w('-- create all 200 itself, and the FIPE seed that followed would then drop those same 200 rows from');
w('-- its own load, because its rerun guard skips any (brand, model, model_year) already present - and');
w('-- all 200 model names here match FIPE\'s exactly. The catalogue would end up holding every one of');
w('-- these motorcycles with price_eur NULL and no way to tell that a price had ever been available.');
w('-- Rename this file back to the sibling pattern and that is what happens.');
w('--');
w('-- The JSON "id" is the same base slug the FIPE seed derives, so rows join on motorcycles.slug rather');
w(`-- than on any fuzzy name match. All ${snapshot.motos.length} scraped model-years already exist in the catalogue, so`);
if (emitted.length === snapshot.motos.length) {
    w('-- against today\'s seeds this import creates no motorcycles and only fills gaps. All of them carry');
    w('-- something usable and are emitted.');
} else {
    w(`-- against today's seeds this import creates no motorcycles and only fills gaps. ${emitted.length} of them carry`);
    w(`-- something usable and are emitted; the ${skipped.length === 1 ? 'one that does not is' : `${skipped.length} that do not are`} listed at the foot of this header.`);
}
w('-- The insert path below still runs: whether a row is new is derived from the database at run');
w('-- time rather than from a flag baked in here, so a slug the catalogue does not have is created rather');
w('-- than dropped.');
w('--');
w('-- Nothing is estimated. A value absent from the source stays NULL, and every figure below was parsed');
w('-- from the source string: hp from hp/bhp/cv/PS/kW, Nm from Nm/kgf.m/lb-ft, and litres from the litre');
w('-- form only (the sheets print "22 L / 5.8 US gal", and the gallon figure must never win). The sources');
w('-- disagree on decimal convention inside the same file ("17,5 kgf.m" beside "107 Nm", "7.750 RPM"');
w('-- beside "7500 rpm"), so a separator is read from its own shape: exactly three trailing digits is a');
w('-- thousands group, anything else is a decimal point. These are metric sheets, so a measurement is');
w(`-- read in mm wherever mm was printed and converted from inches only where the metric figure beside`);
w(`-- it is unreadable - ${CONVERTED.count} values came in that way, from a mistyped unit ("2324 m / 91.5 in",`);
w('-- "2489m m / 98.0 in") or a split number ("80 0 mm / 31.4 in").');
w('--');
w('-- Where a row publishes power twice - "72.9 kW / 100 hp @ 7500 rpm (70.8 kW 94.9 hp @ 7400 rpm at');
w('-- rear wheel)" - the crank figure is the stored one, because it is the figure every other brand in');
w('-- this catalogue publishes. The rear-wheel figure is not discarded: it goes to the long tail as');
w("-- 'Potência na Roda Traseira'.");
w('--');
w('-- One source figure is not taken on trust: the cylinder count. Bore, stroke and displacement are');
w('-- printed on the same sheet, so their product decides the count whenever the prose beside them');
w(`-- disagrees (${CORRECTED.count} correction${CORRECTED.count === 1 ? '' : 's'} here). That check matters more for BMW than for a single-layout brand,`);
w('-- because this snapshot spans singles, flat twins, parallel twins, inline fours and the K 1600 six.');
w('-- In the prose, "Boxer" is read as two cylinders; the "Four" in "Four stroke" is not read as a count,');
w('-- and neither is the 4 in "4 valves per cylinder".');
w('--');
w('-- Two extraction faults in the snapshot are handled rather than stored. The first is a word split');
w(`-- after its first letter - "F our stroke", "E lectric", "t hrottle butterfly", "C hill-cast" - which`);
w(`-- is repaired on the way in (${REPAIRED.count} values). Only those four forms are repaired: a general rule would`);
w('-- corrupt the legitimate text beside them, because "BMS-K with overrun fuel cut off", "Optional M');
w('-- forged aluminum wheels" and "by mean of a hydraulic handwheel" are all correct as printed. The');
w('-- second is spaced-out digits, which are refused rather than read as their first fragment, because a');
w(`-- plausibility bound cannot catch a wrong figure that lands inside the range - ${MALFORMED.count} numeric read${MALFORMED.count === 1 ? '' : 's'}`);
w(`-- ${MALFORMED.count === 1 ? 'was' : 'were'} refused on that rule. The K 1200 LT is the row that makes the rule earn its keep: its`);
w('-- capacity is published as "11 72 cc / 71.5 cu in", where 1172 has been split in two. A split like');
w('-- that cannot be rejoined safely - "11 72" is not distinguishable from two numbers by shape alone -');
w(`-- so the metric read is refused and the cubic-inch figure printed beside it is converted instead`);
w(`-- (${CUBIC_INCHES.count} rows). Reading the fragment would have stored 11 cc, which is a number small enough that no`);
w('-- plausibility bound would have looked twice at it.');
w('--');
w('-- Implausible figures are dropped rather than stored, each against the range its column can sensibly');
w('-- hold:');
const droppedEntries = Object.entries(dropped).sort((a, b) => b[1] - a[1]);
w(`--   ${droppedEntries.map(([k, v]) => `${k} ${v}`).join(', ') || 'none'}`);
if (dropped.ground_clearance_mm) {
    w(`-- The ${dropped.ground_clearance_mm} ground clearances dropped are the F 650 rows, where the source repeats the seat height`);
    w('-- (800 mm) in the ground-clearance field. That is a source error, and the bound is what catches it.');
}
w(`-- ${TRUNCATED.count} values exceeded their column width and were cut at a word boundary rather than mid-word.`);
w('--');
w('-- Caveat worth knowing before trusting any single row. None of these 200 model-years is in current');
w('-- production, so motorcyclist.com.br reaches only a handful of them and the rest lean entirely on');
w('-- motorcyclespecs.co.za, which publishes the global sheet rather than the Brazilian one and reuses a');
w('-- single sheet across a whole generation. Figures are representative of the generation, not specific');
w(`-- to the year on the row. The scraper grades its own matches, and not all ${grades.modelos} are exact:`);
w(`-- ${grades.casamento_exato} matched a sheet outright, ${grades.casamento_modelo_base} matched the base model, ${grades.casamento_geracao_vizinha} matched a neighbouring generation,`);
w(`-- ${grades.casamento_geracao_distante} matched a distant generation, and ${grades.sem_ficha} matched nothing at all. The last two grades are the`);
w('-- ones to distrust on a spec-by-spec basis.');
w('--');
w('-- Images. The scraper stores its gallery under img/bmw/<generation>/, which is per generation, not');
w(`-- per model-year, so the ${distinctImages} distinct photos below cover ${withImage === emitted.length ? `all ${emitted.length}` : `${withImage} of the ${emitted.length}`} rows. Each row still gets its own`);
w('-- stored copy under a name derived from its slug (UUID v5, matching the shape FileStorageServiceImpl');
w('-- will read): MotorcycleService treats the file as owned by the row and deletes it when the');
w('-- motorcycle or its image is deleted, so a shared file name would blank every other row pointing at');
w('-- it.');
if (withoutImage.length) {
    w(`-- The remaining ${withoutImage.length} row${withoutImage.length === 1 ? '' : 's'} had no photo published by any source: ${withoutImage.join(', ')}.`);
}
w('-- The files themselves are NOT in the repository - uploads/ is gitignored. Run');
w('-- `node tools/import-bmw-specs.mjs` to materialise them from a zontes-scraper checkout; until then');
w('-- these rows serve a 404 for their image.');
w('--');
w('-- Existing rows are only ever gap-filled: every write is COALESCE(existing, imported), so an admin');
w('-- edit, an uploaded image or a richer earlier import is never overwritten. These rows arrived from');
w('-- FIPE with every specification column NULL, so for them a gap-fill is a full population. Long-tail');
w("-- specs use ON CONFLICT DO NOTHING, which leaves the FIPE seed's own 'Fuel' and 'Reference price");
w("-- (BRL)' intact, and reuse the key names the Honda, Yamaha, Royal Enfield and Harley-Davidson");
w("-- imports chose so a cross-brand comparison lines its rows up. BMW's own fuel-grade field is stored");
w("-- as 'Combustível Recomendado' rather than 'Fuel' for exactly that reason: the FIPE key of that name");
w('-- means the fuel type, not the octane rating. Category is deliberately not touched on existing rows.');
w('-- Repeatable and idempotent: re-running changes nothing once it has been applied.');
w('--');
w(`-- Populated column counts across the ${emitted.length} imported rows:`);
w(`--   engine: displacement_cc ${populated.displacement_cc}, max_power_hp ${populated.max_power_hp}, max_torque_nm ${populated.max_torque_nm}, gears ${populated.gears}, cylinders ${populated.cylinders}`);
w(`--   frame:  frame_type ${populated.frame_type}, front_suspension ${populated.front_suspension}, front_tyre ${populated.front_tyre}, abs_type ${populated.abs_type}`);
w(`--   dims:   kerb_weight_kg ${populated.kerb_weight_kg}, dry_weight_kg ${populated.dry_weight_kg}, seat_height_mm ${populated.seat_height_mm}, fuel_capacity_l ${populated.fuel_capacity_l} (${withDimensions} rows get a dimension block)`);
w(`--   other:  top_speed_kph ${populated.top_speed_kph}, fuel_consumption_l_100km ${populated.fuel_consumption_l_100km}, emission_standard ${populated.emission_standard}, final_drive ${populated.final_drive}`);
w(`--   image_url ${withImage}; long-tail spec rows: ${specRowCount}`);
if (skipped.length) w(`-- Rows carrying nothing usable and therefore not emitted: ${skipped.join(', ')}`);
w();
w('BEGIN;');
w();
w('CREATE TEMP TABLE tmp_bmw_spec_import (');
w('    row_no                   bigint PRIMARY KEY,');
w('    slug                     varchar(160) NOT NULL,');
w('    is_new                   boolean NOT NULL,');
w('    brand                    varchar(60),');
w('    model                    varchar(120),');
w('    model_year               integer,');
w('    category                 varchar(20),');
w('    image_url                varchar(512),');
w('    frame_type               varchar(120),');
w('    front_suspension         varchar(160),');
w('    rear_suspension          varchar(160),');
w('    front_brake              varchar(160),');
w('    rear_brake               varchar(160),');
w('    abs_type                 varchar(80),');
w('    front_tyre               varchar(60),');
w('    rear_tyre                varchar(60),');
w('    engine_type              varchar(80),');
w('    displacement_cc          integer,');
w('    cylinders                integer,');
w('    valves_per_cylinder      integer,');
w('    max_power_hp             numeric(6,1),');
w('    max_power_rpm            integer,');
w('    max_torque_nm            numeric(6,1),');
w('    max_torque_rpm           integer,');
w('    compression_ratio        varchar(20),');
w('    bore_mm                  numeric(6,2),');
w('    stroke_mm                numeric(6,2),');
w('    cooling_system           varchar(40),');
w('    fuel_system              varchar(120),');
w('    transmission_type        varchar(60),');
w('    gears                    integer,');
w('    final_drive              varchar(40),');
w('    top_speed_kph            integer,');
w('    fuel_consumption_l_100km numeric(5,2),');
w('    emission_standard        varchar(30),');
w('    length_mm                integer,');
w('    width_mm                 integer,');
w('    height_mm                integer,');
w('    wheelbase_mm             integer,');
w('    seat_height_mm           integer,');
w('    ground_clearance_mm      integer,');
w('    kerb_weight_kg           numeric(6,1),');
w('    dry_weight_kg            numeric(6,1),');
w('    fuel_capacity_l          numeric(5,1),');
w('    motorcycle_id            bigint,');
w('    engine_id                bigint,');
w('    dimension_id             bigint');
w(') ON COMMIT DROP;');
w();
w('CREATE TEMP TABLE tmp_bmw_spec_kv (');
w('    row_no     bigint NOT NULL,');
w('    spec_key   varchar(80) NOT NULL,');
w('    spec_value varchar(500),');
w('    PRIMARY KEY (row_no, spec_key)');
w(') ON COMMIT DROP;');
w();

const BATCH = 200;
for (let start = 0; start < emitted.length; start += BATCH) {
    const batch = emitted.slice(start, start + BATCH);
    w(`INSERT INTO tmp_bmw_spec_import (${IMPORT_COLUMNS.join(', ')}) VALUES`);
    batch.forEach((row, i) => {
        const rowNo = start + i + 1;
        w(`(${valuesFor(row, rowNo)})${i === batch.length - 1 ? ';' : ','}`);
    });
    w();
}

const kvPairs = [];
emitted.forEach((row, i) => {
    for (const [key, value] of row.specs) kvPairs.push(`(${i + 1}, ${q(key)}, ${q(value)})`);
});
for (let start = 0; start < kvPairs.length; start += 500) {
    const batch = kvPairs.slice(start, start + 500);
    w('INSERT INTO tmp_bmw_spec_kv (row_no, spec_key, spec_value) VALUES');
    batch.forEach((pair, i) => w(`${pair}${i === batch.length - 1 ? ';' : ','}`));
    w();
}

w('-- Bind every imported row to its catalogue row. The FIPE seed derives the same slug from the same');
w('-- FIPE model descriptor, so this is an equality join and not a fuzzy match.');
w('UPDATE tmp_bmw_spec_import s');
w('SET motorcycle_id = m.id,');
w('    engine_id     = m.engine_specification_id,');
w('    dimension_id  = m.dimension_id');
w('FROM motorcycles m');
w('WHERE m.slug = s.slug;');
w();
w('-- What counts as new is decided here, from the database, not from the flag carried in the VALUES');
w('-- above. That flag records what was true when this file was generated; the catalogue may since have');
w('-- gained or lost the model. Deriving it means an existing model is never inserted twice, and a model');
w('-- the catalogue does not have is created rather than quietly dropped.');
w('UPDATE tmp_bmw_spec_import SET is_new = (motorcycle_id IS NULL);');
w();
w('DO $$');
w('BEGIN');
w("    IF pg_get_serial_sequence('engine_specifications', 'id') IS NULL");
w("       OR pg_get_serial_sequence('dimensions', 'id') IS NULL");
w("       OR pg_get_serial_sequence('motorcycles', 'id') IS NULL THEN");
w("        RAISE EXCEPTION 'A target table has no serial/identity sequence';");
w('    END IF;');
w('END $$;');
w();
w('-- ---------------------------------------------------------------------------');
w('-- New models: allocate ids, then build engine, dimension and catalogue rows.');
w('-- ---------------------------------------------------------------------------');
w('UPDATE tmp_bmw_spec_import');
w("SET motorcycle_id = nextval(pg_get_serial_sequence('motorcycles', 'id')::regclass),");
w("    engine_id     = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)");
w('WHERE is_new;');
w();
w('-- A dimension row is created only where the source published a measurement; an empty placeholder');
w('-- would add a row that exists only to render dashes in the comparison table.');
w('UPDATE tmp_bmw_spec_import');
w("SET dimension_id = nextval(pg_get_serial_sequence('dimensions', 'id')::regclass)");
w('WHERE is_new');
w('  AND num_nonnulls(length_mm, width_mm, height_mm, wheelbase_mm, seat_height_mm,');
w('                   ground_clearance_mm, kerb_weight_kg, dry_weight_kg, fuel_capacity_l) > 0;');
w();
w('INSERT INTO engine_specifications (');
w('    id, engine_type, displacement_cc, cylinders, valves_per_cylinder, max_power_hp, max_power_rpm,');
w('    max_torque_nm, max_torque_rpm, compression_ratio, bore_mm, stroke_mm, cooling_system, fuel_system,');
w('    transmission_type, gears, final_drive, top_speed_kph, fuel_consumption_l_100km, emission_standard');
w(')');
w('SELECT engine_id, engine_type, displacement_cc, cylinders, valves_per_cylinder, max_power_hp, max_power_rpm,');
w('       max_torque_nm, max_torque_rpm, compression_ratio, bore_mm, stroke_mm, cooling_system, fuel_system,');
w('       transmission_type, gears, final_drive, top_speed_kph, fuel_consumption_l_100km, emission_standard');
w('FROM tmp_bmw_spec_import');
w('WHERE is_new');
w('ORDER BY row_no;');
w();
w('INSERT INTO dimensions (');
w('    id, length_mm, width_mm, height_mm, wheelbase_mm, seat_height_mm, ground_clearance_mm,');
w('    kerb_weight_kg, dry_weight_kg, fuel_capacity_l');
w(')');
w('SELECT dimension_id, length_mm, width_mm, height_mm, wheelbase_mm, seat_height_mm, ground_clearance_mm,');
w('       kerb_weight_kg, dry_weight_kg, fuel_capacity_l');
w('FROM tmp_bmw_spec_import');
w('WHERE is_new AND dimension_id IS NOT NULL');
w('ORDER BY row_no;');
w();
w('-- price_eur stays NULL: a model outside the FIPE snapshot has no reference price here, and a guessed');
w('-- one would be indistinguishable from a real one once it is in the catalogue.');
w('INSERT INTO motorcycles (');
w('    id, slug, version, brand, model, model_year, category, price_eur, image_url, description,');
w('    frame_type, front_suspension, rear_suspension, front_brake, rear_brake, abs_type,');
w('    front_tyre, rear_tyre, engine_specification_id, dimension_id, created_at, updated_at');
w(')');
w('SELECT motorcycle_id, slug, 0, brand, model, model_year, category, NULL, image_url, NULL,');
w('       frame_type, front_suspension, rear_suspension, front_brake, rear_brake, abs_type,');
w('       front_tyre, rear_tyre, engine_id, dimension_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP');
w('FROM tmp_bmw_spec_import');
w('WHERE is_new');
w('ORDER BY row_no;');
w();
w('-- ---------------------------------------------------------------------------');
w('-- Existing models: gap-fill only.');
w('-- ---------------------------------------------------------------------------');
w();
w('-- The FIPE seed created an engine block for every row it inserted, but an earlier import may not');
w('-- have. Give those an engine row first, so their figures are not silently dropped below.');
w('UPDATE tmp_bmw_spec_import');
w("SET engine_id = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)");
w('WHERE NOT is_new AND engine_id IS NULL;');
w();
w('INSERT INTO engine_specifications (id)');
w('SELECT s.engine_id');
w('FROM tmp_bmw_spec_import s');
w('WHERE NOT s.is_new');
w('  AND s.engine_id IS NOT NULL');
w('  AND NOT EXISTS (SELECT 1 FROM engine_specifications e WHERE e.id = s.engine_id)');
w('ORDER BY s.row_no;');
w();
w('UPDATE motorcycles m');
w('SET engine_specification_id = s.engine_id');
w('FROM tmp_bmw_spec_import s');
w('WHERE m.id = s.motorcycle_id AND NOT s.is_new AND m.engine_specification_id IS NULL;');
w();
w('UPDATE engine_specifications e');
w('SET engine_type              = COALESCE(e.engine_type, s.engine_type),');
w('    displacement_cc          = COALESCE(e.displacement_cc, s.displacement_cc),');
w('    cylinders                = COALESCE(e.cylinders, s.cylinders),');
w('    valves_per_cylinder      = COALESCE(e.valves_per_cylinder, s.valves_per_cylinder),');
w('    max_power_hp             = COALESCE(e.max_power_hp, s.max_power_hp),');
w('    max_power_rpm            = COALESCE(e.max_power_rpm, s.max_power_rpm),');
w('    max_torque_nm            = COALESCE(e.max_torque_nm, s.max_torque_nm),');
w('    max_torque_rpm           = COALESCE(e.max_torque_rpm, s.max_torque_rpm),');
w('    compression_ratio        = COALESCE(e.compression_ratio, s.compression_ratio),');
w('    bore_mm                  = COALESCE(e.bore_mm, s.bore_mm),');
w('    stroke_mm                = COALESCE(e.stroke_mm, s.stroke_mm),');
w('    cooling_system           = COALESCE(e.cooling_system, s.cooling_system),');
w('    fuel_system              = COALESCE(e.fuel_system, s.fuel_system),');
w('    transmission_type        = COALESCE(e.transmission_type, s.transmission_type),');
w('    gears                    = COALESCE(e.gears, s.gears),');
w('    final_drive              = COALESCE(e.final_drive, s.final_drive),');
w('    top_speed_kph            = COALESCE(e.top_speed_kph, s.top_speed_kph),');
w('    fuel_consumption_l_100km = COALESCE(e.fuel_consumption_l_100km, s.fuel_consumption_l_100km),');
w('    emission_standard        = COALESCE(e.emission_standard, s.emission_standard)');
w('FROM tmp_bmw_spec_import s');
w('WHERE e.id = s.engine_id AND NOT s.is_new;');
w();
w('-- A row seeded from FIPE carries no dimension block at all, so allocate one wherever this import');
w('-- has something to put in it.');
w('UPDATE tmp_bmw_spec_import');
w("SET dimension_id = nextval(pg_get_serial_sequence('dimensions', 'id')::regclass)");
w('WHERE NOT is_new');
w('  AND dimension_id IS NULL');
w('  AND num_nonnulls(length_mm, width_mm, height_mm, wheelbase_mm, seat_height_mm,');
w('                   ground_clearance_mm, kerb_weight_kg, dry_weight_kg, fuel_capacity_l) > 0;');
w();
w('INSERT INTO dimensions (');
w('    id, length_mm, width_mm, height_mm, wheelbase_mm, seat_height_mm, ground_clearance_mm,');
w('    kerb_weight_kg, dry_weight_kg, fuel_capacity_l');
w(')');
w('SELECT s.dimension_id, s.length_mm, s.width_mm, s.height_mm, s.wheelbase_mm, s.seat_height_mm,');
w('       s.ground_clearance_mm, s.kerb_weight_kg, s.dry_weight_kg, s.fuel_capacity_l');
w('FROM tmp_bmw_spec_import s');
w('WHERE NOT s.is_new');
w('  AND s.dimension_id IS NOT NULL');
w('  AND NOT EXISTS (SELECT 1 FROM dimensions d WHERE d.id = s.dimension_id)');
w('ORDER BY s.row_no;');
w();
w('-- Where a dimension block already existed, gap-fill it the same way as the engine block.');
w('UPDATE dimensions d');
w('SET length_mm           = COALESCE(d.length_mm, s.length_mm),');
w('    width_mm            = COALESCE(d.width_mm, s.width_mm),');
w('    height_mm           = COALESCE(d.height_mm, s.height_mm),');
w('    wheelbase_mm        = COALESCE(d.wheelbase_mm, s.wheelbase_mm),');
w('    seat_height_mm      = COALESCE(d.seat_height_mm, s.seat_height_mm),');
w('    ground_clearance_mm = COALESCE(d.ground_clearance_mm, s.ground_clearance_mm),');
w('    kerb_weight_kg      = COALESCE(d.kerb_weight_kg, s.kerb_weight_kg),');
w('    dry_weight_kg       = COALESCE(d.dry_weight_kg, s.dry_weight_kg),');
w('    fuel_capacity_l     = COALESCE(d.fuel_capacity_l, s.fuel_capacity_l)');
w('FROM tmp_bmw_spec_import s');
w('WHERE d.id = s.dimension_id AND NOT s.is_new;');
w();
w('-- A gap-fill that would push dry weight above a kerb weight already on the row is dropped: the');
w('-- CHECK would otherwise abort the entire import over one bad source figure.');
w('UPDATE dimensions');
w('SET dry_weight_kg = NULL');
w('WHERE dry_weight_kg IS NOT NULL');
w('  AND kerb_weight_kg IS NOT NULL');
w('  AND dry_weight_kg > kerb_weight_kg;');
w();
w('-- image_url is gap-filled like everything else, so an image an admin uploaded through');
w('-- POST /api/v1/motorcycles/{id}/image always wins over the scraped one. It is never cleared here:');
w('-- removeImage deletes the file behind a URL this API issued, and this import must not orphan one.');
w('UPDATE motorcycles m');
w('SET frame_type       = COALESCE(m.frame_type, s.frame_type),');
w('    front_suspension = COALESCE(m.front_suspension, s.front_suspension),');
w('    rear_suspension  = COALESCE(m.rear_suspension, s.rear_suspension),');
w('    front_brake      = COALESCE(m.front_brake, s.front_brake),');
w('    rear_brake       = COALESCE(m.rear_brake, s.rear_brake),');
w('    abs_type         = COALESCE(m.abs_type, s.abs_type),');
w('    front_tyre       = COALESCE(m.front_tyre, s.front_tyre),');
w('    rear_tyre        = COALESCE(m.rear_tyre, s.rear_tyre),');
w('    image_url        = COALESCE(m.image_url, s.image_url),');
w('    dimension_id     = COALESCE(m.dimension_id, s.dimension_id),');
w("    -- @Version belongs to Hibernate; bump it here because this writes behind the ORM's back, so a");
w('    -- session holding a stale copy of one of these rows fails its next flush instead of quietly');
w('    -- overwriting the import.');
w('    version          = m.version + 1,');
w('    updated_at       = CURRENT_TIMESTAMP');
w('FROM tmp_bmw_spec_import s');
w('WHERE m.id = s.motorcycle_id');
w('  AND NOT s.is_new');
w('  -- Each arm is "the row lacks it AND the import supplies it", so a row this file has nothing left');
w('  -- to add is not touched at all. Testing only for NULL would bump version and updated_at on every');
w('  -- re-run for any column the source never published.');
w('  AND ((m.frame_type IS NULL AND s.frame_type IS NOT NULL)');
w('       OR (m.front_suspension IS NULL AND s.front_suspension IS NOT NULL)');
w('       OR (m.rear_suspension IS NULL AND s.rear_suspension IS NOT NULL)');
w('       OR (m.front_brake IS NULL AND s.front_brake IS NOT NULL)');
w('       OR (m.rear_brake IS NULL AND s.rear_brake IS NOT NULL)');
w('       OR (m.abs_type IS NULL AND s.abs_type IS NOT NULL)');
w('       OR (m.front_tyre IS NULL AND s.front_tyre IS NOT NULL)');
w('       OR (m.rear_tyre IS NULL AND s.rear_tyre IS NOT NULL)');
w('       OR (m.image_url IS NULL AND s.image_url IS NOT NULL)');
w('       OR (m.dimension_id IS NULL AND s.dimension_id IS NOT NULL));');
w();
w('-- ---------------------------------------------------------------------------');
w("-- Long-tail specs. DO NOTHING preserves whatever is already stored under the same key,");
w("-- including the FIPE seed's own 'Fuel' and 'Reference price (BRL)'.");
w('-- ---------------------------------------------------------------------------');
w('INSERT INTO motorcycle_additional_specs (motorcycle_id, spec_key, spec_value)');
w('SELECT s.motorcycle_id, k.spec_key, k.spec_value');
w('FROM tmp_bmw_spec_kv k');
w('JOIN tmp_bmw_spec_import s ON s.row_no = k.row_no');
w('WHERE s.motorcycle_id IS NOT NULL');
w('  AND k.spec_value IS NOT NULL');
w("  AND btrim(k.spec_value) <> ''");
w('ON CONFLICT (motorcycle_id, spec_key) DO NOTHING;');
w();
w('-- ---------------------------------------------------------------------------');
w('-- Defensive checks before commit.');
w('-- ---------------------------------------------------------------------------');
w('DO $$');
w('DECLARE');
w('    bad bigint;');
w('BEGIN');
w('    SELECT count(*) INTO bad');
w('    FROM tmp_bmw_spec_import s');
w('    LEFT JOIN motorcycles m ON m.id = s.motorcycle_id');
w('    WHERE m.id IS NULL;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'BMW spec import: % rows did not resolve to a catalogue row', bad;");
w('    END IF;');
w();
w('    SELECT count(*) INTO bad');
w('    FROM tmp_bmw_spec_import s');
w('    JOIN motorcycles m ON m.id = s.motorcycle_id');
w('    WHERE s.engine_id IS NOT NULL AND m.engine_specification_id IS DISTINCT FROM s.engine_id;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'BMW spec import: % rows have a mismatched engine block', bad;");
w('    END IF;');
w();
w('    SELECT count(*) INTO bad');
w('    FROM dimensions');
w('    WHERE dry_weight_kg IS NOT NULL AND kerb_weight_kg IS NOT NULL AND dry_weight_kg > kerb_weight_kg;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'BMW spec import: % dimension rows have dry weight above kerb weight', bad;");
w('    END IF;');
w();
w('    -- lower(brand) rather than brand: V4 normalised casing before either seed had inserted a row, so');
w('    -- an equality test here would silently depend on which seed wrote the row.');
w('    SELECT count(*) INTO bad');
w('    FROM motorcycles');
w("    WHERE lower(brand) = 'bmw' AND slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';");
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'BMW spec import: % rows have a slug the public routing cannot use', bad;");
w('    END IF;');
w();
w('    -- Every stored image URL has to be a name ImageController can actually serve: FileStorageServiceImpl');
w('    -- reads UUID + jpg/png/webp and nothing else, so a name outside that shape is a silent 404.');
w('    SELECT count(*) INTO bad');
w('    FROM tmp_bmw_spec_import');
w('    WHERE image_url IS NOT NULL');
w("      AND image_url !~ '^/api/v1/images/motorcycles/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$';");
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'BMW spec import: % image URLs are not a name the image endpoint can serve', bad;");
w('    END IF;');
w('END $$;');
w();
w('COMMIT;');
w();

fs.writeFileSync(SQL_PATH, out.join('\n'), 'utf8');
console.log(`sql:    ${emitted.length} rows, ${specRowCount} long-tail specs, ${withImage} image URLs -> ${path.relative(REPO_ROOT, SQL_PATH)}`);
console.log(`dropped as implausible: ${droppedEntries.map(([k, v]) => `${k}=${v}`).join(' ') || 'none'}`);
console.log(`truncated to column width: ${TRUNCATED.count}; refused as garbled digits: ${MALFORMED.count}; split-word repairs: ${REPAIRED.count}`);
console.log(`inch-only measurements converted: ${CONVERTED.count}; displacements read from cubic inches: ${CUBIC_INCHES.count}`);
console.log(`cylinder counts overruled by swept volume: ${CORRECTED.count}`);
