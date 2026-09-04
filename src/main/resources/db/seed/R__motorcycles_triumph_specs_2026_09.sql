-- Motorcycle Comparison API - Triumph technical specifications
-- Generated from zontes-scraper/triumph_motos.json (collected 2026-09-02T06:19:29.637661+00:00) by
-- tools/import-triumph-specs.mjs. The scraper here follows the same field template as the Royal
-- Enfield one, but its JSON shape is its own: top-level "modelos" (not "motos"/"gerado_em"/"fontes"),
-- and no nested "outros_dados" object, so every field this import reads is a direct top-level key.
--
-- 456 model-years were scraped and every one resolves to a slug the FIPE seed (or
-- R__dev_seed.sql) already created - the JSON "slug" is the same base slug that seed derives. Unlike
-- every other brand import in this repository, this one creates NO catalogue row for a slug that does
-- not resolve: the task is to update motorcycles already in the catalogue, not to add new ones, so an
-- unresolved row (none in this snapshot) is counted and left alone rather than inserted. The join and
-- that count are both computed at migration time against the live catalogue, never against a flag
-- baked in here.
--
-- This snapshot is far sparser than the other brand scrapes: of the 36 specification
-- columns below, 20 are never published at all across the 456 rows and stay NULL for
-- every one of them (kept in the import for structural parity with the other brand seeds and so a
-- richer future re-scrape needs no SQL changes, not because this run has anything to put in them):
--   frame_type, front_suspension, rear_suspension, abs_type, max_power_hp, max_power_rpm, compression_ratio, bore_mm, cooling_system, fuel_system, transmission_type, gears, final_drive, top_speed_kph, fuel_consumption_l_100km, emission_standard, length_mm, wheelbase_mm, ground_clearance_mm, dry_weight_kg
-- Only 117 rows carry at least one usable figure or long-tail spec and are emitted below.
--
-- One field is actively wrong at the source rather than merely absent. "Chassis" is documented
-- elsewhere as frame/chassis prose, but every one of its 11 populated rows here is a bare number
-- with an "mm" suffix ("97,1 mm", "92.0 mm", "108 mm") - the shape of the wheel-geometry trail
-- figure, not chassis text, and likely the same label-transposition defect already seen on other
-- sites from this scraper family (see memory: scraper-source-defects). A shape guard rejects anything
-- that is only digits/separators with an optional short unit and no real word before it reaches
-- frame_type, so all 11 are discarded rather than stored as chassis text.
--
-- Number parsing follows the same convention as the other imports: a decimal separator is read from
-- its own shape (exactly three trailing digits is a thousands group, e.g. "2.458 cc" -> 2458 and
-- "9.500 RPM" -> 9500), hp from hp/bhp/cv/PS/kW and Nm from Nm/kgf.m, never estimated. Cylinder count
-- is read from the "Motor" prose the same way as the other imports (a number or word adjacent to
-- "cilindro(s)", plus the reversed Portuguese construction "cilindro único" this source also uses).
-- No bore is ever published here, so - unlike Royal Enfield or the Kawasaki research seed - there is
-- no swept-volume figure available at generation time to cross-check that count against. The runtime
-- SQL below still guards the bore/stroke/cylinders/displacement quartet against itself for every
-- Triumph row after the merge (defence in depth against a future import mixing sources on the same
-- row), it just never fires against anything this particular import contributes.
--
-- Other implausible figures are dropped rather than stored, each against the range its column can
-- sensibly hold:
--   none
-- 6 values exceeded their column width and were cut at a word boundary rather than mid-word;
-- 0 numeric reads were refused as garbled (spaced-out digits); 0 encoding faults were repaired.
--
-- No photo of any kind is published in this snapshot (resumo.com_foto: 0 of 456), so image_url is
-- never touched and there is no image-materialisation step here, unlike the brand imports that have one.
--
-- Existing rows are only ever gap-filled: every write is COALESCE(existing, imported), so an admin
-- edit or a richer earlier import always wins. Category, brand, model, model_year and price are never
-- touched by this import at all: category is NOT NULL on every row already (the FIPE seed always sets
-- it), so unlike a brand import with a new-row path, there is no lossy source-label mapping to make a
-- call on here. Long-tail specs (Rodas, Embreagem) use ON CONFLICT DO NOTHING, preserving the FIPE
-- seed's own 'Fuel' and 'Reference price (BRL)', and reuse the key names the other brand imports chose
-- so a cross-brand comparison lines its rows up. Repeatable and idempotent: re-running changes nothing
-- once it has been applied.
--
-- Populated column counts across the 117 imported rows:
--   engine: displacement_cc 117, max_torque_nm 115, cylinders 10, stroke_mm 10
--   frame:  front_brake 10, front_tyre 117
--   dims:   kerb_weight_kg 115, fuel_capacity_l 117, seat_height_mm 10 (117 rows get a dimension block)
--   long-tail spec rows: 20

BEGIN;

CREATE TEMP TABLE tmp_triumph_spec_import (
    row_no                   bigint PRIMARY KEY,
    slug                     varchar(160) NOT NULL,
    frame_type               varchar(120),
    front_suspension         varchar(160),
    rear_suspension          varchar(160),
    front_brake              varchar(160),
    rear_brake               varchar(160),
    abs_type                 varchar(80),
    front_tyre               varchar(60),
    rear_tyre                varchar(60),
    engine_type              varchar(80),
    displacement_cc          integer,
    cylinders                integer,
    valves_per_cylinder      integer,
    max_power_hp             numeric(6,1),
    max_power_rpm            integer,
    max_torque_nm            numeric(6,1),
    max_torque_rpm           integer,
    compression_ratio        varchar(20),
    bore_mm                  numeric(6,2),
    stroke_mm                numeric(6,2),
    cooling_system           varchar(40),
    fuel_system              varchar(120),
    transmission_type        varchar(60),
    gears                    integer,
    final_drive              varchar(40),
    top_speed_kph            integer,
    fuel_consumption_l_100km numeric(5,2),
    emission_standard        varchar(30),
    length_mm                integer,
    width_mm                 integer,
    height_mm                integer,
    wheelbase_mm             integer,
    seat_height_mm           integer,
    ground_clearance_mm      integer,
    kerb_weight_kg           numeric(6,1),
    dry_weight_kg            numeric(6,1),
    fuel_capacity_l          numeric(5,1),
    motorcycle_id            bigint,
    engine_id                bigint,
    dimension_id             bigint
) ON COMMIT DROP;

CREATE TEMP TABLE tmp_triumph_spec_kv (
    row_no     bigint NOT NULL,
    spec_key   varchar(80) NOT NULL,
    spec_value varchar(500),
    PRIMARY KEY (row_no, spec_key)
) ON COMMIT DROP;

INSERT INTO tmp_triumph_spec_import (row_no, slug, frame_type, front_suspension, rear_suspension, front_brake, rear_brake, abs_type, front_tyre, rear_tyre, engine_type, displacement_cc, cylinders, valves_per_cylinder, max_power_hp, max_power_rpm, max_torque_nm, max_torque_rpm, compression_ratio, bore_mm, stroke_mm, cooling_system, fuel_system, transmission_type, gears, final_drive, top_speed_kph, fuel_consumption_l_100km, emission_standard, length_mm, width_mm, height_mm, wheelbase_mm, seat_height_mm, ground_clearance_mm, kerb_weight_kg, dry_weight_kg, fuel_capacity_l) VALUES
(1, 'triumph-street-triple-765-rs-2024', NULL, NULL, NULL, NULL, NULL, NULL, '120/70ZR17', '180/55ZR17', NULL, 765, NULL, NULL, NULL, NULL, 78.5, 9500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 15),
(2, 'triumph-tiger-sport-660-2024', NULL, NULL, NULL, 'Pinças Nissin de dois pistões com discos duplos de 310 mm, ABS', 'Pinça deslizante Nissin de pistão simples, disco simples de 255 mm, ABS', NULL, '120/70 ZR 17 (58W)', '180/55 ZR 17 (73W)', 'Refrigeração líquida, 12 válvulas, DOHC, 3 cilindros em linha, intervalo de', 660, 3, 4, NULL, NULL, 62.8, 6250, NULL, NULL, 51.1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 834, 1398, NULL, 835, NULL, 206, NULL, 17.2),
(3, 'triumph-rocket-3-gt-2024', NULL, NULL, NULL, NULL, NULL, NULL, '150/80 R17', '240/50 R16', NULL, 2458, NULL, NULL, NULL, NULL, 224.6, 4000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 320, NULL, 18),
(4, 'triumph-tiger-900-1994', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(5, 'triumph-tiger-900-1995', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(6, 'triumph-tiger-900-1996', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(7, 'triumph-tiger-900-1997', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(8, 'triumph-tiger-900-1998', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(9, 'triumph-tiger-900-1999', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(10, 'triumph-tiger-900-2000', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(11, 'triumph-bonneville-750cc-790cc-865cc-2001', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(12, 'triumph-bonneville-750cc-790cc-865cc-2002', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(13, 'triumph-bonneville-750cc-790cc-865cc-2004', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(14, 'triumph-bonneville-t100-2004', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR18', '160/60 ZR18', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 5000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 14),
(15, 'triumph-bonneville-750cc-790cc-865cc-2005', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(16, 'triumph-bonneville-750cc-790cc-865cc-2006', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(17, 'triumph-scrambler-900cc-2006', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70 R17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 15),
(18, 'triumph-thruxton-900cc-2006', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR17', '160/60 ZR17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 197, NULL, 14.5),
(19, 'triumph-bonneville-750cc-790cc-865cc-2007', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(20, 'triumph-scrambler-900cc-2007', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70 R17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 15),
(21, 'triumph-thruxton-900cc-2007', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR17', '160/60 ZR17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 197, NULL, 14.5),
(22, 'triumph-bonneville-750cc-790cc-865cc-2009', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(23, 'triumph-bonneville-750cc-790cc-865cc-2010', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(24, 'triumph-scrambler-900cc-2010', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70 R17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 15),
(25, 'triumph-bonneville-t100-2012', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR18', '160/60 ZR18', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 5000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 14),
(26, 'triumph-bonneville-t100-2013', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR18', '160/60 ZR18', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 5000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 14),
(27, 'triumph-bonneville-t100-2014', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR18', '160/60 ZR18', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 5000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 14),
(28, 'triumph-thruxton-900cc-2014', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR17', '160/60 ZR17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 197, NULL, 14.5),
(29, 'triumph-bonneville-t100-2015', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR18', '160/60 ZR18', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 5000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 14),
(30, 'triumph-thruxton-900cc-2015', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR17', '160/60 ZR17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 197, NULL, 14.5),
(31, 'triumph-bonneville-t120-2016', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 1200, NULL, NULL, NULL, NULL, 103, 3100, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 224, NULL, 14.5),
(32, 'triumph-street-twin-900cc-2016', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70 R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 216, NULL, 12),
(33, 'triumph-thruxton-r-1200cc-2016', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR17', '160/60 ZR17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 197, NULL, 14.5),
(34, 'triumph-bonneville-bobber-1200cc-2017', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(35, 'triumph-bonneville-t120-2017', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 1200, NULL, NULL, NULL, NULL, 103, 3100, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 224, NULL, 14.5),
(36, 'triumph-street-twin-900cc-2017', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70 R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 216, NULL, 12),
(37, 'triumph-street-triple-765-rs-2017', NULL, NULL, NULL, NULL, NULL, NULL, '120/70ZR17', '180/55ZR17', NULL, 765, NULL, NULL, NULL, NULL, 78.5, 9500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 15),
(38, 'triumph-thruxton-r-1200cc-2017', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR17', '160/60 ZR17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 197, NULL, 14.5),
(39, 'triumph-bonneville-bobber-1200cc-2018', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(40, 'triumph-bonneville-t120-2018', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 1200, NULL, NULL, NULL, NULL, 103, 3100, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 224, NULL, 14.5),
(41, 'triumph-street-twin-900cc-2018', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70 R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 216, NULL, 12),
(42, 'triumph-street-triple-765-rs-2018', NULL, NULL, NULL, NULL, NULL, NULL, '120/70ZR17', '180/55ZR17', NULL, 765, NULL, NULL, NULL, NULL, 78.5, 9500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 15),
(43, 'triumph-thruxton-r-1200cc-2018', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR17', '160/60 ZR17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 197, NULL, 14.5),
(44, 'triumph-street-twin-900cc-2019', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70 R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 216, NULL, 12),
(45, 'triumph-street-triple-765-rs-2019', NULL, NULL, NULL, NULL, NULL, NULL, '120/70ZR17', '180/55ZR17', NULL, 765, NULL, NULL, NULL, NULL, 78.5, 9500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 15),
(46, 'triumph-speed-twin-1200cc-2019', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 215, NULL, 12),
(47, 'triumph-street-twin-900cc-2020', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70 R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 216, NULL, 12),
(48, 'triumph-street-triple-765-rs-2020', NULL, NULL, NULL, NULL, NULL, NULL, '120/70ZR17', '180/55ZR17', NULL, 765, NULL, NULL, NULL, NULL, 78.5, 9500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 15),
(49, 'triumph-speed-twin-1200cc-2020', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 215, NULL, 12),
(50, 'triumph-thruxton-r-1200cc-2020', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR17', '160/60 ZR17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 197, NULL, 14.5),
(51, 'triumph-tiger-900-2020', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(52, 'triumph-tiger-900-rally-2020', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(53, 'triumph-tiger-900-rally-pro-2020', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(54, 'triumph-street-twin-900cc-2021', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70 R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 216, NULL, 12),
(55, 'triumph-street-triple-765-rs-2021', NULL, NULL, NULL, NULL, NULL, NULL, '120/70ZR17', '180/55ZR17', NULL, 765, NULL, NULL, NULL, NULL, 78.5, 9500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 15),
(56, 'triumph-speed-twin-1200cc-2021', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 215, NULL, 12),
(57, 'triumph-tiger-900-2021', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(58, 'triumph-tiger-900-rally-2021', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(59, 'triumph-tiger-900-rally-pro-2021', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(60, 'triumph-trident-660-2021', NULL, NULL, NULL, NULL, NULL, NULL, '120/70-17', '180/55-17', NULL, 660, NULL, NULL, NULL, NULL, 63.7, 6250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 14),
(61, 'triumph-bonneville-bobber-1200cc-2022', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(62, 'triumph-bonneville-t100-2022', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR18', '160/60 ZR18', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 5000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 14),
(63, 'triumph-street-twin-900cc-2022', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70 R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 216, NULL, 12),
(64, 'triumph-street-triple-765-rs-2022', NULL, NULL, NULL, NULL, NULL, NULL, '120/70ZR17', '180/55ZR17', NULL, 765, NULL, NULL, NULL, NULL, 78.5, 9500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 15),
(65, 'triumph-speed-twin-1200cc-2022', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 215, NULL, 12),
(66, 'triumph-tiger-900-rally-2022', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(67, 'triumph-tiger-900-rally-pro-2022', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(68, 'triumph-trident-660-2022', NULL, NULL, NULL, NULL, NULL, NULL, '120/70-17', '180/55-17', NULL, 660, NULL, NULL, NULL, NULL, 63.7, 6250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 14),
(69, 'triumph-bonneville-bobber-1200cc-2023', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(70, 'triumph-bonneville-t100-2023', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR18', '160/60 ZR18', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 5000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 14),
(71, 'triumph-scrambler-900cc-2023', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70 R17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 15),
(72, 'triumph-street-triple-765-rs-2023', NULL, NULL, NULL, NULL, NULL, NULL, '120/70ZR17', '180/55ZR17', NULL, 765, NULL, NULL, NULL, NULL, 78.5, 9500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 15),
(73, 'triumph-speed-twin-1200cc-2023', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 215, NULL, 12),
(74, 'triumph-speed-twin-900cc-2023', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 215, NULL, 12),
(75, 'triumph-tiger-900-rally-2023', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(76, 'triumph-tiger-900-rally-pro-2023', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(77, 'triumph-trident-660-2023', NULL, NULL, NULL, NULL, NULL, NULL, '120/70-17', '180/55-17', NULL, 660, NULL, NULL, NULL, NULL, 63.7, 6250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 14),
(78, 'triumph-bonneville-bobber-1200cc-2024', NULL, NULL, NULL, NULL, NULL, NULL, '130/90 B16', '150/80 B16', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4950, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 238, NULL, 9.1),
(79, 'triumph-bonneville-t100-2024', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR18', '160/60 ZR18', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 5000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 14),
(80, 'triumph-daytona-660-2024', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR17', '180/55 ZR17', NULL, 660, NULL, NULL, NULL, NULL, 67.7, 8250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 201, NULL, 14),
(81, 'triumph-scrambler-1200-x-2024', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70 R17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 15),
(82, 'triumph-scrambler-400-x-2024', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-19', '130/80-17', NULL, 398, NULL, NULL, NULL, NULL, 36.8, 6500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 179, NULL, 13),
(83, 'triumph-scrambler-900cc-2024', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70 R17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 15),
(84, 'triumph-speed-400-2024', NULL, NULL, NULL, NULL, NULL, NULL, '110/70R17', '150/60R17', NULL, 398, NULL, NULL, NULL, NULL, 36.8, 6500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 170, NULL, 13),
(85, 'triumph-street-triple-765-r-2024', NULL, NULL, NULL, NULL, NULL, NULL, '120/70ZR17', '180/55ZR17', NULL, 765, NULL, NULL, NULL, NULL, 78.5, 9500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 15),
(86, 'triumph-speed-twin-1200cc-2024', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 215, NULL, 12),
(87, 'triumph-speed-twin-900cc-2024', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 215, NULL, 12),
(88, 'triumph-tiger-1200-gt-pro-2024', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 R19', '150/70 R18', NULL, 1160, NULL, NULL, NULL, NULL, 129.4, 7000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 245, NULL, 20),
(89, 'triumph-tiger-900-rally-pro-2024', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(90, 'triumph-trident-660-2024', NULL, NULL, NULL, NULL, NULL, NULL, '120/70-17', '180/55-17', NULL, 660, NULL, NULL, NULL, NULL, 63.7, 6250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 14),
(91, 'triumph-bonneville-bobber-1200cc-2025', NULL, NULL, NULL, 'Disco gêmeo de Ø310mm, pinças axiais deslizantes Brembo de 2 pistão, ABS', 'Disco simples de Ø255mm, pinça axial deslizante de pistão simples Nissin, ABS', NULL, 'MT 90 B16', '150/80 R16', 'Resfriamento líquido, 8 válvulas, SOHC, ângulo de 270° da manivela com 2', 1200, 2, 4, NULL, NULL, 109.8, 4950, NULL, NULL, 80, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 800, 1024, NULL, 690, NULL, 238, NULL, 9.1),
(92, 'triumph-bonneville-speedmaster-1200cc-2025', NULL, NULL, NULL, 'Disco gêmeo de Ø310mm, pinças axiais deslizantes Brembo de 2 pistão, ABS', 'Disco simples de Ø255mm, pinça axial deslizante de pistão simples Nissin, ABS', NULL, 'MT 90 B16', '150/80 R16', 'Resfriamento líquido, 8 válvulas, SOHC, ângulo de 270° da manivela com 2', 1200, 2, 4, NULL, NULL, NULL, NULL, NULL, NULL, 80, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 910, 1055, NULL, 705, NULL, NULL, NULL, 12),
(93, 'triumph-bonneville-t100-2025', NULL, NULL, NULL, 'Disco flutuante único Ø310mm, pinça axial fixa Brembo de 2 pistão, ABS', 'Disco de 255 mm, pinça flutuante de simples pistão Nissin, ABS', NULL, '100/90-18', '150/70 R17', 'Resfriamento líquido, 8 válvulas, SOHC, ângulo de 270° da manivela com 2', 900, 2, 4, NULL, NULL, 78.5, 5000, NULL, NULL, 80, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 780, 1100, NULL, 790, NULL, 228, NULL, 14),
(94, 'triumph-daytona-660-2025', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR17', '180/55 ZR17', NULL, 660, NULL, NULL, NULL, NULL, 67.7, 8250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 201, NULL, 14),
(95, 'triumph-scrambler-1200-x-2025', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70 R17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 15),
(96, 'triumph-scrambler-400-x-2025', NULL, NULL, NULL, 'Disco fixo de 320 mm, pinça radial de quatro pistões, ABS', 'Disco fixo de 230 mm, pinça flutuante de pistão único ByBreTM, ABS', NULL, '100/90-19', '140/80-17', 'Refrigeração líquida, 4 válvulas, DOHC, cilindro único', 398, 1, 4, NULL, NULL, 36.8, 6500, NULL, NULL, 64, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 901, 1169, NULL, 835, NULL, 179, NULL, 13),
(97, 'triumph-scrambler-900cc-2025', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70 R17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 15),
(98, 'triumph-speed-400-2025', NULL, NULL, NULL, 'Disco fixo de 300 mm, pinça radial de quatro pistões, ABS', 'Disco fixo de 230 mm, pinça flutuante, ABS', NULL, '110/70 R17', '150/60 R17', 'Refrigeração líquida, 4 válvulas, DOHC, cilindro único', 398, 1, 4, NULL, NULL, 36.8, 6500, NULL, NULL, 64, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 814, 1084, NULL, 790, NULL, 170, NULL, 13),
(99, 'triumph-street-triple-765-rs-2025', NULL, NULL, NULL, NULL, NULL, NULL, '120/70ZR17', '180/55ZR17', NULL, 765, NULL, NULL, NULL, NULL, 78.5, 9500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 15),
(100, 'triumph-speed-twin-1200cc-2025', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 215, NULL, 12),
(101, 'triumph-speed-twin-900cc-2025', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 215, NULL, 12),
(102, 'triumph-tiger-1200-gt-pro-2025', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 R19', '150/70 R18', NULL, 1160, NULL, NULL, NULL, NULL, 129.4, 7000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 245, NULL, 20),
(103, 'triumph-tiger-900-rally-pro-2025', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(104, 'triumph-trident-660-2025', NULL, NULL, NULL, NULL, NULL, NULL, '120/70-17', '180/55-17', NULL, 660, NULL, NULL, NULL, NULL, 63.7, 6250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 14),
(105, 'triumph-bonneville-bobber-1200cc-2026', NULL, NULL, NULL, 'Disco gêmeo de Ø310mm, pinças axiais deslizantes Brembo de 2 pistão, ABS', 'Disco simples de Ø255mm, pinça axial deslizante de pistão simples Nissin, ABS', NULL, 'MT 90 B16', '150/80 R16', 'Resfriamento líquido, 8 válvulas, SOHC, ângulo de 270° da manivela com 2', 1200, 2, 4, NULL, NULL, 109.8, 4950, NULL, NULL, 80, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 800, 1024, NULL, 690, NULL, 238, NULL, 9.1),
(106, 'triumph-bonneville-speedmaster-1200cc-2026', NULL, NULL, NULL, 'Disco gêmeo de Ø310mm, pinças axiais deslizantes Brembo de 2 pistão, ABS', 'Disco simples de Ø255mm, pinça axial deslizante de pistão simples Nissin, ABS', NULL, 'MT 90 B16', '150/80 R16', 'Resfriamento líquido, 8 válvulas, SOHC, ângulo de 270° da manivela com 2', 1200, 2, 4, NULL, NULL, NULL, NULL, NULL, NULL, 80, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 910, 1055, NULL, 705, NULL, NULL, NULL, 12),
(107, 'triumph-daytona-660-2026', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 ZR17', '180/55 ZR17', NULL, 660, NULL, NULL, NULL, NULL, 67.7, 8250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 201, NULL, 14),
(108, 'triumph-scrambler-1200-x-2026', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70 R17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 15),
(109, 'triumph-scrambler-400-x-2026', NULL, NULL, NULL, 'Disco fixo de 320 mm, pinça radial de quatro pistões, ABS', 'Disco fixo de 230 mm, pinça flutuante de pistão único ByBreTM, ABS', NULL, '100/90-19', '140/80-17', 'Refrigeração líquida, 4 válvulas, DOHC, cilindro único', 398, 1, 4, NULL, NULL, 36.8, 6500, NULL, NULL, 64, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 901, 1169, NULL, 835, NULL, 179, NULL, 13),
(110, 'triumph-scrambler-900cc-2026', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70 R17', NULL, 1200, NULL, NULL, NULL, NULL, 109.8, 4250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 15),
(111, 'triumph-speed-400-2026', NULL, NULL, NULL, 'Disco fixo de 300 mm, pinça radial de quatro pistões, ABS', 'Disco fixo de 230 mm, pinça flutuante, ABS', NULL, '110/70 R17', '150/60 R17', 'Refrigeração líquida, 4 válvulas, DOHC, cilindro único', 398, 1, 4, NULL, NULL, 36.8, 6500, NULL, NULL, 64, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 814, 1084, NULL, 790, NULL, 170, NULL, 13),
(112, 'triumph-street-triple-765-rs-2026', NULL, NULL, NULL, NULL, NULL, NULL, '120/70ZR17', '180/55ZR17', NULL, 765, NULL, NULL, NULL, NULL, 78.5, 9500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 15),
(113, 'triumph-speed-twin-1200cc-2026', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 215, NULL, 12),
(114, 'triumph-speed-twin-900cc-2026', NULL, NULL, NULL, NULL, NULL, NULL, '100/90-18', '150/70R17', NULL, 900, NULL, NULL, NULL, NULL, 78.5, 3800, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 215, NULL, 12),
(115, 'triumph-tiger-1200-gt-pro-2026', NULL, NULL, NULL, NULL, NULL, NULL, '120/70 R19', '150/70 R18', NULL, 1160, NULL, NULL, NULL, NULL, 129.4, 7000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 245, NULL, 20),
(116, 'triumph-tiger-900-rally-pro-2026', NULL, NULL, NULL, NULL, NULL, NULL, '90/90-21', '150/70R18', NULL, 888, NULL, NULL, NULL, NULL, 85.3, 7250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 228, NULL, 20),
(117, 'triumph-trident-660-2026', NULL, NULL, NULL, NULL, NULL, NULL, '120/70-17', '180/55-17', NULL, 660, NULL, NULL, NULL, NULL, 63.7, 6250, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 189, NULL, 14);

INSERT INTO tmp_triumph_spec_kv (row_no, spec_key, spec_value) VALUES
(2, 'Rodas', 'Alumínio fundido, 17 x 5,5 pol.'),
(2, 'Embreagem', 'Deslizante assistida de multiplaca úmida'),
(91, 'Rodas', 'Fio, 32 raios 16 x 3,5 polegadas'),
(91, 'Embreagem', 'Discos múltiplos, banhados em óleo com embreagem auxiliar de torque'),
(92, 'Rodas', 'Fio, 32 raios 16 x 3,5 polegadas'),
(92, 'Embreagem', 'Discos múltiplos, banhados em óleo com embreagem auxiliar de torque'),
(93, 'Rodas', '32 raios 17 x 4,25 polegadas'),
(93, 'Embreagem', 'Discos múltiplos, banhados em óleo com embreagem auxiliar de torque'),
(96, 'Rodas', 'Liga de alumínio fundido, 10 raios, 17 x 3,5 polegadas'),
(96, 'Embreagem', 'Banhada a óleo, multidiscos, com auxiliar de torque'),
(98, 'Rodas', 'Liga de alumínio fundido, 10 raios, 17 x 4 polegadas'),
(98, 'Embreagem', 'Banhada a óleo, multidiscos, com auxiliar de torque'),
(105, 'Rodas', 'Fio, 32 raios 16 x 3,5 polegadas'),
(105, 'Embreagem', 'Discos múltiplos, banhados em óleo com embreagem auxiliar de torque'),
(106, 'Rodas', 'Fio, 32 raios 16 x 3,5 polegadas'),
(106, 'Embreagem', 'Discos múltiplos, banhados em óleo com embreagem auxiliar de torque'),
(109, 'Rodas', 'Liga de alumínio fundido, 10 raios, 17 x 3,5 polegadas'),
(109, 'Embreagem', 'Banhada a óleo, multidiscos, com auxiliar de torque'),
(111, 'Rodas', 'Liga de alumínio fundido, 10 raios, 17 x 4 polegadas'),
(111, 'Embreagem', 'Banhada a óleo, multidiscos, com auxiliar de torque');

-- Bind every scraped row to its catalogue row. The FIPE seed derives the same slug from the same
-- FIPE model descriptor, so this is an equality join and not a fuzzy match.
UPDATE tmp_triumph_spec_import s
SET motorcycle_id = m.id,
    engine_id     = m.engine_specification_id,
    dimension_id  = m.dimension_id
FROM motorcycles m
WHERE m.slug = s.slug;

DO $$
BEGIN
    IF pg_get_serial_sequence('engine_specifications', 'id') IS NULL
       OR pg_get_serial_sequence('dimensions', 'id') IS NULL THEN
        RAISE EXCEPTION 'A target table has no serial/identity sequence';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Gap-fill only. No motorcycle row is ever created here (see the header note);
-- every statement below is scoped to rows that resolved to an existing catalogue row.
-- ---------------------------------------------------------------------------

-- The FIPE seed created an engine block for every row it inserted, but give a matched row one
-- anyway if it somehow lacks it, so its figures are not silently dropped below.
UPDATE tmp_triumph_spec_import
SET engine_id = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)
WHERE motorcycle_id IS NOT NULL AND engine_id IS NULL;

INSERT INTO engine_specifications (id)
SELECT s.engine_id
FROM tmp_triumph_spec_import s
WHERE s.motorcycle_id IS NOT NULL
  AND s.engine_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM engine_specifications e WHERE e.id = s.engine_id)
ORDER BY s.row_no;

UPDATE motorcycles m
SET engine_specification_id = s.engine_id
FROM tmp_triumph_spec_import s
WHERE m.id = s.motorcycle_id AND m.engine_specification_id IS NULL;

UPDATE engine_specifications e
SET engine_type              = COALESCE(e.engine_type, s.engine_type),
    displacement_cc          = COALESCE(e.displacement_cc, s.displacement_cc),
    cylinders                = COALESCE(e.cylinders, s.cylinders),
    valves_per_cylinder      = COALESCE(e.valves_per_cylinder, s.valves_per_cylinder),
    max_power_hp             = COALESCE(e.max_power_hp, s.max_power_hp),
    max_power_rpm            = COALESCE(e.max_power_rpm, s.max_power_rpm),
    max_torque_nm            = COALESCE(e.max_torque_nm, s.max_torque_nm),
    max_torque_rpm           = COALESCE(e.max_torque_rpm, s.max_torque_rpm),
    compression_ratio        = COALESCE(e.compression_ratio, s.compression_ratio),
    bore_mm                  = COALESCE(e.bore_mm, s.bore_mm),
    stroke_mm                = COALESCE(e.stroke_mm, s.stroke_mm),
    cooling_system           = COALESCE(e.cooling_system, s.cooling_system),
    fuel_system              = COALESCE(e.fuel_system, s.fuel_system),
    transmission_type        = COALESCE(e.transmission_type, s.transmission_type),
    gears                    = COALESCE(e.gears, s.gears),
    final_drive              = COALESCE(e.final_drive, s.final_drive),
    top_speed_kph            = COALESCE(e.top_speed_kph, s.top_speed_kph),
    fuel_consumption_l_100km = COALESCE(e.fuel_consumption_l_100km, s.fuel_consumption_l_100km),
    emission_standard        = COALESCE(e.emission_standard, s.emission_standard)
FROM tmp_triumph_spec_import s
WHERE e.id = s.engine_id AND s.motorcycle_id IS NOT NULL;

-- A row seeded from FIPE carries no dimension block at all, so allocate one wherever this import
-- has something to put in it.
UPDATE tmp_triumph_spec_import
SET dimension_id = nextval(pg_get_serial_sequence('dimensions', 'id')::regclass)
WHERE motorcycle_id IS NOT NULL
  AND dimension_id IS NULL
  AND num_nonnulls(length_mm, width_mm, height_mm, wheelbase_mm, seat_height_mm,
                   ground_clearance_mm, kerb_weight_kg, dry_weight_kg, fuel_capacity_l) > 0;

INSERT INTO dimensions (
    id, length_mm, width_mm, height_mm, wheelbase_mm, seat_height_mm, ground_clearance_mm,
    kerb_weight_kg, dry_weight_kg, fuel_capacity_l
)
SELECT s.dimension_id, s.length_mm, s.width_mm, s.height_mm, s.wheelbase_mm, s.seat_height_mm,
       s.ground_clearance_mm, s.kerb_weight_kg, s.dry_weight_kg, s.fuel_capacity_l
FROM tmp_triumph_spec_import s
WHERE s.motorcycle_id IS NOT NULL
  AND s.dimension_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM dimensions d WHERE d.id = s.dimension_id)
ORDER BY s.row_no;

-- Where a dimension block already existed, gap-fill it the same way as the engine block.
UPDATE dimensions d
SET length_mm           = COALESCE(d.length_mm, s.length_mm),
    width_mm            = COALESCE(d.width_mm, s.width_mm),
    height_mm           = COALESCE(d.height_mm, s.height_mm),
    wheelbase_mm        = COALESCE(d.wheelbase_mm, s.wheelbase_mm),
    seat_height_mm      = COALESCE(d.seat_height_mm, s.seat_height_mm),
    ground_clearance_mm = COALESCE(d.ground_clearance_mm, s.ground_clearance_mm),
    kerb_weight_kg      = COALESCE(d.kerb_weight_kg, s.kerb_weight_kg),
    dry_weight_kg       = COALESCE(d.dry_weight_kg, s.dry_weight_kg),
    fuel_capacity_l     = COALESCE(d.fuel_capacity_l, s.fuel_capacity_l)
FROM tmp_triumph_spec_import s
WHERE d.id = s.dimension_id AND s.motorcycle_id IS NOT NULL;

-- A gap-fill that would push dry weight above a kerb weight already on the row is dropped: the
-- CHECK would otherwise abort the entire import over one bad figure. This import never writes
-- dry_weight_kg itself, but newly gap-filled kerb_weight_kg alone can still trip an existing value.
UPDATE dimensions
SET dry_weight_kg = NULL
WHERE dry_weight_kg IS NOT NULL
  AND kerb_weight_kg IS NOT NULL
  AND dry_weight_kg > kerb_weight_kg;

UPDATE motorcycles m
SET frame_type       = COALESCE(m.frame_type, s.frame_type),
    front_suspension = COALESCE(m.front_suspension, s.front_suspension),
    rear_suspension  = COALESCE(m.rear_suspension, s.rear_suspension),
    front_brake      = COALESCE(m.front_brake, s.front_brake),
    rear_brake       = COALESCE(m.rear_brake, s.rear_brake),
    abs_type         = COALESCE(m.abs_type, s.abs_type),
    front_tyre       = COALESCE(m.front_tyre, s.front_tyre),
    rear_tyre        = COALESCE(m.rear_tyre, s.rear_tyre),
    dimension_id     = COALESCE(m.dimension_id, s.dimension_id),
    -- @Version belongs to Hibernate; bump it here because this writes behind the ORM's back, so a
    -- session holding a stale copy of one of these rows fails its next flush instead of quietly
    -- overwriting the import.
    version          = m.version + 1,
    updated_at       = CURRENT_TIMESTAMP
FROM tmp_triumph_spec_import s
WHERE m.id = s.motorcycle_id
  -- Each arm is "the row lacks it AND the import supplies it", so a row this file has nothing left
  -- to add is not touched at all. Testing only for NULL would bump version and updated_at on every
  -- re-run for any column the source never published.
  AND ((m.frame_type IS NULL AND s.frame_type IS NOT NULL)
       OR (m.front_suspension IS NULL AND s.front_suspension IS NOT NULL)
       OR (m.rear_suspension IS NULL AND s.rear_suspension IS NOT NULL)
       OR (m.front_brake IS NULL AND s.front_brake IS NOT NULL)
       OR (m.rear_brake IS NULL AND s.rear_brake IS NOT NULL)
       OR (m.abs_type IS NULL AND s.abs_type IS NOT NULL)
       OR (m.front_tyre IS NULL AND s.front_tyre IS NOT NULL)
       OR (m.rear_tyre IS NULL AND s.rear_tyre IS NOT NULL)
       OR (m.dimension_id IS NULL AND s.dimension_id IS NOT NULL));

-- ---------------------------------------------------------------------------
-- Long-tail specs. DO NOTHING preserves whatever is already stored under the same key,
-- including the FIPE seed's own 'Fuel' and 'Reference price (BRL)'.
-- ---------------------------------------------------------------------------
INSERT INTO motorcycle_additional_specs (motorcycle_id, spec_key, spec_value)
SELECT s.motorcycle_id, k.spec_key, k.spec_value
FROM tmp_triumph_spec_kv k
JOIN tmp_triumph_spec_import s ON s.row_no = k.row_no
WHERE s.motorcycle_id IS NOT NULL
  AND k.spec_value IS NOT NULL
  AND btrim(k.spec_value) <> ''
ON CONFLICT (motorcycle_id, spec_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Defensive checks before commit.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad bigint;
BEGIN
    -- Expected, not an error: this import must never invent a motorcycle row for a slug the
    -- catalogue does not have, so an unresolved row is reported and left alone, not aborted.
    SELECT count(*) INTO bad FROM tmp_triumph_spec_import WHERE motorcycle_id IS NULL;
    IF bad <> 0 THEN
        RAISE NOTICE 'Triumph spec import: % scraped rows had no matching catalogue slug and were skipped (no new motorcycle created)', bad;
    END IF;

    SELECT count(*) INTO bad
    FROM tmp_triumph_spec_import s
    JOIN motorcycles m ON m.id = s.motorcycle_id
    WHERE s.engine_id IS NOT NULL AND m.engine_specification_id IS DISTINCT FROM s.engine_id;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Triumph spec import: % rows have a mismatched engine block', bad;
    END IF;

    SELECT count(*) INTO bad
    FROM dimensions
    WHERE dry_weight_kg IS NOT NULL AND kerb_weight_kg IS NOT NULL AND dry_weight_kg > kerb_weight_kg;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Triumph spec import: % dimension rows have dry weight above kerb weight', bad;
    END IF;

    -- lower(brand) rather than brand: V4 normalised casing before any seed had inserted a row, so
    -- an equality test here would silently depend on which seed wrote the row.
    SELECT count(*) INTO bad
    FROM motorcycles
    WHERE lower(brand) = 'triumph' AND slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Triumph spec import: % rows have a slug the public routing cannot use', bad;
    END IF;

    -- The COALESCE gap-fill merges columns independently, so a row can in principle end up with a
    -- bore from one source and a stroke from another; the geometric quartet has to agree with itself
    -- (5% tolerance absorbs published rounding on bore/stroke). No row in this snapshot supplies a
    -- bore, so this never fires against anything this import contributes - it guards a future merge.
    SELECT count(*) INTO bad
    FROM motorcycles m
    JOIN engine_specifications e ON e.id = m.engine_specification_id
    WHERE lower(m.brand) = 'triumph'
      AND e.bore_mm IS NOT NULL AND e.stroke_mm IS NOT NULL
      AND e.cylinders IS NOT NULL AND e.displacement_cc IS NOT NULL
      AND abs(pi() / 4 * e.bore_mm * e.bore_mm * e.stroke_mm * e.cylinders / 1000.0 - e.displacement_cc)
          > 0.05 * e.displacement_cc;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Triumph spec import: % engine rows have bore/stroke/cylinders inconsistent with displacement_cc', bad;
    END IF;
END $$;

COMMIT;
