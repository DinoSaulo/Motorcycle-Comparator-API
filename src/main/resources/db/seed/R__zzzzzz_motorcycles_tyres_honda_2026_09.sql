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
--   autoevolution.com, denniskirk.com, fichatecnica.motosblog.com.br, honda.com.br, moto.com.br,
--   motonline.com.br, motorcyclenews.com, motorcyclespecifications.com, motorcyclespecs.co.za,
--   museuhondafanclub.com.br, pulpmx.com, ultimatemotorcycling.com, ultimatespecs.com
--
-- Scope of this batch: Honda. 117 catalogue slugs of this brand were missing front_tyre or
-- rear_tyre; 97 are staged below (96 carry a front value, 97 a rear).
-- Every staged slug is still missing front_tyre or rear_tyre in the live catalogue.
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. A slug with no corresponding motorcycles.slug is counted and left alone rather than
-- fuzzy-matched or inserted - never insert a catalogue row here.
--
-- Generation granularity: research was done per nameplate but written per exact slug, and split by
-- year range wherever the model actually changed - 0 of the 42 nameplates in this batch carry
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
(1, 'honda-anf-125-innova-2011', '70/100-17', '80/90-17'),
(2, 'honda-biz-125-2006', '60/100-17', '80/100-14'),
(3, 'honda-biz-125-2007', '60/100-17', '80/100-14'),
(4, 'honda-biz-125-2008', '60/100-17', '80/100-14'),
(5, 'honda-biz-125-es-125-es-flex-2006', '60/100-17', '80/100-14'),
(6, 'honda-biz-125-ks-125-ks-flex-2006', '60/100-17', '80/100-14'),
(7, 'honda-biz-125-ks-125-ks-flex-2007', '60/100-17', '80/100-14'),
(8, 'honda-biz-125-ks-125-ks-flex-2008', '60/100-17', '80/100-14'),
(9, 'honda-biz-125-ks-125-ks-flex-2009', '60/100-17', '80/100-14'),
(10, 'honda-biz-125-ks-125-ks-flex-2010', '60/100-17', '80/100-14'),
(11, 'honda-biz-125-ks-125-ks-flex-2011', '60/100-17', '80/100-14'),
(12, 'honda-biz-125-ks-125-ks-flex-2012', '60/100-17', '80/100-14'),
(13, 'honda-c-100-biz-100-biz-ks-1998', '60/100-17', '80/100-14'),
(14, 'honda-c-100-biz-100-biz-ks-1999', '60/100-17', '80/100-14'),
(15, 'honda-c-100-biz-100-biz-ks-2000', '60/100-17', '80/100-14'),
(16, 'honda-c-100-biz-100-biz-ks-2001', '60/100-17', '80/100-14'),
(17, 'honda-c-100-biz-100-biz-ks-2002', '60/100-17', '80/100-14'),
(18, 'honda-c-100-biz-100-biz-ks-2003', '60/100-17', '80/100-14'),
(19, 'honda-c-100-biz-100-biz-ks-2004', '60/100-17', '80/100-14'),
(20, 'honda-c-100-biz-100-biz-ks-2005', '60/100-17', '80/100-14'),
(21, 'honda-c-100-biz-100-biz-ks-2013', '60/100-17', '80/100-14'),
(22, 'honda-c-100-biz-100-biz-ks-2014', '60/100-17', '80/100-14'),
(23, 'honda-c-100-biz-100-biz-ks-2015', '60/100-17', '80/100-14'),
(24, 'honda-c-100-biz-2004', '60/100-17', '80/100-14'),
(25, 'honda-c-100-biz-2005', '60/100-17', '80/100-14'),
(26, 'honda-c-100-biz-es-2004', '60/100-17', '80/100-14'),
(27, 'honda-c-100-biz-es-2005', '60/100-17', '80/100-14'),
(28, 'honda-c-100-biz-es-2006', '60/100-17', '80/100-14'),
(29, 'honda-c-100-biz-es-2012', '60/100-17', '80/100-14'),
(30, 'honda-c-100-biz-es-2013', '60/100-17', '80/100-14'),
(31, 'honda-c-100-biz-es-2014', '60/100-17', '80/100-14'),
(32, 'honda-c-100-biz-es-2015', '60/100-17', '80/100-14'),
(33, 'honda-cb-1300-2009', '130/70ZR17', '190/60ZR17'),
(34, 'honda-cb-900-f-hornet-2007', '120/70ZR17', '180/55ZR17'),
(35, 'honda-cbf-250-2007', '100/80-17', '130/70-17'),
(36, 'honda-cbf-500-2008', '120/70-17', '160/60-17'),
(37, 'honda-cbr-1100-xx-super-blackbird-2007', '120/70ZR17', '180/55ZR17'),
(38, 'honda-cbx-150-aero-1990', '80/100-18', '90/90-18'),
(39, 'honda-cbx-150-aero-1991', '80/100-18', '90/90-18'),
(40, 'honda-cbx-150-aero-1992', '80/100-18', '90/90-18'),
(41, 'honda-cbx-150-aero-1993', '80/100-18', '90/90-18'),
(42, 'honda-cg-125-fan-fan-ks-125-i-fan-2005', '80/100-18', '90/90-18'),
(43, 'honda-cg-125-fan-fan-ks-125-i-fan-2006', '80/100-18', '90/90-18'),
(44, 'honda-cg-125-fan-fan-ks-125-i-fan-2007', '80/100-18', '90/90-18'),
(45, 'honda-cg-125-fan-fan-ks-125-i-fan-2008', '80/100-18', '90/90-18'),
(46, 'honda-cg-125-fan-fan-ks-125-i-fan-2014', '80/100-18', '90/90-18'),
(47, 'honda-cg-125-fan-fan-ks-125-i-fan-2015', '80/100-18', '90/90-18'),
(48, 'honda-cg-125-fan-fan-ks-125-i-fan-2016', '80/100-18', '90/90-18'),
(49, 'honda-cg-125-fan-fan-ks-125-i-fan-2017', '80/100-18', '90/90-18'),
(50, 'honda-cg-125-fan-fan-ks-125-i-fan-2018', '80/100-18', '90/90-18'),
(51, 'honda-ch-125-r-spacy-1994', '3.50-10', '3.50-10'),
(52, 'honda-ch-125-r-spacy-1995', '3.50-10', '3.50-10'),
(53, 'honda-ch-125-r-spacy-1996', '3.50-10', '3.50-10'),
(54, 'honda-cr-250-1991', '80/100-21', '110/90-19'),
(55, 'honda-cr-250-1992', '80/100-21', '110/90-19'),
(56, 'honda-cr-250-1993', '80/100-21', '110/90-19'),
(57, 'honda-cr-250-1994', '80/100-21', '110/90-19'),
(58, 'honda-crf-150-f-2010', '70/100-19', '90/100-16'),
(59, 'honda-crf-70-f-2014', '2.50-14', '3.00-12'),
(60, 'honda-crf250-rally-2020', '3.00-21', '120/80-18'),
(61, 'honda-crf250l-2020', '3.00-21', '120/80-18'),
(62, 'honda-fes-125-pantheon-2007', '110/90x12', '130/70x12'),
(63, 'honda-fmx650-2007', '120/70-17', '150/60-17'),
(64, 'honda-fss-400-silver-wing-2008', '120/80-14', '150/70-13'),
(65, 'honda-nps-50-zoomer-2011', '120/90-10', '130/90-10'),
(66, 'honda-nrx-1800-rune-2006', '150/60-18', '180/55-17'),
(67, 'honda-nss-250-jazz-2005', '110/90-13', '130/70-12'),
(68, 'honda-nt-650v-deauville-2011', '120/70-17', '150/70-17'),
(69, 'honda-nx-150-1990', '2.75-21', '4.10-18'),
(70, 'honda-nx-150-1991', '2.75-21', '4.10-18'),
(71, 'honda-nx-150-1992', '2.75-21', '4.10-18'),
(72, 'honda-nx-150-1993', '2.75-21', '4.10-18'),
(73, 'honda-pop-100-97cc-2007', '60/100-17', '80/100-14'),
(74, 'honda-pop-100-97cc-2008', '60/100-17', '80/100-14'),
(75, 'honda-pop-100-97cc-2009', '60/100-17', '80/100-14'),
(76, 'honda-pop-100-97cc-2010', '60/100-17', '80/100-14'),
(77, 'honda-pop-100-97cc-2011', '60/100-17', '80/100-14'),
(78, 'honda-pop-100-97cc-2012', '60/100-17', '80/100-14'),
(79, 'honda-pop-100-97cc-2013', '60/100-17', '80/100-14'),
(80, 'honda-pop-100-97cc-2014', '60/100-17', '80/100-14'),
(81, 'honda-pop-100-97cc-2015', '60/100-17', '80/100-14'),
(82, 'honda-ses-125-dylan-2006', '110/90-13', '130/70-13'),
(83, 'honda-silver-wing-600-2011', '120/80-14', '150/70-13'),
(84, 'honda-st-1100-pan-european-2010', '120/70ZR18', '160/70ZR17'),
(85, 'honda-trx-420-fourtrax-fm-4x4-quadriciclo-2008', 'AT24x10-11', 'AT24x8-12'),
(86, 'honda-trx-420-fourtrax-fm-4x4-quadriciclo-2025', 'AT24x10-11', 'AT24x8-12'),
(87, 'honda-trx-420-fourtrax-fm-4x4-quadriciclo-2026', 'AT24x10-11', 'AT24x8-12'),
(88, 'honda-trx-420-fourtrax-tm-4x2-quadriciclo-2008', 'AT24x10-11', 'AT24x8-12'),
(89, 'honda-vt-125-shadow-2008', NULL, '130/90-15'),
(90, 'honda-vtr-1000-f-fire-storm-2006', '120/70ZR17', '180/55ZR17'),
(91, 'honda-vtr-1000-sp-2-2005', '120/70ZR17', '180/55ZR17'),
(92, 'honda-vtx-1300-2007', '110/90-19', '170/80-15'),
(93, 'honda-xl-650v-transalp-2007', '90/90-21', '120/90-17'),
(94, 'honda-xr-125-l-2005', '90/90-19', '110/90-17'),
(95, 'honda-xr-300l-tornado-flex-2025', '90/90-21', '120/80-18'),
(96, 'honda-xr-300l-tornado-flex-2026', '90/90-21', '120/80-18'),
(97, 'honda-xr-300l-tornado-special-edition-2026', '90/90-21', '120/80-18');

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
