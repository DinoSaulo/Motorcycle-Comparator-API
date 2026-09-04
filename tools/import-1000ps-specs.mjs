#!/usr/bin/env node
// Regenerates db/seed/R__zzzz_motorcycles_1000ps_specs_2026_09.sql from the zontes-scraper new_data snapshot and the
// images it points at. Usage: node <this> [--source <dir, default ../zontes-scraper/new_data>] [--sql-only|--images-only]

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const args = process.argv.slice(2);
const option = (name, fallback) => {
    const i = args.indexOf(name);
    return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};
const flag = (name) => args.includes(name);

const SOURCE_DIR = path.resolve(option('--source', path.join(REPO_ROOT, '..', 'zontes-scraper', 'new_data')));
const JSON_PATH = path.resolve(option('--json', path.join(SOURCE_DIR, 'models.json')));
// The quadruple "zzzz" is load-bearing. Flyway orders repeatable migrations by description, and this file has to run
// after "zzz motorcycle available countries brazil" so the rows it creates are not swept into the Brazil backfill.
const SQL_PATH = path.join(REPO_ROOT, 'src/main/resources/db/seed/R__zzzz_motorcycles_1000ps_specs_2026_09.sql');
const IMAGE_DIR = path.join(REPO_ROOT, 'uploads/motorcycles');
const IMAGE_URL_PREFIX = '/api/v1/images/motorcycles/';

const doSql = !flag('--images-only');
const doImages = !flag('--sql-only');

// --- slug -------------------------------------------------------------------------------------
/** The Unicode combining-mark block, matching the \p{M} that MotorcycleService.slugify strips. */
const COMBINING_MARKS = /[̀-ͯ]/g;

// Byte-for-byte MotorcycleService.slugify: NFD, strip combining marks, lowercase, non-alnum to
// dashes, trim edge dashes. The base slug the FIPE seed derives is brand + model + model year.
function slugify(raw) {
    const ascii = String(raw).normalize('NFD').replace(COMBINING_MARKS, '').toLowerCase();
    return ascii.replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

const SLUG_FORMAT = /^[a-z0-9]+(-[a-z0-9]+)*$/;

// --- numbers ----------------------------------------------------------------------------------
const round = (n, dp) => (n == null ? null : Math.round(n * 10 ** dp) / 10 ** dp);

/** Out-of-range means the source string was misread or the source itself is wrong; either way, drop it. */
function bounded(value, min, max, dropped, label) {
    if (value == null || !Number.isFinite(value)) return null;
    if (value < min || value > max) {
        dropped[label] = (dropped[label] || 0) + 1;
        return null;
    }
    return value;
}

/** A number already typed as a number in the JSON; anything else is refused rather than coerced. */
const numberOf = (value) => (typeof value === 'number' && Number.isFinite(value) ? value : null);

// A separator is read from its own shape: exactly three trailing digits is a thousands group.
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

/** A number may not begin immediately after a digit, or after a digit and a space. */
const NUMBER_START = '(?<!\\d)(?<!\\d\\s)';

const unit = (rawText, unitPattern) => {
    if (!rawText) return null;
    const m = new RegExp(`${NUMBER_START}(\\d[\\d.,]*)\\s*(?:${unitPattern})`, 'i').exec(String(rawText));
    return m ? toNumber(m[1]) : null;
};

/** A {value, unit} pair, the shape every measured field inside outros_dados arrives in. */
const measured = (pair) => (pair && typeof pair === 'object' ? numberOf(pair.value) : null);

// --- text -------------------------------------------------------------------------------------
const TRUNCATED = { count: 0 };
const REPAIRED = { count: 0 };

// This source joins the parts of a compound value with a middle dot ("Steel · Double cradle"), which
// reads as punctuation nowhere else in the catalogue; a comma is what the other imports store.
const dotted = (text) => (text == null ? null : String(text).replace(/\s*·\s*/g, ', '));

// Collapses whitespace, drops trailing table punctuation, and swaps a stray replacement
// character for a space - extraction damage, not source content.
function clean(text, maxLength) {
    if (text == null) return null;
    const before = String(text);
    let t = before.replace(/�/g, ' ');
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

// A descriptive column that carries only a measurement is a transposed label, not prose
// (memory: scraper-source-defects). Reject anything with no real word in it.
const BARE_MEASUREMENT = /^[\d.,\s\-–—]+\s*(mm|cm|kg|cc|l|kgf\.?m|rpm|psi|bar)?\.?$/i;

function proseField(raw, maxLen, label, dropped) {
    const val = clean(dotted(raw), maxLen);
    if (val == null) return null;
    if (BARE_MEASUREMENT.test(val)) {
        dropped[label] = (dropped[label] || 0) + 1;
        return null;
    }
    return val;
}

// A brake or suspension entry is routinely published here as nothing but the figure that defines it - a
// 320 mm disc, a 41 mm fork - so for those two the guard above would throw away the field's real content.
const terseField = (raw, maxLen) => clean(dotted(raw), maxLen);

// Tyre sizes are short alnum codes ("120/70 R17") and never a run of 4+ letters, which is
// what a mislabelled prose field landing here would look like.
function tyreField(raw, maxLen, label, dropped) {
    const val = clean(raw, maxLen);
    if (val == null) return null;
    if (!/\d/.test(val) || /[a-z]{4,}/i.test(val) || val.length > 24) {
        dropped[label] = (dropped[label] || 0) + 1;
        return null;
    }
    return val;
}

// --- per-field extraction -----------------------------------------------------------------------
// "In line · 2 cilindro(s) · 4-Stroke · 4 valvulas/cilindro · DOHC" - this source prints the count as a
// digit next to a Portuguese label, so none of the English layout prose the other imports parse applies.
const cylindersOf = (engineText) => (engineText ? toNumber((/(\d+)\s*cilindro/i.exec(String(engineText)) || [])[1]) : null);
const valvesOf = (engineText) => (engineText ? toNumber((/(\d+)\s*valvulas?\s*\/\s*cilindro/i.exec(String(engineText)) || [])[1]) : null);

// "19 HP @ 7500 rpm". Only HP is ever printed here, but kW is read as a fallback so a future
// re-scrape that switches units is not silently dropped.
function powerOf(text, dropped) {
    if (!text) return [null, null];
    const rpm = bounded(unit(text, 'rpm'), 500, 20000, dropped, 'max_power_rpm');
    const hp = unit(text, 'hp|bhp|cv|ps\\b');
    if (hp != null) return [bounded(round(hp, 1), 0.5, 400, dropped, 'max_power_hp'), rpm];
    const kw = unit(text, 'kw\\b');
    if (kw != null) return [bounded(round(kw * 1.34102, 1), 0.5, 400, dropped, 'max_power_hp'), rpm];
    return [null, rpm];
}

// "68 Nm @ 6500 rpm". kgf.m is converted at 9.80665 only when no Nm figure is printed.
function torqueOf(text, dropped) {
    if (!text) return [null, null];
    const rpm = bounded(unit(text, 'rpm'), 500, 20000, dropped, 'max_torque_rpm');
    const nm = unit(text, 'n[\\s.-]?m\\b');
    if (nm != null) return [bounded(round(nm, 1), 1, 400, dropped, 'max_torque_nm'), rpm];
    const kgf = unit(text, 'kgf?[\\s.·-]?m\\b');
    if (kgf != null) return [bounded(round(kgf * 9.80665, 1), 1, 400, dropped, 'max_torque_nm'), rpm];
    return [null, rpm];
}

// The ratio arrives as the bare left-hand side ("12", "11.5"), never as "12:1", so the ":1" the
// column stores everywhere else has to be put back rather than parsed out.
function compressionOf(value, dropped) {
    const n = numberOf(value) ?? toNumber(value);
    if (n == null) return null;
    if (n < 4 || n > 20) {
        dropped.compression_ratio = (dropped.compression_ratio || 0) + 1;
        return null;
    }
    return `${round(n, 1)}:1`;
}

// "liquid" / "Air" / "Oil-air": one lowercase token, capitalised to match what the seeds already store.
const coolingOf = (text) => {
    const val = clean(dotted(text), 40);
    return val == null ? null : val.charAt(0).toUpperCase() + val.slice(1);
};

// The source publishes exactly two values in this field and neither is the phrasing the catalogue uses.
const FUEL_SYSTEMS = { injection: 'Fuel injection', carburator: 'Carburettor' };
const fuelSystemOf = (text) => (text ? FUEL_SYSTEMS[String(text).trim().toLowerCase()] ?? clean(text, 120) : null);

// "Chain · 6 marchas · Gearshift" packs the final drive, the gear count and the gearbox kind into one
// field; each belongs in a column of its own.
const FINAL_DRIVES = { chain: 'Chain', belt: 'Belt', 'prop shaft': 'Shaft', direct: 'Direct' };
const GEARBOX_KINDS = { gearshift: 'manual', automatic: 'automatic', variomatic: 'variomatic' };

function transmissionOf(text, dropped) {
    if (!text) return { transmission_type: null, gears: null, final_drive: null };
    const tokens = String(text).split('·').map((t) => t.trim()).filter(Boolean);
    let finalDrive = null;
    let kind = null;
    let gears = null;
    for (const token of tokens) {
        const key = token.toLowerCase();
        if (FINAL_DRIVES[key]) finalDrive = FINAL_DRIVES[key];
        else if (GEARBOX_KINDS[key]) kind = GEARBOX_KINDS[key];
        else if (/(\d+)\s*marchas/i.test(token)) gears = toNumber(/(\d+)\s*marchas/i.exec(token)[1]);
    }
    gears = bounded(gears, 1, 8, dropped, 'gears');
    let type = null;
    if (gears != null && kind != null) type = `${gears}-speed ${kind}`;
    else if (gears != null) type = `${gears}-speed`;
    else if (kind != null) type = kind.charAt(0).toUpperCase() + kind.slice(1);
    return { transmission_type: clean(type, 60), gears, final_drive: finalDrive };
}

// ABS has no field of its own: it is an entry in the rider-assistance list, or a word inside the brake
// description. Recording the bare word keeps this an observation, not a guess at which generation it is.
function absTypeOf(assists, ...brakeTexts) {
    const listed = Array.isArray(assists) && assists.some((a) => /^abs$/i.test(String(a).trim()));
    return listed || brakeTexts.some((t) => t && /\bABS\b/.test(String(t))) ? 'ABS' : null;
}

// --- category ---------------------------------------------------------------------------------
// The source segment is authoritative here: it is published for 94.5% of the snapshot and its taxonomy
// is finer than any keyword rule over a model name could be.
const SOURCE_CATEGORIES = {
    'scooter': 'SCOOTER',
    'naked bike': 'NAKED',
    'supersport': 'SPORT',
    'sport touring motorcycle': 'TOURING',
    'touring': 'TOURING',
    'chopper/cruiser': 'CRUISER',
    'supermoto': 'SUPERMOTO',
    'motocross': 'OFF_ROAD',
    'trial': 'OFF_ROAD',
    // The source keeps Motocross, Trial and Supermoto as segments of their own, so what is left in its
    // "Enduro" bucket is the road-legal dual-purpose and adventure segment, not the competition dirt bikes.
    'enduro motorcycle': 'ADVENTURE',
};

// Motorcycles the source files under an era rather than a segment; the keyword rules below read the
// model name instead, and NAKED is the fallback a classic roadster fits best.
const ERA_LABELS = new Set(['oldtimer', 'classics']);

// Vehicle classes this catalogue does not carry: quads, UTVs, Aixam microcars, e-bikes, pocketbikes,
// trikes and sidecar outfits. Staged for gap-fill if the slug already exists, but never inserted.
const NON_MOTORCYCLE_LABELS = new Set(['quad/atv', 'atv & side by side', 'side-by-side', 'others', 'e-bike', 'pocketbike', 'trike', 'sidecar']);

// The same keyword table the consolidated gap-fill uses, so a model that reaches both imports lands in
// the same segment. Most specific first, so "Africa Twin" is not read as a twin-cylinder naked.
const CATEGORY_RULES = [
    ['ELECTRIC', /\b(electric|el[ée]tric[ao]|e-?bike|zero\s?sr|livewire)\b/i],
    ['SCOOTER', /\b(pcx|nmax|xmax|burgman|scooter|vespa|lead|elite|biz|adress|address)\b/i],
    ['OFF_ROAD', /\b(cross|enduro|crf|kx|yz|wr|xr|off[\s-]?road|trail|motocross)\b/i],
    ['ADVENTURE', /\b(gs|tiger|t[ée]n[ée]r[ée]|africa\s?twin|v-?strom|versys|adventure|multistrada|transalp|himalayan)\b/i],
    ['CRUISER', /\b(cruiser|custom|chopper|rebel|vulcan|shadow|rocket|bonneville|speedmaster|meteor|classic)\b/i],
    ['SPORT', /\b(ninja|fireblade|r1|r6|panigale|gsx-?r|daytona|sprint|trophy|rr|cbr|zx)\b/i],
    ['NAKED', /\b(z\d|mt-?\d|duke|monster|cb\d|street\s?triple|trident|speed\s?triple|hornet|naked|dominar|pulsar)\b/i],
];

function keywordCategoryOf(model) {
    for (const [category, pattern] of CATEGORY_RULES) {
        if (pattern.test(model)) return category;
    }
    return null;
}

/** Returns the enum member plus whether a row carrying it may be inserted as a new catalogue row. */
function categoryOf(label, model) {
    const key = label ? String(label).trim().toLowerCase() : null;
    if (key && NON_MOTORCYCLE_LABELS.has(key)) return { category: null, mayCreate: false };
    if (key && ERA_LABELS.has(key)) {
        const guessed = keywordCategoryOf(model) ?? 'NAKED';
        return { category: guessed, mayCreate: true };
    }
    const mapped = key ? SOURCE_CATEGORIES[key] ?? null : null;
    return mapped ? { category: mapped, mayCreate: true } : { category: null, mayCreate: false };
}

// --- brand ------------------------------------------------------------------------------------
// Two brands the catalogue already holds under a different spelling. A case-only difference is fixed
// against the live catalogue in SQL instead, so it stays right as the catalogue changes.
const BRAND_ALIASES = { GASGAS: 'Gas Gas', 'Moto Morini': 'Motomorini' };

// --- long-tail specs ----------------------------------------------------------------------------
/** Renders any of the shapes this source uses - list, flag, {value, unit} pair, plain scalar - as one string. */
function specText(value, unitSuffix) {
    if (value == null || value === '') return null;
    if (Array.isArray(value)) return value.length > 0 ? clean(value.join(', '), 500) : null;
    if (typeof value === 'boolean') return value ? 'Sim' : 'Não';
    if (typeof value === 'object') {
        const n = numberOf(value.value);
        return n == null ? null : clean(`${n}${unitSuffix ?? (value.unit ? ` ${value.unit}` : '')}`, 500);
    }
    if (typeof value === 'number') return clean(`${value}${unitSuffix ?? ''}`, 500);
    return clean(dotted(value), 500);
}

// Rider-facing facts only. Scraper provenance (url_origem, coletado_em, model_id), the site's own
// rating chatter, the related-model graph and the price history are all deliberately left out.
const TOP_LEVEL_SPECS = [
    ['rodas', 'Rodas'],
    ['embreagem', 'Embreagem'],
    ['sistema_partida', 'Sistema de Partida'],
    ['sistema_chave_ignicao', 'Sistema de Chave de Ignição'],
    ['iluminacao', 'Iluminação'],
    ['painel', 'Painel'],
    ['modos_conducao', 'Modos de Condução'],
    ['tomada_usb', 'Tomada USB'],
    ['ajuste_parabrisas', 'Ajuste do Para-brisas'],
];

const EXTRA_SPECS = [
    ['trail', 'Trail', null],
    ['angulo_caster', 'Ângulo de Cáster', '°'],
    ['material_balanca', 'Material da Balança', null],
    ['freio_dianteiro_tecnologia', 'Tecnologia do Freio Dianteiro', null],
    ['assistencias', 'Assistências', null],
    ['equipamentos', 'Equipamentos', null],
    ['habilitacao', 'Habilitação', null],
    ['co2_combinado', 'Emissões de CO2', null],
    ['autonomia_km', 'Autonomia', ' km'],
    ['restringivel_a2', 'Restringível a A2', null],
];

function longTailSpecs(moto, extra) {
    const specs = new Map();
    for (const [field, label] of TOP_LEVEL_SPECS) {
        const value = specText(moto[field]);
        if (value) specs.set(label, value);
    }
    for (const [field, label, suffix] of EXTRA_SPECS) {
        const value = specText(extra[field], suffix);
        if (value) specs.set(label, value);
    }
    const years = Array.isArray(extra.anos_disponiveis) ? extra.anos_disponiveis.filter((y) => numberOf(y) != null) : [];
    if (years.length > 1) specs.set('Anos Disponíveis', `${Math.min(...years)}-${Math.max(...years)}`);
    const kwh = numberOf(extra.eletrico && extra.eletrico.batteryKwh);
    const volts = numberOf(extra.eletrico && extra.eletrico.batteryVolts);
    if (kwh != null) specs.set('Bateria', volts != null ? `${kwh} kWh (${volts} V)` : `${kwh} kWh`);
    return specs;
}

// --- image naming -------------------------------------------------------------------------------
// A UUID v5 over the slug: reproducible on any machine, and FileStorageServiceImpl only reads UUID + jpg/png/webp.
const UUID_NAMESPACE = Buffer.from('6ba7b8119dad11d180b400c04fd430c8', 'hex');

function uuidV5(name) {
    const digest = createHash('sha1').update(Buffer.concat([UUID_NAMESPACE, Buffer.from(name, 'utf8')])).digest();
    const bytes = Buffer.from(digest.subarray(0, 16));
    bytes[6] = (bytes[6] & 0x0f) | 0x50;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = bytes.toString('hex');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** One stored copy per motorcycle, keyed by slug: the app treats the file as owned by the row, so two rows
 *  sharing a source photo may not share a file name or deleting either would blank the other. */
function imageFor(moto, slug) {
    for (const relative of moto.imagens_locais || []) {
        const absolute = path.join(SOURCE_DIR, relative);
        const extension = path.extname(absolute).toLowerCase();
        if (!['.jpg', '.png', '.webp'].includes(extension)) continue;
        if (!fs.existsSync(absolute)) continue; // the scraper records a gallery it did not finish downloading
        return { source: absolute, fileName: `${uuidV5(slug)}${extension}` };
    }
    return null;
}

// --- build --------------------------------------------------------------------------------------
const snapshot = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
if (!Array.isArray(snapshot)) throw new Error(`${JSON_PATH} is expected to be a top-level array of models`);

const dropped = {};
const rows = [];
const badSlugs = [];
const excludedByLabel = {};

for (const moto of snapshot) {
    const extra = moto.outros_dados || {};
    const modelYear = bounded(numberOf(extra.ano), 1885, 2100, dropped, 'model_year');
    const brandRaw = clean(moto.marca, 60);
    const model = clean(moto.modelo, 120);
    if (!brandRaw || !model || modelYear == null) {
        dropped.unidentifiable = (dropped.unidentifiable || 0) + 1;
        continue;
    }

    const brand = BRAND_ALIASES[brandRaw] ?? brandRaw;
    const slug = slugify(`${brand} ${model} ${modelYear}`);
    if (!SLUG_FORMAT.test(slug) || slug.length > 160) {
        badSlugs.push(`${brand} ${model} ${modelYear}`);
        continue;
    }

    const { category, mayCreate } = categoryOf(extra.categoria, model);
    if (!mayCreate) {
        const key = extra.categoria ? String(extra.categoria) : '(sem categoria)';
        excludedByLabel[key] = (excludedByLabel[key] || 0) + 1;
    }

    const engineText = moto.motor;
    const cylinders = bounded(cylindersOf(engineText), 1, 8, dropped, 'cylinders');
    const [powerHp, powerRpm] = powerOf(moto.potencia_maxima, dropped);
    const [torqueNm, torqueRpm] = torqueOf(moto.torque_maximo, dropped);
    const transmission = transmissionOf(moto.transmissao, dropped);

    const kerb = bounded(round(numberOf(moto.peso_ordem_marcha_kg), 1), 20, 600, dropped, 'kerb_weight_kg');
    let dry = bounded(round(numberOf(moto.peso_seco_kg), 1), 20, 600, dropped, 'dry_weight_kg');
    // Dry weight excludes fluids and so cannot exceed kerb weight; ck_dimensions_dry_weight_below_kerb
    // would abort the whole import over a row where the source publishes it the other way round.
    if (dry != null && kerb != null && dry > kerb) {
        dropped.dry_weight_above_kerb = (dropped.dry_weight_above_kerb || 0) + 1;
        dry = null;
    }

    let bore = bounded(round(numberOf(moto.diametro_cilindro_mm), 2), 20, 150, dropped, 'bore_mm');
    let stroke = bounded(round(numberOf(moto.curso_mm), 2), 20, 150, dropped, 'stroke_mm');
    const displacement = bounded(round(numberOf(moto.cilindrada_cc), 0), 30, 2500, dropped, 'displacement_cc');
    // Bore, stroke, cylinders and displacement have one algebraic relation. Displacement is the better
    // published figure and the one the catalogue compares on, so the pair that contradicts it is dropped.
    if (bore != null && stroke != null && cylinders != null && displacement != null) {
        const swept = (Math.PI / 4) * bore ** 2 * stroke * cylinders / 1000;
        if (Math.abs(swept - displacement) > 0.05 * displacement) {
            dropped.contradictory_bore_stroke = (dropped.contradictory_bore_stroke || 0) + 1;
            bore = null;
            stroke = null;
        }
    }

    // Only the l/100km readings are taken: the field also carries kWh/100km for the electric models,
    // and the column is litres. A null unit beside a plausible litre figure is read as litres.
    const consumption = extra.consumo_combinado;
    const litres = consumption && (consumption.unit == null || /l\/100\s*km/i.test(String(consumption.unit))) ? measured(consumption) : null;

    rows.push({
        slug,
        may_create: mayCreate,
        brand,
        model,
        model_year: modelYear,
        category,
        price_eur: bounded(round(numberOf(extra.preco_medio_eur), 2), 1, 500000, dropped, 'price_eur'),
        image: imageFor(moto, slug),

        frame_type: proseField(moto.chassis, 120, 'frame_type', dropped),
        front_suspension: terseField(moto.suspensao_dianteira, 160),
        rear_suspension: terseField(moto.suspensao_traseira, 160),
        front_brake: terseField(moto.freio_dianteiro, 160),
        rear_brake: terseField(moto.freio_traseiro, 160),
        abs_type: absTypeOf(extra.assistencias, moto.freio_dianteiro, moto.freio_traseiro),
        front_tyre: tyreField(moto.pneu_dianteiro, 60, 'front_tyre', dropped),
        rear_tyre: tyreField(moto.pneu_traseiro, 60, 'rear_tyre', dropped),

        engine_type: proseField(engineText, 80, 'engine_type', dropped),
        displacement_cc: displacement,
        cylinders,
        valves_per_cylinder: bounded(valvesOf(engineText), 1, 8, dropped, 'valves_per_cylinder'),
        max_power_hp: powerHp,
        max_power_rpm: powerRpm,
        max_torque_nm: torqueNm,
        max_torque_rpm: torqueRpm,
        compression_ratio: compressionOf(moto.taxa_compressao, dropped),
        bore_mm: bore,
        stroke_mm: stroke,
        cooling_system: coolingOf(moto.refrigeracao),
        fuel_system: fuelSystemOf(moto.alimentacao),
        transmission_type: transmission.transmission_type,
        gears: transmission.gears,
        final_drive: transmission.final_drive,
        top_speed_kph: bounded(round(numberOf(moto.velocidade_maxima_kmh), 0), 20, 400, dropped, 'top_speed_kph'),
        fuel_consumption_l_100km: bounded(round(litres, 2), 0.5, 30, dropped, 'fuel_consumption_l_100km'),
        emission_standard: clean(extra.norma_euro, 30),

        length_mm: bounded(round(numberOf(moto.comprimento_mm), 0), 1200, 3500, dropped, 'length_mm'),
        width_mm: bounded(round(numberOf(moto.largura_mm), 0), 400, 1600, dropped, 'width_mm'),
        height_mm: bounded(round(numberOf(moto.altura_mm), 0), 700, 1800, dropped, 'height_mm'),
        wheelbase_mm: bounded(round(numberOf(moto.distancia_entre_eixos_mm), 0), 800, 2200, dropped, 'wheelbase_mm'),
        seat_height_mm: bounded(round(numberOf(moto.altura_assento_mm), 0), 400, 1100, dropped, 'seat_height_mm'),
        ground_clearance_mm: bounded(round(numberOf(moto.distancia_solo_mm), 0), 50, 400, dropped, 'ground_clearance_mm'),
        kerb_weight_kg: kerb,
        dry_weight_kg: dry,
        fuel_capacity_l: bounded(round(numberOf(moto.capacidade_tanque_l), 1), 1, 60, dropped, 'fuel_capacity_l'),

        specs: longTailSpecs(moto, extra),
    });
}

// A slug repeats when the source publishes the same model-year twice; the first row that carries anything
// wins, so the staging table keeps its slug unique and both the join and the insert stay one-to-one.
const bySlug = new Map();
let duplicateSlugs = 0;
for (const row of rows) {
    if (bySlug.has(row.slug)) {
        duplicateSlugs++;
        continue;
    }
    bySlug.set(row.slug, row);
}

// --- SQL emission ---------------------------------------------------------------------------------
const q = (value) => (value == null ? 'NULL' : `'${String(value).replace(/'/g, "''")}'`);
const n = (value) => (value == null ? 'NULL' : String(value));
const b = (value) => (value ? 'true' : 'false');

const MOTORCYCLE_COLUMNS = ['frame_type', 'front_suspension', 'rear_suspension', 'front_brake', 'rear_brake',
    'abs_type', 'front_tyre', 'rear_tyre'];

const ENGINE_COLUMNS = ['engine_type', 'displacement_cc', 'cylinders', 'valves_per_cylinder', 'max_power_hp',
    'max_power_rpm', 'max_torque_nm', 'max_torque_rpm', 'compression_ratio', 'bore_mm', 'stroke_mm',
    'cooling_system', 'fuel_system', 'transmission_type', 'gears', 'final_drive', 'top_speed_kph',
    'fuel_consumption_l_100km', 'emission_standard'];

const DIMENSION_COLUMNS = ['length_mm', 'width_mm', 'height_mm', 'wheelbase_mm', 'seat_height_mm',
    'ground_clearance_mm', 'kerb_weight_kg', 'dry_weight_kg', 'fuel_capacity_l'];

// The two columns whose merge has to wait until cylinders and displacement have settled; see the statement
// that fills them for why they cannot ride along with the rest of the engine block.
const GEOMETRY_COLUMNS = ['bore_mm', 'stroke_mm'];

const SPEC_COLUMNS = [...MOTORCYCLE_COLUMNS, ...ENGINE_COLUMNS, ...DIMENSION_COLUMNS];

const QUOTED = new Set(['frame_type', 'front_suspension', 'rear_suspension', 'front_brake', 'rear_brake',
    'abs_type', 'front_tyre', 'rear_tyre', 'engine_type', 'compression_ratio', 'cooling_system', 'fuel_system',
    'transmission_type', 'final_drive', 'emission_standard']);

// A row that may not create a catalogue entry and carries nothing usable would add bytes to this file and
// change nothing in the database, whether or not its slug resolves.
const emitted = [...bySlug.values()].filter((row) => row.may_create || SPEC_COLUMNS.some((c) => row[c] != null) || row.specs.size > 0 || row.image);

const creatable = emitted.filter((r) => r.may_create);
const gapFillOnly = emitted.length - creatable.length;
const populated = Object.fromEntries(SPEC_COLUMNS.map((c) => [c, emitted.filter((r) => r[c] != null).length]));
const specRowCount = emitted.reduce((sum, row) => sum + row.specs.size, 0);
const withDimensions = emitted.filter((r) => DIMENSION_COLUMNS.some((c) => r[c] != null)).length;
const withEngine = emitted.filter((r) => ENGINE_COLUMNS.some((c) => r[c] != null)).length;
const withImage = emitted.filter((r) => r.image).length;
const withPrice = emitted.filter((r) => r.price_eur != null).length;
const neverPublished = SPEC_COLUMNS.filter((c) => populated[c] === 0);
const specKeyCount = new Map();
for (const row of emitted) {
    for (const key of row.specs.keys()) specKeyCount.set(key, (specKeyCount.get(key) || 0) + 1);
}
const categoryCount = new Map();
for (const row of creatable) categoryCount.set(row.category, (categoryCount.get(row.category) || 0) + 1);

// --- image materialisation --------------------------------------------------------------------------
if (doImages) {
    fs.mkdirSync(IMAGE_DIR, { recursive: true });
    let copied = 0;
    let bytes = 0;
    for (const row of emitted) {
        if (!row.image) continue;
        const target = path.join(IMAGE_DIR, row.image.fileName);
        if (fs.existsSync(target)) continue;
        bytes += fs.statSync(row.image.source).size;
        fs.copyFileSync(row.image.source, target);
        copied++;
    }
    console.log(`images: ${withImage} rows carry one, ${copied} written to ${IMAGE_DIR} (${(bytes / 1048576).toFixed(1)} MB)`);
}

if (!doSql) process.exit(0);

const out = [];
const w = (line = '') => out.push(line);

w('-- Motorcycle Comparison API - 1000ps.com model catalogue import');
w('-- Generated from zontes-scraper/new_data/models.json by tools/import-1000ps-specs.mjs.');
w('--');
w(`-- ${snapshot.length} model-years across ${new Set(snapshot.map((m) => m.marca)).size} brands, scraped from 1000ps.com. Unlike every other import in this`);
w('-- directory this one is mostly an insert, not a gap-fill: the catalogue up to now came from the Brazilian');
w('-- FIPE table and this source uses European model naming, so almost nothing lines up. Which rows are new is');
w('-- decided at migration time against the live catalogue, never from a flag baked in here, so this file stays');
w('-- correct as the catalogue changes underneath it.');
w('--');
w('-- The source publishes a slug of its own, and it is deliberately ignored. Each row is keyed by');
w('-- slugify(marca + modelo + ano), byte-for-byte what MotorcycleService.slugify produces and the same base slug');
w('-- the FIPE seed derives, which is why the join is on motorcycles.slug and never on a fuzzy name match.');
w('--');
w('-- Flyway runs repeatable migrations in description order, so the file name alone decides when this runs, and');
w('-- the "zzzz" prefix is load-bearing twice over. It sorts after every spec seed, including');
w('-- "zz motorcycles specs gapfill", so a per-brand scrape keeps first claim on its own brand under COALESCE.');
w('-- And it sorts after "zzz motorcycle available countries brazil", which is what leaves the rows created here');
w('-- with no country at all: this catalogue is not a Brazilian-market snapshot and claiming BR for it would be');
w('-- an invention. Reorder this file before that one and every model below is silently stamped as sold in Brazil.');
w('--');
w('-- Not every vehicle in the source is a motorcycle. It also lists quads, UTVs, Aixam microcars, e-bikes,');
w('-- pocketbikes, trikes and sidecar outfits, and this is a motorcycle catalogue, so those never create a row.');
w(`-- ${gapFillOnly} such rows are still staged below with may_create = false: if one of them happens to share a slug with a`);
w('-- motorcycle the catalogue already holds, its figures are still worth merging. The same applies to the rows');
w('-- the source files under no segment at all, which could not be inserted anyway because category is NOT NULL.');
w('-- Rows excluded from insertion, by the source label that excluded them:');
for (const [label, count] of Object.entries(excludedByLabel).sort((x, y) => y[1] - x[1])) {
    w(`--   ${label}: ${count}`);
}
w('--');
w('-- Category comes from the source segment, which it publishes for 94.5% of the snapshot and whose taxonomy is');
w('-- finer than any keyword rule over a model name. "Enduro motorcycle" maps to ADVENTURE rather than OFF_ROAD:');
w('-- the source keeps Motocross, Trial and Supermoto as segments of their own, so what is left in that bucket is');
w('-- the road-legal dual-purpose segment. Only the handful of rows filed under an era ("Oldtimer", "Classics")');
w('-- fall back to the keyword table the consolidated gap-fill uses. Distribution of the categories written:');
for (const [category, count] of [...categoryCount].sort((x, y) => y[1] - x[1])) {
    w(`--   ${category}: ${count}`);
}
w('--');
w('-- Brand names are aligned with the catalogue rather than the source. A case-only difference ("CFMOTO" against');
w('-- the stored "CFMoto") is resolved in SQL below against the live catalogue, so it stays right as the catalogue');
w('-- changes; the two spellings that differ by more than case ("GASGAS" -> "Gas Gas", "Moto Morini" ->');
w('-- "Motomorini") are mapped at generation time. Without this the brand facet splits one manufacturer in two,');
w('-- which is the same defect V4__normalize_motorcycle_brand_casing.sql was written to repair.');
w('--');
w('-- Numbers: the dimension, displacement, bore, stroke, top-speed and price fields arrive already typed as');
w('-- numbers in the JSON and are only bounded and rounded to their column scale, never re-parsed from text. Power');
w('-- and torque are parsed from prose ("19 HP @ 7500 rpm", "68 Nm @ 6500 rpm"); kW and kgf.m are converted only');
w('-- when no HP or Nm figure is printed. Cylinder count and valves per cylinder are read from the "Motor" prose,');
w('-- which prints them as digits next to a Portuguese label ("In line, 2 cilindro(s), 4 valvulas/cilindro").');
w('-- The compression ratio arrives as the bare left-hand side ("11.5"), so the ":1" the column stores everywhere');
w('-- else is put back rather than parsed out. "Transmissao" packs three columns into one field');
w('-- ("Chain, 6 marchas, Gearshift") and is split into final_drive, gears and transmission_type.');
w('--');
w('-- price_eur is the source\'s own average market price in euros, the same kind of figure the FIPE reference');
w('-- price already in this column is; it is only ever gap-filled, so an existing price always wins.');
w('--');
w('-- The brake and suspension columns keep values that are nothing but a measurement ("320 mm", "41 mm"),');
w('-- unlike the other imports, which read a bare measurement in a prose column as a transposed label (memory:');
w('-- scraper-source-defects). Here it is the field\'s real content: this source publishes those entries as');
w('-- "Single disk, 256 mm" and simply omits the leading type token on about two thirds of the rows, so');
w('-- rejecting them would throw away the disc and fork diameters for most of the catalogue. frame_type keeps');
w('-- the guard, because that is the column the transposition defect was actually observed in.');
w('--');
w('-- One source defect is corrected rather than stored: dry weight excludes fluids and so cannot exceed kerb');
w(`-- weight, and ${dropped.dry_weight_above_kerb ?? 0} rows publish it above. Those are dropped here, and the merge below resolves the pair`);
w('-- again after COALESCE, because one bad figure would otherwise abort the entire import.');
w('--');
if (neverPublished.length > 0) {
    w(`-- ${neverPublished.length} of the ${SPEC_COLUMNS.length} specification columns are never published in this snapshot and stay NULL for every`);
    w('-- row (kept for structural parity with the other imports, so a richer re-scrape needs no SQL changes):');
    w(`--   ${neverPublished.join(', ')}`);
    w('--');
}
w(`-- Images. The scraper stores one gallery directory per model-year, and the first file in it that exists and is`);
w('-- a jpg/png/webp becomes this row\'s image, renamed to a UUID v5 over the slug so the name is reproducible on');
w('-- any machine and ImageController can serve it. One stored copy per motorcycle even where several model-years');
w('-- share a source photo: the app treats the file as owned by the row, so a shared name would blank the others.');
w(`-- Those files are not in the repository; run "node tools/import-1000ps-specs.mjs --images-only" against a`);
w('-- zontes-scraper checkout to materialise them, and until then these rows serve a 404 for their image.');
w('--');
w('-- Existing rows are only ever gap-filled: every write is COALESCE(existing, imported), so an admin edit or a');
w('-- richer earlier import always wins. Long-tail specs use ON CONFLICT DO NOTHING and reuse the Portuguese');
w('-- Title Case key names the other brand imports chose, so a cross-brand comparison lines its rows up.');
w('-- Repeatable and idempotent: re-running changes nothing once it has been applied.');
w('--');
w(`-- ${emitted.length} rows are emitted below, ${creatable.length} of them eligible to create a catalogue row. Populated column counts:`);
w(`--   engine: displacement_cc ${populated.displacement_cc}, max_power_hp ${populated.max_power_hp}, max_torque_nm ${populated.max_torque_nm}, cylinders ${populated.cylinders}, bore_mm ${populated.bore_mm}, stroke_mm ${populated.stroke_mm} (${withEngine} rows)`);
w(`--   frame:  front_brake ${populated.front_brake}, front_suspension ${populated.front_suspension}, frame_type ${populated.frame_type}, front_tyre ${populated.front_tyre}, abs_type ${populated.abs_type}`);
w(`--   dims:   wheelbase_mm ${populated.wheelbase_mm}, fuel_capacity_l ${populated.fuel_capacity_l}, kerb_weight_kg ${populated.kerb_weight_kg}, seat_height_mm ${populated.seat_height_mm} (${withDimensions} rows get a dimension block)`);
w(`--   other:  price_eur ${withPrice}, image_url ${withImage}; long-tail spec rows: ${specRowCount} across ${specKeyCount.size} distinct keys`);
w();
w('BEGIN;');
w();
w('CREATE TEMP TABLE tmp_1000ps_import (');
w('    row_no                   bigint PRIMARY KEY,');
w('    slug                     varchar(160) NOT NULL,');
w('    may_create               boolean NOT NULL,');
w('    brand                    varchar(60) NOT NULL,');
w('    model                    varchar(120) NOT NULL,');
w('    model_year               integer NOT NULL,');
w('    category                 varchar(20),');
w('    price_eur                numeric(10,2),');
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
w('    dimension_id             bigint,');
w('    is_new                   boolean NOT NULL DEFAULT false,');
w('    -- The engine geometry as it stood before this import merged anything, so the check before COMMIT can');
w('    -- tell a quartet this file broke from one it merely joined to and did not write.');
w('    prior_bore_mm            numeric(6,2),');
w('    prior_stroke_mm          numeric(6,2),');
w('    prior_cylinders          integer,');
w('    prior_displacement_cc    integer,');
w('    CONSTRAINT uk_tmp_1000ps_import_slug UNIQUE (slug)');
w(') ON COMMIT DROP;');
w();
w('CREATE TEMP TABLE tmp_1000ps_kv (');
w('    row_no     bigint NOT NULL,');
w('    spec_key   varchar(80) NOT NULL,');
w('    spec_value varchar(500),');
w('    PRIMARY KEY (row_no, spec_key)');
w(') ON COMMIT DROP;');
w();

const HEAD_COLUMNS = ['slug', 'may_create', 'brand', 'model', 'model_year', 'category', 'price_eur', 'image_url'];
const IMPORT_COLUMNS = ['row_no', ...HEAD_COLUMNS, ...SPEC_COLUMNS];
w(`INSERT INTO tmp_1000ps_import (${IMPORT_COLUMNS.join(', ')}) VALUES`);
const valueLines = emitted.map((row, i) => {
    const head = [q(row.slug), b(row.may_create), q(row.brand), q(row.model), n(row.model_year), q(row.category),
        n(row.price_eur), q(row.image ? IMAGE_URL_PREFIX + row.image.fileName : null)];
    const cells = [String(i + 1), ...head, ...SPEC_COLUMNS.map((c) => (QUOTED.has(c) ? q(row[c]) : n(row[c])))];
    return `(${cells.join(', ')})`;
});
w(`${valueLines.join(',\n')};`);
w();

const kvLines = [];
emitted.forEach((row, i) => {
    for (const [key, value] of row.specs) kvLines.push(`(${i + 1}, ${q(key)}, ${q(value)})`);
});
if (kvLines.length > 0) {
    w('INSERT INTO tmp_1000ps_kv (row_no, spec_key, spec_value) VALUES');
    w(`${kvLines.join(',\n')};`);
    w();
}

w('-- ---------------------------------------------------------------------------');
w('-- Align brand spelling with the catalogue before anything else reads it, so a new row never opens a');
w('-- second bucket for a manufacturer that is already there under a different casing. Resolved against the');
w('-- live catalogue rather than a list baked in here; DISTINCT ON picks one spelling where the catalogue');
w('-- itself still holds two, and the ORDER BY makes that choice deterministic instead of arbitrary.');
w('-- ---------------------------------------------------------------------------');
w('UPDATE tmp_1000ps_import s');
w('SET brand = c.brand');
w('FROM (SELECT DISTINCT ON (lower(brand)) brand FROM motorcycles ORDER BY lower(brand), brand) c');
w('WHERE lower(c.brand) = lower(s.brand) AND c.brand <> s.brand;');
w();
w('-- Bind every staged row to its catalogue row, if it has one.');
w('UPDATE tmp_1000ps_import s');
w('SET motorcycle_id = m.id,');
w('    engine_id     = m.engine_specification_id,');
w('    dimension_id  = m.dimension_id');
w('FROM motorcycles m');
w('WHERE m.slug = s.slug;');
w();
w('-- What counts as new is decided here, from the database, not from a flag in the VALUES above: an existing');
w('-- model is never inserted twice, and a model the catalogue does not have is created rather than dropped.');
w('-- may_create is the other half of it and does come from the VALUES, because whether a vehicle is a');
w('-- motorcycle at all is a property of the source row, not of the catalogue.');
w('UPDATE tmp_1000ps_import SET is_new = (motorcycle_id IS NULL AND may_create);');
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
w('UPDATE tmp_1000ps_import');
w("SET motorcycle_id = nextval(pg_get_serial_sequence('motorcycles', 'id')::regclass),");
w("    engine_id     = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)");
w('WHERE is_new;');
w();
w('-- A dimension row is created only where the source published a measurement; an empty placeholder would');
w('-- add a row that exists only to render dashes in the comparison table.');
w('UPDATE tmp_1000ps_import');
w("SET dimension_id = nextval(pg_get_serial_sequence('dimensions', 'id')::regclass)");
w('WHERE is_new');
w(`  AND num_nonnulls(${DIMENSION_COLUMNS.join(', ')}) > 0;`);
w();
w('INSERT INTO engine_specifications (');
w(`    id, ${ENGINE_COLUMNS.join(', ')}`);
w(')');
w(`SELECT engine_id, ${ENGINE_COLUMNS.join(', ')}`);
w('FROM tmp_1000ps_import');
w('WHERE is_new');
w('ORDER BY row_no;');
w();
w('INSERT INTO dimensions (');
w(`    id, ${DIMENSION_COLUMNS.join(', ')}`);
w(')');
w(`SELECT dimension_id, ${DIMENSION_COLUMNS.join(', ')}`);
w('FROM tmp_1000ps_import');
w('WHERE is_new AND dimension_id IS NOT NULL');
w('ORDER BY row_no;');
w();
w('-- description stays NULL: the source publishes no prose about the bike that is not the scraped page title.');
w('INSERT INTO motorcycles (');
w('    id, slug, version, brand, model, model_year, category, price_eur, image_url, description,');
w(`    ${MOTORCYCLE_COLUMNS.join(', ')}, engine_specification_id, dimension_id, created_at, updated_at`);
w(')');
w('SELECT motorcycle_id, slug, 0, brand, model, model_year, category, price_eur, image_url, NULL,');
w(`       ${MOTORCYCLE_COLUMNS.join(', ')}, engine_id, dimension_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP`);
w('FROM tmp_1000ps_import');
w('WHERE is_new');
w('ORDER BY row_no;');
w();
w('-- ---------------------------------------------------------------------------');
w('-- Existing models: gap-fill only.');
w('-- ---------------------------------------------------------------------------');
w();
w('-- The FIPE seed created an engine block for every row it inserted, but an earlier import may not have.');
w('-- Give those one first, so their figures are not silently dropped below.');
w('UPDATE tmp_1000ps_import');
w("SET engine_id = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)");
w('WHERE NOT is_new AND motorcycle_id IS NOT NULL AND engine_id IS NULL;');
w();
w('INSERT INTO engine_specifications (id)');
w('SELECT s.engine_id');
w('FROM tmp_1000ps_import s');
w('WHERE NOT s.is_new');
w('  AND s.motorcycle_id IS NOT NULL');
w('  AND s.engine_id IS NOT NULL');
w('  AND NOT EXISTS (SELECT 1 FROM engine_specifications e WHERE e.id = s.engine_id)');
w('ORDER BY s.row_no;');
w();
w('UPDATE motorcycles m');
w('SET engine_specification_id = s.engine_id');
w('FROM tmp_1000ps_import s');
w('WHERE m.id = s.motorcycle_id AND NOT s.is_new AND m.engine_specification_id IS NULL;');
w();
w('-- Photograph the geometry before merging into it. Without this the check before COMMIT cannot tell a');
w('-- contradiction this import introduced from one that was already stored, and a defect in another seed');
w('-- would abort an import that never touched the row.');
w('UPDATE tmp_1000ps_import s');
w('SET prior_bore_mm         = e.bore_mm,');
w('    prior_stroke_mm       = e.stroke_mm,');
w('    prior_cylinders       = e.cylinders,');
w('    prior_displacement_cc = e.displacement_cc');
w('FROM engine_specifications e');
w('WHERE e.id = s.engine_id AND NOT s.is_new;');
w();
w('UPDATE engine_specifications e');
w('SET ' + ENGINE_COLUMNS.filter((c) => !GEOMETRY_COLUMNS.includes(c)).map((c) => `${c.padEnd(24)} = COALESCE(e.${c}, s.${c})`).join(',\n    '));
w('FROM tmp_1000ps_import s');
w('WHERE e.id = s.engine_id AND NOT s.is_new AND s.motorcycle_id IS NOT NULL;');
w();
w('-- Bore and stroke are merged in a statement of their own, after cylinders and displacement have settled');
w('-- above. The four have one algebraic relation and COALESCE merges each column independently, so a bore and');
w('-- stroke from this source can contradict a cylinder count another seed already claimed even though neither');
w('-- source was wrong on its own (memory: coalesce-gapfill-mixes-sources). Where they would, the row keeps what');
w('-- it had: this file only ever fills gaps, so it does not get to overrule a column another import owns, and a');
w('-- quartet that cannot describe one engine would fail the check before COMMIT and abort the whole import.');
w('-- The tolerance is the same 25% that check uses; correctly published figures agree far inside it.');
w('UPDATE engine_specifications e');
w('SET bore_mm   = COALESCE(e.bore_mm, s.bore_mm),');
w('    stroke_mm = COALESCE(e.stroke_mm, s.stroke_mm)');
w('FROM tmp_1000ps_import s');
w('WHERE e.id = s.engine_id');
w('  AND NOT s.is_new');
w('  AND s.motorcycle_id IS NOT NULL');
w('  AND ((e.bore_mm IS NULL AND s.bore_mm IS NOT NULL) OR (e.stroke_mm IS NULL AND s.stroke_mm IS NOT NULL))');
w('  AND (e.cylinders IS NULL');
w('       OR e.displacement_cc IS NULL');
w('       OR e.displacement_cc <= 0');
w('       OR COALESCE(e.bore_mm, s.bore_mm) IS NULL');
w('       OR COALESCE(e.stroke_mm, s.stroke_mm) IS NULL');
w('       OR abs((pi() / 4 * (COALESCE(e.bore_mm, s.bore_mm)::double precision) ^ 2 * (COALESCE(e.stroke_mm, s.stroke_mm)::double precision) * e.cylinders / 1000)');
w('              - e.displacement_cc::double precision) <= 0.25 * e.displacement_cc::double precision);');
w();
w('-- A row seeded from FIPE carries no dimension block at all, so allocate one wherever this import has');
w('-- something to put in it.');
w('UPDATE tmp_1000ps_import');
w("SET dimension_id = nextval(pg_get_serial_sequence('dimensions', 'id')::regclass)");
w('WHERE NOT is_new');
w('  AND motorcycle_id IS NOT NULL');
w('  AND dimension_id IS NULL');
w(`  AND num_nonnulls(${DIMENSION_COLUMNS.join(', ')}) > 0;`);
w();
w('INSERT INTO dimensions (');
w(`    id, ${DIMENSION_COLUMNS.join(', ')}`);
w(')');
w(`SELECT s.dimension_id, ${DIMENSION_COLUMNS.map((c) => `s.${c}`).join(', ')}`);
w('FROM tmp_1000ps_import s');
w('WHERE NOT s.is_new');
w('  AND s.motorcycle_id IS NOT NULL');
w('  AND s.dimension_id IS NOT NULL');
w('  AND NOT EXISTS (SELECT 1 FROM dimensions d WHERE d.id = s.dimension_id)');
w('ORDER BY s.row_no;');
w();
w('-- Where a dimension block already existed, gap-fill it the same way as the engine block. The two weight');
w('-- columns are the exception: they merge from different sources independently, so a dry weight this import');
w('-- supplies can land above a kerb weight another source already stored even though neither figure is wrong');
w('-- on its own (memory: coalesce-gapfill-mixes-sources). Resolving it inside the same statement is what keeps');
w('-- ck_dimensions_dry_weight_below_kerb from aborting the entire import over one row; a NULL on either side');
w('-- makes the comparison NULL and falls through to the merged value unchanged.');
w('UPDATE dimensions d');
w('SET ' + DIMENSION_COLUMNS.filter((c) => c !== 'dry_weight_kg').map((c) => `${c.padEnd(19)} = COALESCE(d.${c}, s.${c})`).join(',\n    ') + ',');
w('    dry_weight_kg       = CASE WHEN COALESCE(d.dry_weight_kg, s.dry_weight_kg) > COALESCE(d.kerb_weight_kg, s.kerb_weight_kg) THEN NULL');
w('                               ELSE COALESCE(d.dry_weight_kg, s.dry_weight_kg) END');
w('FROM tmp_1000ps_import s');
w('WHERE d.id = s.dimension_id AND NOT s.is_new AND s.motorcycle_id IS NOT NULL;');
w();
w('-- brand, model, model_year and category are never touched on an existing row: they are the row\'s identity');
w('-- and its slug was derived from them, so rewriting one here would leave the public URL describing a');
w('-- different motorcycle. image_url is not touched either, though this import does set it on the rows it');
w('-- creates: the file behind it only exists once someone runs the image step against a scraper checkout, and');
w('-- an existing row belongs to the brand import that already materialised its photo, or to no one.');
w('UPDATE motorcycles m');
w('SET ' + [...MOTORCYCLE_COLUMNS, 'price_eur', 'dimension_id'].map((c) => `${c.padEnd(16)} = COALESCE(m.${c}, s.${c})`).join(',\n    ') + ',');
w('    -- @Version belongs to Hibernate; bump it here because this writes behind the ORM\'s back, so a session');
w('    -- holding a stale copy of one of these rows fails its next flush instead of quietly overwriting the import.');
w('    version          = m.version + 1,');
w('    updated_at       = CURRENT_TIMESTAMP');
w('FROM tmp_1000ps_import s');
w('WHERE m.id = s.motorcycle_id');
w('  AND NOT s.is_new');
w('  -- Each arm is "the row lacks it AND the import supplies it", so a row this file has nothing left to add');
w('  -- is not touched at all. Testing only for NULL would bump version and updated_at on every re-run.');
w('  AND (' + [...MOTORCYCLE_COLUMNS, 'price_eur', 'dimension_id'].map((c) => `(m.${c} IS NULL AND s.${c} IS NOT NULL)`).join('\n       OR ') + ');');
w();
w('-- ---------------------------------------------------------------------------');
w('-- Long-tail specs, for rows this import created and rows it gap-filled alike. DO NOTHING preserves');
w('-- whatever is already stored under the same key, including the FIPE seed\'s own \'Fuel\' and');
w('-- \'Reference price (BRL)\'.');
w('-- ---------------------------------------------------------------------------');
w('INSERT INTO motorcycle_additional_specs (motorcycle_id, spec_key, spec_value)');
w('SELECT s.motorcycle_id, k.spec_key, k.spec_value');
w('FROM tmp_1000ps_kv k');
w('JOIN tmp_1000ps_import s ON s.row_no = k.row_no');
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
w('    bad       bigint;');
w('    created   bigint;');
w('    merged    bigint;');
w('    skipped   bigint;');
w('    offenders text;');
w('BEGIN');
w('    -- Every staged slug has to be one the public routing can use, whether it resolved or was inserted.');
w('    SELECT count(*) INTO bad');
w('    FROM tmp_1000ps_import');
w("    WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';");
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION '1000ps import: % staged slugs are not a shape the public routing can use', bad;");
w('    END IF;');
w();
w('    SELECT count(*) FILTER (WHERE is_new),');
w('           count(*) FILTER (WHERE NOT is_new AND motorcycle_id IS NOT NULL),');
w('           count(*) FILTER (WHERE NOT is_new AND motorcycle_id IS NULL)');
w('    INTO created, merged, skipped');
w('    FROM tmp_1000ps_import;');
w('    -- Expected, not an error: a staged row that is neither in the catalogue nor a motorcycle is left alone.');
w("    RAISE NOTICE '1000ps import: % rows created, % existing rows gap-filled, % skipped as neither in the catalogue nor a motorcycle',");
w('        created, merged, skipped;');
w();
w('    SELECT count(*) INTO bad');
w('    FROM tmp_1000ps_import s');
w('    JOIN motorcycles m ON m.id = s.motorcycle_id');
w('    WHERE s.engine_id IS NOT NULL AND m.engine_specification_id IS DISTINCT FROM s.engine_id;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION '1000ps import: % rows have a mismatched engine block', bad;");
w('    END IF;');
w();
w('    SELECT count(*) INTO bad');
w('    FROM tmp_1000ps_import s');
w('    JOIN motorcycles m ON m.id = s.motorcycle_id');
w('    WHERE s.dimension_id IS NOT NULL AND m.dimension_id IS DISTINCT FROM s.dimension_id;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION '1000ps import: % rows have a mismatched dimension block', bad;");
w('    END IF;');
w();
w('    SELECT count(*) INTO bad');
w('    FROM dimensions d');
w('    JOIN tmp_1000ps_import s ON s.dimension_id = d.id');
w('    WHERE d.dry_weight_kg IS NOT NULL AND d.kerb_weight_kg IS NOT NULL AND d.dry_weight_kg > d.kerb_weight_kg;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION '1000ps import: % dimension rows have dry weight above kerb weight', bad;");
w('    END IF;');
w();
w('    -- No row this import created may be left claiming a country: the catalogue this comes from is not a');
w('    -- Brazilian-market snapshot. This fires if the file is ever reordered before the Brazil backfill.');
w('    SELECT count(*) INTO bad');
w('    FROM tmp_1000ps_import s');
w('    JOIN motorcycle_available_countries c ON c.motorcycle_id = s.motorcycle_id');
w('    WHERE s.is_new;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION '1000ps import: % country rows were attached to models created here; this seed must run after the country backfill', bad;");
w('    END IF;');
w();
w('    -- Swept volume against the merged bore/stroke/cylinders quartet. The four columns are merged one at a');
w("    -- time, so a row can end up holding one source's bore beside another's displacement even though neither");
w('    -- source was wrong (memory: coalesce-gapfill-mixes-sources). The tolerance is deliberately wide: this is');
w('    -- here to catch a quartet that cannot describe one engine, not to police rounding.');
w('    CREATE TEMP TABLE tmp_1000ps_quartet ON COMMIT DROP AS');
w('    SELECT s.slug,');
w("           s.slug || ' (cc=' || e.displacement_cc || ', bore=' || e.bore_mm || ', stroke=' || e.stroke_mm || ', cyl=' || e.cylinders || ')' AS detail,");
w('           s.is_new');
w('           OR e.bore_mm IS DISTINCT FROM s.prior_bore_mm');
w('           OR e.stroke_mm IS DISTINCT FROM s.prior_stroke_mm');
w('           OR e.cylinders IS DISTINCT FROM s.prior_cylinders');
w('           OR e.displacement_cc IS DISTINCT FROM s.prior_displacement_cc AS written_here');
w('    FROM engine_specifications e');
w('    JOIN tmp_1000ps_import s ON s.engine_id = e.id');
w('    WHERE e.bore_mm IS NOT NULL AND e.stroke_mm IS NOT NULL');
w('      AND e.cylinders IS NOT NULL AND e.displacement_cc IS NOT NULL');
w('      AND e.displacement_cc > 0');
w('      AND abs((pi() / 4 * (e.bore_mm::double precision) ^ 2 * (e.stroke_mm::double precision) * e.cylinders / 1000)');
w('              - e.displacement_cc::double precision) > 0.25 * e.displacement_cc::double precision;');
w();
w('    -- A quartet that was already contradictory before this file ran belongs to whichever seed wrote it, and');
w('    -- aborting here would make this import fail over data it never touched. Reported, not raised.');
w("    SELECT count(*), string_agg(detail, '; ' ORDER BY slug) INTO bad, offenders FROM tmp_1000ps_quartet WHERE NOT written_here;");
w('    IF bad <> 0 THEN');
w("        RAISE NOTICE '1000ps import: % engine blocks were already carrying a bore/stroke/cylinders/displacement quartet that cannot describe one engine before this import ran, and were left alone: %', bad, offenders;");
w('    END IF;');
w();
w("    SELECT count(*), string_agg(detail, '; ' ORDER BY slug) INTO bad, offenders FROM tmp_1000ps_quartet WHERE written_here;");
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION '1000ps import: % engine blocks have a bore/stroke/cylinders/displacement quartet that cannot describe one engine: %', bad, offenders;");
w('    END IF;');
w('END $$;');
w();
w('COMMIT;');
w();

fs.writeFileSync(SQL_PATH, out.join('\n'), 'utf8');

process.stdout.write([
    `source            ${JSON_PATH}`,
    `models read       ${snapshot.length}`,
    `unique slugs      ${bySlug.size} (${duplicateSlugs} duplicates collapsed, ${badSlugs.length} unslugifiable)`,
    `rows emitted      ${emitted.length} (${creatable.length} may create a catalogue row, ${gapFillOnly} gap-fill only)`,
    `  with engine     ${withEngine}`,
    `  with dimensions ${withDimensions}`,
    `  with image      ${withImage}`,
    `  with price      ${withPrice}`,
    `categories        ${[...categoryCount].sort((x, y) => y[1] - x[1]).map(([c, v]) => `${c} ${v}`).join(', ')}`,
    `excluded labels   ${Object.entries(excludedByLabel).sort((x, y) => y[1] - x[1]).map(([k, v]) => `${k} ${v}`).join(', ') || 'none'}`,
    `long-tail specs   ${specRowCount} rows across ${specKeyCount.size} keys`,
    `dropped figures   ${Object.entries(dropped).sort((x, y) => y[1] - x[1]).map(([k, v]) => `${k} ${v}`).join(', ') || 'none'}`,
    `truncated         ${TRUNCATED.count}`,
    `written           ${SQL_PATH} (${(fs.statSync(SQL_PATH).size / 1048576).toFixed(1)} MB)`,
    '',
].join('\n'));
