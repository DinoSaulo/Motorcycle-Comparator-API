-- Motorcycle Comparison API - engine_specifications core-field backfill
--
-- Purpose: back-fill engine_specifications columns for catalogue rows sql-pro identified as
-- "core-incomplete" - missing at least one of displacement_cc, cylinders, max_power_hp,
-- max_power_rpm, max_torque_nm, max_torque_rpm. Every motorcycle already has an
-- engine_specifications row (1:1 via motorcycles.engine_specification_id, UNIQUE), so unlike the
-- suspension seed beside this one this is a column-level gap-fill inside an existing child row, not
-- a bare-columns-on-motorcycles fill. The defensive "give it an engine block if it somehow lacks
-- one" step below is inherited from R__motorcycles_displacement_cc_2026_09.sql for the same table -
-- it is not expected to fire against a catalogue where that seed has already run.
--
-- Provenance: researched by engine platform (one spec sheet covering every badge/trim sharing that
-- engine) via tools/engine-specs-research.json, gathered by a deep-research pass against
-- manufacturer press kits and official spec pages, corroborated against enthusiast references.
-- Regenerate this file with tools/import-engine-specs.mjs - every count in this header is computed
-- by that script, never typed. Sources drawn on for this batch:
--   50factory.com, autoevolution.com, bennetts.co.uk, cyclenews.com, cycleworld.com, ducati.com,
--   en.wikipedia.org, globalsuzuki.com, h-dmediakit.com, honda-mideast.com, honda.co.uk,
--   hondanews.com, hondanews.eu, jdpower.com, kawasaki.com, kawasaki.eu, moto-data.net,
--   motorcycle.com, motorcyclenews.com, motorcyclespecifications.com, peugeot-motocycle.com,
--   powersports.honda.com, suzukicycles.com, triumph-mediakits.com, triumphmotorcycles.com,
--   ultimatemotorcycling.com, ultimatespecs.com, visordown.com, webbikeworld.com,
--   yamaha-motor.eu, yamahamotorsports.com
--
-- Scope of this batch (staged/gap-slugs by brand): Bmw 6/44, Ducati 10/157, Harley 23/128, Honda
-- 48/152, Kawasaki 26/132, Peugeot 25/255, Suzuki 31/121, Triumph 38/155, Yamaha 21/156.
-- 4082 catalogue slugs were core-incomplete; 228 are staged below, 3854 deliberately left out.
-- The omitted slugs are overwhelmingly motocross/enduro competition models (KTM EXC/SX, Yamaha YZ,
-- Kawasaki KX, Suzuki RM, Honda CRF-R, Beta, Sherco, Gas Gas) whose manufacturers do not publish
-- power or torque figures - displacement and bore/stroke alone cannot clear this file's floor of 3
-- of the 6 key fields. A NULL is preferable to an invented figure, the same policy the displacement
-- and suspension seeds already apply.
--
-- Field fill across the 228 staged rows: engine_type 228, displacement_cc 228, cylinders 228,
-- valves_per_cylinder 228, max_power_hp 211, max_power_rpm 178, max_torque_nm 215, max_torque_rpm
-- 161, compression_ratio 140, bore_mm 182, stroke_mm 188, cooling_system 228, fuel_system 228,
-- transmission_type 204, gears 192, final_drive 228, top_speed_kph 7, fuel_consumption_l_100km 1.
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. The slugs staged here already carry the model year (motorcycles.slug is
-- slugify(brand + model + model_year)), so this join resolves for real.
--
-- Ordering, and why the filename is load-bearing: Flyway runs repeatable migrations in description
-- order. "zzzz motorcycles engine specs 2026 09" sorts after "zzzz motorcycles 1000ps specs 2026 09"
-- ('1' < 'e') so it runs after every per-brand seed and the 1000PS gap-fill and cannot pre-empt any
-- of their claims, and before "zzzz motorcycles suspension 2026 09" ('e' < 's') - though the two do
-- not compete for any column, so their relative order carries no correctness weight.
--
-- 0 values exceeded their column width and were cut at a word boundary rather than mid-word.
--
-- Idempotent and repeatable: every write below is COALESCE(existing, staged), so re-running this file
-- changes nothing once it has been applied. No CHECK constraint in this table has a ceiling, only a
-- floor (> 0 / >= 0), so values like a 225 Nm cruiser or a 106 mm big-bore twin are staged as-is.

BEGIN;

CREATE TEMP TABLE tmp_motorcycle_engine_specs (
    row_no        bigint PRIMARY KEY,
    slug          varchar(160) NOT NULL,
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
    motorcycle_id bigint,
    engine_id     bigint,
    CONSTRAINT uk_tmp_motorcycle_engine_specs_slug UNIQUE (slug)
) ON COMMIT DROP;

INSERT INTO tmp_motorcycle_engine_specs (row_no, slug, engine_type, displacement_cc, cylinders, valves_per_cylinder, max_power_hp, max_power_rpm, max_torque_nm, max_torque_rpm, compression_ratio, bore_mm, stroke_mm, cooling_system, fuel_system, transmission_type, gears, final_drive, top_speed_kph, fuel_consumption_l_100km, emission_standard) VALUES
(1, 'bmw-r-ninet-100-years-2023', 'Air/oil-cooled DOHC boxer twin 4-stroke', 1170, 2, 4, 109, NULL, 116, 6000, NULL, NULL, 73, 'Air-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Shaft', NULL, NULL, NULL),
(2, 'bmw-r-ninet-2023', 'Air/oil-cooled DOHC boxer twin 4-stroke', 1170, 2, 4, 109, NULL, 116, 6000, NULL, NULL, 73, 'Air-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Shaft', NULL, NULL, NULL),
(3, 'bmw-r-ninet-5-2020', 'Air/oil-cooled DOHC boxer twin 4-stroke', 1170, 2, 4, 109, NULL, 116, 6000, NULL, NULL, 73, 'Air-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Shaft', NULL, NULL, NULL),
(4, 'bmw-r-ninet-pure-2023', 'Air/oil-cooled DOHC boxer twin 4-stroke', 1170, 2, 4, 109, NULL, 116, 6000, NULL, NULL, 73, 'Air-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Shaft', NULL, NULL, NULL),
(5, 'bmw-r-ninet-racer-2020', 'Air/oil-cooled DOHC boxer twin 4-stroke', 1170, 2, 4, 109, NULL, 116, 6000, NULL, NULL, 73, 'Air-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Shaft', NULL, NULL, NULL),
(6, 'bmw-r-ninet-urban-g-s-2023', 'Air/oil-cooled DOHC boxer twin 4-stroke', 1170, 2, 4, 109, NULL, 116, 6000, NULL, NULL, 73, 'Air-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Shaft', NULL, NULL, NULL),
(7, 'ducati-multistrada-1260-2020', 'Liquid-cooled Testastretta DVT L-twin', 1262, 2, 4, 158, 9500, 129, 7500, '13.0:1', 106, 71.5, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(8, 'ducati-multistrada-1260-pikes-peak-2020', 'Liquid-cooled Testastretta DVT L-twin', 1262, 2, 4, 158, 9500, 129, 7500, '13.0:1', 106, 71.5, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(9, 'ducati-multistrada-1260-s-2020', 'Liquid-cooled Testastretta DVT L-twin', 1262, 2, 4, 158, 9500, 129, 7500, '13.0:1', 106, 71.5, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(10, 'ducati-multistrada-1260-s-dair-2020', 'Liquid-cooled Testastretta DVT L-twin', 1262, 2, 4, 158, 9500, 129, 7500, '13.0:1', 106, 71.5, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(11, 'ducati-multistrada-1260-s-grand-tour-2020', 'Liquid-cooled Testastretta DVT L-twin', 1262, 2, 4, 158, 9500, 129, 7500, '13.0:1', 106, 71.5, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(12, 'ducati-panigale-v4-2026', 'Liquid-cooled Desmosedici Stradale V4', 1103, 4, 4, 216, NULL, 120.6, NULL, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(13, 'ducati-scrambler-10-anniversario-rizoma-edition-2025', 'Air-cooled Desmodue L-twin 4-stroke', 803, 2, 2, 73, 8250, 65.2, 7000, '11.0:1', 88, 66, 'Air-cooled', 'Electronic fuel injection, 50mm throttle body', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(14, 'ducati-scrambler-100-2026', 'Air-cooled Desmodue L-twin 4-stroke', 803, 2, 2, 73, 8250, 65.2, 7000, '11.0:1', 88, 66, 'Air-cooled', 'Electronic fuel injection, 50mm throttle body', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(15, 'ducati-scrambler-full-throttle-2026', 'Air-cooled Desmodue L-twin 4-stroke', 803, 2, 2, 73, 8250, 65.2, 7000, '11.0:1', 88, 66, 'Air-cooled', 'Electronic fuel injection, 50mm throttle body', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(16, 'ducati-scrambler-icon-dark-2026', 'Air-cooled Desmodue L-twin 4-stroke', 803, 2, 2, 73, 8250, 65.2, 7000, '11.0:1', 88, 66, 'Air-cooled', 'Electronic fuel injection, 50mm throttle body', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(17, 'harley-davidson-softail-breakout-114-fxbrs-2022', 'Air-cooled Milwaukee-Eight 114 V-twin', 1868, 2, 4, 100.6, 5020, 161, NULL, NULL, NULL, NULL, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 6-speed', 6, 'Belt', NULL, NULL, NULL),
(18, 'harley-davidson-softail-fat-bob-114-fxfbs-2024', 'Air-cooled Milwaukee-Eight 114 V-twin', 1868, 2, 4, 100.6, 5020, 161, NULL, NULL, NULL, NULL, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 6-speed', 6, 'Belt', NULL, NULL, NULL),
(19, 'harley-davidson-softail-fat-boy-114-flfbs-2024', 'Air-cooled Milwaukee-Eight 114 V-twin', 1868, 2, 4, 100.6, 5020, 161, NULL, NULL, NULL, NULL, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 6-speed', 6, 'Belt', NULL, NULL, NULL),
(20, 'harley-davidson-softail-fxdr-114-fxdrs-2020', 'Air-cooled Milwaukee-Eight 114 V-twin', 1868, 2, 4, 100.6, 5020, 161, NULL, NULL, NULL, NULL, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 6-speed', 6, 'Belt', NULL, NULL, NULL),
(21, 'harley-davidson-softail-heritage-classic-114-flhcs-2024', 'Air-cooled Milwaukee-Eight 114 V-twin', 1868, 2, 4, 100.6, 5020, 161, NULL, NULL, NULL, NULL, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 6-speed', 6, 'Belt', NULL, NULL, NULL),
(22, 'harley-davidson-softail-street-bob-114-fxbbs-2024', 'Air-cooled Milwaukee-Eight 114 V-twin', 1868, 2, 4, 100.6, 5020, 161, NULL, NULL, NULL, NULL, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 6-speed', 6, 'Belt', NULL, NULL, NULL),
(23, 'harley-davidson-sportster-xl-1200-l-low-2009', 'Air-cooled Evolution 45-degree V-twin', 1200, 2, 2, NULL, NULL, 93, NULL, '9.7:1', 88.9, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(24, 'harley-davidson-sportster-xl-1200-n-nightster-2012', 'Air-cooled Evolution 45-degree V-twin', 1200, 2, 2, NULL, NULL, 93, NULL, '9.7:1', 88.9, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(25, 'harley-davidson-sportster-xl-1200-r-roadster-2017', 'Air-cooled Evolution 45-degree V-twin', 1200, 2, 2, NULL, NULL, 93, NULL, '9.7:1', 88.9, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(26, 'harley-davidson-sportster-xl-1200-v-seventy-two-2016', 'Air-cooled Evolution 45-degree V-twin', 1200, 2, 2, NULL, NULL, 93, NULL, '9.7:1', 88.9, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(27, 'harley-davidson-sportster-xl-1200c-custom-2020', 'Air-cooled Evolution 45-degree V-twin', 1200, 2, 2, NULL, NULL, 93, NULL, '9.7:1', 88.9, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(28, 'harley-davidson-sportster-xl-1200ca-2016', 'Air-cooled Evolution 45-degree V-twin', 1200, 2, 2, NULL, NULL, 93, NULL, '9.7:1', 88.9, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(29, 'harley-davidson-sportster-xl-1200cb-2016', 'Air-cooled Evolution 45-degree V-twin', 1200, 2, 2, NULL, NULL, 93, NULL, '9.7:1', 88.9, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(30, 'harley-davidson-sportster-xl-1200cx-roadster-2020', 'Air-cooled Evolution 45-degree V-twin', 1200, 2, 2, NULL, NULL, 93, NULL, '9.7:1', 88.9, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(31, 'harley-davidson-sportster-xl-1200ns-iron-2020', 'Air-cooled Evolution 45-degree V-twin', 1200, 2, 2, NULL, NULL, 93, NULL, '9.7:1', 88.9, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(32, 'harley-davidson-sportster-xl-1200t-superlow-2020', 'Air-cooled Evolution 45-degree V-twin', 1200, 2, 2, NULL, NULL, 93, NULL, '9.7:1', 88.9, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(33, 'harley-davidson-sportster-xl-1200x-forty-eight-2020', 'Air-cooled Evolution 45-degree V-twin', 1200, 2, 2, NULL, NULL, 93, NULL, '9.7:1', 88.9, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(34, 'harley-davidson-sportster-xl-1200xs-forty-eight-special-2020', 'Air-cooled Evolution 45-degree V-twin', 1200, 2, 2, NULL, NULL, 93, NULL, '9.7:1', 88.9, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(35, 'harley-davidson-sportster-xl-883-2009', 'Air-cooled Evolution 45-degree V-twin', 883, 2, 2, NULL, NULL, 69, NULL, '8.9:1', 76.2, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(36, 'harley-davidson-sportster-xl-883-c-custom-2010', 'Air-cooled Evolution 45-degree V-twin', 883, 2, 2, NULL, NULL, 69, NULL, '8.9:1', 76.2, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(37, 'harley-davidson-sportster-xl-883-l-superlow-2020', 'Air-cooled Evolution 45-degree V-twin', 883, 2, 2, NULL, NULL, 69, NULL, '8.9:1', 76.2, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(38, 'harley-davidson-sportster-xl-883-n-iron-2020', 'Air-cooled Evolution 45-degree V-twin', 883, 2, 2, NULL, NULL, 69, NULL, '8.9:1', 76.2, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(39, 'harley-davidson-sportster-xl-883-r-roadster-2016', 'Air-cooled Evolution 45-degree V-twin', 883, 2, 2, NULL, NULL, 69, NULL, '8.9:1', 76.2, 96.8, 'Air-cooled', 'Electronic sequential port fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(40, 'honda-adv350-2026', 'Liquid-cooled eSP+ SOHC single 4-stroke', 330, 1, 4, 28.8, 7500, 31.5, 5250, NULL, NULL, NULL, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(41, 'honda-adv350-special-edition-2026', 'Liquid-cooled eSP+ SOHC single 4-stroke', 330, 1, 4, 28.8, 7500, 31.5, 5250, NULL, NULL, NULL, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(42, 'honda-cb-1000-r-2024', 'Liquid-cooled DOHC inline-four 4-stroke', 998, 4, 4, 142, NULL, 104, NULL, '11.6:1', 75, 56.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(43, 'honda-cb1000-hornet-sp-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 1000, 4, 4, 155, 11000, 108.5, 9000, '11.7:1', 76, 55.1, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(44, 'honda-cb1000r-black-edition-2024', 'Liquid-cooled DOHC inline-four 4-stroke', 998, 4, 4, 142, NULL, 104, NULL, '11.6:1', 75, 56.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(45, 'honda-cb125r-2026', 'Liquid-cooled DOHC single 4-stroke', 125, 1, 4, 14.8, 10000, 11.6, 8000, '11.3:1', 57.3, 48.4, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(46, 'honda-cb300r-2026', 'Liquid-cooled DOHC single 4-stroke', 286, 1, 4, 31, 8500, 27.5, 7500, '10.7:1', 76, 63, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(47, 'honda-cb500-hornet-2026', 'Liquid-cooled parallel-twin 4-stroke', 471, 2, 4, 47, 8500, 43, 6500, '10.7:1', 67, 66.8, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(48, 'honda-cb500-hornet-e-clutch-2026', 'Liquid-cooled parallel-twin 4-stroke', 471, 2, 4, 47, 8500, 43, 6500, '10.7:1', 67, 66.8, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed with Honda E-Clutch', 6, 'Chain', NULL, NULL, NULL),
(49, 'honda-cb500f-2023', 'Liquid-cooled parallel-twin 4-stroke', 471, 2, 4, 46.9, 8600, 43, 6500, '10.7:1', 67, 66.8, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(50, 'honda-cb500x-2023', 'Liquid-cooled parallel-twin 4-stroke', 471, 2, 4, 46.9, 8600, 43, 6500, '10.7:1', 67, 66.8, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(51, 'honda-cb650r-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 649, 4, 4, 94, 12000, 63, 9500, '11.6:1', 67, 46, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed with Honda E-Clutch', 6, 'Chain', NULL, NULL, NULL),
(52, 'honda-cb750-hornet-2026', 'Liquid-cooled Unicam SOHC parallel-twin', 755, 2, 4, 90.5, 9500, 75, 7250, '11.0:1', 87, 63.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(53, 'honda-cbr1000rr-r-fireblade-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 999, 4, 4, 217.6, 14500, 113, 12500, NULL, 81, 48.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(54, 'honda-cbr1000rr-r-fireblade-sp-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 999, 4, 4, 217.6, 14500, 113, 12500, NULL, 81, 48.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(55, 'honda-cbr500r-2026', 'Liquid-cooled parallel-twin 4-stroke', 471, 2, 4, 47, 8500, 43, 6500, '10.7:1', 67, 66.8, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(56, 'honda-cbr500r-e-clutch-2026', 'Liquid-cooled parallel-twin 4-stroke', 471, 2, 4, 47, 8500, 43, 6500, '10.7:1', 67, 66.8, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed with Honda E-Clutch', 6, 'Chain', NULL, NULL, NULL),
(57, 'honda-cbr650r-e-clutch-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 649, 4, 4, 94, 12000, 63, 9500, '11.6:1', 67, 46, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed with Honda E-Clutch', 6, 'Chain', NULL, NULL, NULL),
(58, 'honda-cmx1100-rebel-2026', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 88, 7250, 98, 4750, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(59, 'honda-cmx1100-rebel-dct-2026', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 88, 7250, 98, 4750, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic DCT, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(60, 'honda-cmx1100-rebel-se-2025', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 88, 7250, 98, 4750, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(61, 'honda-cmx1100-rebel-se-dct-2026', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 88, 7250, 98, 4750, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic DCT, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(62, 'honda-cmx1100t-rebel-2026', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 88, 7250, 98, 4750, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(63, 'honda-cmx1100t-rebel-dct-2026', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 88, 7250, 98, 4750, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic DCT, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(64, 'honda-crf1100l-africa-twin-2026', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 101, 7500, 112, 5500, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(65, 'honda-crf1100l-africa-twin-adventure-sports-dct-2026', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 101, 7500, 112, 5500, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic DCT, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(66, 'honda-crf1100l-africa-twin-dct-2026', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 101, 7500, 112, 5500, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic DCT, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(67, 'honda-crf1100l-africa-twin-dct-es-2026', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 101, 7500, 112, 5500, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic DCT, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(68, 'honda-crf1100l-africa-twin-es-2026', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 101, 7500, 112, 5500, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(69, 'honda-crf300-rally-2026', 'Liquid-cooled DOHC single 4-stroke', 286, 1, 4, 27, 8500, 26.6, 6500, '10.7:1', 76, 63, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(70, 'honda-crf300l-2026', 'Liquid-cooled DOHC single 4-stroke', 286, 1, 4, 27, 8500, 26.6, 6500, '10.7:1', 76, 63, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(71, 'honda-dax-125-2026', 'Air-cooled SOHC single 4-stroke', 124, 1, 2, 9.3, 7000, 10.8, 5000, '10.0:1', 50, 63.1, 'Air-cooled', 'PGM-FI fuel injection', NULL, NULL, 'Chain', NULL, NULL, NULL),
(72, 'honda-forza-125-2026', 'Liquid-cooled eSP+ SOHC single 4-stroke', 125, 1, 4, 14.3, 8750, 12.3, 6500, '11.5:1', 53.5, 55.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic CVT', NULL, 'Belt', 108, NULL, NULL),
(73, 'honda-forza-125-special-edition-2026', 'Liquid-cooled eSP+ SOHC single 4-stroke', 125, 1, 4, 14.3, 8750, 12.3, 6500, '11.5:1', 53.5, 55.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic CVT', NULL, 'Belt', 108, NULL, NULL),
(74, 'honda-forza-350-2026', 'Liquid-cooled eSP+ SOHC single 4-stroke', 330, 1, 4, 28.8, 7500, 31.5, 5250, NULL, NULL, NULL, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic CVT', NULL, 'Belt', 137, NULL, NULL),
(75, 'honda-forza-350-special-edition-2026', 'Liquid-cooled eSP+ SOHC single 4-stroke', 330, 1, 4, 28.8, 7500, 31.5, 5250, NULL, NULL, NULL, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic CVT', NULL, 'Belt', 137, NULL, NULL),
(76, 'honda-gl-1800-goldwing-2021', 'Liquid-cooled flat-six 4-stroke', 1833, 6, 4, 124.7, 5500, 170, 4500, '10.5:1', 73, 73, 'Liquid-cooled', 'PGM-FI fuel injection', NULL, NULL, 'Shaft', NULL, NULL, NULL),
(77, 'honda-gl-1800-goldwing-dct-2026', 'Liquid-cooled flat-six 4-stroke', 1833, 6, 4, 124.7, 5500, 170, 4500, '10.5:1', 73, 73, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic DCT, 7-speed', 7, 'Shaft', NULL, NULL, NULL),
(78, 'honda-gl-1800-goldwing-tour-2023', 'Liquid-cooled flat-six 4-stroke', 1833, 6, 4, 124.7, 5500, 170, 4500, '10.5:1', 73, 73, 'Liquid-cooled', 'PGM-FI fuel injection', NULL, NULL, 'Shaft', NULL, NULL, NULL),
(79, 'honda-nc750x-dct-2027', 'Liquid-cooled SOHC parallel-twin 4-stroke', 745, 2, 4, 57.8, 6750, 69, 4750, '10.7:1', 77, 80, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic DCT, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(80, 'honda-nt1100-dct-2025', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 100.6, 7500, 112, 5500, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic DCT, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(81, 'honda-nt1100-dct-electronic-suspension-2026', 'Liquid-cooled Unicam parallel-twin 4-stroke', 1084, 2, 4, 100.6, 7500, 112, 5500, '10.5:1', 92, 81.5, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic DCT, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(82, 'honda-nx500-2026', 'Liquid-cooled parallel-twin 4-stroke', 471, 2, 4, 47, 8500, 43, 6500, '10.7:1', 67, 66.8, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(83, 'honda-super-cub-c-125-2026', 'Air-cooled SOHC single 4-stroke', 124, 1, 2, 9.7, 7500, 10.4, 6250, '10.0:1', 50, 63.1, 'Air-cooled', 'PGM-FI fuel injection', NULL, NULL, 'Chain', NULL, NULL, NULL),
(84, 'honda-vfr-800-f-2020', 'Liquid-cooled DOHC 90-degree V4 4-stroke', 782, 4, 4, 104.6, 10250, 75, 8500, '11.8:1', 72, 48, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(85, 'honda-vfr800x-crossrunner-2020', 'Liquid-cooled DOHC 90-degree V4 4-stroke', 782, 4, 4, 107, 10250, 77, 8500, '11.8:1', 72, 48, 'Liquid-cooled', 'PGM-FI fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(86, 'honda-x-adv-2027', 'Liquid-cooled SOHC parallel-twin 4-stroke', 745, 2, 4, 57.8, 6750, 69, 4750, '10.7:1', 77, 80, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic DCT, 6-speed', 6, 'Chain', 168, NULL, NULL),
(87, 'honda-x-adv-special-edition-2027', 'Liquid-cooled SOHC parallel-twin 4-stroke', 745, 2, 4, 57.8, 6750, 69, 4750, '10.7:1', 77, 80, 'Liquid-cooled', 'PGM-FI fuel injection', 'Automatic DCT, 6-speed', 6, 'Chain', 168, NULL, NULL),
(88, 'kawasaki-eliminator-500-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 451, 2, 4, 51, 10000, 43, 7500, NULL, 70, 58.6, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(89, 'kawasaki-eliminator-500-se-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 451, 2, 4, 51, 10000, 43, 7500, NULL, 70, 58.6, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(90, 'kawasaki-ninja-1100sx-2027', 'Liquid-cooled DOHC inline-four 4-stroke', 1099, 4, 4, 134.1, 9000, 112.8, NULL, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(91, 'kawasaki-ninja-1100sx-se-2027', 'Liquid-cooled DOHC inline-four 4-stroke', 1099, 4, 4, 134.1, 9000, 112.8, NULL, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(92, 'kawasaki-ninja-500-2025', 'Liquid-cooled DOHC parallel-twin 4-stroke', 451, 2, 4, 51, 10000, 43, 7500, NULL, 70, 58.6, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(93, 'kawasaki-ninja-500-se-2027', 'Liquid-cooled DOHC parallel-twin 4-stroke', 451, 2, 4, 51, 10000, 43, 7500, NULL, 70, 58.6, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(94, 'kawasaki-ninja-650-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 649, 2, 4, 67, NULL, 65.1, NULL, '10.8:1', 83, 60, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(95, 'kawasaki-ninja-650-40th-anniversary-2024', 'Liquid-cooled DOHC parallel-twin 4-stroke', 649, 2, 4, 67, NULL, 65.1, NULL, '10.8:1', 83, 60, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(96, 'kawasaki-ninja-zx-10r-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 998, 4, 4, 196, 11500, 114, 11300, NULL, 76, 55, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(97, 'kawasaki-ninja-zx-10r-40th-anniversary-edition-2024', 'Liquid-cooled DOHC inline-four 4-stroke', 998, 4, 4, 196, 11500, 114, 11300, NULL, 76, 55, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(98, 'kawasaki-vn-900-classic-2014', 'Liquid-cooled SOHC V-twin 4-stroke', 903, 2, 4, 50, 5700, 79, 3700, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(99, 'kawasaki-vn-900-classic-special-edition-2014', 'Liquid-cooled SOHC V-twin 4-stroke', 903, 2, 4, 50, 5700, 79, 3700, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(100, 'kawasaki-vulcan-900-classic-2017', 'Liquid-cooled SOHC V-twin 4-stroke', 903, 2, 4, 50, 5700, 79, 3700, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(101, 'kawasaki-vulcan-900-custom-2017', 'Liquid-cooled SOHC V-twin 4-stroke', 903, 2, 4, 50, 5700, 79, 3700, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 5-speed', 5, 'Belt', NULL, NULL, NULL),
(102, 'kawasaki-w800-cafe-2021', 'Air-cooled SOHC parallel-twin 4-stroke', 773, 2, 2, 47, 6500, 60, 2500, '8.4:1', 77, 83, 'Air-cooled', 'Electronic fuel injection', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(103, 'kawasaki-w800-street-2021', 'Air-cooled SOHC parallel-twin 4-stroke', 773, 2, 2, 47, 6500, 60, 2500, '8.4:1', 77, 83, 'Air-cooled', 'Electronic fuel injection', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(104, 'kawasaki-z-500-2025', 'Liquid-cooled DOHC parallel-twin 4-stroke', 451, 2, 4, 51, 10000, 43, 7500, NULL, 70, 58.6, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(105, 'kawasaki-z-500-se-2027', 'Liquid-cooled DOHC parallel-twin 4-stroke', 451, 2, 4, 51, 10000, 43, 7500, NULL, 70, 58.6, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(106, 'kawasaki-z1100-2027', 'Liquid-cooled DOHC inline-four 4-stroke', 1099, 4, 4, 134.1, 9000, 112.8, NULL, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(107, 'kawasaki-z1100-se-2027', 'Liquid-cooled DOHC inline-four 4-stroke', 1099, 4, 4, 134.1, 9000, 112.8, NULL, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(108, 'kawasaki-z650-50th-anniversary-2024', 'Liquid-cooled DOHC parallel-twin 4-stroke', 649, 2, 4, 67, NULL, 65.1, NULL, '10.8:1', 83, 60, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(109, 'kawasaki-z650-rs-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 649, 2, 4, 67, NULL, 65.1, NULL, '10.8:1', 83, 60, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(110, 'kawasaki-z650-rs-50th-anniversary-2024', 'Liquid-cooled DOHC parallel-twin 4-stroke', 649, 2, 4, 67, NULL, 65.1, NULL, '10.8:1', 83, 60, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(111, 'kawasaki-z650-s-2027', 'Liquid-cooled DOHC parallel-twin 4-stroke', 649, 2, 4, 67, NULL, 65.1, NULL, '10.8:1', 83, 60, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(112, 'kawasaki-z900-70kw-2027', 'Liquid-cooled DOHC inline-four 4-stroke', 948, 4, 4, 93.9, NULL, NULL, NULL, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(113, 'kawasaki-z900-70kw-50th-anniversary-2024', 'Liquid-cooled DOHC inline-four 4-stroke', 948, 4, 4, 93.9, NULL, NULL, NULL, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(114, 'peugeot-django-50-4t-2024', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(115, 'peugeot-django-50-4t-allure-2018', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(116, 'peugeot-django-50-4t-blue-2020', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(117, 'peugeot-django-50-4t-dark-2024', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(118, 'peugeot-django-50-4t-evasion-2019', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(119, 'peugeot-django-50-4t-heritage-2018', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(120, 'peugeot-django-50-4t-red-2020', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(121, 'peugeot-django-50-4t-sport-2024', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(122, 'peugeot-django-50-4t-summer-2019', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(123, 'peugeot-django-50-classic-dark-2025', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(124, 'peugeot-django-50-classic-hot-color-2025', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(125, 'peugeot-django-50-classic-shadow-2025', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(126, 'peugeot-django-50-classic-sport-2025', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(127, 'peugeot-django-50-shadow-2024', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(128, 'peugeot-django-classic-50-2026', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.7, 8000, 3.5, NULL, NULL, 39, 41.4, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(129, 'peugeot-kisbee-50-4t-2020', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.5, 7700, 3.2, 7300, NULL, NULL, NULL, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(130, 'peugeot-kisbee-50-4t-active-2025', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.5, 7700, 3.2, 7300, NULL, NULL, NULL, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(131, 'peugeot-kisbee-50-4t-black-edition-2024', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.5, 7700, 3.2, 7300, NULL, NULL, NULL, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(132, 'peugeot-kisbee-50-4t-r-2021', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.5, 7700, 3.2, 7300, NULL, NULL, NULL, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(133, 'peugeot-kisbee-50-4t-rs-2021', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.5, 7700, 3.2, 7300, NULL, NULL, NULL, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(134, 'peugeot-kisbee-50-black-edition-2025', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.5, 7700, 3.2, 7300, NULL, NULL, NULL, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(135, 'peugeot-kisbee-50-gt-2025', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.5, 7700, 3.2, 7300, NULL, NULL, NULL, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(136, 'peugeot-kisbee-50-rs-4t-2020', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.5, 7700, 3.2, 7300, NULL, NULL, NULL, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(137, 'peugeot-kisbee-50-streetline-2024', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.5, 7700, 3.2, 7300, NULL, NULL, NULL, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(138, 'peugeot-kisbee-50-streetzone-4t-2015', 'Air-cooled single-cylinder 4-stroke', 50, 1, 2, 3.5, 7700, 3.2, 7300, NULL, NULL, NULL, 'Air-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', NULL, NULL, NULL),
(139, 'suzuki-gsx-8r-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 776, 2, 4, 83, 8500, 78.5, 6800, '12.8:1', 84, 70, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(140, 'suzuki-gsx-8r-daidai-iro-edition-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 776, 2, 4, 83, 8500, 78.5, 6800, '12.8:1', 84, 70, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(141, 'suzuki-gsx-8r-daidai-iro-power-edition-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 776, 2, 4, 83, 8500, 78.5, 6800, '12.8:1', 84, 70, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(142, 'suzuki-gsx-8r-power-edition-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 776, 2, 4, 83, 8500, 78.5, 6800, '12.8:1', 84, 70, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(143, 'suzuki-gsx-8s-evo-2024', 'Liquid-cooled DOHC parallel-twin 4-stroke', 776, 2, 4, 83, 8500, 78.5, 6800, '12.8:1', 84, 70, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(144, 'suzuki-gsx-8s-power-edition-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 776, 2, 4, 83, 8500, 78.5, 6800, '12.8:1', 84, 70, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(145, 'suzuki-gsx-8t-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 776, 2, 4, 83, 8500, 78.5, 6800, '12.8:1', 84, 70, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(146, 'suzuki-gsx-8t-power-edition-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 776, 2, 4, 83, 8500, 78.5, 6800, '12.8:1', 84, 70, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(147, 'suzuki-gsx-8tt-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 776, 2, 4, 83, 8500, 78.5, 6800, '12.8:1', 84, 70, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(148, 'suzuki-gsx-8tt-power-edition-2026', 'Liquid-cooled DOHC parallel-twin 4-stroke', 776, 2, 4, 83, 8500, 78.5, 6800, '12.8:1', 84, 70, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(149, 'suzuki-gsx-s1000-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 999, 4, 4, 150.8, 11300, 107.9, 9400, '12.2:1', 73.4, 59, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(150, 'suzuki-gsx-s1000-evo-2024', 'Liquid-cooled DOHC inline-four 4-stroke', 999, 4, 4, 150.8, 11300, 107.9, 9400, '12.2:1', 73.4, 59, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(151, 'suzuki-gsx-s1000-power-edition-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 999, 4, 4, 150.8, 11300, 107.9, 9400, '12.2:1', 73.4, 59, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(152, 'suzuki-gsx-s1000gt-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 999, 4, 4, 150.8, 11300, 107.9, 9400, '12.2:1', 73.4, 59, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(153, 'suzuki-gsx-s1000gt-travel-edition-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 999, 4, 4, 150.8, 11300, 107.9, 9400, '12.2:1', 73.4, 59, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(154, 'suzuki-gsx-s1000gx-power-edition-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 999, 4, 4, 150.8, 11300, 107.9, 9400, '12.2:1', 73.4, 59, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(155, 'suzuki-gsx-s1000gx-travel-edition-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 999, 4, 4, 150.8, 11300, 107.9, 9400, '12.2:1', 73.4, 59, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(156, 'suzuki-hayabusa-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 1340, 4, 4, 190, 9700, 150, 7000, '12.5:1', 81, 65, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(157, 'suzuki-hayabusa-power-edition-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 1340, 4, 4, 190, 9700, 150, 7000, '12.5:1', 81, 65, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(158, 'suzuki-hayabusa-special-edition-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 1340, 4, 4, 190, 9700, 150, 7000, '12.5:1', 81, 65, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(159, 'suzuki-hayabusa-special-edition-power-edition-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 1340, 4, 4, 190, 9700, 150, 7000, '12.5:1', 81, 65, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(160, 'suzuki-katana-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 999, 4, 4, 150.8, 11300, 107.9, 9400, '12.2:1', 73.4, 59, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(161, 'suzuki-katana-limited-edition-2026', 'Liquid-cooled DOHC inline-four 4-stroke', 999, 4, 4, 150.8, 11300, 107.9, 9400, '12.2:1', 73.4, 59, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(162, 'suzuki-v-strom-1050-2026', 'Liquid-cooled DOHC 90-degree V-twin 4-stroke', 1037, 2, 4, 106, 8500, 100, 6000, '11.5:1', 100, 66, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(163, 'suzuki-v-strom-1050-touring-edition-2026', 'Liquid-cooled DOHC 90-degree V-twin 4-stroke', 1037, 2, 4, 106, 8500, 100, 6000, '11.5:1', 100, 66, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(164, 'suzuki-v-strom-1050-xt-2024', 'Liquid-cooled DOHC 90-degree V-twin 4-stroke', 1037, 2, 4, 106, 8500, 100, 6000, '11.5:1', 100, 66, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(165, 'suzuki-v-strom-1050de-2026', 'Liquid-cooled DOHC 90-degree V-twin 4-stroke', 1037, 2, 4, 106, 8500, 100, 6000, '11.5:1', 100, 66, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(166, 'suzuki-v-strom-1050de-travel-edition-2026', 'Liquid-cooled DOHC 90-degree V-twin 4-stroke', 1037, 2, 4, 106, 8500, 100, 6000, '11.5:1', 100, 66, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(167, 'suzuki-v-strom-1050de-xtreme-2024', 'Liquid-cooled DOHC 90-degree V-twin 4-stroke', 1037, 2, 4, 106, 8500, 100, 6000, '11.5:1', 100, 66, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(168, 'suzuki-v-strom-650xt-comfort-2024', 'Liquid-cooled DOHC 90-degree V-twin 4-stroke', 645, 2, 4, 66, 8800, 60.3, 6400, '11.2:1', 81, 62.6, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(169, 'suzuki-v-strom-650xt-touring-edition-2026', 'Liquid-cooled DOHC 90-degree V-twin 4-stroke', 645, 2, 4, 66, 8800, 60.3, 6400, '11.2:1', 81, 62.6, 'Liquid-cooled', 'Electronic fuel injection', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(170, 'triumph-bonneville-bobber-black-2021', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 77, 6100, 106, 4000, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(171, 'triumph-bonneville-bobber-chrome-edition-2023', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 77, 6100, 106, 4000, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(172, 'triumph-bonneville-bobber-gold-line-2022', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 77, 6100, 106, 4000, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(173, 'triumph-bonneville-bobber-icon-edition-2025', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 77, 6100, 106, 4000, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(174, 'triumph-bonneville-speedmaster-2026', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 77, 6100, 106, 4000, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(175, 'triumph-bonneville-speedmaster-chrome-edition-2023', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 77, 6100, 106, 4000, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(176, 'triumph-bonneville-speedmaster-gold-line-2022', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 77, 6100, 106, 4000, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(177, 'triumph-bonneville-speedmaster-icon-edition-2025', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 77, 6100, 106, 4000, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(178, 'triumph-bonneville-t100-2025', 'Liquid-cooled SOHC 270-degree parallel-twin', 900, 2, 4, 64.1, 7500, 80, 3800, '11.0:1', 84.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(179, 'triumph-bonneville-t100-bud-ekins-special-edition-2020', 'Liquid-cooled SOHC 270-degree parallel-twin', 900, 2, 4, 64.1, 7500, 80, 3800, '11.0:1', 84.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(180, 'triumph-bonneville-t100-chrome-edition-2023', 'Liquid-cooled SOHC 270-degree parallel-twin', 900, 2, 4, 64.1, 7500, 80, 3800, '11.0:1', 84.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(181, 'triumph-bonneville-t100-gold-line-2022', 'Liquid-cooled SOHC 270-degree parallel-twin', 900, 2, 4, 64.1, 7500, 80, 3800, '11.0:1', 84.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(182, 'triumph-bonneville-t100-icon-edition-2025', 'Liquid-cooled SOHC 270-degree parallel-twin', 900, 2, 4, 64.1, 7500, 80, 3800, '11.0:1', 84.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(183, 'triumph-bonneville-t120-2026', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 79, 6550, 104.4, 3500, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(184, 'triumph-bonneville-t120-black-2026', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 79, 6550, 104.4, 3500, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(185, 'triumph-bonneville-t120-black-gold-line-2022', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 79, 6550, 104.4, 3500, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(186, 'triumph-bonneville-t120-bud-ekins-special-edition-2020', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 79, 6550, 104.4, 3500, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(187, 'triumph-bonneville-t120-chrome-edition-2023', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 79, 6550, 104.4, 3500, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(188, 'triumph-bonneville-t120-elvis-presley-special-edition-2024', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 79, 6550, 104.4, 3500, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(189, 'triumph-bonneville-t120-gold-line-2022', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 79, 6550, 104.4, 3500, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(190, 'triumph-bonneville-t120-icon-edition-2025', 'Liquid-cooled SOHC 270-degree parallel-twin', 1200, 2, 4, 79, 6550, 104.4, 3500, NULL, 97.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(191, 'triumph-daytona-660-2026', 'Liquid-cooled DOHC inline-triple 4-stroke', 660, 3, 4, 94, 11250, 68, 8250, NULL, 74, 51.1, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(192, 'triumph-rocket-3-r-2024', 'Liquid-cooled DOHC inline-triple 4-stroke', 2458, 3, 4, 180, 7000, 225, 4000, NULL, NULL, NULL, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Shaft', NULL, NULL, NULL),
(193, 'triumph-scrambler-900-2026', 'Liquid-cooled SOHC 270-degree parallel-twin', 900, 2, 4, 64.1, 7500, 80, 3800, '11.0:1', 84.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(194, 'triumph-scrambler-900-chrome-edition-2023', 'Liquid-cooled SOHC 270-degree parallel-twin', 900, 2, 4, 64.1, 7500, 80, 3800, '11.0:1', 84.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(195, 'triumph-scrambler-900-icon-edition-2025', 'Liquid-cooled SOHC 270-degree parallel-twin', 900, 2, 4, 64.1, 7500, 80, 3800, '11.0:1', 84.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(196, 'triumph-speed-twin-900-chrome-edition-2023', 'Liquid-cooled SOHC 270-degree parallel-twin', 900, 2, 4, 64.1, 7500, 80, 3800, '11.0:1', 84.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(197, 'triumph-street-scrambler-gold-line-2022', 'Liquid-cooled SOHC 270-degree parallel-twin', 900, 2, 4, 64.1, 7500, 80, 3800, '11.0:1', 84.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(198, 'triumph-street-scrambler-sandstorm-edition-2021', 'Liquid-cooled SOHC 270-degree parallel-twin', 900, 2, 4, 64.1, 7500, 80, 3800, '11.0:1', 84.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(199, 'triumph-street-triple-765-rx-2026', 'Liquid-cooled DOHC inline-triple 4-stroke', 765, 3, 4, 128, NULL, 80, 9500, '13.25:1', NULL, NULL, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(200, 'triumph-street-triple-rs-2023', 'Liquid-cooled DOHC inline-triple 4-stroke', 765, 3, 4, 128, NULL, 80, 9500, '13.25:1', NULL, NULL, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(201, 'triumph-street-twin-gold-line-limited-edition-2021', 'Liquid-cooled SOHC 270-degree parallel-twin', 900, 2, 4, 64.1, 7500, 80, 3800, '11.0:1', 84.6, 80, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 5-speed', 5, 'Chain', NULL, NULL, NULL),
(202, 'triumph-tiger-900-alpine-edition-2026', 'Liquid-cooled DOHC T-plane inline-triple', 888, 3, 4, 106.5, 9500, 90, 6850, '11.27:1', 78, 61.9, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(203, 'triumph-tiger-900-desert-edition-2026', 'Liquid-cooled DOHC T-plane inline-triple', 888, 3, 4, 106.5, 9500, 90, 6850, '11.27:1', 78, 61.9, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(204, 'triumph-tiger-900-gt-2026', 'Liquid-cooled DOHC T-plane inline-triple', 888, 3, 4, 106.5, 9500, 90, 6850, '11.27:1', 78, 61.9, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(205, 'triumph-tiger-900-rally-pro-2026', 'Liquid-cooled DOHC T-plane inline-triple', 888, 3, 4, 106.5, 9500, 90, 6850, '11.27:1', 78, 61.9, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(206, 'triumph-tiger-sport-660-2026', 'Liquid-cooled DOHC inline-triple 4-stroke', 660, 3, 4, 94, 11250, 68, 8250, NULL, 74, 51.1, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(207, 'triumph-trident-660-2026', 'Liquid-cooled DOHC inline-triple 4-stroke', 660, 3, 4, 94, 11250, 68, 8250, NULL, 74, 51.1, 'Liquid-cooled', 'Multipoint sequential EFI', 'Manual, 6-speed', 6, 'Chain', NULL, NULL, NULL),
(208, 'yamaha-fjr1300a-2021', 'Liquid-cooled DOHC inline-four 4-stroke', 1298, 4, 4, 142, NULL, 138.3, NULL, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Shaft', NULL, NULL, NULL),
(209, 'yamaha-fjr1300ae-2021', 'Liquid-cooled DOHC inline-four 4-stroke', 1298, 4, 4, 142, NULL, 138.3, NULL, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Shaft', NULL, NULL, NULL),
(210, 'yamaha-fjr1300as-2021', 'Liquid-cooled DOHC inline-four 4-stroke', 1298, 4, 4, 142, NULL, 138.3, NULL, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Shaft', NULL, NULL, NULL),
(211, 'yamaha-mt-07-35kw-2026', 'Liquid-cooled DOHC CP2 parallel-twin 4-stroke', 689, 2, 4, 46.9, NULL, NULL, NULL, '11.5:1', 80, 78, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(212, 'yamaha-mt-07-y-amt-2026', 'Liquid-cooled DOHC CP2 parallel-twin 4-stroke', 689, 2, 4, 73.8, 9000, 68, 6500, '11.5:1', 80, 78, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(213, 'yamaha-mt-07-y-amt-35kw-2026', 'Liquid-cooled DOHC CP2 parallel-twin 4-stroke', 689, 2, 4, 46.9, NULL, NULL, NULL, '11.5:1', 80, 78, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(214, 'yamaha-mt-09-35kw-2026', 'Liquid-cooled DOHC CP3 inline-triple 4-stroke', 890, 3, 4, 46.9, NULL, NULL, NULL, '11.5:1', 78, 62.1, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(215, 'yamaha-mt-09-sp-35-kw-2026', 'Liquid-cooled DOHC CP3 inline-triple 4-stroke', 890, 3, 4, 46.9, NULL, NULL, NULL, '11.5:1', 78, 62.1, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(216, 'yamaha-mt-09-y-amt-35kw-2026', 'Liquid-cooled DOHC CP3 inline-triple 4-stroke', 890, 3, 4, 46.9, NULL, NULL, NULL, '11.5:1', 78, 62.1, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(217, 'yamaha-tenere-700-low-2026', 'Liquid-cooled DOHC CP2 parallel-twin 4-stroke', 689, 2, 4, 72.4, 9000, 68, 6500, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(218, 'yamaha-tenere-700-low-35kw-2026', 'Liquid-cooled DOHC CP2 parallel-twin 4-stroke', 689, 2, 4, 46.9, NULL, NULL, NULL, '11.5:1', 80, 78, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(219, 'yamaha-tenere-700-rally-35kw-2026', 'Liquid-cooled DOHC CP2 parallel-twin 4-stroke', 689, 2, 4, 46.9, NULL, NULL, NULL, '11.5:1', 80, 78, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(220, 'yamaha-tenere-700-world-raid-2026', 'Liquid-cooled DOHC CP2 parallel-twin 4-stroke', 689, 2, 4, 72.4, 9000, 68, 6500, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(221, 'yamaha-tmax-tech-max-2025', 'Liquid-cooled DOHC parallel-twin 4-stroke', 562, 2, 4, 47, 7000, 55.7, 5250, NULL, NULL, NULL, 'Liquid-cooled', 'Electronic fuel injection', 'Automatic CVT', NULL, 'Belt', 165, 4.8, NULL),
(222, 'yamaha-tracer-7-35-kw-2026', 'Liquid-cooled DOHC CP2 parallel-twin 4-stroke', 689, 2, 4, 46.9, NULL, NULL, NULL, '11.5:1', 80, 78, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(223, 'yamaha-tracer-7-35kw-y-amt-2026', 'Liquid-cooled DOHC CP2 parallel-twin 4-stroke', 689, 2, 4, 46.9, NULL, NULL, NULL, '11.5:1', 80, 78, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(224, 'yamaha-tracer-7-gt-35kw-2026', 'Liquid-cooled DOHC CP2 parallel-twin 4-stroke', 689, 2, 4, 46.9, NULL, NULL, NULL, '11.5:1', 80, 78, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(225, 'yamaha-tracer-9-gt-y-amt-2026', 'Liquid-cooled DOHC CP3 inline-triple 4-stroke', 890, 3, 4, 117.3, 10000, 93, 7000, '11.5:1', 78, 62.1, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(226, 'yamaha-tracer-9-y-amt-2026', 'Liquid-cooled DOHC CP3 inline-triple 4-stroke', 890, 3, 4, 117.3, 10000, 93, 7000, '11.5:1', 78, 62.1, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(227, 'yamaha-xsr900-35kw-2026', 'Liquid-cooled DOHC CP3 inline-triple 4-stroke', 890, 3, 4, 46.9, NULL, NULL, NULL, '11.5:1', 78, 62.1, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL),
(228, 'yamaha-xsr900-gp-2026', 'Liquid-cooled DOHC CP3 inline-triple 4-stroke', 890, 3, 4, 117.3, 10000, 93, 7000, '11.5:1', 78, 62.1, 'Liquid-cooled', 'Electronic fuel injection', NULL, 6, 'Chain', NULL, NULL, NULL);

-- ---------------------------------------------------------------------------
-- Resolve every staged row against the live catalogue. An exact slug match only: a slug with no
-- corresponding motorcycles.slug is counted and left alone rather than fuzzy-matched or inserted -
-- never insert a catalogue row here.
-- ---------------------------------------------------------------------------
UPDATE tmp_motorcycle_engine_specs t
SET motorcycle_id = m.id,
    engine_id     = m.engine_specification_id
FROM motorcycles m
WHERE m.slug = t.slug;

-- A resolved motorcycle may not have an engine block yet. Give it one before the merge below, so a
-- real match is never silently dropped. Not expected to fire (see header), kept for parity with
-- R__motorcycles_displacement_cc_2026_09.sql against the same table.
UPDATE tmp_motorcycle_engine_specs
SET engine_id = nextval(pg_get_serial_sequence('engine_specifications', 'id')::regclass)
WHERE motorcycle_id IS NOT NULL
  AND engine_id IS NULL;

INSERT INTO engine_specifications (id)
SELECT t.engine_id
FROM tmp_motorcycle_engine_specs t
WHERE t.engine_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM engine_specifications e WHERE e.id = t.engine_id)
ORDER BY t.row_no;

UPDATE motorcycles m
SET engine_specification_id = t.engine_id
FROM tmp_motorcycle_engine_specs t
WHERE m.id = t.motorcycle_id AND m.engine_specification_id IS NULL AND t.engine_id IS NOT NULL;

-- Gap-fill only: an existing value (an admin edit, or an earlier import) always wins. Only columns
-- this batch actually staged get an arm - see activeColumns in the generator.
UPDATE engine_specifications e
SET
    engine_type              = COALESCE(e.engine_type, t.engine_type),
    displacement_cc          = COALESCE(e.displacement_cc, t.displacement_cc),
    cylinders                = COALESCE(e.cylinders, t.cylinders),
    valves_per_cylinder      = COALESCE(e.valves_per_cylinder, t.valves_per_cylinder),
    max_power_hp             = COALESCE(e.max_power_hp, t.max_power_hp),
    max_power_rpm            = COALESCE(e.max_power_rpm, t.max_power_rpm),
    max_torque_nm            = COALESCE(e.max_torque_nm, t.max_torque_nm),
    max_torque_rpm           = COALESCE(e.max_torque_rpm, t.max_torque_rpm),
    compression_ratio        = COALESCE(e.compression_ratio, t.compression_ratio),
    bore_mm                  = COALESCE(e.bore_mm, t.bore_mm),
    stroke_mm                = COALESCE(e.stroke_mm, t.stroke_mm),
    cooling_system           = COALESCE(e.cooling_system, t.cooling_system),
    fuel_system              = COALESCE(e.fuel_system, t.fuel_system),
    transmission_type        = COALESCE(e.transmission_type, t.transmission_type),
    gears                    = COALESCE(e.gears, t.gears),
    final_drive              = COALESCE(e.final_drive, t.final_drive),
    top_speed_kph            = COALESCE(e.top_speed_kph, t.top_speed_kph),
    fuel_consumption_l_100km = COALESCE(e.fuel_consumption_l_100km, t.fuel_consumption_l_100km)
FROM tmp_motorcycle_engine_specs t
WHERE e.id = t.engine_id;

-- ---------------------------------------------------------------------------
-- Defensive checks before commit, mirroring R__motorcycles_displacement_cc_2026_09.sql.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad        bigint;
    unresolved bigint;
BEGIN
    SELECT count(*) INTO bad
    FROM tmp_motorcycle_engine_specs
    WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle engine specs backfill: % staged slugs are not a shape the public routing can use', bad;
    END IF;

    SELECT count(*) INTO unresolved FROM tmp_motorcycle_engine_specs WHERE motorcycle_id IS NULL;
    RAISE NOTICE 'Motorcycle engine specs backfill: % of % staged rows matched a catalogue slug, % left unresolved',
        (SELECT count(*) FROM tmp_motorcycle_engine_specs WHERE motorcycle_id IS NOT NULL),
        (SELECT count(*) FROM tmp_motorcycle_engine_specs), unresolved;

    SELECT count(*) INTO bad
    FROM tmp_motorcycle_engine_specs t
    JOIN motorcycles m ON m.id = t.motorcycle_id
    WHERE t.engine_id IS NOT NULL AND m.engine_specification_id IS DISTINCT FROM t.engine_id;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle engine specs backfill: % rows have a mismatched engine block', bad;
    END IF;

    SELECT count(*) INTO bad
    FROM engine_specifications e
    JOIN tmp_motorcycle_engine_specs t ON t.engine_id = e.id
    WHERE e.displacement_cc IS NULL OR e.displacement_cc <= 0;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle engine specs backfill: % resolved engine blocks ended up with no positive displacement_cc', bad;
    END IF;
END $$;

COMMIT;
