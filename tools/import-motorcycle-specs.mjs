#!/usr/bin/env node
// Regenerates db/seed/R__motorcycles_specs_gapfill.sql from the zontes-scraper consolidated output.json.
// Gap-fills existing catalogue rows only, never creates one. Usage: node <this> [--source <dir>] [--json <file>]

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

const SOURCE_DIR = path.resolve(option('--source', path.join(REPO_ROOT, '..', 'zontes-scraper')));
const JSON_PATH = path.resolve(option('--json', path.join(SOURCE_DIR, 'output.json')));
// The "zz" prefix is load-bearing, not decoration: Flyway orders repeatable migrations by description,
// and this one has to run after every per-brand seed so those keep first claim on their own brand.
const SQL_PATH = path.join(REPO_ROOT, 'src/main/resources/db/seed/R__zz_motorcycles_specs_gapfill.sql');

// The prompt for this import specified kebab-case spec keys ("Taxa de Compressão" -> "taxa-compressao").
// Every seed already in this repository stores Portuguese Title Case ('Rodas', 'Embreagem', 'Trail'),
// so the two vocabularies do not line up in a cross-brand comparison. Flip this to 'title' to switch.
const SPEC_KEY_STYLE = 'kebab';

// --- slug ---------------------------------------------------------------------------------
/** The Unicode combining-mark block, matching the \p{M} that MotorcycleService.slugify strips. */
const COMBINING_MARKS = /[̀-ͯ]/g;

// Byte-for-byte MotorcycleService.slugify: NFD, strip combining marks, lowercase, non-alnum to
// dashes, trim edge dashes. The base slug the FIPE seed derives is brand + model + model year.
function slugify(raw) {
    const ascii = String(raw).normalize('NFD').replace(COMBINING_MARKS, '').toLowerCase();
    return ascii.replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

const SLUG_FORMAT = /^[a-z0-9]+(-[a-z0-9]+)*$/;

// --- numbers ------------------------------------------------------------------------------
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

// --- text ---------------------------------------------------------------------------------
const TRUNCATED = { count: 0 };
const REPAIRED = { count: 0 };

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
    const val = clean(raw, maxLen);
    if (val == null) return null;
    if (BARE_MEASUREMENT.test(val)) {
        dropped[label] = (dropped[label] || 0) + 1;
        return null;
    }
    return val;
}

// A third of the descriptions this source publishes are the scraped page's <title>, not prose about the
// bike ("The 2019 BMW R 1250 GS HP and all other motorcycles made 1894-2025. Specifications. Pictures.
// Rating. Discussions"). description is user-facing, so the boilerplate is refused rather than shown.
const PAGE_TITLE = /and all other motorcycles made|Specifications\.\s*Pictures\.\s*Rating\.\s*Discussions/i;

function descriptionField(raw, dropped) {
    const val = proseField(raw, 2000, 'description', dropped);
    if (val == null) return null;
    if (PAGE_TITLE.test(val)) {
        dropped.description_is_page_title = (dropped.description_is_page_title || 0) + 1;
        return null;
    }
    return val;
}

// Tyre sizes are short alnum codes ("120/70-ZR17") and never a run of 4+ letters, which is
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

// --- per-field extraction -------------------------------------------------------------------
const CYLINDER_WORDS = { single: 1, twin: 2, two: 2, three: 3, four: 4, five: 5, six: 6 };

// This source writes the engine layout in English ("In-line four, four-stroke", "V2, four-stroke",
// "Two cylinder boxer"), so none of the Portuguese patterns the brand imports use apply here.
function cylindersOf(engineText) {
    if (!engineText) return null;
    const text = String(engineText).toLowerCase();
    const vee = /\bv(\d)\b/.exec(text);
    if (vee) return Number(vee[1]);
    const counted = /\b(single|twin|two|three|four|five|six)[- ]cylinder\b/.exec(text);
    if (counted) return CYLINDER_WORDS[counted[1]];
    const inLine = /\bin[- ]line\s+(two|three|four|five|six)\b/.exec(text);
    if (inLine) return CYLINDER_WORDS[inLine[1]];
    const trailing = /\b(two|three|four|five|six)\s+cylinder/.exec(text);
    if (trailing) return CYLINDER_WORDS[trailing[1]];
    const bare = /\b(single|twin)\b/.exec(text);
    return bare ? CYLINDER_WORDS[bare[1]] : null;
}

// "162.3 HP (118.4 kW )) @ 11000 RPM" - the HP figure is the published one and the kW in
// parentheses is its conversion, so kW is only ever a fallback.
function powerOf(text, dropped) {
    if (!text) return [null, null];
    const rpm = bounded(unit(text, 'rpm'), 500, 20000, dropped, 'max_power_rpm');
    const hp = unit(text, 'hp|bhp|cv|ps\\b');
    if (hp != null) return [bounded(round(hp, 1), 0.5, 400, dropped, 'max_power_hp'), rpm];
    const kw = unit(text, 'kw\\b');
    if (kw != null) return [bounded(round(kw * 1.34102, 1), 0.5, 400, dropped, 'max_power_hp'), rpm];
    return [null, rpm];
}

// "140.0 Nm (14.3 kgf-m or 103.3 ft.lbs) @ 8250 RPM" - Nm first, kgf.m converted at 9.80665
// only when no Nm figure is printed, and the ft.lbs conversion is never read.
function torqueOf(text, dropped) {
    if (!text) return [null, null];
    const rpm = bounded(unit(text, 'rpm'), 500, 20000, dropped, 'max_torque_rpm');
    const nm = unit(text, 'n[\\s.-]?m\\b');
    if (nm != null) return [bounded(round(nm, 1), 1, 400, dropped, 'max_torque_nm'), rpm];
    const kgf = unit(text, 'kgf?[\\s.·-]?m\\b');
    if (kgf != null) return [bounded(round(kgf * 9.80665, 1), 1, 400, dropped, 'max_torque_nm'), rpm];
    return [null, rpm];
}

function compressionOf(text) {
    if (!text) return null;
    const m = /(\d[\d.,]*)\s*[:.]\s*1\b/.exec(String(text));
    if (m) {
        const v = toNumber(m[1]);
        if (v != null && v >= 4 && v <= 20) return `${round(v, 1)}:1`;
    }
    return clean(text, 20);
}

// The source prints the standard three ways ("Euro 3", "EU-5", "EU3") inside a longer sentence
// about the catalytic converter; only the standard itself belongs in this column.
function emissionStandardOf(text) {
    if (!text) return null;
    const m = /\b(?:euro|eu)\s*-?\s*([1-7])\b/i.exec(String(text));
    return m ? `Euro ${m[1]}` : null;
}

// "6-speed" / "Automatic" from outros_dados.gearbox, falling back to the "6-speed / Chain
// (final drive)" compound the top-level transmissao field prints.
function gearsOf(...texts) {
    for (const text of texts) {
        if (!text) continue;
        const m = /(\d+)[\s-]*(?:speed|velocidades|marchas)/i.exec(String(text));
        if (m) return Number(m[1]);
    }
    return null;
}

// "Chain (final drive)" and "Shaft drive (cardan) (final drive)" name the drive; the trailing
// "(final drive)" is the source's own label for the field and not part of the value.
function finalDriveOf(text) {
    if (!text) return null;
    return clean(String(text).replace(/\s*\(final drive\)\s*/i, ' '), 40);
}

// ABS is never published as its own field here: it appears as a word inside the brake description.
// Recording only the bare word keeps this an observation about the source, not an inference about
// which ABS generation the bike has.
function absTypeOf(...brakeTexts) {
    return brakeTexts.some((t) => t && /\bABS\b/.test(String(t))) ? 'ABS' : null;
}

// --- category -------------------------------------------------------------------------------
// Keyword rules over the model name, most specific segment first so "Africa Twin" is not read as a
// twin-cylinder naked and "V-Strom" is not read by its trailing letters.
const CATEGORY_RULES = [
    ['ELECTRIC', /\b(electric|el[ée]tric[ao]|e-?bike|zero\s?sr|livewire)\b/i],
    ['SCOOTER', /\b(pcx|nmax|xmax|burgman|scooter|vespa|lead|elite|biz|adress|address)\b/i],
    ['OFF_ROAD', /\b(cross|enduro|crf|kx|yz|wr|xr|off[\s-]?road|trail|motocross)\b/i],
    ['ADVENTURE', /\b(gs|tiger|t[ée]n[ée]r[ée]|africa\s?twin|v-?strom|versys|adventure|multistrada|transalp|himalayan)\b/i],
    ['CRUISER', /\b(cruiser|custom|chopper|rebel|vulcan|shadow|rocket|bonneville|speedmaster|meteor|classic)\b/i],
    ['SPORT', /\b(ninja|fireblade|r1|r6|panigale|gsx-?r|daytona|sprint|trophy|rr|cbr|zx)\b/i],
    ['NAKED', /\b(z\d|mt-?\d|duke|monster|cb\d|street\s?triple|trident|speed\s?triple|hornet|naked|dominar|pulsar)\b/i],
];

// The source's own segment label, used only where no keyword matches. ATV and UTV quad bodies have
// no enum member of their own; OFF_ROAD is the closest the closed CHECK constraint allows.
const SOURCE_CATEGORIES = {
    'sport': 'SPORT',
    'sport touring': 'TOURING',
    'touring': 'TOURING',
    'naked bike': 'NAKED',
    'allround': 'NAKED',
    'scooter': 'SCOOTER',
    'enduro / offroad': 'OFF_ROAD',
    'atv': 'OFF_ROAD',
    'custom / cruiser': 'CRUISER',
    'super motard': 'SUPERMOTO',
};

function categoryOf(model, extra) {
    for (const [category, pattern] of CATEGORY_RULES) {
        if (pattern.test(model)) return category;
    }
    const label = extra.categoria || extra.category || extra.type;
    return label ? SOURCE_CATEGORIES[String(label).trim().toLowerCase()] ?? null : null;
}

// --- long-tail specs --------------------------------------------------------------------------
/** "Taxa de Compressão" -> "taxa-compressao": lowercase, unaccented, non-alphanumerics folded to hyphens. */
function specKey(label) {
    if (SPEC_KEY_STYLE !== 'kebab') return label;
    return String(label).normalize('NFD').replace(COMBINING_MARKS, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

// Only rider-facing facts. Scraper provenance (fontes, aviso, status, campos_preenchidos), the
// source site's own rating chatter and price_as_new are all deliberately left out.
const TOP_LEVEL_SPECS = [
    ['pressao_pneu_dianteiro', 'Pressão do Pneu Dianteiro'],
    ['pressao_pneu_traseiro', 'Pressão do Pneu Traseiro'],
    ['iluminacao', 'Iluminação'],
    ['painel', 'Painel'],
    ['sistema_partida', 'Sistema de Partida'],
    ['sistema_chave_ignicao', 'Sistema de Chave de Ignição'],
    ['rodas', 'Rodas'],
    ['bateria_gel', 'Bateria'],
    ['embreagem', 'Embreagem'],
    ['modos_conducao', 'Modos de Condução'],
    ['tomada_usb', 'Tomada USB'],
    ['ajuste_parabrisas', 'Ajuste do Para-brisas'],
];

const EXTRA_SPECS = [
    ['fuel_control', 'Sistema de Válvulas'],
    ['front_wheel_travel', 'Curso da Suspensão Dianteira'],
    ['rear_wheel_travel', 'Curso da Suspensão Traseira'],
    ['trail', 'Trail'],
    ['rake_fork_angle', 'Ângulo de Cáster'],
    ['oil_capacity', 'Capacidade de Óleo'],
    ['reserve_fuel_capacity', 'Reserva do Tanque'],
    ['carrying_capacity', 'Capacidade de Carga'],
    ['alternate_seat_height', 'Altura do Assento (ajuste alto)'],
    ['service_interval', 'Intervalo de Revisão'],
    ['power_weight_ratio', 'Relação Peso-Potência'],
    ['driveline', 'Transmissão Final'],
    ['exhaust_system', 'Escape'],
    ['lubrication_system', 'Lubrificação'],
    ['electrical', 'Sistema Elétrico'],
    ['engine_details', 'Detalhes do Motor'],
    ['factory_warranty', 'Garantia de Fábrica'],
    ['emissoes_co2', 'Emissões de CO2'],
    ['cores', 'Cores'],
    ['comments', 'Observações'],
];

function longTailSpecs(moto, extra) {
    const specs = new Map();
    for (const [field, label] of TOP_LEVEL_SPECS) {
        const value = clean(moto[field], 500);
        if (value) specs.set(specKey(label), value);
    }
    for (const [field, label] of EXTRA_SPECS) {
        const value = clean(extra[field], 500);
        if (value) specs.set(specKey(label), value);
    }
    const idle = numberOf(moto.marcha_lenta_rpm);
    if (idle != null) specs.set(specKey('Marcha Lenta'), `${Math.round(idle)} rpm`);
    return specs;
}

// --- build ------------------------------------------------------------------------------------
const snapshot = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
if (!Array.isArray(snapshot)) throw new Error(`${JSON_PATH} is expected to be a top-level array of models`);

const dropped = {};
const rows = [];
const badSlugs = [];
const categoryFromKeyword = { count: 0 };
const categoryFromLabel = { count: 0 };

for (const moto of snapshot) {
    const extra = moto.outros_dados || {};
    const modelYear = numberOf(extra.ano);
    if (!moto.marca || !moto.modelo || modelYear == null) {
        dropped.unidentifiable = (dropped.unidentifiable || 0) + 1;
        continue;
    }

    const slug = slugify(`${moto.marca} ${moto.modelo} ${modelYear}`);
    if (!SLUG_FORMAT.test(slug) || slug.length > 160) {
        badSlugs.push(`${moto.marca} ${moto.modelo} ${modelYear}`);
        continue;
    }

    const engineText = moto.motor;
    const cylinders = bounded(cylindersOf(engineText), 1, 8, dropped, 'cylinders');
    const [powerHp, powerRpm] = powerOf(moto.potencia_maxima, dropped);
    const [torqueNm, torqueRpm] = torqueOf(moto.torque_maximo, dropped);

    const kerb = bounded(round(numberOf(moto.peso_ordem_marcha_kg), 1), 20, 600, dropped, 'kerb_weight_kg');
    let dry = bounded(round(numberOf(moto.peso_seco_kg), 1), 20, 600, dropped, 'dry_weight_kg');
    // Dry weight excludes fluids and so cannot exceed kerb weight; this source publishes 22 rows
    // where it does, and ck_dimensions_dry_weight_below_kerb would abort the whole import over them.
    if (dry != null && kerb != null && dry > kerb) {
        dropped.dry_weight_above_kerb = (dropped.dry_weight_above_kerb || 0) + 1;
        dry = null;
    }

    let bore = bounded(round(numberOf(moto.diametro_cilindro_mm), 2), 20, 150, dropped, 'bore_mm');
    let stroke = bounded(round(numberOf(moto.curso_mm), 2), 20, 150, dropped, 'stroke_mm');
    const displacement = bounded(round(numberOf(moto.cilindrada_cc), 0), 30, 2500, dropped, 'displacement_cc');
    // Bore, stroke, cylinders and displacement have one algebraic relation, and this source breaks it two ways:
    // it mixes engines of the same family (Tiger 750 1995 gets the 1200 four's 82 mm stroke, sweeping 1116 cc
    // against a declared 855) and it sometimes copies the bore into the stroke field (Speed Triple 1050 as
    // "79 x 79" when it is 79 x 71.4). Displacement is the better-published figure and the one the catalogue
    // compares on, so the pair that contradicts it is dropped. The 5% tolerance is the one this repository
    // already asserts for the same invariant; correctly published figures agree far inside it.
    if (bore != null && stroke != null && cylinders != null && displacement != null) {
        const swept = (Math.PI / 4) * bore ** 2 * stroke * cylinders / 1000;
        if (Math.abs(swept - displacement) > 0.05 * displacement) {
            dropped.contradictory_bore_stroke = (dropped.contradictory_bore_stroke || 0) + 1;
            bore = null;
            stroke = null;
        }
    }

    const category = categoryOf(String(moto.modelo), extra);
    if (category) {
        if (CATEGORY_RULES.some(([, pattern]) => pattern.test(String(moto.modelo)))) categoryFromKeyword.count++;
        else categoryFromLabel.count++;
    }

    rows.push({
        slug,
        category,
        description: descriptionField(extra.descricao, dropped),

        frame_type: proseField(moto.chassis, 120, 'frame_type', dropped),
        front_suspension: proseField(moto.suspensao_dianteira, 160, 'front_suspension', dropped),
        rear_suspension: proseField(moto.suspensao_traseira, 160, 'rear_suspension', dropped),
        front_brake: proseField(moto.freio_dianteiro, 160, 'front_brake', dropped),
        rear_brake: proseField(moto.freio_traseiro, 160, 'rear_brake', dropped),
        abs_type: absTypeOf(moto.freio_dianteiro, moto.freio_traseiro),
        front_tyre: tyreField(moto.pneu_dianteiro, 60, 'front_tyre', dropped),
        rear_tyre: tyreField(moto.pneu_traseiro, 60, 'rear_tyre', dropped),

        engine_type: proseField(engineText, 80, 'engine_type', dropped),
        displacement_cc: displacement,
        cylinders,
        valves_per_cylinder: bounded(toNumber(extra.valves_per_cylinder), 1, 8, dropped, 'valves_per_cylinder'),
        max_power_hp: powerHp,
        max_power_rpm: powerRpm,
        max_torque_nm: torqueNm,
        max_torque_rpm: torqueRpm,
        compression_ratio: compressionOf(moto.taxa_compressao),
        bore_mm: bore,
        stroke_mm: stroke,
        cooling_system: clean(moto.refrigeracao, 40),
        fuel_system: clean(moto.alimentacao, 120),
        transmission_type: clean(extra.gearbox ?? moto.transmissao, 60),
        gears: bounded(gearsOf(extra.gearbox, moto.transmissao), 1, 8, dropped, 'gears'),
        final_drive: finalDriveOf(extra.transmission_type),
        top_speed_kph: bounded(round(numberOf(moto.velocidade_maxima_kmh), 0), 20, 400, dropped, 'top_speed_kph'),
        fuel_consumption_l_100km: bounded(round(toNumber(extra.consumo_l_100km), 2), 0.5, 30, dropped, 'fuel_consumption_l_100km'),
        emission_standard: emissionStandardOf(extra.emission_details),

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

// A slug can repeat when the source publishes the same model-year twice; the first row that carries
// anything wins, so the staging table keeps its slug unique and the join stays one-to-one.
const bySlug = new Map();
let duplicateSlugs = 0;
for (const row of rows) {
    if (bySlug.has(row.slug)) {
        duplicateSlugs++;
        continue;
    }
    bySlug.set(row.slug, row);
}

// --- SQL emission -------------------------------------------------------------------------------
const q = (value) => (value == null ? 'NULL' : `'${String(value).replace(/'/g, "''")}'`);
const n = (value) => (value == null ? 'NULL' : String(value));

const MOTORCYCLE_COLUMNS = ['category', 'description', 'frame_type', 'front_suspension', 'rear_suspension',
    'front_brake', 'rear_brake', 'abs_type', 'front_tyre', 'rear_tyre'];

const ENGINE_COLUMNS = ['engine_type', 'displacement_cc', 'cylinders', 'valves_per_cylinder', 'max_power_hp',
    'max_power_rpm', 'max_torque_nm', 'max_torque_rpm', 'compression_ratio', 'bore_mm', 'stroke_mm',
    'cooling_system', 'fuel_system', 'transmission_type', 'gears', 'final_drive', 'top_speed_kph',
    'fuel_consumption_l_100km', 'emission_standard'];

const DIMENSION_COLUMNS = ['length_mm', 'width_mm', 'height_mm', 'wheelbase_mm', 'seat_height_mm',
    'ground_clearance_mm', 'kerb_weight_kg', 'dry_weight_kg', 'fuel_capacity_l'];

const SPEC_COLUMNS = [...MOTORCYCLE_COLUMNS, ...ENGINE_COLUMNS, ...DIMENSION_COLUMNS];

const QUOTED = new Set(['category', 'description', 'frame_type', 'front_suspension', 'rear_suspension',
    'front_brake', 'rear_brake', 'abs_type', 'front_tyre', 'rear_tyre', 'engine_type', 'compression_ratio',
    'cooling_system', 'fuel_system', 'transmission_type', 'final_drive', 'emission_standard']);

// Rows the source published nothing usable for are not emitted at all: an all-NULL row would add
// bytes to this file and change nothing in the database.
const emitted = [...bySlug.values()].filter((row) => SPEC_COLUMNS.some((c) => row[c] != null) || row.specs.size > 0);

const populated = Object.fromEntries(SPEC_COLUMNS.map((c) => [c, emitted.filter((r) => r[c] != null).length]));
const specRowCount = emitted.reduce((sum, row) => sum + row.specs.size, 0);
const withDimensions = emitted.filter((r) => DIMENSION_COLUMNS.some((c) => r[c] != null)).length;
const withEngine = emitted.filter((r) => ENGINE_COLUMNS.some((c) => r[c] != null)).length;
const neverPublished = SPEC_COLUMNS.filter((c) => populated[c] === 0);
const specKeyCount = new Map();
for (const row of emitted) {
    for (const key of row.specs.keys()) specKeyCount.set(key, (specKeyCount.get(key) || 0) + 1);
}

const out = [];
const w = (line = '') => out.push(line);

w('-- Motorcycle Comparison API - cross-brand technical specification gap-fill');
w(`-- Generated from zontes-scraper/output.json by tools/import-motorcycle-specs.mjs.`);
w('--');
w('-- Unlike the per-brand imports beside it, this file covers whatever brands the consolidated');
w(`-- scraper output happens to carry - ${snapshot.length} model-years across ${new Set(snapshot.map((m) => m.marca)).size} brands in this run - and it never creates a`);
w('-- catalogue row. The task is to fill gaps in motorcycles the catalogue already holds, so a slug');
w('-- that does not resolve is counted and left alone rather than inserted. The join and that count');
w('-- are both computed at migration time against the live catalogue, never against a flag baked in');
w('-- here, so this file stays correct as the catalogue changes underneath it.');
w('--');
w('-- The source publishes no slug of its own. Each row is keyed by slugify(marca + modelo + ano),');
w('-- byte-for-byte what MotorcycleService.slugify produces and the same base slug the FIPE seed');
w('-- derives, which is why the join is on motorcycles.slug and never on a fuzzy name match.');
w('--');
w('-- Flyway runs repeatable migrations in description order, so the file name alone decides when this');
w('-- runs, and the "zz" prefix is load-bearing rather than decoration: "zz motorcycles specs gapfill"');
w('-- sorts after every other seed here, including "motorcycles yamaha specs 2026 08", the last one');
w('-- alphabetically. That matters because this snapshot now covers brands that have a dedicated seed');
w('-- of their own (Triumph, Harley-Davidson, BMW, Honda, Yamaha). Every write below is COALESCE, so');
w('-- whichever seed runs first claims a column for good; a per-brand scrape parses that brand with');
w('-- rules written for it, and must therefore win over this consolidated one. Drop the prefix and');
w('-- this file would run before the Triumph and Yamaha seeds and silently pre-empt both.');
w('--');
if (neverPublished.length > 0) {
    w(`-- Of the ${SPEC_COLUMNS.length} specification columns below, ${neverPublished.length} are never published across the whole snapshot and`);
    w('-- stay NULL for every row (kept for structural parity with the brand seeds, and so a richer');
    w('-- future scrape needs no SQL change here):');
    w(`--   ${neverPublished.join(', ')}`);
} else {
    w(`-- All ${SPEC_COLUMNS.length} specification columns below are published for at least one row in this snapshot.`);
}
w(`-- ${emitted.length} rows carry at least one usable figure or long-tail spec and are emitted below;`);
w(`-- ${bySlug.size - emitted.length} resolved to a slug but published nothing usable and are omitted entirely.`);
w('--');
w('-- Numbers: the dimension, displacement, bore, stroke and top-speed fields arrive already typed as');
w('-- numbers in the JSON and are only bounded and rounded to their column scale, never re-parsed from');
w('-- text. Power and torque are parsed from prose ("162.3 HP (118.4 kW )) @ 11000 RPM", "140.0 Nm');
w('-- (14.3 kgf-m or 103.3 ft.lbs) @ 8250 RPM"): the printed HP and Nm figures win, kW and kgf.m are');
w('-- converted only when no HP/Nm figure is printed, and the ft.lbs conversion is never read.');
w('-- Cylinder count is read from the English layout prose this source uses ("In-line four", "V2",');
w('-- "Two cylinder boxer", "Single cylinder"), which covers every distinct value it publishes.');
w('--');
w('-- One source defect is corrected rather than stored: dry weight excludes fluids and so cannot');
w(`-- exceed kerb weight, but ${dropped.dry_weight_above_kerb || 0} rows publish it above (BMW R 18 at 345.2 vs 344.96 kg, S 1000`);
w('-- RR at 208 vs 196.9 kg). Those dry weights are dropped at generation time, and the merge below');
w('-- drops any that ck_dimensions_dry_weight_below_kerb would still reject after COALESCE, because');
w('-- one bad figure would otherwise abort the entire import.');
w('--');
w(`-- Two more source defects are refused rather than stored. ${dropped.contradictory_bore_stroke || 0} rows carry a bore and stroke that`);
w('-- cannot sweep the displacement printed beside them, either because the sheet mixed two engines of the');
w("-- same family (Triumph Tiger 750 1995 gets the 1200 four's 82 mm stroke: 76 x 82 x 3 sweeps 1116 cc");
w('-- against a declared 855) or because the bore was copied into the stroke field (Speed Triple 1050 as');
w('-- "79 x 79" when it is 79 x 71.4). Displacement is the better-published figure and the one the catalogue');
w(`-- compares on, so the contradicting pair is dropped and the displacement kept. Separately, ${dropped.description_is_page_title || 0} descriptions`);
w("-- published are the scraped page's <title> rather than prose about the bike (\"The 2019 BMW R 1250 GS HP");
w('-- and all other motorcycles made 1894-2025. Specifications. Pictures. Rating. Discussions"); description');
w('-- is user-facing, so that boilerplate is refused and the column left NULL for those rows.');
const droppedEntries = Object.entries(dropped).filter(([k]) => k !== 'dry_weight_above_kerb').sort((a, b) => b[1] - a[1]);
w('--');
w('-- Other implausible figures are dropped rather than stored, each against the range its column can');
w('-- sensibly hold:');
w(`--   ${droppedEntries.map(([k, v]) => `${k} ${v}`).join(', ') || 'none'}`);
w(`-- ${TRUNCATED.count} values exceeded their column width and were cut at a word boundary rather than mid-word;`);
w(`-- ${REPAIRED.count} encoding faults were repaired; ${duplicateSlugs} duplicate slugs collapsed to their first usable row.`);
w('--');
w('-- category is derived (keyword rules over the model name first, the source segment label second)');
w(`-- for ${populated.category} rows and merged with COALESCE like everything else. It is a structural no-op today:`);
w('-- category is NOT NULL on every catalogue row and the FIPE seed always sets it, so the existing');
w('-- value always wins. It is emitted so that a future row reaching this import without one is');
w(`-- covered - ${categoryFromKeyword.count} of those values came from a model-name keyword, ${categoryFromLabel.count} from the source label.`);
w('--');
w('-- image_url is never touched. The snapshot records images as local relative paths under the');
w('-- scraper working directory ("./img/bajaj-dominar-250-2025-1.jpg"), which is not a URL this API');
w('-- can serve; storing one would put a guaranteed 404 in the catalogue. Importing those images');
w("-- means copying the files through the image endpoint first, which is not this file's job.");
w('--');
w('-- Existing rows are only ever gap-filled: every write is COALESCE(existing, imported), so an admin');
w('-- edit or a richer earlier import always wins. brand, model, model_year and price_eur are never');
w('-- touched at all. Long-tail specs use ON CONFLICT DO NOTHING, preserving whatever is already');
w("-- stored under the same key including the FIPE seed's own 'Fuel' and 'Reference price (BRL)'.");
w('-- Repeatable and idempotent: re-running changes nothing once it has been applied.');
if (SPEC_KEY_STYLE === 'kebab') {
    w('--');
    w('-- Long-tail spec keys are normalised to lowercase unaccented kebab-case as this import was');
    w("-- specified ('Taxa de Compressão' -> 'taxa-compressao'). Every seed already in this repository");
    w("-- stores Portuguese Title Case instead ('Rodas', 'Embreagem', 'Trail', 'Sistema de Partida'), so");
    w('-- the same fact reaches a cross-brand comparison under two different keys and renders as two');
    w('-- rows. SPEC_KEY_STYLE in the generator switches the whole file to the existing vocabulary.');
}
w('--');
w(`-- Populated column counts across the ${emitted.length} emitted rows:`);
w(`--   engine: displacement_cc ${populated.displacement_cc}, max_power_hp ${populated.max_power_hp}, max_torque_nm ${populated.max_torque_nm}, bore_mm ${populated.bore_mm}, stroke_mm ${populated.stroke_mm}, cylinders ${populated.cylinders} (${withEngine} rows)`);
w(`--   frame:  front_brake ${populated.front_brake}, front_suspension ${populated.front_suspension}, frame_type ${populated.frame_type}, front_tyre ${populated.front_tyre}, abs_type ${populated.abs_type}`);
w(`--   dims:   wheelbase_mm ${populated.wheelbase_mm}, fuel_capacity_l ${populated.fuel_capacity_l}, kerb_weight_kg ${populated.kerb_weight_kg}, seat_height_mm ${populated.seat_height_mm} (${withDimensions} rows get a dimension block)`);
w(`--   text:   description ${populated.description}`);
w(`--   long-tail spec rows: ${specRowCount} across ${specKeyCount.size} distinct keys`);
w();
w('BEGIN;');
w();
w('CREATE TEMP TABLE tmp_motorcycle_specs (');
w('    row_no                   bigint PRIMARY KEY,');
w('    slug                     varchar(160) NOT NULL,');
w('    category                 varchar(20),');
w('    description              varchar(2000),');
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
w('    CONSTRAINT uk_tmp_motorcycle_specs_slug UNIQUE (slug)');
w(') ON COMMIT DROP;');
w();
w('CREATE TEMP TABLE tmp_motorcycle_specs_kv (');
w('    row_no     bigint NOT NULL,');
w('    spec_key   varchar(80) NOT NULL,');
w('    spec_value varchar(500),');
w('    PRIMARY KEY (row_no, spec_key)');
w(') ON COMMIT DROP;');
w();

const IMPORT_COLUMNS = ['row_no', 'slug', ...SPEC_COLUMNS];
w(`INSERT INTO tmp_motorcycle_specs (${IMPORT_COLUMNS.join(', ')}) VALUES`);
const valueLines = emitted.map((row, i) => {
    const cells = [String(i + 1), q(row.slug), ...SPEC_COLUMNS.map((c) => (QUOTED.has(c) ? q(row[c]) : n(row[c])))];
    return `(${cells.join(', ')})`;
});
w(`${valueLines.join(',\n')};`);
w();

const kvLines = [];
emitted.forEach((row, i) => {
    for (const [key, value] of row.specs) kvLines.push(`(${i + 1}, ${q(key)}, ${q(value)})`);
});
if (kvLines.length > 0) {
    w('INSERT INTO tmp_motorcycle_specs_kv (row_no, spec_key, spec_value) VALUES');
    w(`${kvLines.join(',\n')};`);
    w();
}

w('-- ---------------------------------------------------------------------------');
w('-- Resolve every staged row against the live catalogue. A slug with no row here is');
w('-- deliberately left unresolved: this import updates motorcycles, it never adds them.');
w('-- ---------------------------------------------------------------------------');
w('UPDATE tmp_motorcycle_specs t');
w('SET motorcycle_id = m.id,');
w('    engine_id     = m.engine_specification_id,');
w('    dimension_id  = m.dimension_id');
w('FROM motorcycles m');
w('WHERE m.slug = t.slug;');
w();
w('-- An earlier import may have left a catalogue row without an engine block. Give those one first,');
w('-- so the figures below are not silently dropped on the join.');
w('UPDATE tmp_motorcycle_specs');
w("SET engine_id = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)");
w('WHERE motorcycle_id IS NOT NULL');
w('  AND engine_id IS NULL');
w(`  AND num_nonnulls(${ENGINE_COLUMNS.join(', ')}) > 0;`);
w();
w('INSERT INTO engine_specifications (id)');
w('SELECT t.engine_id');
w('FROM tmp_motorcycle_specs t');
w('WHERE t.engine_id IS NOT NULL');
w('  AND NOT EXISTS (SELECT 1 FROM engine_specifications e WHERE e.id = t.engine_id)');
w('ORDER BY t.row_no;');
w();
w('UPDATE motorcycles m');
w('SET engine_specification_id = t.engine_id');
w('FROM tmp_motorcycle_specs t');
w('WHERE m.id = t.motorcycle_id AND m.engine_specification_id IS NULL AND t.engine_id IS NOT NULL;');
w();
w('-- ---------------------------------------------------------------------------');
w('-- Engine block: gap-fill only.');
w('-- ---------------------------------------------------------------------------');
w('UPDATE engine_specifications e');
const engineWidth = Math.max(...ENGINE_COLUMNS.map((c) => c.length));
ENGINE_COLUMNS.forEach((c, i) => {
    const pad = ' '.repeat(engineWidth - c.length);
    w(`${i === 0 ? 'SET ' : '    '}${c}${pad} = COALESCE(e.${c}, t.${c})${i === ENGINE_COLUMNS.length - 1 ? '' : ','}`);
});
w('FROM tmp_motorcycle_specs t');
w('WHERE e.id = t.engine_id;');
w();
w('-- A bore and stroke this import supplies can contradict a displacement an earlier seed already claimed,');
w('-- because COALESCE merges the four columns one at a time (memory: coalesce-gapfill-mixes-sources). The');
w('-- Bonneville T100 2004 is the live case: the Triumph seed stores 900 cc, this source says 790 with an');
w('-- 86 x 68 twin, and 86 x 68 x 2 sweeps exactly 790. The stored displacement wins by design, so the pair');
w('-- that cannot sweep it is withdrawn - and only when both figures are the ones this import staged, so a');
w("-- bore another seed published is never cleared. Same 5% the generator applies to a single source's row.");
w('UPDATE engine_specifications e');
w('SET bore_mm   = NULL,');
w('    stroke_mm = NULL');
w('FROM tmp_motorcycle_specs t');
w('WHERE e.id = t.engine_id');
w('  AND e.bore_mm = t.bore_mm AND e.stroke_mm = t.stroke_mm');
w('  AND e.cylinders IS NOT NULL AND e.displacement_cc IS NOT NULL AND e.displacement_cc > 0');
w('  AND abs((pi() / 4 * (e.bore_mm::double precision) ^ 2 * (e.stroke_mm::double precision) * e.cylinders / 1000)');
w('          - e.displacement_cc::double precision) > 0.05 * e.displacement_cc::double precision;');
w();
w('-- ---------------------------------------------------------------------------');
w('-- Dimension block. A row seeded from FIPE carries none at all, so allocate one wherever');
w('-- this import has something to put in it.');
w('-- ---------------------------------------------------------------------------');
w('UPDATE tmp_motorcycle_specs');
w("SET dimension_id = nextval(pg_get_serial_sequence('dimensions', 'id')::regclass)");
w('WHERE motorcycle_id IS NOT NULL');
w('  AND dimension_id IS NULL');
w(`  AND num_nonnulls(${DIMENSION_COLUMNS.join(', ')}) > 0;`);
w();
w(`INSERT INTO dimensions (id, ${DIMENSION_COLUMNS.join(', ')})`);
w(`SELECT t.dimension_id, ${DIMENSION_COLUMNS.map((c) => `t.${c}`).join(', ')}`);
w('FROM tmp_motorcycle_specs t');
w('WHERE t.dimension_id IS NOT NULL');
w('  AND NOT EXISTS (SELECT 1 FROM dimensions d WHERE d.id = t.dimension_id)');
w('ORDER BY t.row_no;');
w();
w('-- Where a dimension block already existed, gap-fill it the same way as the engine block.');
w('UPDATE dimensions d');
const dimWidth = Math.max(...DIMENSION_COLUMNS.map((c) => c.length));
DIMENSION_COLUMNS.forEach((c, i) => {
    const pad = ' '.repeat(dimWidth - c.length);
    w(`${i === 0 ? 'SET ' : '    '}${c}${pad} = COALESCE(d.${c}, t.${c})${i === DIMENSION_COLUMNS.length - 1 ? '' : ','}`);
});
w('FROM tmp_motorcycle_specs t');
w('WHERE d.id = t.dimension_id;');
w();
w("-- A merge that pairs one source's dry weight with another's kerb weight can land above it even");
w('-- though neither source was wrong on its own (memory: coalesce-gapfill-mixes-sources). Scoped to');
w('-- the rows this import touched, so a violation stored elsewhere is left for its own seed to own.');
w('UPDATE dimensions d');
w('SET dry_weight_kg = NULL');
w('FROM tmp_motorcycle_specs t');
w('WHERE d.id = t.dimension_id');
w('  AND d.dry_weight_kg IS NOT NULL');
w('  AND d.kerb_weight_kg IS NOT NULL');
w('  AND d.dry_weight_kg > d.kerb_weight_kg;');
w();
w('-- ---------------------------------------------------------------------------');
w('-- Catalogue row: gap-fill only. brand, model, model_year and price_eur are never touched,');
w('-- and image_url is not written at all - see the header.');
w('-- ---------------------------------------------------------------------------');
w('UPDATE motorcycles m');
const motoWidth = Math.max(...MOTORCYCLE_COLUMNS.map((c) => c.length), 'dimension_id'.length);
MOTORCYCLE_COLUMNS.forEach((c, i) => {
    const pad = ' '.repeat(motoWidth - c.length);
    w(`${i === 0 ? 'SET ' : '    '}${c}${pad} = COALESCE(m.${c}, t.${c}),`);
});
w(`    dimension_id${' '.repeat(motoWidth - 'dimension_id'.length)} = COALESCE(m.dimension_id, t.dimension_id),`);
w("    -- @Version belongs to Hibernate; bump it here because this writes behind the ORM's back, so a");
w('    -- session holding a stale copy of one of these rows fails its next flush instead of quietly');
w('    -- overwriting the import.');
w(`    version${' '.repeat(motoWidth - 'version'.length)} = m.version + 1,`);
w(`    updated_at${' '.repeat(motoWidth - 'updated_at'.length)} = CURRENT_TIMESTAMP`);
w('FROM tmp_motorcycle_specs t');
w('WHERE m.id = t.motorcycle_id');
w('  -- Each arm is "the row lacks it AND the import supplies it", so a row this file has nothing left');
w('  -- to add is not touched at all. Testing only for NULL would bump version and updated_at on every');
w('  -- re-run for any column the source never published.');
[...MOTORCYCLE_COLUMNS, 'dimension_id'].forEach((c, i) => {
    const prefix = i === 0 ? '  AND (' : '       OR ';
    const suffix = i === MOTORCYCLE_COLUMNS.length ? ');' : '';
    w(`${prefix}(m.${c} IS NULL AND t.${c} IS NOT NULL)${suffix}`);
});
w();
w('-- ---------------------------------------------------------------------------');
w('-- Long-tail specs. DO NOTHING preserves whatever is already stored under the same key.');
w('-- ---------------------------------------------------------------------------');
w('INSERT INTO motorcycle_additional_specs (motorcycle_id, spec_key, spec_value)');
w('SELECT t.motorcycle_id, k.spec_key, k.spec_value');
w('FROM tmp_motorcycle_specs_kv k');
w('JOIN tmp_motorcycle_specs t ON t.row_no = k.row_no');
w('WHERE t.motorcycle_id IS NOT NULL');
w('  AND k.spec_value IS NOT NULL');
w('  AND btrim(k.spec_value) <> \'\'');
w('ON CONFLICT (motorcycle_id, spec_key) DO NOTHING;');
w();
w('-- ---------------------------------------------------------------------------');
w('-- Defensive checks before commit.');
w('-- ---------------------------------------------------------------------------');
w('DO $$');
w('DECLARE');
w('    bad         bigint;');
w('    unresolved  bigint;');
w('    offenders   text;');
w('BEGIN');
w('    -- Every staged slug has to be one the public routing can use, whether or not it resolved:');
w('    -- an unresolved row is a catalogue gap to report, a malformed slug is a generator bug.');
w('    SELECT count(*) INTO bad');
w('    FROM tmp_motorcycle_specs');
w("    WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';");
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Motorcycle spec gap-fill: % staged slugs are not a shape the public routing can use', bad;");
w('    END IF;');
w();
w('    SELECT count(*) INTO unresolved FROM tmp_motorcycle_specs WHERE motorcycle_id IS NULL;');
w("    RAISE NOTICE 'Motorcycle spec gap-fill: % of % staged rows matched a catalogue slug, % left unresolved',");
w('        (SELECT count(*) FROM tmp_motorcycle_specs WHERE motorcycle_id IS NOT NULL),');
w('        (SELECT count(*) FROM tmp_motorcycle_specs), unresolved;');
w();
w('    SELECT count(*) INTO bad');
w('    FROM tmp_motorcycle_specs t');
w('    JOIN motorcycles m ON m.id = t.motorcycle_id');
w('    WHERE t.engine_id IS NOT NULL AND m.engine_specification_id IS DISTINCT FROM t.engine_id;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Motorcycle spec gap-fill: % rows have a mismatched engine block', bad;");
w('    END IF;');
w();
w('    SELECT count(*) INTO bad');
w('    FROM tmp_motorcycle_specs t');
w('    JOIN motorcycles m ON m.id = t.motorcycle_id');
w('    WHERE t.dimension_id IS NOT NULL AND m.dimension_id IS DISTINCT FROM t.dimension_id;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Motorcycle spec gap-fill: % rows have a mismatched dimension block', bad;");
w('    END IF;');
w();
w('    SELECT count(*) INTO bad');
w('    FROM dimensions d');
w('    JOIN tmp_motorcycle_specs t ON t.dimension_id = d.id');
w('    WHERE d.dry_weight_kg IS NOT NULL AND d.kerb_weight_kg IS NOT NULL AND d.dry_weight_kg > d.kerb_weight_kg;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Motorcycle spec gap-fill: % dimension rows have dry weight above kerb weight', bad;");
w('    END IF;');
w();
w('    -- Swept volume against the merged bore/stroke/cylinders quartet. The four columns are merged');
w("    -- one at a time, so a row can end up holding one source's bore beside another's displacement");
w('    -- even though neither source was wrong (memory: coalesce-gapfill-mixes-sources). The tolerance');
w('    -- is deliberately wide: this is here to catch a quartet that cannot describe one engine, not');
w('    -- to police rounding or the difference between swept and nominal displacement.');
w("    SELECT count(*), string_agg(t.slug || ' (cc=' || e.displacement_cc || ', bore=' || e.bore_mm || ', stroke=' || e.stroke_mm || ', cyl=' || e.cylinders || ')', '; ' ORDER BY t.slug)");
w('    INTO bad, offenders');
w('    FROM engine_specifications e');
w('    JOIN tmp_motorcycle_specs t ON t.engine_id = e.id');
w('    WHERE e.bore_mm IS NOT NULL AND e.stroke_mm IS NOT NULL');
w('      AND e.cylinders IS NOT NULL AND e.displacement_cc IS NOT NULL');
w('      AND e.displacement_cc > 0');
w('      AND abs((pi() / 4 * (e.bore_mm::double precision) ^ 2 * (e.stroke_mm::double precision) * e.cylinders / 1000)');
w('              - e.displacement_cc::double precision) > 0.25 * e.displacement_cc::double precision;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Motorcycle spec gap-fill: % engine blocks have a bore/stroke/cylinders/displacement quartet that cannot describe one engine: %', bad, offenders;");
w('    END IF;');
w('END $$;');
w();
w('COMMIT;');
w();

fs.writeFileSync(SQL_PATH, out.join('\n'), 'utf8');

const resolvedNote = 'slug resolution is computed at migration time, not here';
process.stdout.write([
    `source            ${JSON_PATH}`,
    `models read       ${snapshot.length}`,
    `unique slugs      ${bySlug.size} (${duplicateSlugs} duplicates collapsed, ${badSlugs.length} unslugifiable)`,
    `rows emitted      ${emitted.length} (${bySlug.size - emitted.length} carried nothing usable)`,
    `  with engine     ${withEngine}`,
    `  with dimensions ${withDimensions}`,
    `  with category   ${populated.category} (${categoryFromKeyword.count} keyword, ${categoryFromLabel.count} source label)`,
    `long-tail specs   ${specRowCount} rows across ${specKeyCount.size} keys (style: ${SPEC_KEY_STYLE})`,
    `dropped figures   ${Object.entries(dropped).sort((a, b) => b[1] - a[1]).map(([k, v]) => `${k} ${v}`).join(', ') || 'none'}`,
    `truncated         ${TRUNCATED.count}`,
    `catalogue match   ${resolvedNote}`,
    `written           ${SQL_PATH} (${(fs.statSync(SQL_PATH).size / 1024).toFixed(0)} KB)`,
    '',
].join('\n'));
