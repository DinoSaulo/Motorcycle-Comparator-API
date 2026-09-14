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
--   15744-braking-kt02cd-w-flow-disc-husaberg-husqvarna-ktm-1991-2019.html, 2008.php, 2011.php,
--   2013.htm, autoevolution.com, bikez.com,
--   brake-disc-rear-braking-s3-wave-floating-for-ktm-620-lc4-sc-supercompetition-98-04-1-disc.html,
--   detail.html, ktm-125-exc-2000.html, ktm-125-sx-2005.html, ktm-1290-super-duke-gt-2016.html,
--   ktm-1290-super-duke-r-2016.html, ktm-200-exc-2005.html, ktm-250-exc-2005.html,
--   ktm-250-exc-f-2017.html, ktm-250-sx-2005.html, ktm-250-sx-f-2025.html, ktm-300-exc-1997.html,
--   ktm-300-sx-2025.html, ktm-350-exc-f-2017.html, ktm-350-sx-f-2023.html,
--   ktm-450-exc-racing-2005.html, ktm-450-sx-2005.html, ktm-450-sx-f-2022.html,
--   ktm-50-sx-2006.html, ktm-525-exc-racing-2003.html, ktm-625-sxc-2005.html,
--   ktm-640-lc4-adventure-1999.html, ktm-65-sx-2006.html, ktm-85-sx-1714-2006.html,
--   ktm-950-adventure-2003.html, ktm-950-adventure-s-2003.html, ktm-950-super-enduro-r-2006.html,
--   ktm-950-supermoto-2006.html, ktm-990-adventure-2006.html, ktm-990-duke-2024.html,
--   ktm-990-super-duke-2005.html, ktm-990-superduke-r-2007.html,
--   ktm-duke-200-abs-is-single-channel-12289928.html, ktm-freeride-250-r-2016.html, ktm.com,
--   motorcyclespecifications.com, technical-specifications.html, ultimatespecs.com,
--   www.autoevolution.com, www.ktm.com, www.motoracingshop.com, www.motorcycle.com,
--   www.motorcyclespecs.co.za, www.rushlane.com, www.topfun.com, www.ultimatespecs.com
--
-- Scope of this batch: Ktm. 247 catalogue slugs of this brand were missing front_brake or
-- rear_brake; 242 are staged below (242 carry a front value, 241 a rear, 27 an abs_type).
-- Every staged slug is still missing front_brake or rear_brake in the live catalogue.
--
-- Keying: joins on motorcycles.slug, exact match only, exactly like every other seed in this
-- directory. A slug with no corresponding motorcycles.slug is counted and left alone rather than
-- fuzzy-matched or inserted - never insert a catalogue row here.
--
-- Generation granularity: research was done per nameplate but written per exact slug, and split by
-- year range wherever the model actually changed - 1 of the 93 nameplates in this batch carry
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
(1, 'ktm-1190-adventure-2016', '2x 320mm discs, Brembo 4-piston radial caliper', '267mm disc, Brembo 2-piston caliper', 'Bosch 9ME combined ABS, switchable'),
(2, 'ktm-125-exc-2016', '260mm disc', '220mm disc', NULL),
(3, 'ktm-125-sx-2027', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(4, 'ktm-125-xc-w-2027', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(5, 'ktm-1290-super-adventure-2016', '2x 320mm discs, Brembo 4-piston radial caliper', '267mm disc, Brembo 2-piston fixed caliper', 'Bosch 9ME combined cornering ABS, switchable off-road mode'),
(6, 'ktm-200-duke-2021', '300mm disc, ByBre 4-piston caliper', '230mm disc, ByBre single-piston caliper', 'Bosch 10 MB two-channel ABS, switchable rear'),
(7, 'ktm-200-exc-2016', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(8, 'ktm-250-exc-f-2027', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(9, 'ktm-250-exc-f-six-days-2027', '260mm semi-floating disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(10, 'ktm-250-exc-racing-2006', '260mm disc', '220mm disc', NULL),
(11, 'ktm-250-sx-2027', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(12, 'ktm-250-sx-f-2027', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(13, 'ktm-250-xc-w-2027', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(14, 'ktm-300-exc-2027', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(15, 'ktm-300-sx-2027', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(16, 'ktm-350-exc-f-2027', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(17, 'ktm-350-exc-f-six-days-2027', '260mm semi-floating disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(18, 'ktm-350-sx-f-2027', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(19, 'ktm-400-exc-2010', '260mm disc', '220mm disc', NULL),
(20, 'ktm-450-exc-2016', '260mm disc', '220mm disc', NULL),
(21, 'ktm-450-exc-f-2027', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(22, 'ktm-450-exc-f-six-days-2027', '260mm semi-floating disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(23, 'ktm-450-sx-f-2027', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(24, 'ktm-50-sx-2027', '160mm disc', '160mm disc', NULL),
(25, 'ktm-50-sx-factory-edition-2026', '160mm disc', '160mm disc', NULL),
(26, 'ktm-500-exc-2016', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(27, 'ktm-500-exc-f-2026-2025', '260mm disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(28, 'ktm-500-exc-f-six-days-2027', '260mm semi-floating disc, Brembo caliper', '220mm disc, Brembo caliper', NULL),
(29, 'ktm-505-xc-f-2009', '260mm disc, Brembo 2-piston caliper', '220mm disc, Brembo single-piston caliper', NULL),
(30, 'ktm-525-exc-2005', '260mm disc', '220mm disc', NULL),
(31, 'ktm-525-mxc-desert-racing-2007', '260mm disc', '220mm disc', NULL),
(32, 'ktm-530-exc-2010', '260mm disc', '220mm disc', NULL),
(33, 'ktm-625-smc-2006', '320mm disc, 4-piston caliper', '220mm disc', NULL),
(34, 'ktm-625-sxc-2007', '260mm disc, dual-piston caliper', '220mm disc, single-piston caliper', NULL),
(35, 'ktm-640-duke-2-2007', '320mm disc', '220mm disc', NULL),
(36, 'ktm-640-lc4-adventure-2006', '300mm disc, 4-piston floating caliper', '220mm disc, single-piston floating caliper', NULL),
(37, 'ktm-640-lc4-enduro-2006', 'Single front disc, floating caliper', 'Single rear disc, floating caliper', NULL),
(38, 'ktm-640-lc4-supermoto-2006', '320mm disc', '220mm disc', NULL),
(39, 'ktm-660-smc-2006', '320mm fixed disc, 4-piston Brembo caliper', '220mm disc, single-piston Brembo caliper', NULL),
(40, 'ktm-690-enduro-2010', '300mm disc, hydraulic caliper', '240mm disc, hydraulic caliper', NULL),
(41, 'ktm-690-smc-2011', '320mm floating disc, radial 4-piston caliper', '240mm floating disc, single-piston caliper', NULL),
(42, 'ktm-690-supermoto-2009', '320mm disc, Brembo 4-piston radially-bolted caliper', '240mm disc, Brembo single-piston floating caliper', NULL),
(43, 'ktm-690-supermoto-limited-2010', '320mm disc, Brembo 4-piston radially-bolted caliper', '240mm disc, Brembo single-piston floating caliper', NULL),
(44, 'ktm-85-motocross-2007', '220mm disc', '200mm disc', NULL),
(45, 'ktm-890-smt-2024', '2x 320mm discs, radial-mount 4-piston calipers', '260mm disc, 2-piston floating caliper', 'Bosch 9.3 MP cornering ABS, switchable Supermoto mode'),
(46, 'ktm-950-adventure-s-2005', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo two-piston floating caliper', NULL),
(47, 'ktm-950-superenduro-r-2008', 'Dual 300 mm floating discs, Brembo two-piston caliper', 'Single 240 mm floating disc, Brembo two-piston caliper', NULL),
(48, 'ktm-950-supermoto-2007', 'Dual 305 mm discs, four-piston calipers', 'Single 240 mm disc, single-piston caliper', NULL),
(49, 'ktm-990-adventure-dakar-2011', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo two-piston floating caliper', 'Brembo two-channel ABS, standard'),
(50, 'ktm-990-adventure-r-2012', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo two-piston floating caliper', 'Bosch dual-circuit ABS, disconnectable'),
(51, 'ktm-990-adventure-s-2009', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo single-piston floating caliper', NULL),
(52, 'ktm-990-duke-2024', 'Dual 300 mm discs, four-piston radially mounted calipers', 'Single 240 mm disc, single-piston floating caliper', 'Bosch cornering ABS, Supermoto mode'),
(53, 'ktm-990-super-duke-2011', 'Dual 320 mm discs, four-piston calipers', 'Single 240 mm disc, single-piston caliper', NULL),
(54, 'ktm-990-super-duke-r-2013', 'Dual 320 mm discs, Brembo four-piston fixed caliper', 'Single 240 mm disc, Brembo single-piston floating caliper', NULL),
(55, 'ktm-990-supermoto-2010', 'Dual disc, four-piston caliper', 'Single disc, floating caliper', NULL),
(56, 'ktm-990-supermoto-r-2014', 'Dual disc, radially-mounted four-piston caliper', 'Single disc, two-piston caliper', NULL),
(57, 'ktm-990-supermoto-t-2013', 'Dual disc, radially-mounted four-piston Brembo caliper', NULL, 'Disconnectable Bosch dual-circuit ABS'),
(58, 'ktm-adventure-1190cc-2014', 'Dual disc, Brembo radially-mounted four-piston caliper', 'Single disc, Brembo two-piston caliper', 'Bosch MSC cornering ABS, standard'),
(59, 'ktm-adventure-1190cc-2015', 'Dual disc, Brembo radially-mounted four-piston caliper', 'Single disc, Brembo two-piston caliper', 'Bosch MSC cornering ABS, standard'),
(60, 'ktm-adventure-640-st-2003', 'Single 300 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(61, 'ktm-adventure-640-st-2004', 'Single 300 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(62, 'ktm-adventure-640-st-2005', 'Single 300 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(63, 'ktm-adventure-640-st-2006', 'Single 300 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(64, 'ktm-adventure-640-st-2007', 'Single 300 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(65, 'ktm-adventure-950cc-2004', 'Dual 300 mm discs, Brembo floating caliper', 'Single 240 mm disc, Brembo floating caliper', NULL),
(66, 'ktm-adventure-950cc-2005', 'Dual 300 mm discs, Brembo floating caliper', 'Single 240 mm disc, Brembo floating caliper', NULL),
(67, 'ktm-adventure-950cc-2006', 'Dual 300 mm discs, Brembo floating caliper', 'Single 240 mm disc, Brembo floating caliper', NULL),
(68, 'ktm-adventure-950cc-2007', 'Dual 300 mm discs, Brembo floating caliper', 'Single 240 mm disc, Brembo floating caliper', NULL),
(69, 'ktm-adventure-950cc-2008', 'Dual 300 mm discs, Brembo floating caliper', 'Single 240 mm disc, Brembo floating caliper', NULL),
(70, 'ktm-adventure-990cc-2006', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo two-piston floating caliper', 'Brembo two-channel ABS, standard'),
(71, 'ktm-adventure-990cc-2007', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo two-piston floating caliper', 'Brembo two-channel ABS, standard'),
(72, 'ktm-adventure-990cc-2008', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo two-piston floating caliper', 'Brembo two-channel ABS, standard'),
(73, 'ktm-adventure-990cc-2009', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo two-piston floating caliper', 'Brembo two-channel ABS, standard'),
(74, 'ktm-adventure-990cc-2010', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo two-piston floating caliper', 'Brembo two-channel ABS, standard'),
(75, 'ktm-adventure-990cc-2011', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo two-piston floating caliper', 'Brembo two-channel ABS, standard'),
(76, 'ktm-adventure-990cc-2012', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo two-piston floating caliper', 'Brembo two-channel ABS, standard'),
(77, 'ktm-adventure-r-dakar-990cc-2010', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo two-piston floating caliper', 'Brembo two-channel ABS, standard'),
(78, 'ktm-adventure-r-dakar-990cc-2011', 'Dual 300 mm discs, Brembo two-piston floating caliper', 'Single 240 mm disc, Brembo two-piston floating caliper', 'Brembo two-channel ABS, standard'),
(79, 'ktm-duke-200-abs-2018', 'Single disc, BYBRE four-piston caliper', 'Single disc, BYBRE single-piston caliper', 'Bosch single-channel ABS'),
(80, 'ktm-duke-200-abs-2019', 'Single disc, BYBRE four-piston caliper', 'Single disc, BYBRE single-piston caliper', 'Bosch single-channel ABS'),
(81, 'ktm-duke-200-abs-2020', 'Single disc, BYBRE four-piston caliper', 'Single disc, BYBRE single-piston caliper', 'Bosch single-channel ABS'),
(82, 'ktm-duke-200-abs-2021', 'Single disc, BYBRE four-piston caliper', 'Single disc, BYBRE single-piston caliper', 'Bosch single-channel ABS'),
(83, 'ktm-exc-125-2006', 'Single 260 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(84, 'ktm-exc-125-2007', 'Single 260 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(85, 'ktm-exc-125-2008', 'Single 260 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(86, 'ktm-exc-125-2010', 'Single 260 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(87, 'ktm-exc-150-2025', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(88, 'ktm-exc-200-2007', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(89, 'ktm-exc-200-2008', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(90, 'ktm-exc-200-2010', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(91, 'ktm-exc-250-1994', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(92, 'ktm-exc-250-1995', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(93, 'ktm-exc-250-1998', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(94, 'ktm-exc-250-1999', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(95, 'ktm-exc-250-2000', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(96, 'ktm-exc-250-2001', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(97, 'ktm-exc-250-2002', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(98, 'ktm-exc-250-2004', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(99, 'ktm-exc-250-2005', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(100, 'ktm-exc-250-2006', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(101, 'ktm-exc-250-2007', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(102, 'ktm-exc-250-2008', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(103, 'ktm-exc-250-2009', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(104, 'ktm-exc-250-2010', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(105, 'ktm-exc-250-2011', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(106, 'ktm-exc-250-2012', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(107, 'ktm-exc-300-2001', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(108, 'ktm-exc-300-2002', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(109, 'ktm-exc-300-2009', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(110, 'ktm-exc-300-2010', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(111, 'ktm-exc-300-2015', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(112, 'ktm-exc-300-2016', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(113, 'ktm-exc-300-2017', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(114, 'ktm-exc-300-2018', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(115, 'ktm-exc-300-2019', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(116, 'ktm-exc-300-2020', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(117, 'ktm-exc-300-2021', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(118, 'ktm-exc-300-2022', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(119, 'ktm-exc-300-2023', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(120, 'ktm-exc-300-2024', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(121, 'ktm-exc-300-2025', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(122, 'ktm-exc-300-2026', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(123, 'ktm-exc-380-1999', 'Single disc, two-piston caliper', 'Single disc, single-piston caliper', NULL),
(124, 'ktm-exc-380-2000', 'Single disc, two-piston caliper', 'Single disc, single-piston caliper', NULL),
(125, 'ktm-exc-380-2001', 'Single disc, two-piston caliper', 'Single disc, single-piston caliper', NULL),
(126, 'ktm-exc-450-2003', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(127, 'ktm-exc-450-2004', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(128, 'ktm-exc-450-2005', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(129, 'ktm-exc-450-2006', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(130, 'ktm-exc-450-2007', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(131, 'ktm-exc-450-2008', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(132, 'ktm-exc-450-2009', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(133, 'ktm-exc-450-2010', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(134, 'ktm-exc-450-2011', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(135, 'ktm-exc-520-2000', 'Single disc, two-piston caliper', 'Single disc, single-piston caliper', NULL),
(136, 'ktm-exc-520-2001', 'Single disc, two-piston caliper', 'Single disc, single-piston caliper', NULL),
(137, 'ktm-exc-520-2002', 'Single disc, two-piston caliper', 'Single disc, single-piston caliper', NULL),
(138, 'ktm-exc-525-2005', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(139, 'ktm-exc-525-2006', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(140, 'ktm-exc-525-2007', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(141, 'ktm-exc-525-2008', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(142, 'ktm-exc-f-250-2018', 'Single disc, hydraulic caliper, semi-floating rotor', 'Single disc, hydraulic caliper', NULL),
(143, 'ktm-exc-f-250-2019', 'Single disc, hydraulic caliper, semi-floating rotor', 'Single disc, hydraulic caliper', NULL),
(144, 'ktm-exc-f-250-2020', 'Single disc, hydraulic caliper, semi-floating rotor', 'Single disc, hydraulic caliper', NULL),
(145, 'ktm-exc-f-250-2021', 'Single disc, hydraulic caliper, semi-floating rotor', 'Single disc, hydraulic caliper', NULL),
(146, 'ktm-exc-f-250-2022', 'Single disc, hydraulic caliper, semi-floating rotor', 'Single disc, hydraulic caliper', NULL),
(147, 'ktm-exc-f-250-2026', 'Single disc, hydraulic caliper, semi-floating rotor', 'Single disc, hydraulic caliper', NULL),
(148, 'ktm-exc-f-350-2015', 'Single 260 mm disc, Brembo caliper, Galfer wave rotor', 'Single 220 mm disc, Brembo caliper', NULL),
(149, 'ktm-exc-f-350-2016', 'Single 260 mm disc, Brembo caliper, Galfer wave rotor', 'Single 220 mm disc, Brembo caliper', NULL),
(150, 'ktm-exc-f-350-2017', 'Single 260 mm disc, Brembo caliper, Galfer wave rotor', 'Single 220 mm disc, Brembo caliper', NULL),
(151, 'ktm-exc-f-350-2018', 'Single 260 mm disc, Brembo caliper, Galfer wave rotor', 'Single 220 mm disc, Brembo caliper', NULL),
(152, 'ktm-exc-f-350-2019', 'Single 260 mm disc, Brembo caliper, Galfer wave rotor', 'Single 220 mm disc, Brembo caliper', NULL),
(153, 'ktm-exc-f-350-2020', 'Single 260 mm disc, Brembo caliper, Galfer wave rotor', 'Single 220 mm disc, Brembo caliper', NULL),
(154, 'ktm-exc-f-350-2021', 'Single 260 mm disc, Brembo caliper, Galfer wave rotor', 'Single 220 mm disc, Brembo caliper', NULL),
(155, 'ktm-exc-f-350-2022', 'Single 260 mm disc, Brembo caliper, Galfer wave rotor', 'Single 220 mm disc, Brembo caliper', NULL),
(156, 'ktm-exc-f-350-2023', 'Single 260 mm disc, Brembo caliper, Galfer wave rotor', 'Single 220 mm disc, Brembo caliper', NULL),
(157, 'ktm-exc-f-350-2024', 'Single 260 mm disc, Brembo caliper, Galfer wave rotor', 'Single 220 mm disc, Brembo caliper', NULL),
(158, 'ktm-exc-f-350-2025', 'Single 260 mm disc, Brembo caliper, Galfer wave rotor', 'Single 220 mm disc, Brembo caliper', NULL),
(159, 'ktm-exc-f-450-2026', 'Single disc, Brembo caliper', 'Single disc, Brembo caliper', NULL),
(160, 'ktm-exc-f-500-2026', 'Single 260 mm disc, Brembo caliper', 'Single 220 mm disc, Brembo caliper', NULL),
(161, 'ktm-freeride-250-r-2017', 'Single 260 mm disc, dual-piston caliper', 'Single 210 mm disc, single-piston caliper', NULL),
(162, 'ktm-freeride-e-sm-2017', 'Single disc, dual-piston caliper', 'Single disc, single-piston caliper', NULL),
(163, 'ktm-sc-620-1998', 'Single disc, hydraulic caliper', 'Single disc, hydraulic caliper', NULL),
(164, 'ktm-superduke-1290-gt-2017', 'Dual 320 mm discs, Brembo M50 four-piston caliper', 'Single 240 mm disc, Brembo two-piston caliper', 'Bosch 9ME cornering ABS, standard'),
(165, 'ktm-superduke-1290-r-2014', 'Dual 320 mm discs, Brembo M50 four-piston monoblock caliper', 'Single 240 mm disc, Brembo caliper', 'Bosch two-channel ABS, standard'),
(166, 'ktm-superduke-1290-r-2015', 'Dual 320 mm discs, Brembo M50 four-piston monoblock caliper', 'Single 240 mm disc, Brembo caliper', 'Bosch two-channel ABS, standard'),
(167, 'ktm-superduke-1290-r-2017', 'Dual 320 mm discs, Brembo M50 four-piston monoblock caliper', 'Single 240 mm disc, Brembo caliper', 'Bosch two-channel ABS, standard'),
(168, 'ktm-sx-125-2005', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(169, 'ktm-sx-125-2006', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(170, 'ktm-sx-125-2007', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(171, 'ktm-sx-125-2008', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(172, 'ktm-sx-125-2009', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(173, 'ktm-sx-125-2010', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(174, 'ktm-sx-250-sx-250-f-1999', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(175, 'ktm-sx-250-sx-250-f-2000', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(176, 'ktm-sx-250-sx-250-f-2001', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(177, 'ktm-sx-250-sx-250-f-2002', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(178, 'ktm-sx-250-sx-250-f-2020', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(179, 'ktm-sx-250-sx-250-f-2026', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(180, 'ktm-sx-300-2025', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(181, 'ktm-sx-300-2026', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(182, 'ktm-sx-350-350-f-2011', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(183, 'ktm-sx-350-350-f-2026', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(184, 'ktm-sx-450-sx-450f-2004', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(185, 'ktm-sx-450-sx-450f-2005', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(186, 'ktm-sx-450-sx-450f-2006', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(187, 'ktm-sx-450-sx-450f-2007', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(188, 'ktm-sx-450-sx-450f-2008', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(189, 'ktm-sx-450-sx-450f-2009', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(190, 'ktm-sx-450-sx-450f-2010', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(191, 'ktm-sx-450-sx-450f-2011', 'Single 260 mm disc, hydraulic caliper', 'Single 220 mm disc, hydraulic caliper', NULL),
(192, 'ktm-sx-450-sx-450f-2024', 'Single 260 mm disc, Brembo dual-piston caliper', 'Single 220 mm disc, Brembo single-piston caliper', NULL),
(193, 'ktm-sx-450-sx-450f-2025', 'Single 260 mm disc, Brembo dual-piston caliper', 'Single 220 mm disc, Brembo single-piston caliper', NULL),
(194, 'ktm-sx-450-sx-450f-2026', 'Single 260 mm disc, Brembo dual-piston caliper', 'Single 220 mm disc, Brembo single-piston caliper', NULL),
(195, 'ktm-sx-50-1999', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(196, 'ktm-sx-50-2000', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(197, 'ktm-sx-50-2001', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(198, 'ktm-sx-50-2002', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(199, 'ktm-sx-50-2003', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(200, 'ktm-sx-50-2004', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(201, 'ktm-sx-50-2005', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(202, 'ktm-sx-50-2006', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(203, 'ktm-sx-50-2007', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(204, 'ktm-sx-50-2008', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(205, 'ktm-sx-50-2009', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(206, 'ktm-sx-50-2010', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(207, 'ktm-sx-50-2011', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(208, 'ktm-sx-50-2012', 'Single 160 mm disc, hydraulic caliper', 'Single 140 mm disc, hydraulic caliper', NULL),
(209, 'ktm-sx-65-1999', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(210, 'ktm-sx-65-2000', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(211, 'ktm-sx-65-2001', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(212, 'ktm-sx-65-2002', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(213, 'ktm-sx-65-2003', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(214, 'ktm-sx-65-2004', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(215, 'ktm-sx-65-2005', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(216, 'ktm-sx-65-2006', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(217, 'ktm-sx-65-2007', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(218, 'ktm-sx-65-2008', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(219, 'ktm-sx-65-2009', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(220, 'ktm-sx-65-2010', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(221, 'ktm-sx-65-2011', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(222, 'ktm-sx-65-2025', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(223, 'ktm-sx-65-2026', 'Single 198 mm wave disc, Formula floating caliper', 'Single 180 mm wave disc, Formula floating caliper', NULL),
(224, 'ktm-sx-85-2004', 'Single 220 mm disc, hydraulic caliper', 'Single 200 mm disc, hydraulic caliper', NULL),
(225, 'ktm-sx-85-2005', 'Single 220 mm disc, hydraulic caliper', 'Single 200 mm disc, hydraulic caliper', NULL),
(226, 'ktm-sx-85-2006', 'Single 220 mm disc, hydraulic caliper', 'Single 200 mm disc, hydraulic caliper', NULL),
(227, 'ktm-sx-85-2007', 'Single 220 mm disc, hydraulic caliper', 'Single 200 mm disc, hydraulic caliper', NULL),
(228, 'ktm-sx-85-2008', 'Single 220 mm disc, hydraulic caliper', 'Single 200 mm disc, hydraulic caliper', NULL),
(229, 'ktm-sx-85-2009', 'Single 220 mm disc, hydraulic caliper', 'Single 200 mm disc, hydraulic caliper', NULL),
(230, 'ktm-sx-85-2010', 'Single 220 mm disc, hydraulic caliper', 'Single 200 mm disc, hydraulic caliper', NULL),
(231, 'ktm-sx-85-2011', 'Single 220 mm disc, hydraulic caliper', 'Single 200 mm disc, hydraulic caliper', NULL),
(232, 'ktm-sx-85-2012', 'Single 220 mm disc, hydraulic caliper', 'Single 200 mm disc, hydraulic caliper', NULL),
(233, 'ktm-sx-e-3-2025', 'Single 160 mm disc, hydraulic caliper', 'Single 160 mm disc, hydraulic caliper', NULL),
(234, 'ktm-sx-f-450-factory-edition-2026', 'Single 260 mm disc, Brembo dual-piston caliper', 'Single 220 mm disc, Brembo single-piston caliper', NULL),
(235, 'ktm-sxc-520-540-1998', 'Single disc, hydraulic caliper', 'Single disc, hydraulic caliper', NULL),
(236, 'ktm-sxc-520-540-1999', 'Single disc, hydraulic caliper', 'Single disc, hydraulic caliper', NULL),
(237, 'ktm-sxc-520-540-2000', 'Single disc, hydraulic caliper', 'Single disc, hydraulic caliper', NULL),
(238, 'ktm-sxc-625-2004', 'Single 260 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(239, 'ktm-sxc-625-2005', 'Single 260 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(240, 'ktm-sxc-625-2006', 'Single 260 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(241, 'ktm-sxc-625-2007', 'Single 260 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL),
(242, 'ktm-sxc-625-2008', 'Single 260 mm disc, two-piston caliper', 'Single 220 mm disc, single-piston caliper', NULL);

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
