-- Motorcycle Comparison API - front_brake / rear_brake / abs_type backfill
--
-- Purpose: back-fill motorcycles.front_brake, motorcycles.rear_brake and motorcycles.abs_type for
-- catalogue rows still missing them. All three columns live directly on motorcycles (VARCHAR(160),
-- VARCHAR(160), VARCHAR(80) - see V1__initial_schema.sql), there is no child table involved.
--
-- Provenance: hand-curated from tools/brakes-research.json (web research, validated by
-- tools/validate-brakes-research.mjs), which only accepts a value carrying a real specification -
-- a diameter, an explicit disc/drum type, or an explicit "None" - and requires a source that owns a
-- diameter figure before accepting it. Regenerate this file with tools/import-brakes.mjs - every
-- count in this header is computed by that script, never typed.
-- Sources drawn on for this batch:
--   autoevolution.com, bikez.com, fichatecnica.motosblog.com.br, globalsuzuki.com,
--   intruder125.com.br, motonline.com.br, motorcyclespecs.co.za, revista.moto.com.br,
--   soymotero.net, suzukicycles.com, suzukicycles.org, ultimatespecs.com
--
-- Scope of this batch: Suzuki. 217 catalogue slugs of this brand were missing front_brake or
-- rear_brake; 167 are staged below (167 carry a front value, 167 a rear, 40 an abs_type).
-- Every staged slug is still missing front_brake or rear_brake in the live catalogue.
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. A slug with no corresponding motorcycles.slug is counted and left alone rather than
-- fuzzy-matched or inserted - never insert a catalogue row here.
--
-- Generation granularity: research was done per nameplate but written per exact slug, and split by
-- year range wherever the model actually changed - 0 of the 87 nameplates in this batch carry
-- more than one distinct value (front_brake, rear_brake or abs_type) across their model years. A
-- single value was never stretched across a nameplate's whole life.
--
-- Ordering, and why the filename is load-bearing: Flyway runs repeatable migrations in description
-- order. "zzzzz motorcycles brakes 2026 09" sorts after every other seed in this directory,
-- including "zzzz motorcycles suspension 2026 09" (the previous last-to-run file: 'zzzzz' > 'zzzz_'
-- because 'z' > '_'). That is deliberate and safe, mirroring the suspension backfill: the input list
-- is exactly the set of rows still missing front_brake or rear_brake after every existing seed, so
-- this file is pure gap-fill and cannot pre-empt any other import's claim on these columns.
--
-- 0 values exceeded their column width and were cut at a word boundary rather than mid-word.
--
-- Idempotent and repeatable: every write below is COALESCE(existing, staged), so re-running this
-- file changes nothing once it has been applied, and an admin edit or a richer earlier import
-- always wins over this one.

BEGIN;

CREATE TEMP TABLE tmp_motorcycle_brakes (
    row_no        bigint PRIMARY KEY,
    slug          varchar(160) NOT NULL,
    front         varchar(160),
    rear          varchar(160),
    abs_type      varchar(80),
    motorcycle_id bigint,
    CONSTRAINT uk_tmp_motorcycle_brakes_slug UNIQUE (slug),
    CONSTRAINT ck_tmp_motorcycle_brakes_any CHECK (front IS NOT NULL OR rear IS NOT NULL)
) ON COMMIT DROP;

INSERT INTO tmp_motorcycle_brakes (row_no, slug, front, rear, abs_type) VALUES
(1, 'suzuki-b-king-2010', 'Dual 310mm floating discs, radial-mount 4-piston calipers', 'Single 260mm disc, 1-piston caliper', NULL),
(2, 'suzuki-bandit-1200-2006', 'Dual disc, 6-piston calipers', 'Single disc, 2-piston caliper', 'ABS standard'),
(3, 'suzuki-bandit-1250-2013', 'Dual disc, 6-piston calipers', 'Single disc', 'ABS standard'),
(4, 'suzuki-bandit-1250s-2017', 'Dual disc, 6-piston calipers', 'Single disc', 'ABS standard'),
(5, 'suzuki-bandit-600s-2005', 'Dual disc, 2-piston calipers', 'Single disc, 2-piston caliper', NULL),
(6, 'suzuki-bandit-650-2017', 'Dual disc, 2-piston calipers', 'Single disc, 2-piston caliper', NULL),
(7, 'suzuki-bandit-650s-2017', 'Dual disc, 2-piston calipers', 'Single disc, 2-piston caliper', NULL),
(8, 'suzuki-bandit-n-1200-1996', 'Dual disc, 4-piston calipers', 'Single disc, 2-piston caliper', NULL),
(9, 'suzuki-bandit-n-1200-1997', 'Dual disc, 4-piston calipers', 'Single disc, 2-piston caliper', NULL),
(10, 'suzuki-bandit-n-1200-1998', 'Dual disc, 4-piston calipers', 'Single disc, 2-piston caliper', NULL),
(11, 'suzuki-bandit-n-600-650-1996', 'Dual disc, 2-piston calipers', 'Single disc, 1-piston caliper', NULL),
(12, 'suzuki-bandit-n-600-650-1997', 'Dual disc, 2-piston calipers', 'Single disc, 1-piston caliper', NULL),
(13, 'suzuki-boulevard-c1500-2006', 'Single disc, 2-piston caliper', 'Single disc, 2-piston caliper', NULL),
(14, 'suzuki-boulevard-m800r-2012', 'Single disc, 2-piston caliper', 'Drum brake', NULL),
(15, 'suzuki-boulevard-m800r-2013', 'Single disc, 2-piston caliper', 'Drum brake', NULL),
(16, 'suzuki-boulevard-m800r-2014', 'Single disc, 2-piston caliper', 'Drum brake', NULL),
(17, 'suzuki-boulevard-m800r-2015', 'Single disc, 2-piston caliper', 'Drum brake', NULL),
(18, 'suzuki-boulevard-m800r-2016', 'Single disc, 2-piston caliper', 'Drum brake', NULL),
(19, 'suzuki-burgman-200-2017', 'Single disc, 3-piston caliper', 'Single disc, 1-piston caliper', NULL),
(20, 'suzuki-burgman-400z-2019', 'Dual disc', 'Single disc', 'ABS standard'),
(21, 'suzuki-burgman-650-2008', 'Dual disc, 2-piston calipers', 'Single disc, 2-piston caliper', NULL),
(22, 'suzuki-burgman-i-125cc-2012', 'Single disc, 1-piston caliper', 'Drum brake', NULL),
(23, 'suzuki-burgman-i-125cc-2013', 'Single disc, 1-piston caliper', 'Drum brake', NULL),
(24, 'suzuki-burgman-i-125cc-2014', 'Single disc, 1-piston caliper', 'Drum brake', NULL),
(25, 'suzuki-burgman-i-125cc-2015', 'Single disc, 1-piston caliper', 'Drum brake', NULL),
(26, 'suzuki-burgman-i-125cc-2016', 'Single disc, 1-piston caliper', 'Drum brake', NULL),
(27, 'suzuki-burgman-i-125cc-2017', 'Single disc, 1-piston caliper', 'Drum brake', NULL),
(28, 'suzuki-burgman-i-125cc-2018', 'Single disc, 1-piston caliper', 'Drum brake', NULL),
(29, 'suzuki-burgman-i-125cc-2019', 'Single disc, 1-piston caliper', 'Drum brake', NULL),
(30, 'suzuki-dl-1000-xt-v-strom-2020', 'Dual 310mm floating discs, 2-piston sliding calipers', 'Single 260mm disc, 1-piston sliding caliper', 'ABS standard'),
(31, 'suzuki-dl-1000-xt-v-strom-2021', 'Dual 310mm floating discs, 2-piston sliding calipers', 'Single 260mm disc, 1-piston sliding caliper', 'ABS standard'),
(32, 'suzuki-dl-1050-v-strom-2026', 'Dual disc, Tokico 4-piston calipers', 'Single disc, Nissin 2-piston caliper', NULL),
(33, 'suzuki-dl-1050-v-strom-2027', 'Dual disc, Tokico 4-piston calipers', 'Single disc, Nissin 2-piston caliper', NULL),
(34, 'suzuki-dl-1050-xt-v-strom-2026', 'Dual disc, Tokico 4-piston calipers', 'Single disc, Nissin 2-piston caliper', NULL),
(35, 'suzuki-dl-1050-xt-v-strom-2027', 'Dual disc, Tokico 4-piston calipers', 'Single disc, Nissin 2-piston caliper', NULL),
(36, 'suzuki-dl-650-xt-v-strom-2026', 'Dual disc, Tokico 2-piston calipers', 'Single disc, Nissin 1-piston caliper', 'ABS standard'),
(37, 'suzuki-dl-650-xt-v-strom-2027', 'Dual disc, Tokico 2-piston calipers', 'Single disc, Nissin 1-piston caliper', 'ABS standard'),
(38, 'suzuki-dl-800-de-v-strom-2024', 'Dual 310mm discs, Nissin 2-piston calipers', 'Single disc, Nissin 1-piston caliper', 'ABS standard, adjustable'),
(39, 'suzuki-dl-800-de-v-strom-2025', 'Dual 310mm discs, Nissin 2-piston calipers', 'Single disc, Nissin 1-piston caliper', 'ABS standard, adjustable'),
(40, 'suzuki-dl-800-de-v-strom-2026', 'Dual 310mm discs, Nissin 2-piston calipers', 'Single disc, Nissin 1-piston caliper', 'ABS standard, adjustable'),
(41, 'suzuki-dl-800-de-v-strom-2027', 'Dual 310mm discs, Nissin 2-piston calipers', 'Single disc, Nissin 1-piston caliper', 'ABS standard, adjustable'),
(42, 'suzuki-dl-800-v-strom-2025', 'Dual 310mm discs, Nissin 4-piston calipers', 'Single disc, Nissin 1-piston caliper', 'ABS standard, adjustable'),
(43, 'suzuki-dl-800-v-strom-2026', 'Dual 310mm discs, Nissin 4-piston calipers', 'Single disc, Nissin 1-piston caliper', 'ABS standard, adjustable'),
(44, 'suzuki-dl-800-v-strom-2027', 'Dual 310mm discs, Nissin 4-piston calipers', 'Single disc, Nissin 1-piston caliper', 'ABS standard, adjustable'),
(45, 'suzuki-dr-125-sm-2010', 'Single disc, 2-piston caliper', 'Single disc, 1-piston caliper', NULL),
(46, 'suzuki-dr-z-400-e-2005', 'Single disc, 2-piston caliper', 'Single disc, 1-piston caliper', NULL),
(47, 'suzuki-dr-z-400-s-2005', 'Single disc, 2-piston caliper', 'Single disc, 1-piston caliper', NULL),
(48, 'suzuki-dr-z-400-sm-2007', 'Single floating disc, 2-piston caliper', 'Single disc, 1-piston caliper', NULL),
(49, 'suzuki-e-address-2026', 'Single 190mm disc', 'Single 130mm drum', 'CBS (Combined Brake System)'),
(50, 'suzuki-en-125-yes-2005', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(51, 'suzuki-en-125-yes-2006', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(52, 'suzuki-en-125-yes-2007', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(53, 'suzuki-en-125-yes-2008', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(54, 'suzuki-en-125-yes-2009', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(55, 'suzuki-en-125-yes-2010', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(56, 'suzuki-en-125-yes-2011', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(57, 'suzuki-en-125-yes-2012', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(58, 'suzuki-en-125-yes-cargo-2012', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(59, 'suzuki-en-125-yes-se-2011', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(60, 'suzuki-en-125-yes-se-2012', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(61, 'suzuki-en-125-yes-se-2013', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(62, 'suzuki-en-125-yes-se-2014', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(63, 'suzuki-en-125-yes-se-2015', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(64, 'suzuki-en-125-yes-se-2016', 'Single 240mm disc, 2-piston sliding caliper', 'Drum, 130mm', NULL),
(65, 'suzuki-gs-500-2007', 'Single 310mm disc, 2-piston caliper', 'Single 250mm disc, 1-piston caliper', NULL),
(66, 'suzuki-gs-500f-2007', 'Single 310mm disc, 2-piston caliper', 'Single 250mm disc, 1-piston caliper', NULL),
(67, 'suzuki-gsr-125-2013', 'Single disc, hydraulic caliper', 'Drum brake', NULL),
(68, 'suzuki-gsr-125-2014', 'Single disc, hydraulic caliper', 'Drum brake', NULL),
(69, 'suzuki-gsr-125-2015', 'Single disc, hydraulic caliper', 'Drum brake', NULL),
(70, 'suzuki-gsr-125-2016', 'Single disc, hydraulic caliper', 'Drum brake', NULL),
(71, 'suzuki-gsr-125s-2013', 'Single disc, hydraulic caliper', 'Drum brake', NULL),
(72, 'suzuki-gsr-125s-2014', 'Single disc, hydraulic caliper', 'Drum brake', NULL),
(73, 'suzuki-gsr-125s-2015', 'Single disc, hydraulic caliper', 'Drum brake', NULL),
(74, 'suzuki-gsr-125s-2016', 'Single disc, hydraulic caliper', 'Drum brake', NULL),
(75, 'suzuki-gsr-150i-2011', 'Single ventilated disc, 2-piston sliding caliper', 'Drum brake', NULL),
(76, 'suzuki-gsr-150i-2012', 'Single ventilated disc, 2-piston sliding caliper', 'Drum brake', NULL),
(77, 'suzuki-gsr-150i-2013', 'Single ventilated disc, 2-piston sliding caliper', 'Drum brake', NULL),
(78, 'suzuki-gsr-150i-2014', 'Single ventilated disc, 2-piston sliding caliper', 'Drum brake', NULL),
(79, 'suzuki-gsr-150i-2015', 'Single ventilated disc, 2-piston sliding caliper', 'Drum brake', NULL),
(80, 'suzuki-gsr-150i-2016', 'Single ventilated disc, 2-piston sliding caliper', 'Drum brake', NULL),
(81, 'suzuki-gsr-150i-2017', 'Single ventilated disc, 2-piston sliding caliper', 'Drum brake', NULL),
(82, 'suzuki-gsr-600-2013', 'Dual disc, 4-piston calipers', 'Single disc, 1-piston caliper', NULL),
(83, 'suzuki-gsr-750-freegun-2017', 'Dual disc, 4-piston calipers', 'Single disc, 1-piston caliper', 'ABS standard'),
(84, 'suzuki-gsr-750-motogp-2017', 'Dual disc, 4-piston calipers', 'Single disc, 1-piston caliper', 'ABS standard'),
(85, 'suzuki-gsx-1250-f-2017', 'Dual floating discs, 4-piston calipers', 'Single disc', 'ABS standard'),
(86, 'suzuki-gsx-1300-r-hayabusa-2026', 'Dual 320mm discs, Brembo Stylema 4-piston calipers', 'Single 260mm disc, Nissin 1-piston caliper', 'ABS standard, Combined Brake System'),
(87, 'suzuki-gsx-1300-r-hayabusa-2027', 'Dual 320mm discs, Brembo Stylema 4-piston calipers', 'Single 260mm disc, Nissin 1-piston caliper', 'ABS standard, Combined Brake System'),
(88, 'suzuki-gsx-1400-2006', 'Dual disc, 6-piston calipers', 'Single disc, 2-piston caliper', NULL),
(89, 'suzuki-gsx-650-f-2017', 'Dual disc, 4-piston calipers (Tokico)', 'Single disc, 1-piston caliper', NULL),
(90, 'suzuki-gsx-8r-2027', 'Dual 310mm discs, radial-mount 4-piston calipers (NISSIN)', 'Single disc, 1-piston caliper (NISSIN)', 'ABS standard'),
(91, 'suzuki-gsx-8s-2027', 'Dual 310mm discs, radial-mount 4-piston calipers (NISSIN)', 'Single 240mm disc, 1-piston caliper (NISSIN)', 'ABS standard'),
(92, 'suzuki-gsx-8s-evo-2024', 'Dual 310mm discs, radial-mount 4-piston calipers (NISSIN)', 'Single 240mm disc, 1-piston caliper (NISSIN)', 'ABS standard'),
(93, 'suzuki-gsx-8t-2026', 'Dual disc, radial-mount 4-piston calipers (NISSIN)', 'Single disc, 1-piston caliper (NISSIN)', 'ABS standard'),
(94, 'suzuki-gsx-8t-power-edition-2026', 'Dual disc, radial-mount 4-piston calipers (NISSIN)', 'Single disc, 1-piston caliper (NISSIN)', 'ABS standard'),
(95, 'suzuki-gsx-s-1000-2026', 'Dual 310mm floating discs, Brembo Monobloc 4-piston calipers', 'Single 240mm disc, Nissin 1-piston caliper', 'ABS standard'),
(96, 'suzuki-gsx-s-1000-2027', 'Dual 310mm floating discs, Brembo Monobloc 4-piston calipers', 'Single 240mm disc, Nissin 1-piston caliper', 'ABS standard'),
(97, 'suzuki-gsx-s-1000-gt-2026', 'Dual 310mm discs, Brembo 4-piston calipers', 'Single 240mm disc, Nissin 1-piston caliper', 'ABS standard'),
(98, 'suzuki-gsx-s-1000-gx-2026', 'Dual 310mm discs, Brembo radial-mount 4-piston calipers', 'Single 240mm disc, Nissin 1-piston caliper', 'ABS standard'),
(99, 'suzuki-gsx-s-1000-gx-2027', 'Dual 310mm discs, Brembo radial-mount 4-piston calipers', 'Single 240mm disc, Nissin 1-piston caliper', 'ABS standard'),
(100, 'suzuki-inazuma-250cc-2014', 'Single disc', 'Single disc, 1-piston caliper', NULL),
(101, 'suzuki-inazuma-250cc-2015', 'Single disc', 'Single disc, 1-piston caliper', NULL),
(102, 'suzuki-inazuma-250cc-2016', 'Single disc', 'Single disc, 1-piston caliper', NULL),
(103, 'suzuki-intruder-125-2015', 'Single disc', 'Drum brake', NULL),
(104, 'suzuki-intruder-125-2016', 'Single disc', 'Drum brake', NULL),
(105, 'suzuki-intruder-c1500t-2017', 'Single disc, 2-piston caliper', 'Single disc, 2-piston caliper', NULL),
(106, 'suzuki-intruder-c1800rt-2013', 'Dual disc, 3-piston calipers', 'Single disc, 2-piston caliper', NULL),
(107, 'suzuki-intruder-c800-2017', 'Single disc, 2-piston caliper', 'Drum brake', NULL),
(108, 'suzuki-intruder-c800c-2017', 'Single disc, 2-piston caliper', 'Drum brake', NULL),
(109, 'suzuki-intruder-m1500-2011', 'Dual disc, 3-piston calipers', 'Single disc, 2-piston caliper', NULL),
(110, 'suzuki-intruder-m1800r2-2009', 'Dual disc, 2-piston calipers', 'Single disc, 2-piston caliper', NULL),
(111, 'suzuki-intruder-m1800rz-2017', 'Dual disc, 2-piston calipers', 'Single disc, 2-piston caliper', NULL),
(112, 'suzuki-intruder-m800-2017', 'Single disc, 2-piston caliper', 'Drum brake', NULL),
(113, 'suzuki-intruder-m800z-2009', 'Single disc, 2-piston caliper', 'Drum brake', NULL),
(114, 'suzuki-intruder-vl-125-lc-2006', 'Single disc', 'Drum, internal expanding', NULL),
(115, 'suzuki-intruder-vl-1500-lc-2007', 'Single disc, 2-piston caliper', 'Single disc, 2-piston caliper', NULL),
(116, 'suzuki-marauder-125-2013', 'Single disc', 'Drum brake', NULL),
(117, 'suzuki-rm-125-1992', 'Single disc', 'Single disc', NULL),
(118, 'suzuki-rm-125-1993', 'Single disc', 'Single disc', NULL),
(119, 'suzuki-rm-125-1994', 'Single disc', 'Single disc', NULL),
(120, 'suzuki-rm-125-1995', 'Single disc', 'Single disc', NULL),
(121, 'suzuki-rm-125-1996', 'Single disc', 'Single disc', NULL),
(122, 'suzuki-rm-125-2011', 'Single disc', 'Single disc', NULL),
(123, 'suzuki-rm-250-1990', 'Single disc', 'Single disc', NULL),
(124, 'suzuki-rm-250-1991', 'Single disc', 'Single disc', NULL),
(125, 'suzuki-rm-250-1992', 'Single disc', 'Single disc', NULL),
(126, 'suzuki-rm-250-1993', 'Single disc', 'Single disc', NULL),
(127, 'suzuki-rm-250-1994', 'Single disc', 'Single disc', NULL),
(128, 'suzuki-rm-250-1995', 'Single disc', 'Single disc', NULL),
(129, 'suzuki-rm-250-1996', 'Single disc', 'Single disc', NULL),
(130, 'suzuki-rm-250-2013', 'Single disc', 'Single disc', NULL),
(131, 'suzuki-rm-80-1996', 'Single disc, hydraulic caliper', 'Single disc', NULL),
(132, 'suzuki-rm-80-1997', 'Single disc, hydraulic caliper', 'Single disc', NULL),
(133, 'suzuki-rm-80-2000', 'Single disc, hydraulic caliper', 'Single disc', NULL),
(134, 'suzuki-rm-85-2013', 'Single disc', 'Single disc', NULL),
(135, 'suzuki-rm-85l-2024', 'Single disc', 'Single disc', NULL),
(136, 'suzuki-rmx-250-1990', 'Single disc', 'Single disc', NULL),
(137, 'suzuki-rmx-250-1991', 'Single disc', 'Single disc', NULL),
(138, 'suzuki-rmx-250-1992', 'Single disc', 'Single disc', NULL),
(139, 'suzuki-rmx-250-1993', 'Single disc', 'Single disc', NULL),
(140, 'suzuki-rmx-250-1994', 'Single disc', 'Single disc', NULL),
(141, 'suzuki-rmx-250-1995', 'Single disc', 'Single disc', NULL),
(142, 'suzuki-rmx-250-1996', 'Single disc', 'Single disc', NULL),
(143, 'suzuki-rmx-250-1997', 'Single disc', 'Single disc', NULL),
(144, 'suzuki-rmx-250-1998', 'Single disc', 'Single disc', NULL),
(145, 'suzuki-rmx-250-1999', 'Single disc', 'Single disc', NULL),
(146, 'suzuki-rmx-250-2000', 'Single disc', 'Single disc', NULL),
(147, 'suzuki-rmx-250-2001', 'Single disc', 'Single disc', NULL),
(148, 'suzuki-rmx-250-2002', 'Single disc', 'Single disc', NULL),
(149, 'suzuki-rv-125-2008', 'Single disc', 'Single disc', NULL),
(150, 'suzuki-sfv-650-gladius-2017', 'Dual 290mm floating discs, 2-piston sliding calipers', 'Single 240mm disc, 1-piston caliper', NULL),
(151, 'suzuki-sv-1000-2006', 'Dual disc, 4-piston calipers', 'Single disc, 2-piston caliper', NULL),
(152, 'suzuki-sv-1000s-2006', 'Dual disc, 4-piston calipers', 'Single disc, 2-piston caliper', NULL),
(153, 'suzuki-sv-650s-2008', 'Dual disc, 2-piston calipers', 'Single disc, 2-piston caliper', NULL),
(154, 'suzuki-v-strom-1000-desert-2017', 'Dual 310mm floating discs, 2-piston sliding calipers', 'Single 260mm disc, 1-piston sliding caliper', 'ABS standard'),
(155, 'suzuki-v-strom-1000-touring-2017', 'Dual 310mm floating discs, 2-piston sliding calipers', 'Single 260mm disc, 1-piston sliding caliper', 'ABS standard'),
(156, 'suzuki-v-strom-1000-traveller-2017', 'Dual 310mm floating discs, 2-piston sliding calipers', 'Single 260mm disc, 1-piston sliding caliper', 'ABS standard'),
(157, 'suzuki-v-strom-650-touring-2017', 'Dual 310mm floating discs, 2-piston sliding calipers', 'Single 260mm disc, 1-piston sliding caliper', 'ABS standard'),
(158, 'suzuki-v-strom-650-xplorer-2017', 'Dual 310mm floating discs, 2-piston sliding calipers', 'Single 260mm disc, 1-piston sliding caliper', 'ABS standard'),
(159, 'suzuki-v-strom-650-xt-touring-2017', 'Dual 310mm floating discs, 2-piston sliding calipers', 'Single 260mm disc, 1-piston sliding caliper', 'ABS standard'),
(160, 'suzuki-v-strom-650-xt-traveller-2017', 'Dual 310mm floating discs, 2-piston sliding calipers', 'Single 260mm disc, 1-piston sliding caliper', 'ABS standard'),
(161, 'suzuki-v-strom-650-xt-xplorer-2017', 'Dual 310mm floating discs, 2-piston sliding calipers', 'Single 260mm disc, 1-piston sliding caliper', 'ABS standard'),
(162, 'suzuki-v-strom-800de-big-2024', 'Dual 310mm discs, Nissin 2-piston calipers', 'Single disc, Nissin 1-piston caliper', 'ABS standard, adjustable'),
(163, 'suzuki-vanvan-125-2019', 'Single disc', 'Single disc', NULL),
(164, 'suzuki-vl-800-volusia-2007', 'Single disc, 2-piston caliper', 'Drum, internal expanding', NULL),
(165, 'suzuki-vx-800cc-1994', 'Single disc, 2-piston caliper', 'Single disc, 2-piston caliper', NULL),
(166, 'suzuki-vx-800cc-1995', 'Single disc, 2-piston caliper', 'Single disc, 2-piston caliper', NULL),
(167, 'suzuki-vz-1600-marauder-2007', 'Dual disc, 6-piston calipers', 'Single disc, 2-piston caliper', NULL);

-- ---------------------------------------------------------------------------
-- Resolve every staged row against the live catalogue. An exact slug match only.
-- ---------------------------------------------------------------------------
UPDATE tmp_motorcycle_brakes t SET motorcycle_id = m.id FROM motorcycles m WHERE m.slug = t.slug;

-- Gap-fill only: an existing value (an admin edit, or an earlier import) always wins. Each column
-- carries its own COALESCE guard so a row missing only one of the three is never disturbed on the
-- other two.
UPDATE motorcycles m
SET front_brake = COALESCE(m.front_brake, t.front),
    rear_brake  = COALESCE(m.rear_brake,  t.rear),
    abs_type    = COALESCE(m.abs_type,    t.abs_type)
FROM tmp_motorcycle_brakes t
WHERE m.id = t.motorcycle_id;

-- ---------------------------------------------------------------------------
-- Defensive checks before commit, mirroring R__zzzz_motorcycles_suspension_2026_09.sql.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad        bigint;
    unresolved bigint;
BEGIN
    SELECT count(*) INTO bad
    FROM tmp_motorcycle_brakes
    WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle brakes backfill: % staged slugs are not a shape the public routing can use', bad;
    END IF;

    SELECT count(*) INTO bad
    FROM tmp_motorcycle_brakes
    WHERE length(front) > 160 OR length(rear) > 160 OR length(abs_type) > 80;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle brakes backfill: % staged rows exceed their column limit', bad;
    END IF;

    SELECT count(*) INTO unresolved FROM tmp_motorcycle_brakes WHERE motorcycle_id IS NULL;
    RAISE NOTICE 'Motorcycle brakes backfill: % of % staged rows matched a catalogue slug, % left unresolved',
        (SELECT count(*) FROM tmp_motorcycle_brakes WHERE motorcycle_id IS NOT NULL),
        (SELECT count(*) FROM tmp_motorcycle_brakes), unresolved;
END $$;

COMMIT;
