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
--   autoevolution.com, dirtrider.com, ktm.com, ktmindia.com, motorcycle.com, motorcyclenews.com
--
-- Scope of this batch: Ktm. 278 catalogue slugs of this brand were missing front_tyre or
-- rear_tyre; 266 are staged below (266 carry a front value, 266 a rear).
-- Every staged slug is still missing front_tyre or rear_tyre in the live catalogue.
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. A slug with no corresponding motorcycles.slug is counted and left alone rather than
-- fuzzy-matched or inserted - never insert a catalogue row here.
--
-- Generation granularity: research was done per nameplate but written per exact slug, and split by
-- year range wherever the model actually changed - 2 of the 97 nameplates in this batch carry
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
(1, 'ktm-125-sx-2027', '80/100-21', '100/90-19'),
(2, 'ktm-125-xc-w-2027', '90/90-21', '140/80-18'),
(3, 'ktm-1390-super-duke-rr-track-2026', '120/70ZR17', '200/55ZR17'),
(4, 'ktm-150-exc-tbi-2024', '90/90-21', '140/80-18'),
(5, 'ktm-150-exc-tpi-2023', '90/90-21', '140/80-18'),
(6, 'ktm-250-exc-f-2027', '90/90-21', '140/80-18'),
(7, 'ktm-250-exc-f-six-days-2027', '90/90-21', '140/80-18'),
(8, 'ktm-250-exc-racing-2006', '90/90-21', '120/90-18'),
(9, 'ktm-250-exc-tbi-2024', '90/90-21', '140/80-18'),
(10, 'ktm-250-exc-tbi-sixdays-2024', '90/90-21', '140/80-18'),
(11, 'ktm-250-sx-2027', '80/100-21', '110/90-19'),
(12, 'ktm-250-sx-f-2027', '80/100-21', '110/90-19'),
(13, 'ktm-250-xc-w-2027', '90/90-21', '140/80-18'),
(14, 'ktm-300-exc-2027', '90/90-21', '140/80-18'),
(15, 'ktm-300-exc-six-days-2027', '90/90-21', '140/80-18'),
(16, 'ktm-300-exc-tbi-sixdays-2024', '90/90-21', '140/80-18'),
(17, 'ktm-300-sx-2027', '80/100-21', '110/90-19'),
(18, 'ktm-350-exc-f-2027', '90/90-21', '140/80-18'),
(19, 'ktm-350-exc-f-six-days-2027', '90/90-21', '140/80-18'),
(20, 'ktm-350-sx-f-2027', '80/100-21', '110/90-19'),
(21, 'ktm-450-exc-f-2027', '90/90-21', '140/80-18'),
(22, 'ktm-450-exc-f-six-days-2027', '90/90-21', '140/80-18'),
(23, 'ktm-450-rally-replica-2027', '90/90-21', '140/80-18'),
(24, 'ktm-450-smr-2027', '125/75-16.5', '165/55-17'),
(25, 'ktm-450-sx-f-2027', '80/100-21', '120/80-19'),
(26, 'ktm-50-sx-2027', '2.50-12', '2.75-10'),
(27, 'ktm-50-sx-factory-edition-2026', '2.50-12', '2.75-10'),
(28, 'ktm-50-sx-mini-2023', '2.6x10', '2.75x10'),
(29, 'ktm-500-exc-f-2026-2025', '90/90-21', '140/80-18'),
(30, 'ktm-500-exc-f-2027', '90/90-21', '140/80-18'),
(31, 'ktm-500-exc-f-six-days-2027', '90/90-21', '140/80-18'),
(32, 'ktm-505-xc-f-2009', '80/100-21', '110/100-18'),
(33, 'ktm-525-exc-2005', '90/90-21', '140/80-18'),
(34, 'ktm-525-mxc-desert-racing-2007', '90/90-21', '140/80-18'),
(35, 'ktm-625-smc-2006', '120/70-17', '160/60-17'),
(36, 'ktm-625-sxc-2007', '90/90-21', '140/90-18'),
(37, 'ktm-640-lc4-adventure-2006', '90/90-21', '140/80-18'),
(38, 'ktm-640-lc4-enduro-2006', '90/90-21', '140/80-18'),
(39, 'ktm-640-lc4-supermoto-2006', '110/90-17', '160/60-17'),
(40, 'ktm-65-sx-2027', '60/100-14', '80/100-12'),
(41, 'ktm-660-smc-2006', '120/70-17', '120/70-17'),
(42, 'ktm-690-enduro-enduro-r-2017', '90/90-21', '140/80-18'),
(43, 'ktm-690-supermoto-2009', '120/70-17', '160/60-17'),
(44, 'ktm-690-supermoto-r-2009', '120/70-17', '160/60-17'),
(45, 'ktm-790-duke-l-2020', '120/70R17', '180/55R17'),
(46, 'ktm-85-motocross-2007', '70/100-17', '90/100-14'),
(47, 'ktm-85-sx-17-14-2027', '70/100-17', '90/100-14'),
(48, 'ktm-85-sx-19-16-2027', '70/100x19', '90/100x16'),
(49, 'ktm-890-smt-2024', '120/70-17', '180/55-17'),
(50, 'ktm-950-adventure-s-2005', '90/90R21', '150/70R18'),
(51, 'ktm-950-superenduro-r-2008', '90/90-21', '140/80-18'),
(52, 'ktm-950-supermoto-2007', '120/70-17', '180/55-17'),
(53, 'ktm-990-adventure-s-2009', '90/90R21', '150/70R18'),
(54, 'ktm-990-duke-2024', '120/70R17', '180/55R17'),
(55, 'ktm-adventure-1190-r-2016', '90/90ZR21', '150/70ZR18'),
(56, 'ktm-adventure-1190cc-2014', '120/70R19', '170/60R17'),
(57, 'ktm-adventure-1190cc-2015', '120/70R19', '170/60R17'),
(58, 'ktm-adventure-640-st-1998', '90/90-21', '140/80-18'),
(59, 'ktm-adventure-640-st-1999', '90/90-21', '140/80-18'),
(60, 'ktm-adventure-640-st-2000', '90/90-21', '140/80-18'),
(61, 'ktm-adventure-640-st-2003', '90/90-21', '140/80-18'),
(62, 'ktm-adventure-640-st-2004', '90/90-21', '140/80-18'),
(63, 'ktm-adventure-640-st-2005', '90/90-21', '140/80-18'),
(64, 'ktm-adventure-640-st-2006', '90/90-21', '140/80-18'),
(65, 'ktm-adventure-640-st-2007', '90/90-21', '140/80-18'),
(66, 'ktm-adventure-950cc-2004', '90/90R21', '150/70R18'),
(67, 'ktm-adventure-950cc-2005', '90/90R21', '150/70R18'),
(68, 'ktm-adventure-950cc-2006', '90/90R21', '150/70R18'),
(69, 'ktm-adventure-950cc-2007', '90/90R21', '150/70R18'),
(70, 'ktm-adventure-950cc-2008', '90/90R21', '150/70R18'),
(71, 'ktm-adventure-990cc-2006', '90/90R21', '150/70R18'),
(72, 'ktm-adventure-990cc-2007', '90/90R21', '150/70R18'),
(73, 'ktm-adventure-990cc-2008', '90/90R21', '150/70R18'),
(74, 'ktm-adventure-990cc-2009', '90/90R21', '150/70R18'),
(75, 'ktm-adventure-990cc-2010', '90/90R21', '150/70R18'),
(76, 'ktm-adventure-990cc-2011', '90/90R21', '150/70R18'),
(77, 'ktm-adventure-990cc-2012', '90/90R21', '150/70R18'),
(78, 'ktm-adventure-r-dakar-990cc-2010', '90/90-21', '150/70-18'),
(79, 'ktm-adventure-r-dakar-990cc-2011', '90/90-21', '150/70-18'),
(80, 'ktm-duke-200-abs-2018', '110/70R17', '150/60R17'),
(81, 'ktm-duke-200-abs-2019', '110/70R17', '150/60R17'),
(82, 'ktm-duke-200-abs-2020', '110/70R17', '150/60R17'),
(83, 'ktm-duke-200-abs-2021', '110/70R17', '150/60R17'),
(84, 'ktm-duke-390-2015', '110/70R17', '150/60R17'),
(85, 'ktm-duke-390-2016', '110/70R17', '150/60R17'),
(86, 'ktm-duke-390-2017', '110/70R17', '150/60R17'),
(87, 'ktm-exc-125-1999', '90/90-21', '140/80-18'),
(88, 'ktm-exc-125-2000', '90/90-21', '140/80-18'),
(89, 'ktm-exc-125-2001', '90/90-21', '140/80-18'),
(90, 'ktm-exc-125-2002', '90/90-21', '140/80-18'),
(91, 'ktm-exc-125-2003', '90/90-21', '140/80-18'),
(92, 'ktm-exc-125-2004', '90/90-21', '140/80-18'),
(93, 'ktm-exc-125-2005', '90/90-21', '140/80-18'),
(94, 'ktm-exc-125-2006', '90/90-21', '140/80-18'),
(95, 'ktm-exc-125-2007', '90/90-21', '140/80-18'),
(96, 'ktm-exc-125-2008', '90/90-21', '140/80-18'),
(97, 'ktm-exc-125-2010', '90/90-21', '140/80-18'),
(98, 'ktm-exc-150-2025', '90/90-21', '140/80-18'),
(99, 'ktm-exc-200-2007', '90/90-21', '140/80-18'),
(100, 'ktm-exc-200-2008', '90/90-21', '140/80-18'),
(101, 'ktm-exc-200-2010', '90/90-21', '140/80-18'),
(102, 'ktm-exc-250-1994', '90/90-21', '140/80-18'),
(103, 'ktm-exc-250-1995', '90/90-21', '140/80-18'),
(104, 'ktm-exc-250-1998', '90/90-21', '140/80-18'),
(105, 'ktm-exc-250-1999', '90/90-21', '140/80-18'),
(106, 'ktm-exc-250-2000', '90/90-21', '140/80-18'),
(107, 'ktm-exc-250-2001', '90/90-21', '140/80-18'),
(108, 'ktm-exc-250-2002', '90/90-21', '140/80-18'),
(109, 'ktm-exc-250-2004', '90/90-21', '140/80-18'),
(110, 'ktm-exc-250-2005', '90/90-21', '140/80-18'),
(111, 'ktm-exc-250-2006', '90/90-21', '140/80-18'),
(112, 'ktm-exc-250-2007', '90/90-21', '140/80-18'),
(113, 'ktm-exc-250-2008', '90/90-21', '140/80-18'),
(114, 'ktm-exc-250-2009', '90/90-21', '140/80-18'),
(115, 'ktm-exc-250-2010', '90/90-21', '140/80-18'),
(116, 'ktm-exc-250-2011', '90/90-21', '140/80-18'),
(117, 'ktm-exc-250-2012', '90/90-21', '140/80-18'),
(118, 'ktm-exc-300-2001', '90/90-21', '140/80-18'),
(119, 'ktm-exc-300-2002', '90/90-21', '140/80-18'),
(120, 'ktm-exc-300-2009', '90/90-21', '140/80-18'),
(121, 'ktm-exc-300-2010', '90/90-21', '140/80-18'),
(122, 'ktm-exc-300-2015', '90/90-21', '140/80-18'),
(123, 'ktm-exc-300-2016', '90/90-21', '140/80-18'),
(124, 'ktm-exc-300-2017', '90/90-21', '140/80-18'),
(125, 'ktm-exc-300-2018', '90/90-21', '140/80-18'),
(126, 'ktm-exc-300-2019', '90/90-21', '140/80-18'),
(127, 'ktm-exc-300-2020', '90/90-21', '140/80-18'),
(128, 'ktm-exc-300-2021', '90/90-21', '140/80-18'),
(129, 'ktm-exc-300-2022', '90/90-21', '140/80-18'),
(130, 'ktm-exc-300-2023', '90/90-21', '140/80-18'),
(131, 'ktm-exc-300-2024', '90/90-21', '140/80-18'),
(132, 'ktm-exc-300-2025', '90/90-21', '140/80-18'),
(133, 'ktm-exc-300-2026', '90/90-21', '140/80-18'),
(134, 'ktm-exc-380-1999', '90/90-21', '140/80-18'),
(135, 'ktm-exc-380-2000', '90/90-21', '140/80-18'),
(136, 'ktm-exc-380-2001', '90/90-21', '140/80-18'),
(137, 'ktm-exc-450-2003', '90/90-21', '140/80-18'),
(138, 'ktm-exc-450-2004', '90/90-21', '140/80-18'),
(139, 'ktm-exc-450-2005', '90/90-21', '140/80-18'),
(140, 'ktm-exc-450-2006', '90/90-21', '140/80-18'),
(141, 'ktm-exc-450-2007', '90/90-21', '140/80-18'),
(142, 'ktm-exc-450-2008', '90/90-21', '140/80-18'),
(143, 'ktm-exc-450-2009', '90/90-21', '140/80-18'),
(144, 'ktm-exc-450-2010', '90/90-21', '140/80-18'),
(145, 'ktm-exc-450-2011', '90/90-21', '140/80-18'),
(146, 'ktm-exc-520-2000', '90/90-21', '140/80-18'),
(147, 'ktm-exc-520-2001', '90/90-21', '140/80-18'),
(148, 'ktm-exc-520-2002', '90/90-21', '140/80-18'),
(149, 'ktm-exc-525-2005', '90/90-21', '140/80-18'),
(150, 'ktm-exc-525-2006', '90/90-21', '140/80-18'),
(151, 'ktm-exc-525-2007', '90/90-21', '140/80-18'),
(152, 'ktm-exc-525-2008', '90/90-21', '140/80-18'),
(153, 'ktm-exc-f-250-2015', '90/90-21', '140/80-18'),
(154, 'ktm-exc-f-250-2016', '90/90-21', '140/80-18'),
(155, 'ktm-exc-f-250-2017', '90/90-21', '140/80-18'),
(156, 'ktm-exc-f-250-2018', '90/90-21', '140/80-18'),
(157, 'ktm-exc-f-250-2019', '90/90-21', '140/80-18'),
(158, 'ktm-exc-f-250-2020', '90/90-21', '140/80-18'),
(159, 'ktm-exc-f-250-2021', '90/90-21', '140/80-18'),
(160, 'ktm-exc-f-250-2022', '90/90-21', '140/80-18'),
(161, 'ktm-exc-f-250-2026', '90/90-21', '140/80-18'),
(162, 'ktm-exc-f-250-six-days-2015', '90/90-21', '140/80-18'),
(163, 'ktm-exc-f-300-six-days-2015', '90/90-21', '140/80-18'),
(164, 'ktm-exc-f-350-2015', '90/90-21', '140/80-18'),
(165, 'ktm-exc-f-350-2016', '90/90-21', '140/80-18'),
(166, 'ktm-exc-f-350-2017', '90/90-21', '140/80-18'),
(167, 'ktm-exc-f-350-2018', '90/90-21', '140/80-18'),
(168, 'ktm-exc-f-350-2019', '90/90-21', '140/80-18'),
(169, 'ktm-exc-f-350-2020', '90/90-21', '140/80-18'),
(170, 'ktm-exc-f-350-2021', '90/90-21', '140/80-18'),
(171, 'ktm-exc-f-350-2022', '90/90-21', '140/80-18'),
(172, 'ktm-exc-f-350-2023', '90/90-21', '140/80-18'),
(173, 'ktm-exc-f-350-2024', '90/90-21', '140/80-18'),
(174, 'ktm-exc-f-350-2025', '90/90-21', '140/80-18'),
(175, 'ktm-exc-f-350-six-days-2018', '90/90-21', '140/80-18'),
(176, 'ktm-exc-f-350-six-days-2020', '90/90-21', '140/80-18'),
(177, 'ktm-exc-f-350-six-days-2021', '90/90-21', '140/80-18'),
(178, 'ktm-exc-f-450-2026', '90/90-21', '140/80-18'),
(179, 'ktm-exc-f-500-2026', '90/90-21', '140/80-18'),
(180, 'ktm-super-adventure-1290-2016', '120/70R19', '170/60R17'),
(181, 'ktm-super-adventure-1290-2017', '120/70R19', '170/60R17'),
(182, 'ktm-super-adventure-1290-r-2017', '90/90R21', '150/70R18'),
(183, 'ktm-super-adventure-1290-s-2017', '120/70R19', '170/60R17'),
(184, 'ktm-superduke-1290-gt-2017', '120/70-17', '190/55-17'),
(185, 'ktm-superduke-1290-r-2014', '120/70-17', '190/55-17'),
(186, 'ktm-superduke-1290-r-2015', '120/70-17', '190/55-17'),
(187, 'ktm-superduke-1290-r-2017', '120/70-17', '190/55-17'),
(188, 'ktm-sx-125-1996', '80/100-21', '100/90-19'),
(189, 'ktm-sx-125-1999', '80/100-21', '100/90-19'),
(190, 'ktm-sx-125-2000', '80/100-21', '100/90-19'),
(191, 'ktm-sx-125-2001', '80/100-21', '100/90-19'),
(192, 'ktm-sx-125-2002', '80/100-21', '100/90-19'),
(193, 'ktm-sx-125-2003', '80/100-21', '100/90-19'),
(194, 'ktm-sx-125-2004', '80/100-21', '100/90-19'),
(195, 'ktm-sx-125-2005', '80/100-21', '100/90-19'),
(196, 'ktm-sx-125-2006', '80/100-21', '100/90-19'),
(197, 'ktm-sx-125-2007', '80/100-21', '100/90-19'),
(198, 'ktm-sx-125-2008', '80/100-21', '100/90-19'),
(199, 'ktm-sx-125-2009', '80/100-21', '100/90-19'),
(200, 'ktm-sx-125-2010', '80/100-21', '100/90-19'),
(201, 'ktm-sx-250-sx-250-f-1999', '80/100-21', '100/90-19'),
(202, 'ktm-sx-250-sx-250-f-2000', '80/100-21', '100/90-19'),
(203, 'ktm-sx-250-sx-250-f-2001', '80/100-21', '100/90-19'),
(204, 'ktm-sx-250-sx-250-f-2002', '80/100-21', '100/90-19'),
(205, 'ktm-sx-250-sx-250-f-2020', '80/100-21', '110/90-19'),
(206, 'ktm-sx-250-sx-250-f-2026', '80/100-21', '110/90-19'),
(207, 'ktm-sx-300-2025', '80/100-21', '110/90-19'),
(208, 'ktm-sx-300-2026', '80/100-21', '110/90-19'),
(209, 'ktm-sx-350-350-f-2011', '80/100-21', '110/90-19'),
(210, 'ktm-sx-350-350-f-2026', '80/100-21', '110/90-19'),
(211, 'ktm-sx-450-sx-450f-2004', '80/100-21', '110/90-19'),
(212, 'ktm-sx-450-sx-450f-2005', '80/100-21', '110/90-19'),
(213, 'ktm-sx-450-sx-450f-2006', '80/100-21', '110/90-19'),
(214, 'ktm-sx-450-sx-450f-2007', '80/100-21', '110/90-19'),
(215, 'ktm-sx-450-sx-450f-2008', '80/100-21', '110/90-19'),
(216, 'ktm-sx-450-sx-450f-2009', '80/100-21', '110/90-19'),
(217, 'ktm-sx-450-sx-450f-2010', '80/100-21', '110/90-19'),
(218, 'ktm-sx-450-sx-450f-2011', '80/100-21', '110/90-19'),
(219, 'ktm-sx-450-sx-450f-2024', '80/100-21', '120/80-19'),
(220, 'ktm-sx-450-sx-450f-2025', '80/100-21', '120/80-19'),
(221, 'ktm-sx-450-sx-450f-2026', '80/100-21', '120/80-19'),
(222, 'ktm-sx-50-2006', '60/100-12', '2.75-10'),
(223, 'ktm-sx-50-2007', '60/100-12', '2.75-10'),
(224, 'ktm-sx-50-2008', '60/100-12', '2.75-10'),
(225, 'ktm-sx-50-2009', '60/100-12', '2.75-10'),
(226, 'ktm-sx-50-2010', '60/100-12', '2.75-10'),
(227, 'ktm-sx-50-2011', '60/100-12', '2.75-10'),
(228, 'ktm-sx-50-2012', '60/100-12', '2.75-10'),
(229, 'ktm-sx-65-1999', '60/100-14', '80/100-12'),
(230, 'ktm-sx-65-2000', '60/100-14', '80/100-12'),
(231, 'ktm-sx-65-2001', '60/100-14', '80/100-12'),
(232, 'ktm-sx-65-2002', '60/100-14', '80/100-12'),
(233, 'ktm-sx-65-2003', '60/100-14', '80/100-12'),
(234, 'ktm-sx-65-2004', '60/100-14', '80/100-12'),
(235, 'ktm-sx-65-2005', '60/100-14', '80/100-12'),
(236, 'ktm-sx-65-2006', '60/100-14', '80/100-12'),
(237, 'ktm-sx-65-2007', '60/100-14', '80/100-12'),
(238, 'ktm-sx-65-2008', '60/100-14', '80/100-12'),
(239, 'ktm-sx-65-2009', '60/100-14', '80/100-12'),
(240, 'ktm-sx-65-2010', '60/100-14', '80/100-12'),
(241, 'ktm-sx-65-2011', '60/100-14', '80/100-12'),
(242, 'ktm-sx-65-2025', '60/100-14', '80/100-12'),
(243, 'ktm-sx-65-2026', '60/100-14', '80/100-12'),
(244, 'ktm-sx-85-2004', '70/100-17', '90/100-14'),
(245, 'ktm-sx-85-2005', '70/100-17', '90/100-14'),
(246, 'ktm-sx-85-2006', '70/100-17', '90/100-14'),
(247, 'ktm-sx-85-2007', '70/100-17', '90/100-14'),
(248, 'ktm-sx-85-2008', '70/100-17', '90/100-14'),
(249, 'ktm-sx-85-2009', '70/100-17', '90/100-14'),
(250, 'ktm-sx-85-2010', '70/100-17', '90/100-14'),
(251, 'ktm-sx-85-2011', '70/100-17', '90/100-14'),
(252, 'ktm-sx-85-2012', '70/100-17', '90/100-14'),
(253, 'ktm-sx-85-2025', '70/100-17', '90/100-14'),
(254, 'ktm-sx-85-2026', '70/100-17', '90/100-14'),
(255, 'ktm-sx-e-2-2025', '2.50-10', '2.50-10'),
(256, 'ktm-sx-e-3-2025', '60/100-10', '2.75-10'),
(257, 'ktm-sx-e-5-2026', '60/100-12', '2.75-10'),
(258, 'ktm-sx-f-450-factory-edition-2026', '80/100-21', '120/80-19'),
(259, 'ktm-sxc-520-540-1998', '90/90-21', '140/90-18'),
(260, 'ktm-sxc-520-540-1999', '90/90-21', '140/90-18'),
(261, 'ktm-sxc-520-540-2000', '90/90-21', '140/90-18'),
(262, 'ktm-sxc-625-2004', '90/90-21', '140/90-18'),
(263, 'ktm-sxc-625-2005', '90/90-21', '140/90-18'),
(264, 'ktm-sxc-625-2006', '90/90-21', '140/90-18'),
(265, 'ktm-sxc-625-2007', '90/90-21', '140/90-18'),
(266, 'ktm-sxc-625-2008', '90/90-21', '140/90-18');

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
