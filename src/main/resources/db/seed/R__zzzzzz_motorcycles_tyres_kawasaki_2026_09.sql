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
--   autoevolution.com, kawasaki.com, motorcyclenews.com
--
-- Scope of this batch: Kawasaki. 365 catalogue slugs of this brand were missing front_tyre or
-- rear_tyre; 349 are staged below (349 carry a front value, 349 a rear).
-- Every staged slug is still missing front_tyre or rear_tyre in the live catalogue.
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. A slug with no corresponding motorcycles.slug is counted and left alone rather than
-- fuzzy-matched or inserted - never insert a catalogue row here.
--
-- Generation granularity: research was done per nameplate but written per exact slug, and split by
-- year range wherever the model actually changed - 4 of the 81 nameplates in this batch carry
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
(1, 'kawasaki-concours14-1352cc-2012', '120/70-ZR17', '190/50-ZR17'),
(2, 'kawasaki-concours14-1352cc-2013', '120/70-ZR17', '190/50-ZR17'),
(3, 'kawasaki-eliminator-125-2007', '90/90-17', '130/90-15'),
(4, 'kawasaki-eliminator-500-2025', '130/70-18', '150/80-16'),
(5, 'kawasaki-eliminator-500-se-2025', '130/70-18', '150/80-16'),
(6, 'kawasaki-er-5-2006', '110/70-17', '130/70-17'),
(7, 'kawasaki-er-6n-650cc-2012', '120/70ZR17', '160/60ZR17'),
(8, 'kawasaki-er-6n-650cc-2013', '120/70ZR17', '160/60ZR17'),
(9, 'kawasaki-er-6n-650cc-2014', '120/70ZR17', '160/60ZR17'),
(10, 'kawasaki-er-6n-650cc-2015', '120/70ZR17', '160/60ZR17'),
(11, 'kawasaki-er-6n-650cc-2017', '120/70ZR17', '160/60ZR17'),
(12, 'kawasaki-kle-500-2007', '90/90-21', '130/80-17'),
(13, 'kawasaki-klv-1000-2006', '110/80-19', '150/70-17'),
(14, 'kawasaki-klx-110-2020', '2.50x14', '3.00x12'),
(15, 'kawasaki-klx-110-2021', '2.50x14', '3.00x12'),
(16, 'kawasaki-klx-110-2022', '2.50x14', '3.00x12'),
(17, 'kawasaki-klx-110-2023', '2.50x14', '3.00x12'),
(18, 'kawasaki-klx-110-2024', '2.50x14', '3.00x12'),
(19, 'kawasaki-klx-110-2025', '2.50x14', '3.00x12'),
(20, 'kawasaki-klx-110r-2026', '2.50x14', '3.00x12'),
(21, 'kawasaki-klx-110r-2027', '2.50x14', '3.00x12'),
(22, 'kawasaki-klx-230-2021', '2.75x21', '4.10x18'),
(23, 'kawasaki-klx-300r-2025', '3.0x21', '4.6x18'),
(24, 'kawasaki-klx-300r-2026', '3.0x21', '4.6x18'),
(25, 'kawasaki-klx-450r-2015', '80/100-21', '120/90-18'),
(26, 'kawasaki-klx-450r-2017', '80/100-21', '120/90-18'),
(27, 'kawasaki-klx-450r-2018', '80/100-21', '120/90-18'),
(28, 'kawasaki-klx-450r-2019', '80/100-21', '120/90-18'),
(29, 'kawasaki-klx-450r-2020', '80/100-21', '120/90-18'),
(30, 'kawasaki-klx-450r-2021', '80/100-21', '120/90-18'),
(31, 'kawasaki-klx-450r-2022', '80/100-21', '120/90-18'),
(32, 'kawasaki-klx-450r-2023', '80/100-21', '120/90-18'),
(33, 'kawasaki-klx-450r-2024', '80/100-21', '120/90-18'),
(34, 'kawasaki-klx140r-f-2027', '2.75x21', '4.10x18'),
(35, 'kawasaki-kx-125-2001', '80/100-21', '100/90-19'),
(36, 'kawasaki-kx-125-2002', '80/100-21', '100/90-19'),
(37, 'kawasaki-kx-125-2003', '80/100-21', '100/90-19'),
(38, 'kawasaki-kx-250-250-f-2011', '80/100-21', '100/90-19'),
(39, 'kawasaki-kx-250-250-f-2012', '80/100-21', '100/90-19'),
(40, 'kawasaki-kx-250-250-f-2013', '80/100-21', '100/90-19'),
(41, 'kawasaki-kx-250-250-f-2014', '80/100-21', '100/90-19'),
(42, 'kawasaki-kx-250-250-f-2015', '80/100-21', '100/90-19'),
(43, 'kawasaki-kx-250-250-f-2016', '80/100-21', '100/90-19'),
(44, 'kawasaki-kx-250-250-f-2021', '80/100-21', '100/90-19'),
(45, 'kawasaki-kx-250-250-f-2022', '80/100-21', '100/90-19'),
(46, 'kawasaki-kx-250-250-f-2023', '80/100-21', '100/90-19'),
(47, 'kawasaki-kx-250-250-f-2024', '80/100-21', '100/90-19'),
(48, 'kawasaki-kx-250-250-f-2025', '80/100-21', '100/90-19'),
(49, 'kawasaki-kx-250-250-f-2026', '80/100-21', '100/90-19'),
(50, 'kawasaki-kx-250-x-2021', '80/100-21', '110/100-18'),
(51, 'kawasaki-kx-250-x-2022', '80/100-21', '110/100-18'),
(52, 'kawasaki-kx-250-x-2023', '80/100-21', '110/100-18'),
(53, 'kawasaki-kx-250-x-2024', '80/100-21', '110/100-18'),
(54, 'kawasaki-kx-250-x-2025', '80/100-21', '110/100-18'),
(55, 'kawasaki-kx-250-x-2026', '80/100-21', '110/100-18'),
(56, 'kawasaki-kx-450-f-2010', '80/100-21', '120/80-19'),
(57, 'kawasaki-kx-450-f-2011', '80/100-21', '120/80-19'),
(58, 'kawasaki-kx-450-f-2012', '80/100-21', '120/80-19'),
(59, 'kawasaki-kx-450-f-2013', '80/100-21', '120/80-19'),
(60, 'kawasaki-kx-450-f-2014', '80/100-21', '120/80-19'),
(61, 'kawasaki-kx-450-f-2015', '80/100-21', '120/80-19'),
(62, 'kawasaki-kx-450-f-2016', '80/100-21', '120/80-19'),
(63, 'kawasaki-kx-450-f-2019', '80/100-21', '120/80-19'),
(64, 'kawasaki-kx-450-f-2020', '80/100-21', '120/80-19'),
(65, 'kawasaki-kx-450-f-2021', '80/100-21', '120/80-19'),
(66, 'kawasaki-kx-450-f-2022', '80/100-21', '120/80-19'),
(67, 'kawasaki-kx-450-f-2023', '80/100-21', '120/80-19'),
(68, 'kawasaki-kx-450-f-2024', '80/100-21', '120/80-19'),
(69, 'kawasaki-kx-450-f-2025', '80/100-21', '120/80-19'),
(70, 'kawasaki-kx-450-f-2026', '80/100-21', '120/80-19'),
(71, 'kawasaki-kx-450-x-2021', '80/100-21', '120/80-18'),
(72, 'kawasaki-kx-450-x-2022', '80/100-21', '120/80-18'),
(73, 'kawasaki-kx-450-x-2023', '80/100-21', '120/80-18'),
(74, 'kawasaki-kx-450-x-2025', '80/100-21', '120/80-18'),
(75, 'kawasaki-kx-450-x-2026', '80/100-21', '120/80-18'),
(76, 'kawasaki-kx-65-2010', '60/100-14', '80/100-12'),
(77, 'kawasaki-ninja-1000-2011', '120/70ZR17', '190/50ZR17'),
(78, 'kawasaki-ninja-1000-2012', '120/70ZR17', '190/50ZR17'),
(79, 'kawasaki-ninja-1000-2013', '120/70ZR17', '190/50ZR17'),
(80, 'kawasaki-ninja-1000-2014', '120/70ZR17', '190/50ZR17'),
(81, 'kawasaki-ninja-1000-2017', '120/70ZR17', '190/50ZR17'),
(82, 'kawasaki-ninja-1000-2018', '120/70ZR17', '190/50ZR17'),
(83, 'kawasaki-ninja-1000-2020', '120/70ZR17', '190/50ZR17'),
(84, 'kawasaki-ninja-1000-tourer-2015', '120/70ZR17', '190/50ZR17'),
(85, 'kawasaki-ninja-1000-tourer-2018', '120/70ZR17', '190/50ZR17'),
(86, 'kawasaki-ninja-1000-tourer-2020', '120/70ZR17', '190/50ZR17'),
(87, 'kawasaki-ninja-250r-2011', '110/70-17', '130/70-17'),
(88, 'kawasaki-ninja-250r-2012', '110/70-17', '130/70-17'),
(89, 'kawasaki-ninja-300-2013', '110/70-17', '140/70-17'),
(90, 'kawasaki-ninja-300-2014', '110/70-17', '140/70-17'),
(91, 'kawasaki-ninja-300-2015', '110/70-17', '140/70-17'),
(92, 'kawasaki-ninja-300-2016', '110/70-17', '140/70-17'),
(93, 'kawasaki-ninja-300-2018', '110/70-17', '140/70-17'),
(94, 'kawasaki-ninja-300-2023', '110/70-17', '140/70-17'),
(95, 'kawasaki-ninja-300-2024', '110/70-17', '140/70-17'),
(96, 'kawasaki-ninja-300-2025', '110/70-17', '140/70-17'),
(97, 'kawasaki-ninja-300-2026', '110/70-17', '140/70-17'),
(98, 'kawasaki-ninja-400-2019', '110/70x17', '150/60x17'),
(99, 'kawasaki-ninja-400-2020', '110/70x17', '150/60x17'),
(100, 'kawasaki-ninja-400-2021', '110/70x17', '150/60x17'),
(101, 'kawasaki-ninja-400-2022', '110/70x17', '150/60x17'),
(102, 'kawasaki-ninja-400-2023', '110/70x17', '150/60x17'),
(103, 'kawasaki-ninja-500-2026', '110/70-17', '150/60-17'),
(104, 'kawasaki-ninja-500-se-2025', '110/70-17', '150/60-17'),
(105, 'kawasaki-ninja-500-se-2026', '110/70-17', '150/60-17'),
(106, 'kawasaki-ninja-650r-649cc-2011', '120/70-17', '160/60-17'),
(107, 'kawasaki-ninja-650r-649cc-2012', '120/70-17', '160/60-17'),
(108, 'kawasaki-ninja-650r-649cc-2013', '120/70-17', '160/60-17'),
(109, 'kawasaki-ninja-650r-649cc-2014', '120/70-17', '160/60-17'),
(110, 'kawasaki-ninja-650r-649cc-2016', '120/70-17', '160/60-17'),
(111, 'kawasaki-ninja-650r-649cc-2017', '120/70-17', '160/60-17'),
(112, 'kawasaki-ninja-650r-649cc-2018', '120/70-17', '160/60-17'),
(113, 'kawasaki-ninja-650r-649cc-2020', '120/70-17', '160/60-17'),
(114, 'kawasaki-ninja-650r-649cc-2021', '120/70-17', '160/60-17'),
(115, 'kawasaki-ninja-650r-649cc-2022', '120/70-17', '160/60-17'),
(116, 'kawasaki-ninja-650r-649cc-2024', '120/70-17', '160/60-17'),
(117, 'kawasaki-ninja-650r-649cc-2025', '120/70-17', '160/60-17'),
(118, 'kawasaki-ninja-650r-649cc-2026', '120/70-17', '160/60-17'),
(119, 'kawasaki-ninja-h2-998cc-2016', '120/70ZR17', '200/55ZR17'),
(120, 'kawasaki-ninja-h2-sx-se-998cc-2019', '120/70ZR17', '190/55ZR17'),
(121, 'kawasaki-ninja-h2-sx-se-998cc-2020', '120/70ZR17', '190/55ZR17'),
(122, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2011', '120/70-17', '190/55-17'),
(123, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2012', '120/70-17', '190/55-17'),
(124, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2013', '120/70-17', '190/55-17'),
(125, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2014', '120/70-17', '190/55-17'),
(126, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2015', '120/70-17', '190/55-17'),
(127, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2017', '120/70ZR17', '190/55ZR17'),
(128, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2018', '120/70ZR17', '190/55ZR17'),
(129, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2019', '120/70ZR17', '190/55ZR17'),
(130, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2020', '120/70ZR17', '190/55ZR17'),
(131, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2021', '120/70ZR17', '190/55ZR17'),
(132, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2022', '120/70ZR17', '190/55ZR17'),
(133, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2023', '120/70ZR17', '190/55ZR17'),
(134, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2024', '120/70ZR17', '190/55ZR17'),
(135, 'kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-2025', '120/70ZR17', '190/55ZR17'),
(136, 'kawasaki-ninja-zx-10r-se-1000cc-2019', '120/70ZR17', '190/55ZR17'),
(137, 'kawasaki-ninja-zx-10r-se-1000cc-2020', '120/70ZR17', '190/55ZR17'),
(138, 'kawasaki-ninja-zx-10rr-998cc-2018', '120/70ZR17', '190/55ZR17'),
(139, 'kawasaki-ninja-zx-4r-2024', '120/70-17', '160/60-17'),
(140, 'kawasaki-ninja-zx-4rr-2026', '120/70-17', '160/60-17'),
(141, 'kawasaki-ninja-zx-6r-600cc-2011', '120/70-ZR17', '180/55-ZR17'),
(142, 'kawasaki-ninja-zx-6r-600cc-2012', '120/70-ZR17', '180/55-ZR17'),
(143, 'kawasaki-ninja-zx-6r-600cc-2013', '120/70-ZR17', '180/55-ZR17'),
(144, 'kawasaki-ninja-zx-6r-636cc-2013', '120/70-ZR17', '180/55-ZR17'),
(145, 'kawasaki-ninja-zx-6r-636cc-2014', '120/70-ZR17', '180/55-ZR17'),
(146, 'kawasaki-ninja-zx-6r-636cc-2015', '120/70-ZR17', '180/55-ZR17'),
(147, 'kawasaki-ninja-zx-6r-636cc-2016', '120/70-ZR17', '180/55-ZR17'),
(148, 'kawasaki-ninja-zx-6r-636cc-2020', '120/70 ZR17', '180/55 ZR17'),
(149, 'kawasaki-ninja-zx-6r-636cc-2021', '120/70 ZR17', '180/55 ZR17'),
(150, 'kawasaki-ninja-zx-6r-636cc-2022', '120/70 ZR17', '180/55 ZR17'),
(151, 'kawasaki-ninja-zx-6r-636cc-2023', '120/70 ZR17', '180/55 ZR17'),
(152, 'kawasaki-ninja-zx-6r-636cc-2024', '120/70 ZR17', '180/55 ZR17'),
(153, 'kawasaki-ninja-zx-6r-636cc-2025', '120/70 ZR17', '180/55 ZR17'),
(154, 'kawasaki-ninja-zx-6r-636cc-2026', '120/70 ZR17', '180/55 ZR17'),
(155, 'kawasaki-versys-1000-2012', '120/70ZR17', '180/55ZR17'),
(156, 'kawasaki-versys-1000-2013', '120/70ZR17', '180/55ZR17'),
(157, 'kawasaki-versys-1000-2015', '120/70ZR17', '180/55ZR17'),
(158, 'kawasaki-versys-1000-2016', '120/70ZR17', '180/55ZR17'),
(159, 'kawasaki-versys-1000-2017', '120/70ZR17', '180/55ZR17'),
(160, 'kawasaki-versys-1000-2018', '120/70ZR17', '180/55ZR17'),
(161, 'kawasaki-versys-1000-2019', '120/70ZR17', '180/55ZR17'),
(162, 'kawasaki-versys-1000-2020', '120/70ZR17', '180/55ZR17'),
(163, 'kawasaki-versys-1000-2021', '120/70ZR17', '180/55ZR17'),
(164, 'kawasaki-versys-1000-2022', '120/70ZR17', '180/55ZR17'),
(165, 'kawasaki-versys-1000-2023', '120/70ZR17', '180/55ZR17'),
(166, 'kawasaki-versys-1000-grand-tourer-2013', '120/70ZR17', '180/55ZR17'),
(167, 'kawasaki-versys-1000-grand-tourer-2015', '120/70ZR17', '180/55ZR17'),
(168, 'kawasaki-versys-1000-grand-tourer-2016', '120/70ZR17', '180/55ZR17'),
(169, 'kawasaki-versys-1000-grand-tourer-2017', '120/70ZR17', '180/55ZR17'),
(170, 'kawasaki-versys-1000-grand-tourer-2018', '120/70ZR17', '180/55ZR17'),
(171, 'kawasaki-versys-1000-grand-tourer-2019', '120/70ZR17', '180/55ZR17'),
(172, 'kawasaki-versys-1000-grand-tourer-2020', '120/70ZR17', '180/55ZR17'),
(173, 'kawasaki-versys-1000-grand-tourer-2021', '120/70ZR17', '180/55ZR17'),
(174, 'kawasaki-versys-1000-grand-tourer-2022', '120/70ZR17', '180/55ZR17'),
(175, 'kawasaki-versys-1000-grand-tourer-2023', '120/70ZR17', '180/55ZR17'),
(176, 'kawasaki-versys-1000-grand-tourer-2024', '120/70ZR17', '180/55ZR17'),
(177, 'kawasaki-versys-1100-grand-tourer-2026', '120/70-17', '180/55-17'),
(178, 'kawasaki-versys-650cc-2011', '120/70-17', '160/60-17'),
(179, 'kawasaki-versys-650cc-2012', '120/70-17', '160/60-17'),
(180, 'kawasaki-versys-650cc-2013', '120/70-17', '160/60-17'),
(181, 'kawasaki-versys-650cc-2015', '120/70ZR17', '160/60ZR17'),
(182, 'kawasaki-versys-650cc-2016', '120/70ZR17', '160/60ZR17'),
(183, 'kawasaki-versys-650cc-2017', '120/70ZR17', '160/60ZR17'),
(184, 'kawasaki-versys-650cc-2018', '120/70ZR17', '160/60ZR17'),
(185, 'kawasaki-versys-650cc-2019', '120/70ZR17', '160/60ZR17'),
(186, 'kawasaki-versys-650cc-2020', '120/70ZR17', '160/60ZR17'),
(187, 'kawasaki-versys-650cc-2021', '120/70ZR17', '160/60ZR17'),
(188, 'kawasaki-versys-650cc-2022', '120/70ZR17', '160/60ZR17'),
(189, 'kawasaki-versys-650cc-2023', '120/70ZR17', '160/60ZR17'),
(190, 'kawasaki-versys-650cc-2024', '120/70ZR17', '160/60ZR17'),
(191, 'kawasaki-versys-650cc-2025', '120/70ZR17', '160/60ZR17'),
(192, 'kawasaki-versys-650cc-2026', '120/70ZR17', '160/60ZR17'),
(193, 'kawasaki-versys-city-650-2012', '120/70-17', '160/60-17'),
(194, 'kawasaki-versys-tourer-650-2011', '120/70-17', '160/60-17'),
(195, 'kawasaki-versys-tourer-650-2012', '120/70-17', '160/60-17'),
(196, 'kawasaki-versys-tourer-650-2013', '120/70-17', '160/60-17'),
(197, 'kawasaki-versys-tourer-650-2016', '120/70ZR17', '160/60ZR17'),
(198, 'kawasaki-versys-tourer-650-2017', '120/70ZR17', '160/60ZR17'),
(199, 'kawasaki-versys-tourer-650-2018', '120/70ZR17', '160/60ZR17'),
(200, 'kawasaki-versys-tourer-650-2019', '120/70ZR17', '160/60ZR17'),
(201, 'kawasaki-versys-tourer-650-2020', '120/70ZR17', '160/60ZR17'),
(202, 'kawasaki-versys-tourer-650-2021', '120/70ZR17', '160/60ZR17'),
(203, 'kawasaki-versys-tourer-650-2022', '120/70ZR17', '160/60ZR17'),
(204, 'kawasaki-versys-tourer-650-2023', '120/70ZR17', '160/60ZR17'),
(205, 'kawasaki-versys-tourer-650-2024', '120/70ZR17', '160/60ZR17'),
(206, 'kawasaki-versys-tourer-650-2025', '120/70ZR17', '160/60ZR17'),
(207, 'kawasaki-versys-tourer-650-2026', '120/70ZR17', '160/60ZR17'),
(208, 'kawasaki-versys-x-300-2018', '100/90-19', '130/80-17'),
(209, 'kawasaki-versys-x-300-2020', '100/90-19', '130/80-17'),
(210, 'kawasaki-versys-x-300-2022', '100/90-19', '130/80-17'),
(211, 'kawasaki-versys-x-300-2023', '100/90-19', '130/80-17'),
(212, 'kawasaki-versys-x-300-2024', '100/90-19', '130/80-17'),
(213, 'kawasaki-versys-x-300-2025', '100/90-19', '130/80-17'),
(214, 'kawasaki-versys-x-300-2026', '100/90-19', '130/80-17'),
(215, 'kawasaki-versys-x-300-tourer-2017', '100/90-19', '130/80-17'),
(216, 'kawasaki-versys-x-300-tourer-2018', '100/90-19', '130/80-17'),
(217, 'kawasaki-versys-x-300-tourer-2020', '100/90-19', '130/80-17'),
(218, 'kawasaki-versys-x-300-tourer-2021', '100/90-19', '130/80-17'),
(219, 'kawasaki-versys-x-300-tourer-2022', '100/90-19', '130/80-17'),
(220, 'kawasaki-versys-x-300-tourer-2023', '100/90-19', '130/80-17'),
(221, 'kawasaki-versys-x-300-tourer-2024', '100/90-19', '130/80-17'),
(222, 'kawasaki-versys-x-300-tourer-2025', '100/90-19', '130/80-17'),
(223, 'kawasaki-versys-x-300-tourer-2026', '100/90-19', '130/80-17'),
(224, 'kawasaki-vn-1500-mean-streak-2007', '130/90R17', '170/60R17'),
(225, 'kawasaki-vn-1600-classic-2007', '130/90-16', '170/70-16'),
(226, 'kawasaki-vn-1600-classic-tourer-2007', '150/80-16', '200/60-16'),
(227, 'kawasaki-vn-2000-2008', '150/80-16', '200/60-16'),
(228, 'kawasaki-vn-2000-classic-2010', '150/80-16', '200/60-16'),
(229, 'kawasaki-vulcan-900-classic-lt-2011', '130/90-16', '180/70-15'),
(230, 'kawasaki-vulcan-900-classic-lt-2012', '130/90-16', '180/70-15'),
(231, 'kawasaki-vulcan-900-classic-lt-2013', '130/90-16', '180/70-15'),
(232, 'kawasaki-vulcan-900-classic-lt-2015', '130/90-16', '180/70-15'),
(233, 'kawasaki-vulcan-900-custom-2011', '80/90-21', '180/70-15'),
(234, 'kawasaki-vulcan-900-custom-2012', '80/90-21', '180/70-15'),
(235, 'kawasaki-vulcan-900-custom-2013', '80/90-21', '180/70-15'),
(236, 'kawasaki-vulcan-900-custom-2015', '80/90-21', '180/70-15'),
(237, 'kawasaki-vulcan-s-650-2015', '120/70x18', '160/60x17'),
(238, 'kawasaki-vulcan-s-650-2016', '120/70x18', '160/60x17'),
(239, 'kawasaki-vulcan-s-650-2017', '120/70x18', '160/60x17'),
(240, 'kawasaki-vulcan-s-650-2018', '120/70x18', '160/60x17'),
(241, 'kawasaki-vulcan-s-650-2019', '120/70x18', '160/60x17'),
(242, 'kawasaki-vulcan-s-650-2020', '120/70x18', '160/60x17'),
(243, 'kawasaki-vulcan-s-650-2021', '120/70x18', '160/60x17'),
(244, 'kawasaki-vulcan-s-650-2022', '120/70x18', '160/60x17'),
(245, 'kawasaki-vulcan-s-650-2023', '120/70x18', '160/60x17'),
(246, 'kawasaki-vulcan-s-650-2024', '120/70x18', '160/60x17'),
(247, 'kawasaki-vulcan-s-650-2025', '120/70x18', '160/60x17'),
(248, 'kawasaki-vulcan-s-650-2026', '120/70x18', '160/60x17'),
(249, 'kawasaki-vulcan-s-650-cafe-2018', '120/70x18', '160/60x17'),
(250, 'kawasaki-vulcan-s-650-cafe-2019', '120/70x18', '160/60x17'),
(251, 'kawasaki-vulcan-s-650-cafe-2020', '120/70x18', '160/60x17'),
(252, 'kawasaki-vulcan-s-650-cafe-2021', '120/70x18', '160/60x17'),
(253, 'kawasaki-vulcan-s-650-cafe-2022', '120/70x18', '160/60x17'),
(254, 'kawasaki-vulcan-s-650-cafe-2023', '120/70x18', '160/60x17'),
(255, 'kawasaki-vulcan-s-650-cafe-2025', '120/70x18', '160/60x17'),
(256, 'kawasaki-vulcan-s-650-cafe-2026', '120/70x18', '160/60x17'),
(257, 'kawasaki-vulcan-s-650-special-edition-2017', '120/70x18', '160/60x17'),
(258, 'kawasaki-vulcan-s-650-special-edition-2018', '120/70x18', '160/60x17'),
(259, 'kawasaki-vulcan-s-650-special-edition-2023', '120/70x18', '160/60x17'),
(260, 'kawasaki-vulcan-s-650-special-edition-2025', '120/70x18', '160/60x17'),
(261, 'kawasaki-vulcan-vn-900-classic-2011', '130/90-16', '180/70-15'),
(262, 'kawasaki-vulcan-vn-900-classic-2012', '130/90-16', '180/70-15'),
(263, 'kawasaki-vulcan-vn-900-classic-2013', '130/90-16', '180/70-15'),
(264, 'kawasaki-vulcan-vn-900-classic-2015', '130/90-16', '180/70-15'),
(265, 'kawasaki-w-650-2006', '100/90-19', '130/80-18'),
(266, 'kawasaki-z-1000-2011', '120/70ZR17', '190/50ZR17'),
(267, 'kawasaki-z-1000-2012', '120/70ZR17', '190/50ZR17'),
(268, 'kawasaki-z-1000-2013', '120/70ZR17', '190/50ZR17'),
(269, 'kawasaki-z-1000-2015', '120/70ZR17', '190/50ZR17'),
(270, 'kawasaki-z-1000-2016', '120/70ZR17', '190/50ZR17'),
(271, 'kawasaki-z-1000-2017', '120/70ZR17', '190/50ZR17'),
(272, 'kawasaki-z-1000-2018', '120/70ZR17', '190/50ZR17'),
(273, 'kawasaki-z-1000-2020', '120/70ZR17', '190/50ZR17'),
(274, 'kawasaki-z-1000-2021', '120/70ZR17', '190/50ZR17'),
(275, 'kawasaki-z-1000-2022', '120/70ZR17', '190/50ZR17'),
(276, 'kawasaki-z-1000-2023', '120/70ZR17', '190/50ZR17'),
(277, 'kawasaki-z-1000-2024', '120/70ZR17', '190/50ZR17'),
(278, 'kawasaki-z-1000-2025', '120/70ZR17', '190/50ZR17'),
(279, 'kawasaki-z-1000-r-edition-2018', '120/70ZR17', '190/50ZR17'),
(280, 'kawasaki-z-1000-r-edition-2020', '120/70ZR17', '190/50ZR17'),
(281, 'kawasaki-z-1000-r-edition-2021', '120/70ZR17', '190/50ZR17'),
(282, 'kawasaki-z-1000-r-edition-2022', '120/70ZR17', '190/50ZR17'),
(283, 'kawasaki-z-1000-r-edition-2023', '120/70ZR17', '190/50ZR17'),
(284, 'kawasaki-z-1000-r-edition-2024', '120/70ZR17', '190/50ZR17'),
(285, 'kawasaki-z-1000-r-edition-2025', '120/70ZR17', '190/50ZR17'),
(286, 'kawasaki-z-300-2015', '110/70-17', '140/70-17'),
(287, 'kawasaki-z-300-2016', '110/70-17', '140/70-17'),
(288, 'kawasaki-z-300-2018', '110/70-17', '140/70-17'),
(289, 'kawasaki-z-400-2019', '110/70-17', '150/60-17'),
(290, 'kawasaki-z-400-2020', '110/70-17', '150/60-17'),
(291, 'kawasaki-z-400-2021', '110/70-17', '150/60-17'),
(292, 'kawasaki-z-400-2022', '110/70-17', '150/60-17'),
(293, 'kawasaki-z-400-2023', '110/70-17', '150/60-17'),
(294, 'kawasaki-z-400-se-2023', '110/70-17', '150/60-17'),
(295, 'kawasaki-z-400-se-2024', '110/70-17', '150/60-17'),
(296, 'kawasaki-z-500-2026', '110/70-17', '150/60-17'),
(297, 'kawasaki-z-500-se-2025', '110/70-17', '150/60-17'),
(298, 'kawasaki-z-500-se-2026', '110/70-17', '150/60-17'),
(299, 'kawasaki-z-650-2018', '120/70ZR17', '160/60ZR17'),
(300, 'kawasaki-z-650-2020', '120/70ZR17', '160/60ZR17'),
(301, 'kawasaki-z-650-2021', '120/70ZR17', '160/60ZR17'),
(302, 'kawasaki-z-650-2024', '120/70ZR17', '160/60ZR17'),
(303, 'kawasaki-z-650-2025', '120/70ZR17', '160/60ZR17'),
(304, 'kawasaki-z-650-2026', '120/70ZR17', '160/60ZR17'),
(305, 'kawasaki-z-650-rs-2023', '120/70-17', '160/60-17'),
(306, 'kawasaki-z-650-rs-2025', '120/70-17', '160/60-17'),
(307, 'kawasaki-z-650-se-abs-2021', '120/70ZR17', '160/60ZR17'),
(308, 'kawasaki-z-650-se-abs-2022', '120/70ZR17', '160/60ZR17'),
(309, 'kawasaki-z-750-2011', '120/70ZR17', '180/55ZR17'),
(310, 'kawasaki-z-750-2012', '120/70ZR17', '180/55ZR17'),
(311, 'kawasaki-z-750s-2006', '120/70-17', '180/55-17'),
(312, 'kawasaki-z-800-2013', '120/70ZR17', '180/55ZR17'),
(313, 'kawasaki-z-800-2014', '120/70ZR17', '180/55ZR17'),
(314, 'kawasaki-z-800-2016', '120/70ZR17', '180/55ZR17'),
(315, 'kawasaki-z-900-2018', '120/70ZR17', '180/55ZR17'),
(316, 'kawasaki-z-900-2019', '120/70ZR17', '180/55ZR17'),
(317, 'kawasaki-z-900-2020', '120/70ZR17', '180/55ZR17'),
(318, 'kawasaki-z-900-2021', '120/70ZR17', '180/55ZR17'),
(319, 'kawasaki-z-900-2022', '120/70ZR17', '180/55ZR17'),
(320, 'kawasaki-z-900-2023', '120/70ZR17', '180/55ZR17'),
(321, 'kawasaki-z-900-2025', '120/70ZR17', '180/55ZR17'),
(322, 'kawasaki-z-900-2026', '120/70ZR17', '180/55ZR17'),
(323, 'kawasaki-z-900-50th-anniversary-2022', '120/70ZR17', '180/55ZR17'),
(324, 'kawasaki-z-900-r-edition-2022', '120/70ZR17', '180/55ZR17'),
(325, 'kawasaki-z-900-r-edition-2023', '120/70ZR17', '180/55ZR17'),
(326, 'kawasaki-z-900-r-edition-2025', '120/70ZR17', '180/55ZR17'),
(327, 'kawasaki-z-900-r-edition-2026', '120/70ZR17', '180/55ZR17'),
(328, 'kawasaki-z-900-rs-2019', '120/70ZR17', '180/55ZR17'),
(329, 'kawasaki-z-900-rs-2020', '120/70ZR17', '180/55ZR17'),
(330, 'kawasaki-z-900-rs-2022', '120/70ZR17', '180/55ZR17'),
(331, 'kawasaki-z-900-rs-2023', '120/70ZR17', '180/55ZR17'),
(332, 'kawasaki-z-900-rs-2024', '120/70ZR17', '180/55ZR17'),
(333, 'kawasaki-z-900-rs-cafe-2019', '120/70ZR17', '180/55ZR17'),
(334, 'kawasaki-z-900-rs-cafe-2020', '120/70ZR17', '180/55ZR17'),
(335, 'kawasaki-z-900-rs-cafe-2023', '120/70ZR17', '180/55ZR17'),
(336, 'kawasaki-z-900-rs-cafe-2024', '120/70ZR17', '180/55ZR17'),
(337, 'kawasaki-z-900-rs-cafe-2025', '120/70ZR17', '180/55ZR17'),
(338, 'kawasaki-z-900-rs-r-edition-2022', '120/70ZR17', '180/55ZR17'),
(339, 'kawasaki-z-900-rs-r-edition-2023', '120/70ZR17', '180/55ZR17'),
(340, 'kawasaki-z-900-rs-r-edition-2024', '120/70ZR17', '180/55ZR17'),
(341, 'kawasaki-z-900-rs-r-edition-2025', '120/70ZR17', '180/55ZR17'),
(342, 'kawasaki-z-900-se-2021', '120/70ZR17', '180/55ZR17'),
(343, 'kawasaki-z-900-se-2026', '120/70ZR17', '180/55ZR17'),
(344, 'kawasaki-zrx-1200-r-2006', '120/70-17', '180/55-17'),
(345, 'kawasaki-zx-14-zx-14r-1352cc-2011', '120/70ZR17', '190/50ZR17'),
(346, 'kawasaki-zx-14-zx-14r-1352cc-2013', '120/70ZR17', '190/50ZR17'),
(347, 'kawasaki-zx-14-zx-14r-1352cc-2014', '120/70ZR17', '190/50ZR17'),
(348, 'kawasaki-zzr-1200-2007', '120/70-ZR17', '180/55-ZR17'),
(349, 'kawasaki-zzr-600-2005', '120/65-17', '180/55-17');

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
