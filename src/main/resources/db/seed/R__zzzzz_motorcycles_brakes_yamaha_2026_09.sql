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
--   1000ps.com, Yamaha-Ec-03.html, autoevolution.com, bennetts.co.uk, bikez.com,
--   en.wikipedia.org, ficha-tecnica.html, fichatecnica.motosblog.com.br, magodoscarros.com,
--   motonewsbrasil.com, motonline.com.br, motorcyclenews.com, motoscoot.net, pt.50factory.com,
--   revista.moto.com.br, soymotero.net, ultimatemotorcycling.com, vivendoduasrodas.com.br,
--   www.autoevolution.com, www.ebay.com, www.manualslib.com, www.motofichas.com,
--   www.motonline.com.br, www.motorcyclenews.com, www.mundomotero.com, www.rrd-preparation.com,
--   www.vferrer.com, yamaha-grizzly-350-4x4-2005.html, yamaha-neo-at-115-2007-3728.html,
--   yamaha-raptor-80-2003.html, yamaha-v-star-1300-2013.html, yamaha-x-max-125-2018.html,
--   yamaha-x-max-400-2018.html, yamaha-xt-660-x-2006.html, yamaha-xv-1600-wild-star-1999.html,
--   yamaha-xv950r-2017.html, yamaha-xvs-1100-dragstar-classic-2009.html,
--   yamaha-xvs-650-a-dragstar-clasic-2000.html, yamaha-ybr-125-custom-2008.html,
--   yamaha-yfm-700r-2008.html, yamaha-yz450f-2009.html, yamaha-yz85-2018.html
--
-- Scope of this batch: Yamaha. 288 catalogue slugs of this brand were missing front_brake or
-- rear_brake; 259 are staged below (259 carry a front value, 251 a rear, 26 an abs_type).
-- Every staged slug is still missing front_brake or rear_brake in the live catalogue.
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. A slug with no corresponding motorcycles.slug is counted and left alone rather than
-- fuzzy-matched or inserted - never insert a catalogue row here.
--
-- Generation granularity: research was done per nameplate but written per exact slug, and split by
-- year range wherever the model actually changed - 1 of the 130 nameplates in this batch carry
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
(1, 'yamaha-aerox-naked-2018', 'Disc', 'Drum', NULL),
(2, 'yamaha-aerox-race-replica-2009', 'Disc', 'Drum', NULL),
(3, 'yamaha-aerox-team-race-replica-2009', 'Disc', 'Drum', NULL),
(4, 'yamaha-aeroxr-ryc-kenny-roberts-2009', 'Disc', 'Drum', NULL),
(5, 'yamaha-aeroxr-wsb-replica-2009', 'Disc', 'Drum', NULL),
(6, 'yamaha-axis-90-1994', 'Drum', 'Drum', NULL),
(7, 'yamaha-axis-90-1995', 'Drum', 'Drum', NULL),
(8, 'yamaha-axis-90-1996', 'Drum', 'Drum', NULL),
(9, 'yamaha-axis-90-1997', 'Drum', 'Drum', NULL),
(10, 'yamaha-axis-90-1998', 'Drum', 'Drum', NULL),
(11, 'yamaha-bt-1100-bulldog-2006', 'Twin 298mm discs, 4-piston calipers', '267mm disc, 1-piston caliper', NULL),
(12, 'yamaha-bw-s-50-1995', 'Disc', 'Drum', NULL),
(13, 'yamaha-bw-s-50-1996', 'Disc', 'Drum', NULL),
(14, 'yamaha-bw-s-50-1997', 'Disc', 'Drum', NULL),
(15, 'yamaha-bw-s-50-1998', 'Disc', 'Drum', NULL),
(16, 'yamaha-bw-s-50-1999', 'Disc', 'Drum', NULL),
(17, 'yamaha-bw-s-50-2000', 'Disc', 'Drum', NULL),
(18, 'yamaha-crypton-100-1997', 'Drum', 'Drum', NULL),
(19, 'yamaha-crypton-100-1998', 'Drum', 'Drum', NULL),
(20, 'yamaha-crypton-100-1999', 'Drum', 'Drum', NULL),
(21, 'yamaha-crypton-100-2000', 'Drum', 'Drum', NULL),
(22, 'yamaha-crypton-100-2001', 'Drum', 'Drum', NULL),
(23, 'yamaha-crypton-100-2002', 'Drum', 'Drum', NULL),
(24, 'yamaha-crypton-100-2003', 'Drum', 'Drum', NULL),
(25, 'yamaha-crypton-100-2004', 'Drum', 'Drum', NULL),
(26, 'yamaha-crypton-100-2005', 'Drum', 'Drum', NULL),
(27, 'yamaha-cygnus-x-2017', 'Disc', 'Drum', NULL),
(28, 'yamaha-dt-125-re-2005', 'Disc', 'Disc', NULL),
(29, 'yamaha-dt-125-x-2005', '298mm disc', '230mm disc', NULL),
(30, 'yamaha-dt-180-z-trail-1990', 'Disc', NULL, NULL),
(31, 'yamaha-dt-180-z-trail-1991', 'Disc', NULL, NULL),
(32, 'yamaha-dt-180-z-trail-1992', 'Disc', NULL, NULL),
(33, 'yamaha-dt-180-z-trail-1993', 'Disc', NULL, NULL),
(34, 'yamaha-dt-180-z-trail-1994', 'Disc', NULL, NULL),
(35, 'yamaha-dt-180-z-trail-1995', 'Disc', NULL, NULL),
(36, 'yamaha-dt-180-z-trail-1996', 'Disc', NULL, NULL),
(37, 'yamaha-dt-180-z-trail-1997', 'Disc', NULL, NULL),
(38, 'yamaha-dt-50-r-2013', 'Disc', 'Disc', NULL),
(39, 'yamaha-dt-50-x-supermoto-2013', 'Disc', 'Disc', NULL),
(40, 'yamaha-ec-03-2018', 'Drum', 'Drum', NULL),
(41, 'yamaha-fjr1300a-2021', 'Hydraulic dual disc, 320mm, 4-piston calipers', 'Hydraulic single disc, 282mm', 'ABS with Unified Brake System (UBS)'),
(42, 'yamaha-fjr1300ae-2021', 'Hydraulic dual disc, 320mm, 4-piston calipers', 'Hydraulic single disc, 282mm', 'ABS with Unified Brake System (UBS)'),
(43, 'yamaha-fjr1300as-2021', 'Hydraulic dual disc, 320mm, 4-piston calipers', 'Hydraulic single disc, 282mm', 'ABS with Unified Brake System (UBS)'),
(44, 'yamaha-fluo-125-abs-2023', 'Disc', 'Drum', 'ABS (front wheel only)'),
(45, 'yamaha-fz-6n-2011', 'Twin 298mm ventilated discs, 2-piston monoblock calipers', '245mm disc, 1-piston caliper', NULL),
(46, 'yamaha-fz-8n-2017', '310mm discs, 4-piston caliper', '267mm disc, 1-piston caliper', NULL),
(47, 'yamaha-fz-8s-fazer-2017', '310mm discs, 4-piston caliper', '267mm disc, 1-piston caliper', NULL),
(48, 'yamaha-fz1-2015', 'Twin 320mm discs', '245mm disc', NULL),
(49, 'yamaha-fz1-fazer-2015', 'Twin 320mm discs', '245mm disc', NULL),
(50, 'yamaha-fz15-150-fazer-connected-flex-2025', '245mm disc', '130mm drum', NULL),
(51, 'yamaha-fz15-150-fazer-connected-flex-2026', '245mm disc', '130mm drum', NULL),
(52, 'yamaha-fz15-150-fazer-flex-2023', '245mm ventilated disc', '130mm drum', NULL),
(53, 'yamaha-fz15-150-fazer-flex-2024', '245mm ventilated disc', '130mm drum', NULL),
(54, 'yamaha-fz25-fazer-thor-flex-2023', 'Disc', 'Disc', NULL),
(55, 'yamaha-fzs-1000-fazer-2005', 'Twin 298mm discs', '245mm disc', NULL),
(56, 'yamaha-fzs-600-fazer-s-2008', 'Twin 298mm discs', '245mm disc', NULL),
(57, 'yamaha-giggle-2011', 'Drum', 'Drum', NULL),
(58, 'yamaha-jog-50-1993', 'Drum', 'Drum', NULL),
(59, 'yamaha-jog-50-1994', 'Drum', 'Drum', NULL),
(60, 'yamaha-jog-50-1995', 'Drum', 'Drum', NULL),
(61, 'yamaha-jog-50-1996', 'Drum', 'Drum', NULL),
(62, 'yamaha-jog-50-1997', 'Drum', 'Drum', NULL),
(63, 'yamaha-jog-50-1998', 'Drum', 'Drum', NULL),
(64, 'yamaha-jog-50-1999', 'Drum', 'Drum', NULL),
(65, 'yamaha-jog-r-2018', 'Disc', 'Drum', NULL),
(66, 'yamaha-jog-r-lorenzo-race-replica-2009', 'Disc', 'Drum', NULL),
(67, 'yamaha-jog-rr-2018', 'Disc', 'Drum', NULL),
(68, 'yamaha-jog-rr-motogp-2009', 'Disc', 'Drum', NULL),
(69, 'yamaha-jog-teen-50-1998', 'Drum', 'Drum', NULL),
(70, 'yamaha-jog-teen-50-1999', 'Drum', 'Drum', NULL),
(71, 'yamaha-jog-teen-50-2000', 'Drum', 'Drum', NULL),
(72, 'yamaha-jog-teen-50-2001', 'Drum', 'Drum', NULL),
(73, 'yamaha-jog-teen-50-2002', 'Drum', 'Drum', NULL),
(74, 'yamaha-jog-teen-50-2003', 'Drum', 'Drum', NULL),
(75, 'yamaha-jog-teen-50-2004', 'Drum', 'Drum', NULL),
(76, 'yamaha-jog-teen-50-2005', 'Drum', 'Drum', NULL),
(77, 'yamaha-majesty-400-2011', '267mm disc, 2-piston caliper', '267mm disc, 1-piston caliper', NULL),
(78, 'yamaha-majesty-400-abs-2013', '267mm disc, 2-piston caliper', '267mm disc, 1-piston caliper', 'ABS'),
(79, 'yamaha-mt-01-2013', 'Twin 320mm discs', '267mm disc', NULL),
(80, 'yamaha-mt-03-321-abs-homem-de-ferro-2022', '298mm single disc, 2-piston caliper', '220mm single disc, 1-piston caliper', 'ABS'),
(81, 'yamaha-mt-07-moto-cage-2018', '2x282mm discs, 4-piston caliper', '245mm single disc, 1-piston caliper', 'ABS'),
(82, 'yamaha-mt-09-sport-tracker-2017', '2x298mm discs, 4-piston monobloc radial calipers', '245mm single disc, 1-piston caliper', NULL),
(83, 'yamaha-mt-09-street-rally-2017', '2x298mm discs, 4-piston monobloc radial calipers', '245mm single disc, 1-piston caliper', NULL),
(84, 'yamaha-neo-at-115cc-2005', 'Disc', 'Drum', NULL),
(85, 'yamaha-neo-at-115cc-2006', 'Disc', 'Drum', NULL),
(86, 'yamaha-neo-at-115cc-2007', 'Disc', 'Drum', NULL),
(87, 'yamaha-neo-automatic-115cc-2008', 'Disc', 'Drum', NULL),
(88, 'yamaha-neo-automatic-115cc-2009', 'Disc', 'Drum', NULL),
(89, 'yamaha-neo-automatic-115cc-2010', 'Disc', 'Drum', NULL),
(90, 'yamaha-neo-automatic-115cc-2011', 'Disc', 'Drum', NULL),
(91, 'yamaha-neo-automatic-115cc-2012', 'Disc', 'Drum', NULL),
(92, 'yamaha-neo-automatic-125cc-2017', 'Single 200mm disc, 1-piston caliper', '130mm drum', NULL),
(93, 'yamaha-neo-automatic-125cc-2018', 'Single 200mm disc, 1-piston caliper', '130mm drum', NULL),
(94, 'yamaha-neo-automatic-125cc-2019', 'Single 200mm disc, 1-piston caliper', '130mm drum', NULL),
(95, 'yamaha-neo-automatic-125cc-2020', 'Single 200mm disc, 1-piston caliper', '130mm drum', NULL),
(96, 'yamaha-neo-automatic-125cc-2021', 'Single 200mm disc, 1-piston caliper', '130mm drum', NULL),
(97, 'yamaha-neo-automatic-125cc-2022', 'Single 200mm disc, 1-piston caliper', '130mm drum', NULL),
(98, 'yamaha-neo-automatic-125cc-2023', 'Single 200mm disc, 1-piston caliper', '130mm drum', NULL),
(99, 'yamaha-neo-automatic-125cc-2024', 'Single 200mm disc, 1-piston caliper', '130mm drum', NULL),
(100, 'yamaha-neo-automatic-125cc-2025', 'Single 200mm disc, 1-piston caliper', '130mm drum', NULL),
(101, 'yamaha-neo-s-2026', 'Single disc, hydraulic caliper', 'Mechanical drum brake', NULL),
(102, 'yamaha-neos-4takt-2021', 'Single 190mm disc, dual-piston caliper', '110mm drum', NULL),
(103, 'yamaha-neos-connected-eletrica-2026', 'Single disc, hydraulic caliper', 'Mechanical drum brake', NULL),
(104, 'yamaha-nmax-160-2019', 'Single 230mm disc, 1-piston caliper', 'Single 230mm disc, 1-piston caliper', 'ABS'),
(105, 'yamaha-nmax-160-2020', 'Single 230mm disc, 1-piston caliper', 'Single 230mm disc, 1-piston caliper', 'ABS'),
(106, 'yamaha-nmax-160-2021', 'Single 230mm disc, 1-piston caliper', 'Single 230mm disc, 1-piston caliper', 'ABS'),
(107, 'yamaha-nmax-160-2022', 'Single 230mm disc, 1-piston caliper', 'Single 230mm disc, 1-piston caliper', 'ABS'),
(108, 'yamaha-nmax-homem-aranha-160-abs-2022', 'Single 230mm disc, 1-piston caliper', 'Single 230mm disc, 1-piston caliper', 'ABS'),
(109, 'yamaha-nmax-star-wars-160-abs-2020', 'Single 230mm disc, 1-piston caliper', 'Single 230mm disc, 1-piston caliper', 'ABS'),
(110, 'yamaha-nmax-star-wars-160-abs-2021', 'Single 230mm disc, 1-piston caliper', 'Single 230mm disc, 1-piston caliper', 'ABS'),
(111, 'yamaha-pw50-2026', '80mm drum', '80mm drum', NULL),
(112, 'yamaha-r1-world-gp-60th-anniversary-2024', 'Dual 320mm discs, 4-piston radial calipers', 'Single 220mm disc', 'ABS'),
(113, 'yamaha-rd-135-1990', 'Front drum brake', 'Rear drum brake', NULL),
(114, 'yamaha-rd-135-1991', 'Front drum brake', 'Rear drum brake', NULL),
(115, 'yamaha-rd-135-1992', 'Front drum brake', 'Rear drum brake', NULL),
(116, 'yamaha-rd-135-1993', 'Front drum brake', 'Rear drum brake', NULL),
(117, 'yamaha-rd-135-1994', 'Front drum brake', 'Rear drum brake', NULL),
(118, 'yamaha-rd-135-1995', 'Front drum brake', 'Rear drum brake', NULL),
(119, 'yamaha-rd-135-1996', 'Front drum brake', 'Rear drum brake', NULL),
(120, 'yamaha-rd-135-1997', 'Front drum brake', 'Rear drum brake', NULL),
(121, 'yamaha-rd-135-1998', 'Front drum brake', 'Rear drum brake', NULL),
(122, 'yamaha-rd-135-1999', 'Front drum brake', 'Rear drum brake', NULL),
(123, 'yamaha-rd-135-2000', 'Front drum brake', 'Rear drum brake', NULL),
(124, 'yamaha-rdz-135-1990', 'Front drum brake', 'Rear drum brake', NULL),
(125, 'yamaha-rdz-135-1991', 'Front drum brake', 'Rear drum brake', NULL),
(126, 'yamaha-rdz-135-1992', 'Front drum brake', 'Rear drum brake', NULL),
(127, 'yamaha-road-star-warrior-2006', 'Dual 298mm discs, 4-piston calipers', 'Single 282mm disc, 1-piston caliper', NULL),
(128, 'yamaha-sr-400-2016', 'Single 298mm disc, hydraulic caliper', 'Rear drum brake', NULL),
(129, 'yamaha-t-max-500-2011', 'Dual 267mm discs, hydraulic calipers', 'Single 267mm disc', NULL),
(130, 'yamaha-t-max-530-abs-2016', 'Dual 267mm discs, hydraulic calipers', 'Single 282mm disc', 'ABS'),
(131, 'yamaha-t-max-530-black-max-2016', 'Dual 267mm discs, hydraulic calipers', 'Single 282mm disc', 'ABS'),
(132, 'yamaha-t-max-530-bronze-max-2016', 'Dual 267mm discs, hydraulic calipers', 'Single 282mm disc', 'ABS'),
(133, 'yamaha-t-max-iron-max-abs-2016', 'Dual 267mm discs, hydraulic calipers', 'Single 282mm disc', 'ABS'),
(134, 'yamaha-t115-crypton-ed-2010', 'Single 220mm disc, 1-piston caliper', '130mm drum', NULL),
(135, 'yamaha-t115-crypton-ed-2011', 'Single 220mm disc, 1-piston caliper', '130mm drum', NULL),
(136, 'yamaha-t115-crypton-ed-2012', 'Single 220mm disc, 1-piston caliper', '130mm drum', NULL),
(137, 'yamaha-t115-crypton-ed-2013', 'Single 220mm disc, 1-piston caliper', '130mm drum', NULL),
(138, 'yamaha-t115-crypton-ed-2014', 'Single 220mm disc, 1-piston caliper', '130mm drum', NULL),
(139, 'yamaha-t115-crypton-ed-2015', 'Single 220mm disc, 1-piston caliper', '130mm drum', NULL),
(140, 'yamaha-t115-crypton-ed-2016', 'Single 220mm disc, 1-piston caliper', '130mm drum', NULL),
(141, 'yamaha-t115-crypton-ed-penelope-2014', 'Single 220mm disc, 1-piston caliper', '130mm drum', NULL),
(142, 'yamaha-t115-crypton-k-2010', '110mm drum', '130mm drum', NULL),
(143, 'yamaha-t115-crypton-k-2011', '110mm drum', '130mm drum', NULL),
(144, 'yamaha-t115-crypton-k-2012', '110mm drum', '130mm drum', NULL),
(145, 'yamaha-t115-crypton-k-2013', '110mm drum', '130mm drum', NULL),
(146, 'yamaha-t115-crypton-k-2014', '110mm drum', '130mm drum', NULL),
(147, 'yamaha-t115-crypton-k-2015', '110mm drum', '130mm drum', NULL),
(148, 'yamaha-t115-crypton-k-2016', '110mm drum', '130mm drum', NULL),
(149, 'yamaha-tdm-900-2013', 'Dual 298mm discs', 'Single 245mm disc', NULL),
(150, 'yamaha-tmax-dx-2019', 'Dual 267mm discs, hydraulic calipers', 'Single 282mm disc', 'ABS'),
(151, 'yamaha-tmax-sx-2019', 'Dual 267mm discs, hydraulic calipers', 'Single 282mm disc', 'ABS'),
(152, 'yamaha-tmax-sx-sport-edition-2019', 'Dual 267mm discs, hydraulic calipers', 'Single 282mm disc', 'ABS'),
(153, 'yamaha-tracer-900-gt-2020', 'Dual 298mm discs, 4-piston calipers', 'Single 245mm disc', 'ABS'),
(154, 'yamaha-tricker-2006', 'Single 220mm disc', 'Single 203mm disc', NULL),
(155, 'yamaha-tt-250-r-2005', 'Single 245mm disc', 'Single 200mm disc', NULL),
(156, 'yamaha-tt-600-re-2005', 'Single 267mm disc, 2-piston caliper', 'Single 220mm disc, 1-piston caliper', NULL),
(157, 'yamaha-tzr-50-2017', 'Front disc brake', 'Rear disc brake', NULL),
(158, 'yamaha-tzr-race-replica-2009', 'Front disc brake', 'Rear disc brake', NULL),
(159, 'yamaha-versity-300-2005', 'Single disc brake', 'Single disc brake', NULL),
(160, 'yamaha-vity-125-2017', 'Single 154mm disc', '110mm drum', NULL),
(161, 'yamaha-vity-2008', 'Single 154mm disc', '110mm drum', NULL),
(162, 'yamaha-wr-125-x-2017', 'Single 298mm disc, 2-piston caliper', 'Single 220mm disc', NULL),
(163, 'yamaha-wr-250r-2018', 'Single 250mm disc', 'Single 230mm disc', NULL),
(164, 'yamaha-wr-250x-2018', 'Single 298mm disc, 2-piston caliper', 'Single 230mm disc', NULL),
(165, 'yamaha-x-city-125-2013', 'Single 270mm disc', 'Single 240mm disc', NULL),
(166, 'yamaha-x-city-250-2017', 'Single 270mm disc', 'Single 240mm disc', NULL),
(167, 'yamaha-x-max-250-sport-2013', 'Single 267mm disc', 'Single 240mm disc', NULL),
(168, 'yamaha-xc-125-city-2008', 'Single 154mm disc', '110mm drum', NULL),
(169, 'yamaha-xc-125-vity-2009', 'Single 154mm disc', '110mm drum', NULL),
(170, 'yamaha-xenter-150-2017', 'Single 267mm disc', '150mm drum', NULL),
(171, 'yamaha-xj6-nsv-2017', 'Dual 298mm discs', 'Single 245mm disc', NULL),
(172, 'yamaha-xjr-1300-racer-2017', 'Dual disc, 298 mm, four-piston calipers', 'Single disc, 267 mm, twin-piston caliper', NULL),
(173, 'yamaha-xmax-125-iron-max-2020', 'Single disc', 'Single disc', NULL),
(174, 'yamaha-xmax-400-2020', 'Hydraulic dual disc, 267 mm', 'Hydraulic single disc, 267 mm', NULL),
(175, 'yamaha-xmax-400-iron-max-2019', 'Hydraulic dual disc, 267 mm', 'Hydraulic single disc, 267 mm', NULL),
(176, 'yamaha-xt-1200-ze-super-tenere-raid-edition-2020', 'Dual disc, 310 mm wave discs, four-piston calipers', 'Single disc, 282 mm wave disc, single-piston caliper', 'ABS with linked brake system'),
(177, 'yamaha-xt-1200-ze-super-tenere-world-crosser-2017', 'Dual disc, 310 mm wave discs, four-piston calipers', 'Single disc, 282 mm wave disc, single-piston caliper', 'ABS with linked brake system'),
(178, 'yamaha-xt-125-r-2013', 'Single disc, 245 mm', 'Disc, 220 mm', NULL),
(179, 'yamaha-xt-125-x-2011', 'Disc, 260 mm', 'Disc, 220 mm', NULL),
(180, 'yamaha-xt-600-2008', 'Single disc, 282 mm', 'Disc, 220 mm', NULL),
(181, 'yamaha-xt-660r-2017', 'Single disc, 298 mm, dual-piston caliper', 'Disc, 245 mm, single-piston caliper', NULL),
(182, 'yamaha-xt-660x-2017', 'Single disc, 225 mm', 'Single disc, 200 mm', NULL),
(183, 'yamaha-xt1200ze-super-tenere-2021', 'Dual disc, 310 mm wave discs, four-piston calipers', 'Single disc, 282 mm wave disc, single-piston caliper', 'ABS with linked brake system'),
(184, 'yamaha-xtz-150-crosser-e-flex-2014', 'Drum, 130 mm', 'Drum, 130 mm', NULL),
(185, 'yamaha-xtz-150-crosser-e-flex-2015', 'Drum, 130 mm', 'Drum, 130 mm', NULL),
(186, 'yamaha-xtz-150-crosser-e-flex-2017', 'Drum, 130 mm', 'Drum, 130 mm', NULL),
(187, 'yamaha-xtz-150-crosser-ed-flex-2014', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(188, 'yamaha-xtz-150-crosser-ed-flex-2015', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(189, 'yamaha-xtz-150-crosser-ed-flex-2016', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(190, 'yamaha-xtz-150-crosser-ed-flex-2017', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(191, 'yamaha-xtz-150-crosser-s-flex-2018', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(192, 'yamaha-xtz-150-crosser-s-flex-2019', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(193, 'yamaha-xtz-150-crosser-s-flex-2020', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(194, 'yamaha-xtz-150-crosser-s-flex-2021', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(195, 'yamaha-xtz-150-crosser-s-flex-2022', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(196, 'yamaha-xtz-150-crosser-z-flex-2018', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(197, 'yamaha-xtz-150-crosser-z-flex-2019', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(198, 'yamaha-xtz-150-crosser-z-flex-2020', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(199, 'yamaha-xtz-150-crosser-z-flex-2021', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(200, 'yamaha-xtz-150-crosser-z-flex-2022', 'Disc, 230 mm, dual-piston caliper', 'Drum, 130 mm', NULL),
(201, 'yamaha-xtz-250x-2007', 'Disc, 245 mm', 'Disc, 203 mm', NULL),
(202, 'yamaha-xtz-250x-2008', 'Disc, 245 mm', 'Disc, 203 mm', NULL),
(203, 'yamaha-xtz-250x-2009', 'Disc, 245 mm', 'Disc, 203 mm', NULL),
(204, 'yamaha-xtz-250x-2010', 'Disc, 245 mm', 'Disc, 203 mm', NULL),
(205, 'yamaha-xtz-250x-2011', 'Disc, 245 mm', 'Disc, 203 mm', NULL),
(206, 'yamaha-xv-1600-wild-star-2005', 'Dual disc, 296 mm, two-piston calipers', 'Single disc, 200 mm, one-piston caliper', NULL),
(207, 'yamaha-xv-950-2017', 'Hydraulic single disc, 298 mm', 'Hydraulic single disc, 298 mm', NULL),
(208, 'yamaha-xv-950-r-2021', 'Hydraulic single disc, 298 mm', 'Hydraulic single disc, 298 mm', NULL),
(209, 'yamaha-xvs-1100-drag-star-classic-2007', 'Discs', 'Disc', NULL),
(210, 'yamaha-xvs-1300-a-2017', 'Dual hydraulic disc, 298 mm', 'Hydraulic disc, 298 mm', NULL),
(211, 'yamaha-xvs-1300-cfd-2017', 'Dual hydraulic disc, 298 mm', 'Hydraulic disc, 298 mm', NULL),
(212, 'yamaha-xvs-1300-custom-2017', 'Dual hydraulic disc, 298 mm', 'Hydraulic disc, 298 mm', NULL),
(213, 'yamaha-xvs-650-drag-star-classic-2007', 'Single disc, 298 mm', 'Drum, 200 mm', NULL),
(214, 'yamaha-ybr-125-custom-2017', 'Single disc, 245 mm, 2-piston calipers', 'Drum', NULL),
(215, 'yamaha-ybr-150-factor-e-flex-2016', 'Disc, 240 mm', 'Drum, 130 mm', NULL),
(216, 'yamaha-ybr-150-factor-e-flex-2017', 'Disc, 240 mm', 'Drum, 130 mm', NULL),
(217, 'yamaha-ybr-150-factor-e-flex-2018', 'Disc, 240 mm', 'Drum, 130 mm', NULL),
(218, 'yamaha-ybr-150-factor-ed-flex-2016', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(219, 'yamaha-ybr-150-factor-ed-flex-2017', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(220, 'yamaha-ybr-150-factor-ed-flex-2018', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(221, 'yamaha-ybr-150-factor-ed-flex-2019', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(222, 'yamaha-ybr-150-factor-ed-flex-2020', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(223, 'yamaha-ybr-150-factor-ed-flex-2021', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(224, 'yamaha-ybr-150-factor-ed-flex-2022', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(225, 'yamaha-ybr-250-2011', 'Single disc, 282 mm', 'Drum, 130 mm', NULL),
(226, 'yamaha-yfm-350-grizzly-350cc-2006', 'Dual hydraulic discs', 'Drum', NULL),
(227, 'yamaha-yfm-350-grizzly-350cc-2007', 'Dual hydraulic discs', 'Drum', NULL),
(228, 'yamaha-yfm-700r-686cc-2008', 'Discs', 'Disc', NULL),
(229, 'yamaha-yfm-700r-686cc-2010', 'Discs', 'Disc', NULL),
(230, 'yamaha-yfm-80-79cc-2003', 'Drum', 'Drum', NULL),
(231, 'yamaha-yfm-80-79cc-2004', 'Drum', 'Drum', NULL),
(232, 'yamaha-yfm-80-79cc-2005', 'Drum', 'Drum', NULL),
(233, 'yamaha-yfm-80-79cc-2006', 'Drum', 'Drum', NULL),
(234, 'yamaha-yfm-80-79cc-2007', 'Drum', 'Drum', NULL),
(235, 'yamaha-ys-150-fazer-ed-flex-2014', 'Disc, 245 mm, ventilated, 1-piston caliper', 'Drum, 130 mm', NULL),
(236, 'yamaha-ys-150-fazer-ed-flex-2015', 'Disc, 245 mm, ventilated, 1-piston caliper', 'Drum, 130 mm', NULL),
(237, 'yamaha-ys-150-fazer-sed-flex-2014', 'Disc, 245 mm, ventilated, 1-piston caliper', 'Drum, 130 mm', NULL),
(238, 'yamaha-ys-150-fazer-sed-flex-2015', 'Disc, 245 mm, ventilated, 1-piston caliper', 'Drum, 130 mm', NULL),
(239, 'yamaha-ys-150-fazer-sed-flex-2016', 'Disc, 245 mm, ventilated, 1-piston caliper', 'Drum, 130 mm', NULL),
(240, 'yamaha-ys-150-fazer-sed-flex-2017', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(241, 'yamaha-ys-150-fazer-sed-flex-2018', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(242, 'yamaha-ys-150-fazer-sed-flex-2019', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(243, 'yamaha-ys-150-fazer-sed-flex-2020', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(244, 'yamaha-ys-150-fazer-sed-flex-2021', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(245, 'yamaha-ys-150-fazer-sed-flex-2022', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(246, 'yamaha-ys-150-fazer-sed-flex-2023', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(247, 'yamaha-ys-150-fazer-sed-flex-2024', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(248, 'yamaha-ys-150-fazer-sed-flex-2025', 'Disc, 245 mm', 'Drum, 130 mm', NULL),
(249, 'yamaha-yz-125-lc-2020', 'Hydraulic disc', 'Hydraulic disc', NULL),
(250, 'yamaha-yz-250-f-250cc-2020', 'Hydraulic disc', 'Hydraulic disc', NULL),
(251, 'yamaha-yz-250-f-250cc-2021', 'Hydraulic disc', 'Hydraulic disc', NULL),
(252, 'yamaha-yz-250-lc-2020', 'Hydraulic disc', 'Hydraulic disc', NULL),
(253, 'yamaha-yz-85-lw-2020', 'Hydraulic single disc, 220 mm', 'Hydraulic single disc, 190 mm', NULL),
(254, 'yamaha-yz-85-lw-85cc-2020', 'Hydraulic single disc, 220 mm', 'Hydraulic single disc, 190 mm', NULL),
(255, 'yamaha-yz-85-lw-85cc-2021', 'Hydraulic single disc, 220 mm', 'Hydraulic single disc, 190 mm', NULL),
(256, 'yamaha-yz-85-lw-85cc-2022', 'Hydraulic single disc, 220 mm', 'Hydraulic single disc, 190 mm', NULL),
(257, 'yamaha-yz-85-lw-85cc-2023', 'Hydraulic single disc, 220 mm', 'Hydraulic single disc, 190 mm', NULL),
(258, 'yamaha-yz-85-lw-85cc-2025', 'Hydraulic single disc, 220 mm', 'Hydraulic single disc, 190 mm', NULL),
(259, 'yamaha-yz450f-team-replica-2009', 'Hydraulic single disc, 250 mm', 'Hydraulic single disc, 245 mm', NULL);

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
