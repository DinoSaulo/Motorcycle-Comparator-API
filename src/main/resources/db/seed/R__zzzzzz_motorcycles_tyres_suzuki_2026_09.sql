-- Motorcycle Comparison API - front_tyre / rear_tyre backfill
--
-- Purpose: back-fill motorcycles.front_tyre and motorcycles.rear_tyre for catalogue rows still
-- missing them. Both columns live directly on motorcycles (VARCHAR(60), VARCHAR(60) - see
-- V1__initial_schema.sql), there is no child table involved, and there is no ABS-equivalent column
-- or field for tyres at all - nothing here needs the brakes backfill's abs_type handling.
--
-- Provenance: hand-curated from tools/tyres-research.json (web research, validated by
-- tools/validate-tyres-research.mjs), which only accepts a value carrying a real tyre-size code -
-- metric notation ("120/70ZR17"), older bias-ply notation ("4.60-18"), or an explicit "None" - and
-- requires a source. Regenerate this file with tools/import-tyres.mjs - every count in this header
-- is computed by that script, never typed.
-- Sources drawn on for this batch:
--   autoevolution.com, bikez.com, fichatecnica.motosblog.com.br, intruder125.com.br,
--   motonline.com.br, motorcyclespecs.co.za, revista.moto.com.br, soymotero.net,
--   suzukicycles.com, ultimatespecs.com
--
-- Scope of this batch: Suzuki. 262 catalogue slugs of this brand were missing front_tyre or
-- rear_tyre; 212 are staged below (212 carry a front value, 212 a rear).
-- Every staged slug is still missing front_tyre or rear_tyre in the live catalogue.
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. A slug with no corresponding motorcycles.slug is counted and left alone rather than
-- fuzzy-matched or inserted - never insert a catalogue row here.
--
-- Generation granularity: research was done per nameplate but written per exact slug, and split by
-- year range wherever the model actually changed - 2 of the 62 nameplates in this batch carry
-- more than one distinct value (front_tyre or rear_tyre) across their model years. A single value
-- was never stretched across a nameplate's whole life.
--
-- Ordering, and why the filename is load-bearing: Flyway runs repeatable migrations in description
-- order. "zzzzzz motorcycles tyres 2026 09" sorts after every other seed in this directory,
-- including "zzzzz motorcycles brakes 2026 09" (the previous last-to-run file: 'zzzzzz' > 'zzzzz_'
-- because 'z' > '_', the same comparison that puts 'zzzzz' after 'zzzz_'). Front_tyre/rear_tyre and
-- front_brake/rear_brake/abs_type share no column, so - exactly like the suspension-vs-brakes
-- precedent - their relative order carries no correctness weight; running last here is purely for
-- consistency with the project convention that every dedicated backfill runs after every other seed.
-- The input list is exactly the set of rows still missing front_tyre or rear_tyre after every
-- existing seed, so this file is pure gap-fill and cannot pre-empt any other import's claim on these
-- columns.
--
-- 0 values exceeded their column width and were cut at a word boundary rather than mid-word.
--
-- Idempotent and repeatable: every write below is COALESCE(existing, staged), so re-running this
-- file changes nothing once it has been applied, and an admin edit or a richer earlier import
-- always wins over this one.

BEGIN;

CREATE TEMP TABLE tmp_motorcycle_tyres (
    row_no        bigint PRIMARY KEY,
    slug          varchar(160) NOT NULL,
    front         varchar(60),
    rear          varchar(60),
    motorcycle_id bigint,
    CONSTRAINT uk_tmp_motorcycle_tyres_slug UNIQUE (slug),
    CONSTRAINT ck_tmp_motorcycle_tyres_any CHECK (front IS NOT NULL OR rear IS NOT NULL)
) ON COMMIT DROP;

INSERT INTO tmp_motorcycle_tyres (row_no, slug, front, rear) VALUES
(1, 'suzuki-bandit-1200-2006', '120/70ZR17', '180/55ZR17'),
(2, 'suzuki-bandit-1200s-2005', '120/70ZR17', '180/55ZR17'),
(3, 'suzuki-bandit-1200s-2006', '120/70ZR17', '180/55ZR17'),
(4, 'suzuki-bandit-1200s-2007', '120/70ZR17', '180/55ZR17'),
(5, 'suzuki-bandit-1200s-2008', '120/70ZR17', '180/55ZR17'),
(6, 'suzuki-bandit-600s-2005', '120/70ZR17', '160/60ZR17'),
(7, 'suzuki-bandit-600s-650s-2005', '120/70ZR17', '160/60ZR17'),
(8, 'suzuki-bandit-600s-650s-2006', '120/70ZR17', '160/60ZR17'),
(9, 'suzuki-bandit-600s-650s-2007', '120/70ZR17', '160/60ZR17'),
(10, 'suzuki-bandit-n-1200-1996', '120/60ZR17', '180/55ZR17'),
(11, 'suzuki-bandit-n-1200-1997', '120/60ZR17', '180/55ZR17'),
(12, 'suzuki-bandit-n-1200-1998', '120/60ZR17', '180/55ZR17'),
(13, 'suzuki-bandit-n-1200-2001', '120/70ZR17', '180/55ZR17'),
(14, 'suzuki-bandit-n-1200-2002', '120/70ZR17', '180/55ZR17'),
(15, 'suzuki-bandit-n-1200-2003', '120/70ZR17', '180/55ZR17'),
(16, 'suzuki-bandit-n-1200-2004', '120/70ZR17', '180/55ZR17'),
(17, 'suzuki-bandit-n-1200-2005', '120/70ZR17', '180/55ZR17'),
(18, 'suzuki-bandit-n-1200-2006', '120/70ZR17', '180/55ZR17'),
(19, 'suzuki-bandit-n-1200-2007', '120/70ZR17', '180/55ZR17'),
(20, 'suzuki-bandit-n-1200-2008', '120/70ZR17', '180/55ZR17'),
(21, 'suzuki-bandit-n-600-650-1996', '110/70-17', '150/70-17'),
(22, 'suzuki-bandit-n-600-650-1997', '110/70-17', '150/70-17'),
(23, 'suzuki-bandit-n-600-650-2001', '110/70-17', '150/70-17'),
(24, 'suzuki-bandit-n-600-650-2002', '110/70-17', '150/70-17'),
(25, 'suzuki-bandit-n-600-650-2003', '110/70-17', '150/70-17'),
(26, 'suzuki-bandit-n-600-650-2004', '110/70-17', '150/70-17'),
(27, 'suzuki-bandit-n-600-650-2005', '120/70ZR17', '160/60ZR17'),
(28, 'suzuki-bandit-n-600-650-2006', '120/70ZR17', '160/60ZR17'),
(29, 'suzuki-bandit-n-600-650-2007', '120/70ZR17', '160/60ZR17'),
(30, 'suzuki-boulevard-c1500-2006', '150/80-16', '180/70-15'),
(31, 'suzuki-boulevard-m800-2006', '130/90-16', '170/80-15'),
(32, 'suzuki-boulevard-m800-2007', '130/90-16', '170/80-15'),
(33, 'suzuki-boulevard-m800r-2012', '130/90-16', '170/80-15'),
(34, 'suzuki-boulevard-m800r-2013', '130/90-16', '170/80-15'),
(35, 'suzuki-boulevard-m800r-2014', '130/90-16', '170/80-15'),
(36, 'suzuki-boulevard-m800r-2015', '130/90-16', '170/80-15'),
(37, 'suzuki-boulevard-m800r-2016', '130/90-16', '170/80-15'),
(38, 'suzuki-burgman-650-2008', '120/70R15', '160/70R14'),
(39, 'suzuki-burgman-i-125cc-2012', '90/90-10', '100/90-10'),
(40, 'suzuki-burgman-i-125cc-2013', '90/90-10', '100/90-10'),
(41, 'suzuki-burgman-i-125cc-2014', '90/90-10', '100/90-10'),
(42, 'suzuki-burgman-i-125cc-2015', '90/90-10', '100/90-10'),
(43, 'suzuki-burgman-i-125cc-2016', '90/90-10', '100/90-10'),
(44, 'suzuki-burgman-i-125cc-2017', '90/90-10', '100/90-10'),
(45, 'suzuki-burgman-i-125cc-2018', '90/90-10', '100/90-10'),
(46, 'suzuki-burgman-i-125cc-2019', '90/90-10', '100/90-10'),
(47, 'suzuki-dl-1000-xt-v-strom-2020', '110/80-19', '150/70-17'),
(48, 'suzuki-dl-1000-xt-v-strom-2021', '110/80-19', '150/70-17'),
(49, 'suzuki-dl-1050-v-strom-2026', '110/80R19', '150/70R17'),
(50, 'suzuki-dl-1050-v-strom-2027', '110/80R19', '150/70R17'),
(51, 'suzuki-dl-1050-xt-v-strom-2026', '110/80R19', '150/70R17'),
(52, 'suzuki-dl-1050-xt-v-strom-2027', '110/80R19', '150/70R17'),
(53, 'suzuki-dl-650-xt-v-strom-2026', '110/80R19', '150/70R17'),
(54, 'suzuki-dl-650-xt-v-strom-2027', '110/80R19', '150/70R17'),
(55, 'suzuki-dl-800-de-v-strom-2024', '90/90-21', '150/70R17'),
(56, 'suzuki-dl-800-de-v-strom-2025', '90/90-21', '150/70R17'),
(57, 'suzuki-dl-800-de-v-strom-2026', '90/90-21', '150/70R17'),
(58, 'suzuki-dl-800-de-v-strom-2027', '90/90-21', '150/70R17'),
(59, 'suzuki-dl-800-v-strom-2025', '110/80R19', '150/70R17'),
(60, 'suzuki-dl-800-v-strom-2026', '110/80R19', '150/70R17'),
(61, 'suzuki-dl-800-v-strom-2027', '110/80R19', '150/70R17'),
(62, 'suzuki-dr-650-re-1995', '90/90-21', '120/90-17'),
(63, 'suzuki-dr-650-re-1996', '90/90-21', '120/90-17'),
(64, 'suzuki-dr-650-re-1997', '90/90-21', '120/90-17'),
(65, 'suzuki-dr-650-rse-1995', '90/90-21', '120/90-17'),
(66, 'suzuki-dr-650-rse-1996', '90/90-21', '120/90-17'),
(67, 'suzuki-dr-650-rse-1997', '90/90-21', '120/90-17'),
(68, 'suzuki-dr-800-s-1994', '90/90-21', '130/80-17'),
(69, 'suzuki-dr-800-s-1995', '90/90-21', '130/80-17'),
(70, 'suzuki-dr-800-s-1996', '90/90-21', '130/80-17'),
(71, 'suzuki-dr-800-s-1997', '90/90-21', '130/80-17'),
(72, 'suzuki-dr-800-s-1998', '90/90-21', '130/80-17'),
(73, 'suzuki-dr-800-s-1999', '90/90-21', '130/80-17'),
(74, 'suzuki-dr-800-s-2000', '90/90-21', '130/80-17'),
(75, 'suzuki-dr-800-s-2001', '90/90-21', '130/80-17'),
(76, 'suzuki-dr-z-400-e-2005', '80/100-21', '120/90-18'),
(77, 'suzuki-dr-z-400-s-2005', '80/100-21', '120/90-18'),
(78, 'suzuki-dr-z-400-sm-2007', '120/70-17', '140/70-17'),
(79, 'suzuki-en-125-yes-2005', '2.75-18', '90/90-18'),
(80, 'suzuki-en-125-yes-2006', '2.75-18', '90/90-18'),
(81, 'suzuki-en-125-yes-2007', '2.75-18', '90/90-18'),
(82, 'suzuki-en-125-yes-2008', '2.75-18', '90/90-18'),
(83, 'suzuki-en-125-yes-2009', '2.75-18', '90/90-18'),
(84, 'suzuki-en-125-yes-2010', '2.75-18', '90/90-18'),
(85, 'suzuki-en-125-yes-2011', '2.75-18', '90/90-18'),
(86, 'suzuki-en-125-yes-2012', '2.75-18', '90/90-18'),
(87, 'suzuki-en-125-yes-cargo-2012', '2.75-18', '90/90-18'),
(88, 'suzuki-en-125-yes-se-2011', '2.75-18', '90/90-18'),
(89, 'suzuki-en-125-yes-se-2012', '2.75-18', '90/90-18'),
(90, 'suzuki-en-125-yes-se-2013', '2.75-18', '90/90-18'),
(91, 'suzuki-en-125-yes-se-2014', '2.75-18', '90/90-18'),
(92, 'suzuki-en-125-yes-se-2015', '2.75-18', '90/90-18'),
(93, 'suzuki-en-125-yes-se-2016', '2.75-18', '90/90-18'),
(94, 'suzuki-freewind-xf-650-2003', '100/90-19', '130/80-17'),
(95, 'suzuki-gs-500-2007', '110/70-17', '130/70-17'),
(96, 'suzuki-gs-500-e-1994', '110/70-17', '130/70-17'),
(97, 'suzuki-gs-500-e-1995', '110/70-17', '130/70-17'),
(98, 'suzuki-gs-500-e-1996', '110/70-17', '130/70-17'),
(99, 'suzuki-gs-500-e-1997', '110/70-17', '130/70-17'),
(100, 'suzuki-gs-500f-2007', '110/70-17', '130/70-17'),
(101, 'suzuki-gsr-125-2013', '2.75-17', '3.00-17'),
(102, 'suzuki-gsr-125-2014', '2.75-17', '3.00-17'),
(103, 'suzuki-gsr-125-2015', '2.75-17', '3.00-17'),
(104, 'suzuki-gsr-125-2016', '2.75-17', '3.00-17'),
(105, 'suzuki-gsr-125s-2013', '2.75-17', '3.00-17'),
(106, 'suzuki-gsr-125s-2014', '2.75-17', '3.00-17'),
(107, 'suzuki-gsr-125s-2015', '2.75-17', '3.00-17'),
(108, 'suzuki-gsr-125s-2016', '2.75-17', '3.00-17'),
(109, 'suzuki-gsr-150i-2011', '2.75-18', '90/90-18'),
(110, 'suzuki-gsr-150i-2012', '2.75-18', '90/90-18'),
(111, 'suzuki-gsr-150i-2013', '2.75-18', '90/90-18'),
(112, 'suzuki-gsr-150i-2014', '2.75-18', '90/90-18'),
(113, 'suzuki-gsr-150i-2015', '2.75-18', '90/90-18'),
(114, 'suzuki-gsr-150i-2016', '2.75-18', '90/90-18'),
(115, 'suzuki-gsr-150i-2017', '2.75-18', '90/90-18'),
(116, 'suzuki-gsx-1300-r-hayabusa-2026', '120/70ZR17', '190/50ZR17'),
(117, 'suzuki-gsx-1300-r-hayabusa-2027', '120/70ZR17', '190/50ZR17'),
(118, 'suzuki-gsx-1400-2006', '120/70ZR17', '190/50ZR17'),
(119, 'suzuki-gsx-750-f-1995', '120/80ZR17', '150/70ZR17'),
(120, 'suzuki-gsx-750-f-1996', '120/80ZR17', '150/70ZR17'),
(121, 'suzuki-gsx-750-f-1997', '120/80ZR17', '150/70ZR17'),
(122, 'suzuki-gsx-750-f-1998', '120/80ZR17', '150/70ZR17'),
(123, 'suzuki-gsx-750-f-1999', '120/80ZR17', '150/70ZR17'),
(124, 'suzuki-gsx-750-f-2000', '120/80ZR17', '150/70ZR17'),
(125, 'suzuki-gsx-750-f-2001', '120/80ZR17', '150/70ZR17'),
(126, 'suzuki-gsx-750-f-2002', '120/80ZR17', '150/70ZR17'),
(127, 'suzuki-gsx-750-f-2003', '120/80ZR17', '150/70ZR17'),
(128, 'suzuki-gsx-750-f-2004', '120/80ZR17', '150/70ZR17'),
(129, 'suzuki-gsx-750-f-2005', '120/80ZR17', '150/70ZR17'),
(130, 'suzuki-gsx-750-f-2006', '120/80ZR17', '150/70ZR17'),
(131, 'suzuki-gsx-750-f-2007', '120/80ZR17', '150/70ZR17'),
(132, 'suzuki-gsx-750-f-2008', '120/80ZR17', '150/70ZR17'),
(133, 'suzuki-gsx-750-f-2009', '120/80ZR17', '150/70ZR17'),
(134, 'suzuki-gsx-8r-2027', '120/70ZR17', '180/55ZR17'),
(135, 'suzuki-gsx-8s-2027', '120/70ZR17', '180/55ZR17'),
(136, 'suzuki-gsx-r-1100-w-1992', '120/70ZR17', '180/55ZR17'),
(137, 'suzuki-gsx-r-1100-w-1993', '120/70ZR17', '180/55ZR17'),
(138, 'suzuki-gsx-r-1100-w-1994', '120/70ZR17', '180/55ZR17'),
(139, 'suzuki-gsx-r-1100-w-1995', '120/70ZR17', '180/55ZR17'),
(140, 'suzuki-gsx-r-1100-w-1996', '120/70ZR17', '180/55ZR17'),
(141, 'suzuki-gsx-r-1100-w-1997', '120/70ZR17', '180/55ZR17'),
(142, 'suzuki-gsx-r-1100-w-1998', '120/70ZR17', '180/55ZR17'),
(143, 'suzuki-gsx-r-1100-w-1999', '120/70ZR17', '180/55ZR17'),
(144, 'suzuki-gsx-r-1100-w-2000', '120/70ZR17', '180/55ZR17'),
(145, 'suzuki-gsx-s-1000-2026', '120/70ZR17', '190/50ZR17'),
(146, 'suzuki-gsx-s-1000-2027', '120/70ZR17', '190/50ZR17'),
(147, 'suzuki-gsx-s-1000-gt-2026', '120/70ZR17', '190/50ZR17'),
(148, 'suzuki-gsx-s-1000-gx-2026', '120/70ZR17', '190/50ZR17'),
(149, 'suzuki-gsx-s-1000-gx-2027', '120/70ZR17', '190/50ZR17'),
(150, 'suzuki-inazuma-250cc-2014', '110/80-17', '140/70-17'),
(151, 'suzuki-inazuma-250cc-2015', '110/80-17', '140/70-17'),
(152, 'suzuki-inazuma-250cc-2016', '110/80-17', '140/70-17'),
(153, 'suzuki-intruder-125-2015', '2.75-18', '3.50-16'),
(154, 'suzuki-intruder-125-2016', '2.75-18', '3.50-16'),
(155, 'suzuki-intruder-vl-125-lc-2006', '90/90-18', '130/90-15'),
(156, 'suzuki-intruder-vl-1500-lc-2007', '150/80-16', '180/70-15'),
(157, 'suzuki-intruder-vs-1400-glp-1994', '110/90-19', '170/80-15'),
(158, 'suzuki-intruder-vs-1400-glp-1995', '110/90-19', '170/80-15'),
(159, 'suzuki-intruder-vs-1400-glp-1996', '110/90-19', '170/80-15'),
(160, 'suzuki-intruder-vs-1400-glp-1997', '110/90-19', '170/80-15'),
(161, 'suzuki-marauder-125-2013', '110/90-16', '130/90-15'),
(162, 'suzuki-rf-600-r-1993', '120/60ZR17', '160/60ZR17'),
(163, 'suzuki-rf-600-r-1994', '120/60ZR17', '160/60ZR17'),
(164, 'suzuki-rf-600-r-1995', '120/60ZR17', '160/60ZR17'),
(165, 'suzuki-rf-600-r-1996', '120/60ZR17', '160/60ZR17'),
(166, 'suzuki-rf-900-r-1993', '120/70-17', '170/60-17'),
(167, 'suzuki-rf-900-r-1994', '120/70-17', '170/60-17'),
(168, 'suzuki-rf-900-r-1995', '120/70-17', '170/60-17'),
(169, 'suzuki-rf-900-r-1996', '120/70-17', '170/60-17'),
(170, 'suzuki-rf-900-r-1997', '120/70-17', '170/60-17'),
(171, 'suzuki-rf-900-r-1998', '120/70-17', '170/60-17'),
(172, 'suzuki-rf-900-r-1999', '120/70-17', '170/60-17'),
(173, 'suzuki-rm-125-1992', '80/100-21', '100/90-19'),
(174, 'suzuki-rm-125-1993', '80/100-21', '100/90-19'),
(175, 'suzuki-rm-125-1994', '80/100-21', '100/90-19'),
(176, 'suzuki-rm-125-1995', '80/100-21', '100/90-19'),
(177, 'suzuki-rm-125-1996', '80/100-21', '100/90-19'),
(178, 'suzuki-rm-250-1990', '80/100-21', '110/90-19'),
(179, 'suzuki-rm-250-1991', '80/100-21', '110/90-19'),
(180, 'suzuki-rm-250-1992', '80/100-21', '110/90-19'),
(181, 'suzuki-rm-250-1993', '80/100-21', '110/90-19'),
(182, 'suzuki-rm-250-1994', '80/100-21', '110/90-19'),
(183, 'suzuki-rm-250-1995', '80/100-21', '110/90-19'),
(184, 'suzuki-rm-250-1996', '80/100-21', '110/90-19'),
(185, 'suzuki-rm-80-1996', '70/100-17', '90/100-14'),
(186, 'suzuki-rm-80-1997', '70/100-17', '90/100-14'),
(187, 'suzuki-rm-80-2000', '70/100-17', '90/100-14'),
(188, 'suzuki-rmx-250-1990', '80/100-21', '110/100-18'),
(189, 'suzuki-rmx-250-1991', '80/100-21', '110/100-18'),
(190, 'suzuki-rmx-250-1992', '80/100-21', '110/100-18'),
(191, 'suzuki-rmx-250-1993', '80/100-21', '110/100-18'),
(192, 'suzuki-rmx-250-1994', '80/100-21', '110/100-18'),
(193, 'suzuki-rmx-250-1995', '80/100-21', '110/100-18'),
(194, 'suzuki-rmx-250-1996', '80/100-21', '110/100-18'),
(195, 'suzuki-rmx-250-1997', '80/100-21', '110/100-18'),
(196, 'suzuki-rmx-250-1998', '80/100-21', '110/100-18'),
(197, 'suzuki-rmx-250-1999', '80/100-21', '110/100-18'),
(198, 'suzuki-rmx-250-2000', '80/100-21', '110/100-18'),
(199, 'suzuki-rmx-250-2001', '80/100-21', '110/100-18'),
(200, 'suzuki-rmx-250-2002', '80/100-21', '110/100-18'),
(201, 'suzuki-rv-125-2008', '130/80-18', '180/80-14'),
(202, 'suzuki-savage-ls-650-1998', '100/90-19', '140/80-15'),
(203, 'suzuki-savage-ls-650-1999', '100/90-19', '140/80-15'),
(204, 'suzuki-savage-ls-650-2000', '100/90-19', '140/80-15'),
(205, 'suzuki-savage-ls-650-2001', '100/90-19', '140/80-15'),
(206, 'suzuki-sv-1000-2006', '120/70-17', '180/55-17'),
(207, 'suzuki-sv-1000s-2006', '120/70-17', '180/55-17'),
(208, 'suzuki-sv-650s-2008', '120/60ZR17', '160/60ZR17'),
(209, 'suzuki-vl-800-volusia-2007', '150/80-16', '180/70-15'),
(210, 'suzuki-vx-800cc-1994', '110/80-18', '150/70-17'),
(211, 'suzuki-vx-800cc-1995', '110/80-18', '150/70-17'),
(212, 'suzuki-vz-1600-marauder-2007', '130/70R17', '170/60R17');

-- ---------------------------------------------------------------------------
-- Resolve every staged row against the live catalogue. An exact slug match only.
-- ---------------------------------------------------------------------------
UPDATE tmp_motorcycle_tyres t SET motorcycle_id = m.id FROM motorcycles m WHERE m.slug = t.slug;

-- Gap-fill only: an existing value (an admin edit, or an earlier import) always wins. Each column
-- carries its own COALESCE guard so a row missing only one of the two is never disturbed on the
-- other.
UPDATE motorcycles m
SET front_tyre = COALESCE(m.front_tyre, t.front),
    rear_tyre  = COALESCE(m.rear_tyre,  t.rear)
FROM tmp_motorcycle_tyres t
WHERE m.id = t.motorcycle_id;

-- ---------------------------------------------------------------------------
-- Defensive checks before commit, mirroring R__zzzzz_motorcycles_brakes_2026_09.sql.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad        bigint;
    unresolved bigint;
BEGIN
    SELECT count(*) INTO bad
    FROM tmp_motorcycle_tyres
    WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle tyres backfill: % staged slugs are not a shape the public routing can use', bad;
    END IF;

    SELECT count(*) INTO bad
    FROM tmp_motorcycle_tyres
    WHERE length(front) > 60 OR length(rear) > 60;
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle tyres backfill: % staged rows exceed their column limit', bad;
    END IF;

    SELECT count(*) INTO unresolved FROM tmp_motorcycle_tyres WHERE motorcycle_id IS NULL;
    RAISE NOTICE 'Motorcycle tyres backfill: % of % staged rows matched a catalogue slug, % left unresolved',
        (SELECT count(*) FROM tmp_motorcycle_tyres WHERE motorcycle_id IS NOT NULL),
        (SELECT count(*) FROM tmp_motorcycle_tyres), unresolved;
END $$;

COMMIT;
