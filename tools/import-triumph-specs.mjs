#!/usr/bin/env node
// Regenerates db/seed/R__motorcycles_triumph_specs_2026_09.sql from the zontes-scraper snapshot, reproducibly.
// Gap-fills existing catalogue rows only - see the generated file's header for why. Usage: node <this> [--source <dir>]

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
const JSON_PATH = path.join(SOURCE_DIR, 'triumph_motos.json');
const SQL_PATH = path.join(REPO_ROOT, 'src/main/resources/db/seed/R__motorcycles_triumph_specs_2026_09.sql');

// --- number parsing ------------------------------------------------------------------------
// A separator is read from its own shape: exactly three trailing digits is a thousands group ("2.458 cc" -> 2458).
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

// A source string that spaces its digits out is garbled, not SI-formatted; the repair only joins a
// digit to a following group of exactly three digits, matching the other brand imports.
const MALFORMED = { count: 0 };
const repairDigitGroups = (text) => String(text).replace(/(\d) (\d{3})(?!\d)/g, '$1$2');

// Collapses a published range ("690 - 700 mm") to its low end for measurements only, never for
// power, torque or rpm - the low end is what the bike stands at as delivered.
const collapseRanges = (text) => text.replace(/(\d[\d.,]*)\s*[-–—]\s*\d[\d.,]*(\s*)(?=[a-z°])/gi, '$1$2');

/** A number may not begin immediately after a digit, or after a digit and a space. */
const NUMBER_START = '(?<!\\d)(?<!\\d\\s)';

function countMalformed(text) {
    if (/\d\s+\d/.test(text)) MALFORMED.count++;
    return null;
}

/** First number carrying one of the given units; `bare` allows a unitless value as a last resort. */
function measure(rawText, unitPattern, { bare = false } = {}) {
    if (!rawText) return null;
    const text = collapseRanges(repairDigitGroups(rawText));
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

// --- text cleaning -------------------------------------------------------------------------
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

// This source transposes labels (memory: scraper-source-defects): "Chassis" carries a bare trail
// figure ("97,1 mm"), never real frame prose. Reject anything with no real word before it, for every prose column.
const BARE_MEASUREMENT = /^[\d.,\s\-–—]+\s*(mm|cm|kg|cc|l|kgf\.?m|rpm)?\.?$/i;
function proseField(raw, maxLen, label, dropped) {
    const val = clean(raw, maxLen);
    if (val == null) return null;
    if (BARE_MEASUREMENT.test(val)) {
        dropped[label] = (dropped[label] || 0) + 1;
        return null;
    }
    return val;
}

// Tyre sizes are short alnum codes ("120/70ZR17", "MT 90 B16") and never a run of 4+ letters,
// which is what a mislabelled prose field landing here would look like.
function tyreField(raw, maxLen, label, dropped) {
    const val = clean(raw, maxLen);
    if (val == null) return null;
    if (!/\d/.test(val) || /[a-z]{4,}/i.test(val) || val.length > 24) {
        dropped[label] = (dropped[label] || 0) + 1;
        return null;
    }
    return val;
}

// --- per-field extraction -----------------------------------------------------------------
const PT_CYLINDERS = { um: 1, uma: 1, dois: 2, duas: 2, três: 3, tres: 3, quatro: 4, seis: 6 };

// Other brand imports cross-check this count against bore/stroke/displacement; this snapshot never
// publishes a bore (Chassis is the mislabelled trail figure above, never bore), so it cannot here.
function cylindersOf(engineText) {
    if (!engineText) return null;
    const numFirst = /\b(um|uma|dois|duas|tr[êe]s|quatro|seis|\d)\s*cilindros?\b/i.exec(engineText);
    if (numFirst) return PT_CYLINDERS[numFirst[1].toLowerCase()] ?? (Number(numFirst[1]) || null);
    // "cilindro único" names a single without ever putting the number before the word.
    if (/\bcilindro\s+[úu]nicos?\b/i.test(engineText)) return 1;
    const mono = /\bmonocil\w*/i.exec(engineText);
    return mono ? 1 : null;
}

function valvesPerCylinderOf(engineText, cylinders) {
    if (!engineText || !cylinders) return null;
    const total = /(\d+)\s*v[aá]lvulas?\b/i.exec(engineText);
    if (!total) return null;
    const n = Number(total[1]);
    return n % cylinders === 0 ? n / cylinders : null;
}

function powerOf(text, dropped) {
    if (!text) return [null, null];
    const rpm = rpmOf(text, dropped, 'max_power_rpm');
    const hp = unit(text, 'hp|bhp|cv|ps\\b');
    if (hp != null) return [bounded(round(hp, 1), 0.5, 350, dropped, 'max_power_hp'), rpm];
    const kw = unit(text, 'kw\\b');
    if (kw != null) return [bounded(round(kw * 1.34102, 1), 0.5, 350, dropped, 'max_power_hp'), rpm];
    return [null, rpm];
}

function torqueOf(text, dropped) {
    if (!text) return [null, null];
    const rpm = rpmOf(text, dropped, 'max_torque_rpm');
    const nm = unit(text, 'n[\\s.-]?m\\b');
    if (nm != null) return [bounded(round(nm, 1), 1, 300, dropped, 'max_torque_nm'), rpm];
    const kgf = unit(text, 'kgf?[\\s.·-]?m\\b');
    if (kgf != null) return [bounded(round(kgf * 9.80665, 1), 1, 300, dropped, 'max_torque_nm'), rpm];
    return [null, rpm];
}

function topSpeedOf(text, dropped) {
    if (!text) return null;
    const kph = unit(text, 'km\\s*/?\\s*h');
    if (kph != null) return bounded(Math.round(kph), 20, 400, dropped, 'top_speed_kph');
    const mph = unit(text, 'mph');
    return mph != null ? bounded(Math.round(mph * 1.60934), 20, 400, dropped, 'top_speed_kph') : null;
}

function compressionOf(text) {
    if (!text) return null;
    const m = /(\d[\d.,]*)\s*[:.]\s*1\b/.exec(text);
    if (m) {
        const v = toNumber(m[1]);
        if (v != null && v >= 4 && v <= 20) return `${round(v, 1)}:1`;
    }
    return clean(text, 20);
}

// --- long-tail specs -----------------------------------------------------------------------
// Same key names the Royal Enfield import uses for a cross-brand comparison; only Rodas and Embreagem ever carry a value.
const TOP_LEVEL_SPEC_KEYS = [
    'Pressão do Pneu Dianteiro', 'Pressão do Pneu Traseiro', 'Iluminação', 'Painel', 'Marcha Lenta',
    'Sistema de Partida', 'Sistema de Chave de Ignição', 'Modos de Condução', 'Rodas', 'Tomada USB',
    'Bateria de Gel', 'Ajuste do Para-brisas', 'Embreagem',
];

function longTailSpecs(moto) {
    const specs = new Map();
    for (const key of TOP_LEVEL_SPEC_KEYS) {
        const value = clean(moto[key], 500);
        if (value) specs.set(key, value);
    }
    return specs;
}

// --- build -----------------------------------------------------------------------------------
const snapshot = JSON.parse(fs.readFileSync(JSON_PATH, 'utf8'));
const dropped = {};
const rows = [];

for (const moto of snapshot.modelos) {
    const displacement = bounded(Math.round(measure(moto['Cilindrada'], 'cc|cm³|cm3|ccm', { bare: true }) ?? NaN) || null, 30, 2500, dropped, 'displacement_cc');
    const stroke = bounded(round(measure(moto['Curso'], 'mm', { bare: true }), 2), 20, 150, dropped, 'stroke_mm');
    const bore = bounded(round(measure(moto['Diâmentro do Cilindro'], 'mm', { bare: true }), 2), 20, 150, dropped, 'bore_mm');
    const engineText = moto['Motor'];
    const cylinders = bounded(cylindersOf(engineText), 1, 8, dropped, 'cylinders');
    const [powerHp, powerRpm] = powerOf(moto['Potência Máxima'], dropped);
    const [torqueNm, torqueRpm] = torqueOf(moto['Torque Máximo'], dropped);
    const kerb = bounded(round(measure(moto['Peso em Ordem de Marcha'], 'kg'), 1), 20, 500, dropped, 'kerb_weight_kg');
    const dry = bounded(round(measure(moto['Peso Seco'], 'kg'), 1), 20, 500, dropped, 'dry_weight_kg');
    const transmissionText = moto['Transmissão'];
    const gearsMatch = transmissionText && /(\d+)\s*(?:speed|velocidades|marchas)/i.exec(transmissionText);

    rows.push({
        slug: moto.slug,

        // Fed through the BARE_MEASUREMENT guard above: every populated Chassis value here is rejected
        // by it (see the header note), so this stays NULL, but a fixed future scrape would flow through.
        frame_type: proseField(moto['Chassis'], 120, 'chassis_mislabeled_trail', dropped),
        front_suspension: proseField(moto['Suspensão Dianteira'], 160, 'front_suspension', dropped),
        rear_suspension: proseField(moto['Suspensão Traseira'], 160, 'rear_suspension', dropped),
        front_brake: proseField(moto['Freio dianteiro'], 160, 'front_brake', dropped),
        rear_brake: proseField(moto['Freio traseiro'], 160, 'rear_brake', dropped),
        // No distinct ABS field is published; "ABS" is embedded in the brake description text instead.
        abs_type: null,
        front_tyre: tyreField(moto['Pneu Dianteiro'], 60, 'front_tyre', dropped),
        rear_tyre: tyreField(moto['Pneu Traseiro'], 60, 'rear_tyre', dropped),

        engine_type: proseField(engineText, 80, 'engine_type', dropped),
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
        transmission_type: clean(transmissionText, 60),
        gears: bounded(gearsMatch ? Number(gearsMatch[1]) : null, 1, 8, dropped, 'gears'),
        // No separate final-drive field is published for this brand's scrape.
        final_drive: null,
        top_speed_kph: topSpeedOf(moto['Velocidade Máxima'], dropped),
        // No fuel-consumption or emission-standard field is published for this brand's scrape.
        fuel_consumption_l_100km: null,
        emission_standard: null,

        length_mm: bounded(Math.round(measure(moto['Comprimento'], 'mm', { bare: true }) ?? NaN) || null, 1200, 3000, dropped, 'length_mm'),
        width_mm: bounded(Math.round(measure(moto['Largura'], 'mm', { bare: true }) ?? NaN) || null, 400, 1500, dropped, 'width_mm'),
        height_mm: bounded(Math.round(measure(moto['Altura'], 'mm', { bare: true }) ?? NaN) || null, 700, 1800, dropped, 'height_mm'),
        wheelbase_mm: bounded(Math.round(measure(moto['Distância Entre Eixos'], 'mm', { bare: true }) ?? NaN) || null, 800, 2000, dropped, 'wheelbase_mm'),
        seat_height_mm: bounded(Math.round(measure(moto['Altura do Assento'], 'mm', { bare: true }) ?? NaN) || null, 400, 1100, dropped, 'seat_height_mm'),
        ground_clearance_mm: bounded(Math.round(measure(moto['Distância do Solo'], 'mm', { bare: true }) ?? NaN) || null, 50, 400, dropped, 'ground_clearance_mm'),
        kerb_weight_kg: kerb,
        dry_weight_kg: dry,
        fuel_capacity_l: bounded(round(measure(moto['Capacidade do Tanque'], 'l\\b|lt\\b|litros?|litres?'), 1), 1, 50, dropped, 'fuel_capacity_l'),

        specs: longTailSpecs(moto),
    });
}

// --- SQL emission ---------------------------------------------------------------------------
const q = (value) => (value == null ? 'NULL' : `'${String(value).replace(/'/g, "''")}'`);
const n = (value) => (value == null ? 'NULL' : String(value));

const IMPORT_COLUMNS = [
    'row_no', 'slug', 'frame_type', 'front_suspension', 'rear_suspension', 'front_brake', 'rear_brake',
    'abs_type', 'front_tyre', 'rear_tyre', 'engine_type', 'displacement_cc', 'cylinders',
    'valves_per_cylinder', 'max_power_hp', 'max_power_rpm', 'max_torque_nm', 'max_torque_rpm',
    'compression_ratio', 'bore_mm', 'stroke_mm', 'cooling_system', 'fuel_system', 'transmission_type',
    'gears', 'final_drive', 'top_speed_kph', 'fuel_consumption_l_100km', 'emission_standard',
    'length_mm', 'width_mm', 'height_mm', 'wheelbase_mm', 'seat_height_mm', 'ground_clearance_mm',
    'kerb_weight_kg', 'dry_weight_kg', 'fuel_capacity_l',
];

const valuesFor = (row, rowNo) => [
    rowNo, q(row.slug), q(row.frame_type), q(row.front_suspension), q(row.rear_suspension),
    q(row.front_brake), q(row.rear_brake), q(row.abs_type), q(row.front_tyre), q(row.rear_tyre),
    q(row.engine_type), n(row.displacement_cc), n(row.cylinders), n(row.valves_per_cylinder),
    n(row.max_power_hp), n(row.max_power_rpm), n(row.max_torque_nm), n(row.max_torque_rpm),
    q(row.compression_ratio), n(row.bore_mm), n(row.stroke_mm), q(row.cooling_system),
    q(row.fuel_system), q(row.transmission_type), n(row.gears), q(row.final_drive),
    n(row.top_speed_kph), n(row.fuel_consumption_l_100km), q(row.emission_standard),
    n(row.length_mm), n(row.width_mm), n(row.height_mm), n(row.wheelbase_mm), n(row.seat_height_mm),
    n(row.ground_clearance_mm), n(row.kerb_weight_kg), n(row.dry_weight_kg), n(row.fuel_capacity_l),
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

// Rows the source published nothing usable for are not emitted at all: an all-NULL row would add
// bytes to this file and change nothing in the database.
const emitted = rows.filter((row) => SPEC_COLUMNS.some((c) => row[c] != null) || row.specs.size > 0);
const populated = Object.fromEntries(SPEC_COLUMNS.map((c) => [c, emitted.filter((r) => r[c] != null).length]));
const specRowCount = emitted.reduce((sum, row) => sum + row.specs.size, 0);
const withDimensions = emitted.filter((r) => ['width_mm', 'height_mm', 'seat_height_mm', 'kerb_weight_kg',
    'fuel_capacity_l'].some((c) => r[c] != null)).length;
const neverPublished = SPEC_COLUMNS.filter((c) => populated[c] === 0);
const chassisDropped = dropped.chassis_mislabeled_trail || 0;

const out = [];
const w = (line = '') => out.push(line);

w('-- Motorcycle Comparison API - Triumph technical specifications');
w(`-- Generated from zontes-scraper/triumph_motos.json (collected ${snapshot.data_coleta}) by`);
w('-- tools/import-triumph-specs.mjs. The scraper here follows the same field template as the Royal');
w('-- Enfield one, but its JSON shape is its own: top-level "modelos" (not "motos"/"gerado_em"/"fontes"),');
w('-- and no nested "outros_dados" object, so every field this import reads is a direct top-level key.');
w('--');
w(`-- ${snapshot.total_modelos} model-years were scraped and every one resolves to a slug the FIPE seed (or`);
w('-- R__dev_seed.sql) already created - the JSON "slug" is the same base slug that seed derives. Unlike');
w('-- every other brand import in this repository, this one creates NO catalogue row for a slug that does');
w('-- not resolve: the task is to update motorcycles already in the catalogue, not to add new ones, so an');
w('-- unresolved row (none in this snapshot) is counted and left alone rather than inserted. The join and');
w('-- that count are both computed at migration time against the live catalogue, never against a flag');
w('-- baked in here.');
w('--');
w(`-- This snapshot is far sparser than the other brand scrapes: of the ${SPEC_COLUMNS.length} specification`);
w(`-- columns below, ${neverPublished.length} are never published at all across the ${snapshot.total_modelos} rows and stay NULL for`);
w('-- every one of them (kept in the import for structural parity with the other brand seeds and so a');
w('-- richer future re-scrape needs no SQL changes, not because this run has anything to put in them):');
w(`--   ${neverPublished.join(', ')}`);
w(`-- Only ${emitted.length} rows carry at least one usable figure or long-tail spec and are emitted below.`);
w('--');
w('-- One field is actively wrong at the source rather than merely absent. "Chassis" is documented');
w('-- elsewhere as frame/chassis prose, but every one of its 11 populated rows here is a bare number');
w('-- with an "mm" suffix ("97,1 mm", "92.0 mm", "108 mm") - the shape of the wheel-geometry trail');
w('-- figure, not chassis text, and likely the same label-transposition defect already seen on other');
w('-- sites from this scraper family (see memory: scraper-source-defects). A shape guard rejects anything');
w('-- that is only digits/separators with an optional short unit and no real word before it reaches');
w(`-- frame_type, so all ${chassisDropped} are discarded rather than stored as chassis text.`);
w('--');
w('-- Number parsing follows the same convention as the other imports: a decimal separator is read from');
w('-- its own shape (exactly three trailing digits is a thousands group, e.g. "2.458 cc" -> 2458 and');
w('-- "9.500 RPM" -> 9500), hp from hp/bhp/cv/PS/kW and Nm from Nm/kgf.m, never estimated. Cylinder count');
w('-- is read from the "Motor" prose the same way as the other imports (a number or word adjacent to');
w('-- "cilindro(s)", plus the reversed Portuguese construction "cilindro único" this source also uses).');
w('-- No bore is ever published here, so - unlike Royal Enfield or the Kawasaki research seed - there is');
w('-- no swept-volume figure available at generation time to cross-check that count against. The runtime');
w('-- SQL below still guards the bore/stroke/cylinders/displacement quartet against itself for every');
w('-- Triumph row after the merge (defence in depth against a future import mixing sources on the same');
w('-- row), it just never fires against anything this particular import contributes.');
const droppedEntries = Object.entries(dropped).filter(([k]) => k !== 'chassis_mislabeled_trail').sort((a, b) => b[1] - a[1]);
w('--');
w('-- Other implausible figures are dropped rather than stored, each against the range its column can');
w('-- sensibly hold:');
w(`--   ${droppedEntries.map(([k, v]) => `${k} ${v}`).join(', ') || 'none'}`);
w(`-- ${TRUNCATED.count} values exceeded their column width and were cut at a word boundary rather than mid-word;`);
w(`-- ${MALFORMED.count} numeric reads were refused as garbled (spaced-out digits); ${REPAIRED.count} encoding faults were repaired.`);
w('--');
w('-- No photo of any kind is published in this snapshot (resumo.com_foto: 0 of 456), so image_url is');
w('-- never touched and there is no image-materialisation step here, unlike the brand imports that have one.');
w('--');
w('-- Existing rows are only ever gap-filled: every write is COALESCE(existing, imported), so an admin');
w("-- edit or a richer earlier import always wins. Category, brand, model, model_year and price are never");
w('-- touched by this import at all: category is NOT NULL on every row already (the FIPE seed always sets');
w('-- it), so unlike a brand import with a new-row path, there is no lossy source-label mapping to make a');
w('-- call on here. Long-tail specs (Rodas, Embreagem) use ON CONFLICT DO NOTHING, preserving the FIPE');
w("-- seed's own 'Fuel' and 'Reference price (BRL)', and reuse the key names the other brand imports chose");
w('-- so a cross-brand comparison lines its rows up. Repeatable and idempotent: re-running changes nothing');
w('-- once it has been applied.');
w('--');
w(`-- Populated column counts across the ${emitted.length} imported rows:`);
w(`--   engine: displacement_cc ${populated.displacement_cc}, max_torque_nm ${populated.max_torque_nm}, cylinders ${populated.cylinders}, stroke_mm ${populated.stroke_mm}`);
w(`--   frame:  front_brake ${populated.front_brake}, front_tyre ${populated.front_tyre}`);
w(`--   dims:   kerb_weight_kg ${populated.kerb_weight_kg}, fuel_capacity_l ${populated.fuel_capacity_l}, seat_height_mm ${populated.seat_height_mm} (${withDimensions} rows get a dimension block)`);
w(`--   long-tail spec rows: ${specRowCount}`);
w();
w('BEGIN;');
w();
w('CREATE TEMP TABLE tmp_triumph_spec_import (');
w('    row_no                   bigint PRIMARY KEY,');
w('    slug                     varchar(160) NOT NULL,');
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
w('CREATE TEMP TABLE tmp_triumph_spec_kv (');
w('    row_no     bigint NOT NULL,');
w('    spec_key   varchar(80) NOT NULL,');
w('    spec_value varchar(500),');
w('    PRIMARY KEY (row_no, spec_key)');
w(') ON COMMIT DROP;');
w();

const BATCH = 200;
for (let start = 0; start < emitted.length; start += BATCH) {
    const batch = emitted.slice(start, start + BATCH);
    w(`INSERT INTO tmp_triumph_spec_import (${IMPORT_COLUMNS.join(', ')}) VALUES`);
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
    w('INSERT INTO tmp_triumph_spec_kv (row_no, spec_key, spec_value) VALUES');
    batch.forEach((pair, i) => w(`${pair}${i === batch.length - 1 ? ';' : ','}`));
    w();
}

w('-- Bind every scraped row to its catalogue row. The FIPE seed derives the same slug from the same');
w('-- FIPE model descriptor, so this is an equality join and not a fuzzy match.');
w('UPDATE tmp_triumph_spec_import s');
w('SET motorcycle_id = m.id,');
w('    engine_id     = m.engine_specification_id,');
w('    dimension_id  = m.dimension_id');
w('FROM motorcycles m');
w('WHERE m.slug = s.slug;');
w();
w('DO $$');
w('BEGIN');
w("    IF pg_get_serial_sequence('engine_specifications', 'id') IS NULL");
w("       OR pg_get_serial_sequence('dimensions', 'id') IS NULL THEN");
w("        RAISE EXCEPTION 'A target table has no serial/identity sequence';");
w('    END IF;');
w('END $$;');
w();
w('-- ---------------------------------------------------------------------------');
w('-- Gap-fill only. No motorcycle row is ever created here (see the header note);');
w('-- every statement below is scoped to rows that resolved to an existing catalogue row.');
w('-- ---------------------------------------------------------------------------');
w();
w('-- The FIPE seed created an engine block for every row it inserted, but give a matched row one');
w('-- anyway if it somehow lacks it, so its figures are not silently dropped below.');
w('UPDATE tmp_triumph_spec_import');
w("SET engine_id = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)");
w('WHERE motorcycle_id IS NOT NULL AND engine_id IS NULL;');
w();
w('INSERT INTO engine_specifications (id)');
w('SELECT s.engine_id');
w('FROM tmp_triumph_spec_import s');
w('WHERE s.motorcycle_id IS NOT NULL');
w('  AND s.engine_id IS NOT NULL');
w('  AND NOT EXISTS (SELECT 1 FROM engine_specifications e WHERE e.id = s.engine_id)');
w('ORDER BY s.row_no;');
w();
w('UPDATE motorcycles m');
w('SET engine_specification_id = s.engine_id');
w('FROM tmp_triumph_spec_import s');
w('WHERE m.id = s.motorcycle_id AND m.engine_specification_id IS NULL;');
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
w('FROM tmp_triumph_spec_import s');
w('WHERE e.id = s.engine_id AND s.motorcycle_id IS NOT NULL;');
w();
w('-- A row seeded from FIPE carries no dimension block at all, so allocate one wherever this import');
w('-- has something to put in it.');
w('UPDATE tmp_triumph_spec_import');
w("SET dimension_id = nextval(pg_get_serial_sequence('dimensions', 'id')::regclass)");
w('WHERE motorcycle_id IS NOT NULL');
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
w('FROM tmp_triumph_spec_import s');
w('WHERE s.motorcycle_id IS NOT NULL');
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
w('FROM tmp_triumph_spec_import s');
w('WHERE d.id = s.dimension_id AND s.motorcycle_id IS NOT NULL;');
w();
w('-- A gap-fill that would push dry weight above a kerb weight already on the row is dropped: the');
w('-- CHECK would otherwise abort the entire import over one bad figure. This import never writes');
w('-- dry_weight_kg itself, but newly gap-filled kerb_weight_kg alone can still trip an existing value.');
w('UPDATE dimensions');
w('SET dry_weight_kg = NULL');
w('WHERE dry_weight_kg IS NOT NULL');
w('  AND kerb_weight_kg IS NOT NULL');
w('  AND dry_weight_kg > kerb_weight_kg;');
w();
w('UPDATE motorcycles m');
w('SET frame_type       = COALESCE(m.frame_type, s.frame_type),');
w('    front_suspension = COALESCE(m.front_suspension, s.front_suspension),');
w('    rear_suspension  = COALESCE(m.rear_suspension, s.rear_suspension),');
w('    front_brake      = COALESCE(m.front_brake, s.front_brake),');
w('    rear_brake       = COALESCE(m.rear_brake, s.rear_brake),');
w('    abs_type         = COALESCE(m.abs_type, s.abs_type),');
w('    front_tyre       = COALESCE(m.front_tyre, s.front_tyre),');
w('    rear_tyre        = COALESCE(m.rear_tyre, s.rear_tyre),');
w('    dimension_id     = COALESCE(m.dimension_id, s.dimension_id),');
w("    -- @Version belongs to Hibernate; bump it here because this writes behind the ORM's back, so a");
w('    -- session holding a stale copy of one of these rows fails its next flush instead of quietly');
w('    -- overwriting the import.');
w('    version          = m.version + 1,');
w('    updated_at       = CURRENT_TIMESTAMP');
w('FROM tmp_triumph_spec_import s');
w('WHERE m.id = s.motorcycle_id');
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
w('       OR (m.dimension_id IS NULL AND s.dimension_id IS NOT NULL));');
w();
w('-- ---------------------------------------------------------------------------');
w("-- Long-tail specs. DO NOTHING preserves whatever is already stored under the same key,");
w("-- including the FIPE seed's own 'Fuel' and 'Reference price (BRL)'.");
w('-- ---------------------------------------------------------------------------');
w('INSERT INTO motorcycle_additional_specs (motorcycle_id, spec_key, spec_value)');
w('SELECT s.motorcycle_id, k.spec_key, k.spec_value');
w('FROM tmp_triumph_spec_kv k');
w('JOIN tmp_triumph_spec_import s ON s.row_no = k.row_no');
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
w('    -- Expected, not an error: this import must never invent a motorcycle row for a slug the');
w('    -- catalogue does not have, so an unresolved row is reported and left alone, not aborted.');
w('    SELECT count(*) INTO bad FROM tmp_triumph_spec_import WHERE motorcycle_id IS NULL;');
w('    IF bad <> 0 THEN');
w("        RAISE NOTICE 'Triumph spec import: % scraped rows had no matching catalogue slug and were skipped (no new motorcycle created)', bad;");
w('    END IF;');
w();
w('    SELECT count(*) INTO bad');
w('    FROM tmp_triumph_spec_import s');
w('    JOIN motorcycles m ON m.id = s.motorcycle_id');
w('    WHERE s.engine_id IS NOT NULL AND m.engine_specification_id IS DISTINCT FROM s.engine_id;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Triumph spec import: % rows have a mismatched engine block', bad;");
w('    END IF;');
w();
w('    SELECT count(*) INTO bad');
w('    FROM dimensions');
w('    WHERE dry_weight_kg IS NOT NULL AND kerb_weight_kg IS NOT NULL AND dry_weight_kg > kerb_weight_kg;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Triumph spec import: % dimension rows have dry weight above kerb weight', bad;");
w('    END IF;');
w();
w('    -- lower(brand) rather than brand: V4 normalised casing before any seed had inserted a row, so');
w('    -- an equality test here would silently depend on which seed wrote the row.');
w('    SELECT count(*) INTO bad');
w('    FROM motorcycles');
w("    WHERE lower(brand) = 'triumph' AND slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';");
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Triumph spec import: % rows have a slug the public routing cannot use', bad;");
w('    END IF;');
w();
w('    -- The COALESCE gap-fill merges columns independently, so a row can in principle end up with a');
w('    -- bore from one source and a stroke from another; the geometric quartet has to agree with itself');
w('    -- (5% tolerance absorbs published rounding on bore/stroke). No row in this snapshot supplies a');
w('    -- bore, so this never fires against anything this import contributes - it guards a future merge.');
w('    SELECT count(*) INTO bad');
w('    FROM motorcycles m');
w('    JOIN engine_specifications e ON e.id = m.engine_specification_id');
w("    WHERE lower(m.brand) = 'triumph'");
w('      AND e.bore_mm IS NOT NULL AND e.stroke_mm IS NOT NULL');
w('      AND e.cylinders IS NOT NULL AND e.displacement_cc IS NOT NULL');
w('      AND abs(pi() / 4 * e.bore_mm * e.bore_mm * e.stroke_mm * e.cylinders / 1000.0 - e.displacement_cc)');
w('          > 0.05 * e.displacement_cc;');
w('    IF bad <> 0 THEN');
w("        RAISE EXCEPTION 'Triumph spec import: % engine rows have bore/stroke/cylinders inconsistent with displacement_cc', bad;");
w('    END IF;');
w('END $$;');
w();
w('COMMIT;');
w();

fs.writeFileSync(SQL_PATH, out.join('\n'), 'utf8');
console.log(`sql: ${emitted.length} of ${snapshot.total_modelos} scraped rows emitted, ${specRowCount} long-tail specs -> ${path.relative(REPO_ROOT, SQL_PATH)}`);
console.log(`dropped as implausible: ${droppedEntries.map(([k, v]) => `${k}=${v}`).join(' ') || 'none'}`);
console.log(`chassis (mislabelled trail) discarded: ${chassisDropped}`);
console.log(`truncated to column width: ${TRUNCATED.count}; refused as garbled digits: ${MALFORMED.count}; encoding repairs: ${REPAIRED.count}`);
console.log(`fields never published in this snapshot: ${neverPublished.join(', ')}`);
