#!/usr/bin/env node
// Regenerates src/main/resources/db/seed/R__motorcycles_yamaha_specs_2026_08.sql from the
// zontes-scraper Yamaha snapshot, and materialises the images that seed points at.
//
// Two outputs, one run, because they have to agree: the SQL stores an image URL whose file name
// is derived from the motorcycle slug, and the copy step writes exactly those names. Re-running
// on another machine reproduces both byte-for-byte.
//
//   node tools/import-yamaha-specs.mjs [--source <dir>] [--sql-only] [--images-only]
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
const JSON_PATH = path.join(SOURCE_DIR, 'yamaha_motos.json');
const SQL_PATH = path.join(REPO_ROOT, 'src/main/resources/db/seed/R__motorcycles_yamaha_specs_2026_08.sql');
const IMAGE_DIR = path.join(REPO_ROOT, 'uploads/motorcycles');
const IMAGE_URL_PREFIX = '/api/v1/images/motorcycles/';

const doSql = !flag('--images-only');
const doImages = !flag('--sql-only');

// --- number parsing -----------------------------------------------------------------------
// The sources mix Portuguese and English conventions in the same file ("68,9 cv" next to
// "84.6 kW", "13.500 RPM" next to "10000 rpm"), so a separator is read from its own shape:
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

// A handful of source strings space out their digits: "1 135.3 mm" is the SI thousands separator and
// means 1135.3, but "2 40 kg" and "@ 4 75 0 rpm" are simply garbled. The first is repaired, and the
// second is then refused by NUMBER_START below rather than read as 40 kg and 0 rpm — a plausibility
// bound cannot catch a wrong figure that happens to land inside the range.
const MALFORMED = { count: 0 };
const repairDigitGroups = (text) => String(text).replace(/(\d) (\d{3})(?!\d)/g, '$1$2');

/** A number may not begin immediately after a digit, or after a digit and a space. */
const NUMBER_START = '(?<!\\d)(?<!\\d\\s)';

function countMalformed(text) {
    if (/\d\s+\d/.test(text)) MALFORMED.count++;
    return null;
}

/** First number carrying one of the given units; `bare` allows a unitless value as a last resort. */
function measure(rawText, unitPattern, { bare = false } = {}) {
    if (!rawText) return null;
    const text = repairDigitGroups(rawText);
    const withUnit = new RegExp(`${NUMBER_START}(\\d[\\d.,]*)\\s*(?:${unitPattern})`, 'i').exec(text);
    if (withUnit) return toNumber(withUnit[1]);
    if (!bare) return countMalformed(text);
    const any = new RegExp(`${NUMBER_START}(\\d[\\d.,]*)`).exec(text);
    return any ? toNumber(any[1]) : countMalformed(text);
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

/** Collapses whitespace, drops trailing punctuation the scraper carried over from the source table. */
function clean(text, maxLength) {
    if (text == null) return null;
    let t = String(text).replace(/\s+/g, ' ').trim().replace(/[,;]+$/, '');
    // One documented repair: motorcyclespecs.co.za publishes "L iquid cooled" for a run of models.
    t = t.replace(/\bL iquid\b/g, 'Liquid');
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

function cylindersOf(engineText, explicit) {
    if (explicit != null) return explicit;
    if (!engineText) return null;
    // Adjacency to "cylinder"/"cilindr" is required: "Four stroke, parallel twin cylinder" describes
    // a twin, and a bare leading "Four stroke" must not be read as a cylinder count.
    const en = /\b(single|mono|one|two|twin|three|triple|four|six|\d)[\s-]*cylinders?\b/i.exec(engineText);
    if (en) return WORD_CYLINDERS[en[1].toLowerCase()] ?? (Number(en[1]) || null);
    if (/\bmonocil/i.test(engineText)) return 1;
    if (/\bbicil/i.test(engineText)) return 2;
    if (/\btricil/i.test(engineText)) return 3;
    const v = /\bV[\s-]?(\d)\b/i.exec(engineText);
    if (v) return Number(v[1]);
    if (/\bV[\s-]?twin\b/i.test(engineText)) return 2;
    const pt = /\b(\d)\s*cilindros?\b/i.exec(engineText);
    return pt ? Number(pt[1]) : null;
}

function valvesPerCylinderOf(engineText, cylinders) {
    if (!engineText) return null;
    const perCylinder = /(\d+)\s*(?:valves?\s*per\s*cylinder|válvulas?\s*por\s*cilindro)/i.exec(engineText);
    if (perCylinder) return Number(perCylinder[1]);
    // A bare total ("DOHC, 8 válvulas") only becomes a per-cylinder figure once the count is known.
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
    const hp = unit(text, 'hp|bhp|cv|ps\\b');
    if (hp != null) return [bounded(round(hp, 1), 0.5, 350, dropped, 'max_power_hp'), rpm];
    const kw = unit(text, 'kw\\b');
    if (kw != null) return [bounded(round(kw * 1.34102, 1), 0.5, 350, dropped, 'max_power_hp'), rpm];
    return [null, rpm];
}

function torqueOf(text, dropped) {
    if (!text) return [null, null];
    const rpm = rpmOf(text, dropped, 'max_torque_rpm');
    // Nm wins wherever the source printed it, however far along the string it sits.
    const nm = unit(text, 'n[\\s.-]?m\\b');
    if (nm != null) return [bounded(round(nm, 1), 1, 300, dropped, 'max_torque_nm'), rpm];
    const kgf = unit(text, 'kgf?[\\s.·-]?m\\b');
    if (kgf != null) return [bounded(round(kgf * 9.80665, 1), 1, 300, dropped, 'max_torque_nm'), rpm];
    const lbft = unit(text, 'lb[\\s.-]?ft\\b');
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
    for (const key of ['Fuel Consumption', 'Consumption Average', 'Consumption average', 'Average Consumption']) {
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

/** UBS is a linked brake system, not ABS, so it is left out rather than filed under abs_type. */
function absTypeOf(extra) {
    const official = extra['[Freios] Sistema de freios'];
    if (official && /^ABS/i.test(official)) return official;
    return extra['ABS'] || extra['ABS System'] || null;
}

function compressionOf(text) {
    if (!text) return null;
    // "11.5 :1", "11,5 : 1" and "13,8.1" all describe the same thing; store one shape.
    const m = /(\d[\d.,]*)\s*[:.]\s*1\b/.exec(text);
    if (m) {
        const v = toNumber(m[1]);
        if (v != null && v >= 4 && v <= 20) return `${round(v, 1)}:1`;
    }
    return clean(text, 20);
}

const EMISSION_ALIASES = { EURO5: 'Euro 5', EURO4: 'Euro 4', EURO3: 'Euro 3' };
const emissionOf = (extra) => {
    const raw = clean(extra['Emission'] || extra['Emissions'], 30);
    return raw ? EMISSION_ALIASES[raw.toUpperCase().replace(/\s+/g, '')] || raw : null;
};

// The catalogue enum has no Street/Trail/Racing member, and the source labels do not partition
// cleanly onto it, so this only ever feeds the (currently empty) insert path for a slug the
// catalogue does not already hold. Existing rows keep the category they were seeded with.
const CATEGORY_BY_SOURCE_LABEL = {
    naked: 'NAKED', street: 'NAKED', 'naked / street': 'NAKED', 'street / sport': 'NAKED',
    sport: 'SPORT', esportiva: 'SPORT', racing: 'SPORT',
    adventure: 'ADVENTURE', touring: 'TOURING', scooter: 'SCOOTER',
    trail: 'OFF_ROAD', 'trail / on-off road': 'OFF_ROAD',
};

// --- long-tail specs ----------------------------------------------------------------------
// Deliberately a curated subset, and deliberately the same key names the Honda import uses, so a
// side-by-side comparison of a Yamaha and a Honda lines its long-tail rows up instead of doubling
// them. Ratios, reduction gears, braking-distance tests and the scraper's own bookkeeping are
// dropped: they are not rider-facing catalogue facts.
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
    'Oil Capacity': 'Capacidade de Óleo',
    'Anos de Fabricação': 'Anos de Fabricação',
    Trail: 'Trail',
    Rake: 'Ângulo de Cáster',
    Caster: 'Ângulo de Cáster',
    'Caster Angle': 'Ângulo de Cáster',
    'Caster angle': 'Ângulo de Cáster',
    Lubrication: 'Lubrificação',
    Battery: 'Bateria',
    'Spark Plug': 'Vela de Ignição',
    Sparkplug: 'Vela de Ignição',
    Exhaust: 'Escape',
    Cor: 'Cores',
    Colours: 'Cores',
    'Curso da Suspensão Dianteira': 'Curso da Suspensão Dianteira',
    'Front Travel': 'Curso da Suspensão Dianteira',
    'Curso da Suspensão Traseira': 'Curso da Suspensão Traseira',
    'Rear Travel': 'Curso da Suspensão Traseira',
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
    const engineText = moto['Motor'];
    const cylinders = bounded(cylindersOf(engineText, toNumber(extra['[Motor] Cilindros'])), 1, 8, dropped, 'cylinders');
    const [powerHp, powerRpm] = powerOf(moto['Potência Máxima'] || extra['[Motor] Potência (Gasolina)'], dropped);
    const [torqueNm, torqueRpm] = torqueOf(moto['Torque Máximo'] || extra['[Motor] Torque (Gasolina)'], dropped);

    const kerb = bounded(round(measure(moto['Peso em Ordem de Marcha'], 'kg'), 1), 20, 500, dropped, 'kerb_weight_kg');
    let dry = bounded(round(measure(moto['Peso Seco'] || extra['Dry Wight'], 'kg'), 1), 20, 500, dropped, 'dry_weight_kg');
    // ck_dimensions_dry_weight_below_kerb: a source pair the wrong way round would abort the import.
    if (dry != null && kerb != null && dry > kerb) {
        dropped.dry_weight_above_kerb = (dropped.dry_weight_above_kerb || 0) + 1;
        dry = null;
    }

    const gearsText = moto['Transmissão'];
    const gearsMatch = gearsText && /(\d+)\s*(?:speed|velocidades|marchas)/i.exec(gearsText);

    rows.push({
        slug: moto.id,
        brand: 'Yamaha',
        model: clean(moto.modelo, 120),
        modelYear: moto.ano,
        category: CATEGORY_BY_SOURCE_LABEL[String(extra['Categoria'] || '').toLowerCase()] || 'NAKED',

        frame_type: clean(moto['Chassis'], 120),
        front_suspension: clean(moto['Suspensão Dianteira'], 160),
        rear_suspension: clean(moto['Suspensão Traseira'], 160),
        front_brake: clean(moto['Freio dianteiro'], 160),
        rear_brake: clean(moto['Freio traseiro'], 160),
        abs_type: clean(absTypeOf(extra), 80),
        front_tyre: clean(moto['Pneu Dianteiro'], 60),
        rear_tyre: clean(moto['Pneu Traseiro'], 60),

        engine_type: clean(engineText, 80),
        displacement_cc: bounded(Math.round(measure(moto['Cilindrada'], 'cc|cm³|cm3|ccm', { bare: true }) ?? NaN) || null, 30, 2500, dropped, 'displacement_cc'),
        cylinders,
        valves_per_cylinder: bounded(valvesPerCylinderOf(engineText, cylinders), 1, 8, dropped, 'valves_per_cylinder'),
        max_power_hp: powerHp,
        max_power_rpm: powerRpm,
        max_torque_nm: torqueNm,
        max_torque_rpm: torqueRpm,
        compression_ratio: compressionOf(moto['Taxa de Compressão']),
        bore_mm: bounded(round(measure(moto['Diâmentro do Cilindro'], 'mm', { bare: true }), 2), 20, 150, dropped, 'bore_mm'),
        stroke_mm: bounded(round(measure(moto['Curso'], 'mm', { bare: true }), 2), 20, 150, dropped, 'stroke_mm'),
        cooling_system: clean(moto['Refrigeração'] || extra['Cooling Systom'], 40),
        fuel_system: clean(moto['Alimentação'], 120),
        transmission_type: clean(gearsText, 60),
        gears: bounded(gearsMatch ? Number(gearsMatch[1]) : null, 1, 8, dropped, 'gears'),
        final_drive: clean(extra['Final Drive'] || extra['Final drive'] || extra['[Câmbio] Transmissão final'], 40),
        top_speed_kph: topSpeedOf(moto['Velocidade Máxima'], dropped),
        fuel_consumption_l_100km: consumptionOf(extra, dropped),
        emission_standard: emissionOf(extra),

        length_mm: bounded(Math.round(measure(moto['Comprimento'], 'mm', { bare: true }) ?? NaN) || null, 1200, 3000, dropped, 'length_mm'),
        width_mm: bounded(Math.round(measure(moto['Largura'], 'mm', { bare: true }) ?? NaN) || null, 400, 1500, dropped, 'width_mm'),
        height_mm: bounded(Math.round(measure(moto['Altura'], 'mm', { bare: true }) ?? NaN) || null, 700, 1800, dropped, 'height_mm'),
        wheelbase_mm: bounded(Math.round(measure(moto['Distância Entre Eixos'], 'mm', { bare: true }) ?? NaN) || null, 800, 2000, dropped, 'wheelbase_mm'),
        seat_height_mm: bounded(Math.round(measure(moto['Altura do Assento'], 'mm', { bare: true }) ?? NaN) || null, 400, 1100, dropped, 'seat_height_mm'),
        ground_clearance_mm: bounded(Math.round(measure(moto['Distância do Solo'], 'mm', { bare: true }) ?? NaN) || null, 50, 400, dropped, 'ground_clearance_mm'),
        kerb_weight_kg: kerb,
        dry_weight_kg: dry,
        // Litres only. "14 Litres / 3.7 US gal" must never fall through to the gallon figure.
        fuel_capacity_l: bounded(round(measure(moto['Capacidade do Tanque'], 'l\\b|lt\\b|litros?|litres?'), 1), 1, 50, dropped, 'fuel_capacity_l'),

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

const out = [];
const w = (line = '') => out.push(line);

w('-- Motorcycle Comparison API - Yamaha technical specifications');
w(`-- Generated from zontes-scraper/yamaha_motos.json (scraped ${snapshot.gerado_em}) by tools/import-yamaha-specs.mjs.`);
w('--');
w('-- Sources, as recorded by the scraper:');
for (const [name, description] of Object.entries(snapshot.fontes)) w(`--   ${name}: ${description}`);
w('--');
w('-- The JSON "id" is the same base slug the FIPE seed derives, so rows join on motorcycles.slug rather');
w(`-- than on any fuzzy name match. All ${snapshot.motos.length} scraped model-years already exist in the catalogue`);
w('-- (785 from the FIPE snapshot, 4 from R__dev_seed.sql), so against today\'s seeds this import creates no');
w(`-- motorcycles and only fills gaps. ${emitted.length} of the ${snapshot.motos.length} carry something usable and are emitted; the`);
w(`-- other ${snapshot.motos.length - emitted.length} were scraped without a single published figure or photo. The insert path below still`);
w('-- runs: whether a row is new is derived from the database at run time rather than from a flag baked in');
w('-- here, so a slug the catalogue does not have is created rather than silently dropped.');
w('--');
w('-- Nothing is estimated. A value absent from the source stays NULL, and every figure below was parsed');
w('-- from the source string: hp from hp/bhp/cv/PS/kW, Nm from Nm/kgf.m/lb-ft, km/h from km/h/mph, and');
w('-- l/100km from the explicit l/100km and km/l forms only (a bare "mpg" is ambiguous UK vs US, so it is');
w('-- skipped rather than guessed). The two sources disagree on decimal convention inside the same file');
w('-- ("68,9 cv" beside "84.6 kW", "13.500 RPM" beside "10000 rpm"), so a separator is read from its own');
w('-- shape: exactly three trailing digits is a thousands group, anything else is a decimal point.');
w('--');
w('-- A few source strings space their digits out. "1 135.3 mm" is the SI thousands separator and is');
w('-- repaired to 1135.3; "2 40 kg" and "@ 4 75 0 rpm" are simply garbled, and are refused rather than');
w(`-- read as 40 kg and 0 rpm - a plausibility bound cannot catch a wrong figure that lands inside the`);
w(`-- range. ${MALFORMED.count} field reads were refused on that rule.`);
w('--');
w('-- Implausible figures are dropped rather than stored, each against the range its column can sensibly');
w('-- hold (the source carries a "790 mm" ground clearance, which is a seat height on the wrong row):');
const droppedEntries = Object.entries(dropped).sort((a, b) => b[1] - a[1]);
w(`--   ${droppedEntries.map(([k, v]) => `${k} ${v}`).join(', ') || 'none'}`);
w(`-- ${TRUNCATED.count} values exceeded their column width and were cut at a word boundary rather than mid-word.`);
w('--');
w('-- Caveat worth knowing before trusting any single row: the official Yamaha sheet covers only the');
w('-- current line, so everything out of production leans on motorcyclespecs.co.za, which publishes the');
w('-- global sheet rather than the Brazilian one, and reuses a single sheet across a model\'s whole');
w('-- production run. Figures are representative of the model, not specific to the year on the row.');
w('--');
w('-- Images. The scraper stores its gallery under img/yamaha/<model>/, which is per model, not per');
w(`-- model-year, so the ${distinctImages} distinct photos below cover ${withImage} rows. Each row still gets its own stored`);
w('-- copy under a name derived from its slug (UUID v5, matching the shape FileStorageServiceImpl will');
w('-- read): MotorcycleService treats the file as owned by the row and deletes it when the motorcycle or');
w('-- its image is deleted, so a shared file name would blank every other row pointing at it. Those files');
w('-- are NOT in the repository - uploads/ is gitignored. Run `node tools/import-yamaha-specs.mjs` to');
w('-- materialise them from a zontes-scraper checkout; until then these rows serve a 404 for their image.');
w('--');
w('-- Existing rows are only ever gap-filled: every write is COALESCE(existing, imported), so an admin');
w('-- edit, an uploaded image or a richer earlier import is never overwritten. Long-tail specs use');
w('-- ON CONFLICT DO NOTHING, which leaves the FIPE seed\'s own \'Fuel\' and \'Reference price (BRL)\' intact,');
w('-- and reuse the key names the Honda import chose so a Yamaha/Honda comparison lines its rows up.');
w('-- Category is deliberately not touched: the source labels (Street, Trail, Racing, Esportiva) do not');
w('-- partition onto the catalogue enum, and overwriting 785 rows on a lossy mapping would degrade the');
w('-- segment filter rather than improve it. Repeatable and idempotent: re-running changes nothing once');
w('-- it has been applied.');
w('--');
w(`-- Populated column counts across the ${emitted.length} imported rows:`);
w(`--   engine: displacement_cc ${populated.displacement_cc}, max_power_hp ${populated.max_power_hp}, max_torque_nm ${populated.max_torque_nm}, gears ${populated.gears}, cylinders ${populated.cylinders}`);
w(`--   frame:  frame_type ${populated.frame_type}, front_suspension ${populated.front_suspension}, front_tyre ${populated.front_tyre}, abs_type ${populated.abs_type}`);
w(`--   dims:   kerb_weight_kg ${populated.kerb_weight_kg}, seat_height_mm ${populated.seat_height_mm}, fuel_capacity_l ${populated.fuel_capacity_l}`);
w(`--   image_url ${withImage}; long-tail spec rows: ${specRowCount}`);
w();
w('BEGIN;');
w();
w('CREATE TEMP TABLE tmp_yamaha_spec_import (');
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
w('CREATE TEMP TABLE tmp_yamaha_spec_kv (');
w('    row_no     bigint NOT NULL,');
w('    spec_key   varchar(80) NOT NULL,');
w('    spec_value varchar(500),');
w('    PRIMARY KEY (row_no, spec_key)');
w(') ON COMMIT DROP;');
w();

const BATCH = 200;
for (let start = 0; start < emitted.length; start += BATCH) {
    const batch = emitted.slice(start, start + BATCH);
    w(`INSERT INTO tmp_yamaha_spec_import (${IMPORT_COLUMNS.join(', ')}) VALUES`);
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
    w('INSERT INTO tmp_yamaha_spec_kv (row_no, spec_key, spec_value) VALUES');
    batch.forEach((pair, i) => w(`${pair}${i === batch.length - 1 ? ';' : ','}`));
    w();
}

w('-- Bind every imported row to its catalogue row. The FIPE seed derives the same slug from the same');
w('-- FIPE model descriptor, so this is an equality join and not a fuzzy match.');
w('UPDATE tmp_yamaha_spec_import s');
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
w('UPDATE tmp_yamaha_spec_import SET is_new = (motorcycle_id IS NULL);');
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
w('UPDATE tmp_yamaha_spec_import');
w("SET motorcycle_id = nextval(pg_get_serial_sequence('motorcycles', 'id')::regclass),");
w("    engine_id     = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)");
w('WHERE is_new;');
w();
w('-- A dimension row is created only where the source published a measurement; an empty placeholder');
w('-- would add a row that exists only to render dashes in the comparison table.');
w('UPDATE tmp_yamaha_spec_import');
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
w('FROM tmp_yamaha_spec_import');
w('WHERE is_new');
w('ORDER BY row_no;');
w();
w('INSERT INTO dimensions (');
w('    id, length_mm, width_mm, height_mm, wheelbase_mm, seat_height_mm, ground_clearance_mm,');
w('    kerb_weight_kg, dry_weight_kg, fuel_capacity_l');
w(')');
w('SELECT dimension_id, length_mm, width_mm, height_mm, wheelbase_mm, seat_height_mm, ground_clearance_mm,');
w('       kerb_weight_kg, dry_weight_kg, fuel_capacity_l');
w('FROM tmp_yamaha_spec_import');
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
w('FROM tmp_yamaha_spec_import');
w('WHERE is_new');
w('ORDER BY row_no;');
w();
w('-- ---------------------------------------------------------------------------');
w('-- Existing models: gap-fill only.');
w('-- ---------------------------------------------------------------------------');
w();
w('-- The FIPE seed created an engine block for every row it inserted, but an earlier import may not');
w('-- have. Give those an engine row first, so their figures are not silently dropped below.');
w('UPDATE tmp_yamaha_spec_import');
w("SET engine_id = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)");
w('WHERE NOT is_new AND engine_id IS NULL;');
w();
w('INSERT INTO engine_specifications (id)');
w('SELECT s.engine_id');
w('FROM tmp_yamaha_spec_import s');
w('WHERE NOT s.is_new');
w('  AND s.engine_id IS NOT NULL');
w('  AND NOT EXISTS (SELECT 1 FROM engine_specifications e WHERE e.id = s.engine_id)');
w('ORDER BY s.row_no;');
w();
w('UPDATE motorcycles m');
w('SET engine_specification_id = s.engine_id');
w('FROM tmp_yamaha_spec_import s');
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
w('FROM tmp_yamaha_spec_import s');
w('WHERE e.id = s.engine_id AND NOT s.is_new;');
w();
w('-- A row seeded from FIPE carries no dimension block at all, so allocate one wherever this import');
w('-- has something to put in it.');
w('UPDATE tmp_yamaha_spec_import');
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
w('FROM tmp_yamaha_spec_import s');
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
w('FROM tmp_yamaha_spec_import s');
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
w('FROM tmp_yamaha_spec_import s');
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
w('FROM tmp_yamaha_spec_kv k');
w('JOIN tmp_yamaha_spec_import s ON s.row_no = k.row_no');
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
w('    FROM tmp_yamaha_spec_import s');
w('    LEFT JOIN motorcycles m ON m.id = s.motorcycle_id');
w('    WHERE m.id IS NULL;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Yamaha spec import: % rows did not resolve to a catalogue row', bad;");
w('    END IF;');
w();
w('    SELECT count(*) INTO bad');
w('    FROM tmp_yamaha_spec_import s');
w('    JOIN motorcycles m ON m.id = s.motorcycle_id');
w('    WHERE s.engine_id IS NOT NULL AND m.engine_specification_id IS DISTINCT FROM s.engine_id;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Yamaha spec import: % rows have a mismatched engine block', bad;");
w('    END IF;');
w();
w('    SELECT count(*) INTO bad');
w('    FROM dimensions');
w('    WHERE dry_weight_kg IS NOT NULL AND kerb_weight_kg IS NOT NULL AND dry_weight_kg > kerb_weight_kg;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Yamaha spec import: % dimension rows have dry weight above kerb weight', bad;");
w('    END IF;');
w();
w('    -- lower(brand), not brand: the FIPE seed stores "YAMAHA" and R__dev_seed.sql stores "Yamaha",');
w('    -- because V4 normalised casing before either seed had inserted a row. An equality test here');
w('    -- would silently check four rows out of 789.');
w('    SELECT count(*) INTO bad');
w('    FROM motorcycles');
w("    WHERE lower(brand) = 'yamaha' AND slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';");
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Yamaha spec import: % rows have a slug the public routing cannot use', bad;");
w('    END IF;');
w();
w('    -- Every stored image URL has to be a name ImageController can actually serve: FileStorageServiceImpl');
w('    -- reads UUID + jpg/png/webp and nothing else, so a name outside that shape is a silent 404.');
w('    SELECT count(*) INTO bad');
w('    FROM tmp_yamaha_spec_import');
w('    WHERE image_url IS NOT NULL');
w("      AND image_url !~ '^/api/v1/images/motorcycles/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$';");
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Yamaha spec import: % image URLs are not a name the image endpoint can serve', bad;");
w('    END IF;');
w('END $$;');
w();
w('COMMIT;');
w();

fs.writeFileSync(SQL_PATH, out.join('\n'), 'utf8');
console.log(`sql:    ${emitted.length} rows, ${specRowCount} long-tail specs, ${withImage} image URLs -> ${path.relative(REPO_ROOT, SQL_PATH)}`);
console.log(`dropped as implausible: ${droppedEntries.map(([k, v]) => `${k}=${v}`).join(' ') || 'none'}`);
console.log(`truncated to column width: ${TRUNCATED.count}; refused as garbled digits: ${MALFORMED.count}`);
