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
--   1000ps.com, 50factory.com, autoevolution.com, checkcarro.com.br, denniskirk.com,
--   honda.com.br, moto.com.br, motonline.com.br, motorcycle.com, motorcyclenews.com,
--   motorcyclespecifications.com, motorcyclespecs.co.za, museuhondafanclub.com.br, pulpmx.com,
--   tecnimotos.com, ultimatespecs.com
--
-- Scope of this batch: Honda. 92 catalogue slugs of this brand were missing front_brake or
-- rear_brake; 86 are staged below (86 carry a front value, 84 a rear, 29 an abs_type).
-- Every staged slug is still missing front_brake or rear_brake in the live catalogue.
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. A slug with no corresponding motorcycles.slug is counted and left alone rather than
-- fuzzy-matched or inserted - never insert a catalogue row here.
--
-- Generation granularity: research was done per nameplate but written per exact slug, and split by
-- year range wherever the model actually changed - 0 of the 70 nameplates in this batch carry
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
(1, 'honda-biz-125-es-125-es-flex-2006', 'Drum, 130mm', 'Drum, 110mm', NULL),
(2, 'honda-biz-125-es-125-es-flex-2010', 'Drum, 130mm', 'Drum, 110mm', NULL),
(3, 'honda-biz-125-es-125-es-flex-2011', 'Drum, 130mm', 'Drum, 110mm', NULL),
(4, 'honda-biz-125-es-125-es-flex-2012', 'Drum, 130mm', 'Drum, 110mm', NULL),
(5, 'honda-biz-125-es-125-es-flex-2013', 'Drum, 130mm', 'Drum, 110mm', NULL),
(6, 'honda-biz-125-es-125-es-flex-2014', 'Drum, 130mm', 'Drum, 110mm', NULL),
(7, 'honda-biz-125-es-125-es-flex-2015', 'Drum, 130mm', 'Drum, 110mm', NULL),
(8, 'honda-cb-1100-2017', 'Dual 296mm discs, four-piston calipers', 'Single 256mm disc, single-piston caliper', 'ABS standard'),
(9, 'honda-cb-1300-2009', 'Dual 310mm discs, four-piston calipers', 'Single 256mm disc, single-piston caliper', 'ABS standard'),
(10, 'honda-cb-900-f-hornet-2007', 'Dual 296mm discs, four-piston calipers', 'Single 240mm disc, single-piston caliper', NULL),
(11, 'honda-cb1100-ex-2020', 'Dual 296mm discs, four-piston calipers', 'Single 256mm disc, single-piston caliper', 'ABS standard'),
(12, 'honda-cb1100-rs-2020', 'Dual 296mm discs, radial four-piston calipers', 'Single 256mm disc, single-piston caliper', 'ABS standard'),
(13, 'honda-cbf-1000-2013', 'Dual 296mm floating discs, two-piston calipers', 'Single 240mm disc, single-piston caliper', NULL),
(14, 'honda-cbf-1000-f-2017', 'Dual 296mm discs, three-piston calipers', 'Single 240mm disc', 'Combined ABS available'),
(15, 'honda-cbf-250-2007', 'Single 276mm disc, two-piston caliper', 'Drum, 130mm', NULL),
(16, 'honda-cbf-500-2008', 'Single 296mm disc, two-piston caliper', 'Single 240mm disc, two-piston caliper', NULL),
(17, 'honda-cbf-600-2011', 'Dual 296mm discs, two-piston calipers', 'Single 240mm disc, single-piston caliper', NULL),
(18, 'honda-cbf-600-s-2013', 'Dual 296mm discs, two-piston calipers', 'Single 240mm disc, single-piston caliper', NULL),
(19, 'honda-cbr-1100-xx-super-blackbird-2007', 'Dual 310mm discs, three-piston calipers', 'Single 256mm disc, three-piston caliper', NULL),
(20, 'honda-cbr-250-r-2017', 'Single 296mm disc', 'Single 226mm disc', 'ABS available'),
(21, 'honda-cbr-300-r-2020', 'Single 296mm disc, two-piston caliper', 'Single 220mm disc, single-piston caliper', 'ABS available'),
(22, 'honda-cbr1000rr-fireblade-sp-2-2019', 'Dual discs, Brembo four-piston radial calipers', NULL, NULL),
(23, 'honda-ch-125-r-spacy-1995', 'Drum, internal expanding shoe', 'Drum, internal expanding shoe', NULL),
(24, 'honda-ch-125-r-spacy-1996', 'Drum, internal expanding shoe', 'Drum, internal expanding shoe', NULL),
(25, 'honda-cr-250-1991', 'Single disc, twin-piston caliper', 'Single disc, single-piston caliper', NULL),
(26, 'honda-crf-150-f-2010', 'Single 240mm disc', 'Drum', NULL),
(27, 'honda-crf-70-f-2014', 'Drum', 'Drum', NULL),
(28, 'honda-ctx-700-n-2017', 'Single 320mm disc, three-piston caliper', 'Single 240mm disc, single-piston caliper', 'ABS standard'),
(29, 'honda-dn-01-2011', 'Dual 296mm full-floating discs, three-piston calipers', 'Single 276mm disc, three-piston caliper', 'Combined ABS standard'),
(30, 'honda-fes-125-pantheon-2007', 'Disc', NULL, NULL),
(31, 'honda-fes-125-s-wing-2013', 'Disc', 'Disc', 'ABS standard'),
(32, 'honda-fmx650-2007', 'Single 296mm disc, two-piston caliper', 'Single 220mm disc, single-piston caliper', NULL),
(33, 'honda-fss-400-silver-wing-2008', 'Single 276mm disc, three-piston caliper', 'Single 240mm disc, twin-piston caliper', 'CBS standard'),
(34, 'honda-gold-wing-f6c-2017', 'Dual 310mm floating discs, four-piston calipers', 'Single 316mm ventilated disc, three-piston caliper', 'ABS standard, 2-channel'),
(35, 'honda-goldwing-f6b-2017', 'Dual full-floating 296mm discs, three-piston calipers', 'Single 316mm disc, three-piston caliper', 'CBS standard'),
(36, 'honda-integra-2020', 'Single 320mm wavy disc, three-piston caliper', 'Single 240mm wavy disc, single-piston caliper', NULL),
(37, 'honda-montesa-cota-4rt-2015', 'Single disc, four-piston caliper', 'Single disc, two-piston caliper', NULL),
(38, 'honda-msx-125-2020', 'Single 220mm disc, hydraulic dual-piston caliper', 'Single 190mm disc, hydraulic dual-piston caliper', NULL),
(39, 'honda-nc700s-2013', 'Single 320mm wavy disc, three-piston caliper', 'Single 240mm wavy disc, single-piston caliper', NULL),
(40, 'honda-nc700x-2013', 'Single 320mm wavy disc, three-piston caliper', 'Single 240mm wavy disc, single-piston caliper', NULL),
(41, 'honda-nm4-vultus-2017', 'Dual 320mm wavy discs, two-piston calipers', 'Single 240mm wavy disc, two-piston caliper', 'ABS standard'),
(42, 'honda-nps-50-zoomer-2011', 'Drum', 'Drum', NULL),
(43, 'honda-nrx-1800-rune-2006', 'Dual 330mm discs', 'Single 336mm disc', 'CBS standard'),
(44, 'honda-nsc50r-2020', 'Single disc, hydraulic caliper', 'Drum', 'CBS standard'),
(45, 'honda-nss-250-jazz-2005', 'Disc', 'Disc', NULL),
(46, 'honda-nss-300-forza-2017', 'Single 256mm disc, twin-piston caliper', 'Single 256mm disc, twin-piston caliper', 'CBS standard'),
(47, 'honda-nt-650v-deauville-2011', 'Dual 296mm discs, three-piston calipers', 'Single 276mm disc, two-piston caliper', NULL),
(48, 'honda-nt700v-deauville-2016', 'Dual 296mm discs, three-piston calipers', 'Single 276mm disc, two-piston caliper', NULL),
(49, 'honda-nx-150-1990', 'Single disc', 'Drum', NULL),
(50, 'honda-nx-150-1991', 'Single disc', 'Drum', NULL),
(51, 'honda-nx-150-1992', 'Single disc', 'Drum', NULL),
(52, 'honda-nx-150-1993', 'Single disc', 'Drum', NULL),
(53, 'honda-nxr-150-bros-ks-mix-flex-2010', 'Drum, 130mm', 'Drum, 110mm', NULL),
(54, 'honda-nxr-150-bros-ks-mix-flex-2011', 'Drum, 130mm', 'Drum, 110mm', NULL),
(55, 'honda-nxr-150-bros-ks-mix-flex-2012', 'Drum, 130mm', 'Drum, 110mm', NULL),
(56, 'honda-ses-125-dylan-2006', 'Disc', 'Drum', 'CBS standard'),
(57, 'honda-sh-150i-dlx-2021', 'Single disc, two-piston caliper', 'Drum', NULL),
(58, 'honda-silver-wing-600-2011', 'Single 276mm disc, three-piston caliper', 'Single 240mm disc, twin-piston caliper', 'CBS standard, ABS optional'),
(59, 'honda-st-1100-pan-european-2010', 'Dual 296mm discs, three-piston calipers', 'Single 296mm disc, three-piston caliper', 'Linked Braking System'),
(60, 'honda-st-1300-pan-european-2017', 'Dual 310mm discs, three-piston calipers', 'Single disc, three-piston caliper', 'Linked Braking System'),
(61, 'honda-sw-t-400-2014', 'Single 276mm disc, three-piston caliper', 'Single 240mm disc, twin-piston caliper', 'CBS standard'),
(62, 'honda-sw-t600-2014', 'Single 276mm disc, three-piston caliper', 'Single 240mm disc, twin-piston caliper', 'CBS standard'),
(63, 'honda-trx-350-fourtrax-fm-quadriciclo-2004', 'Drum', 'Drum', NULL),
(64, 'honda-trx-350-fourtrax-fm-quadriciclo-2005', 'Drum', 'Drum', NULL),
(65, 'honda-trx-350-fourtrax-fm-quadriciclo-2006', 'Drum', 'Drum', NULL),
(66, 'honda-trx-350-fourtrax-fm-quadriciclo-2007', 'Drum', 'Drum', NULL),
(67, 'honda-trx-420-fourtrax-fm-4x4-quadriciclo-2008', 'Disc', 'Drum, sealed', NULL),
(68, 'honda-trx-420-fourtrax-fm-4x4-quadriciclo-2025', 'Disc', 'Drum, sealed', NULL),
(69, 'honda-vfr-800-f-2020', 'Dual 310mm discs, four-piston calipers', 'Single 256mm disc, two-piston caliper', 'ABS standard'),
(70, 'honda-vfr1200x-crosstourer-2020', 'Dual 310mm discs, three-piston calipers', 'Single 276mm disc, twin-piston caliper', 'Combined ABS standard'),
(71, 'honda-vfr1200x-crosstourer-dct-2021', 'Dual 310mm discs, three-piston calipers', 'Single 276mm disc, twin-piston caliper', 'Combined ABS standard'),
(72, 'honda-vfr800x-crossrunner-2020', 'Dual 310mm discs, four-piston radial calipers', 'Single 256mm disc, two-piston caliper', 'ABS standard'),
(73, 'honda-vision-50-2020', 'Single disc, hydraulic caliper', 'Drum', 'CBS standard'),
(74, 'honda-vt-125-shadow-2008', 'Single disc', 'Drum', NULL),
(75, 'honda-vt-1300-cx-2013', 'Single 336mm disc, twin-piston caliper', 'Single 296mm disc, single-piston caliper', NULL),
(76, 'honda-vt-750-c2-shadow-spirit-2017', 'Single 296mm disc, dual-piston caliper', 'Drum, 180mm', NULL),
(77, 'honda-vt-750-s-2014', 'Single 296mm disc, dual-piston caliper', 'Drum, 180mm', NULL),
(78, 'honda-vt-750-shadow-2017', 'Single 296mm disc, dual-piston caliper', 'Drum, 180mm', NULL),
(79, 'honda-vtr-1000-f-fire-storm-2006', 'Dual 296mm discs, four-piston calipers', 'Single 220mm disc, single-piston caliper', NULL),
(80, 'honda-vtr-1000-sp-2-2005', 'Dual discs, four-piston calipers', 'Single disc, single-piston caliper', NULL),
(81, 'honda-vtx-1300-2007', 'Single 336mm disc, twin-piston caliper', 'Single 296mm disc, single-piston caliper', NULL),
(82, 'honda-vtx-1800-2005', 'Dual 296mm discs, three-piston calipers', 'Single 316mm disc, twin-piston caliper', 'Linked Braking System'),
(83, 'honda-wave-110-2020', 'Disc', 'Drum', NULL),
(84, 'honda-xl-125-v-varadero-2014', 'Single 276mm disc, two-piston caliper', 'Single 220mm disc, single-piston caliper', NULL),
(85, 'honda-xl-650v-transalp-2007', 'Dual discs, two-piston calipers', 'Single disc, single-piston caliper', NULL),
(86, 'honda-xr-125-l-2005', 'Single disc, dual-piston caliper', 'Drum', NULL);

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
