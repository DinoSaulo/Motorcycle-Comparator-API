-- Motorcycle Comparison API - list price (EUR) research, written to motorcycle_additional_specs
--
-- Purpose: back-fill a researched manufacturer/importer list price for catalogue rows that have no
-- motorcycles.price_eur. This file deliberately does NOT touch price_eur. That column already mixes
-- three different kinds of number - R__motorcycles_brazil_fipe_2026_08.sql's Brazilian FIPE used-
-- vehicle reference, R__zzzz_motorcycles_1000ps_specs_2026_09.sql's used-listing average, and a
-- handful of hand-written German ex-factory MSRPs in R__dev_seed.sql - and every ORDER BY/range
-- filter on it already compares incompatible numbers. Adding a fourth kind (a new-bike tariff) would
-- deepen that defect, not fix the gap. Instead this writes to the existing long-tail EAV table,
-- motorcycle_additional_specs, under a key ('List price (EUR)') distinct from the FIPE seed's own
-- 'Reference price (BRL)' key so the two are never confused for the same kind of number. The value is
-- visible in the API's additionalSpecs map but intentionally does not feed minPriceEur/maxPriceEur,
-- ORDER BY price_eur, or CatalogStatsRepository - it is queryable by key, not comparable as price.
--
-- Provenance: tools/price-research.json, validated by tools/validate-price-research.mjs (which this
-- generator runs and aborts on failure, rather than re-implementing its rules). Regenerate this file
-- with tools/import-price-research.mjs - every count in this header is computed by that script.
-- Sources drawn on for this batch:
--   insella.it, moto.it
--
-- Scope of this batch (staged/gap-slugs by brand): Aprilia 4/55, Benelli 8/43, Beta 19/92, Bimota
-- 2/19, Bmw 3/10, Brixton 1/7, Can 3/3, Cfmoto 2/7, Derbi 4/47, Ducati 21/70, Fantic 11/26, Fb
-- 3/25, Gas 9/77, Harley 2/21, Husqvarna 20/44, Indian 6/44, Kawasaki 17/55, Keeway 3/68, Ktm
-- 13/29, Kymco 4/95, Lambretta 1/16, Livewire 4/5, Malaguti 3/28, Moto 9/49, Motomorini 2/10, Mv
-- 27/73, Niu 15/42, Peugeot 26/254, Piaggio 9/58, Qj 3/24, Rieju 19/84, Royal 2/38, Sherco 24/76,
-- Suzuki 4/61, Swm 10/25, Sym 9/66, Tm 10/32, Triumph 24/65, Vent 1/2, Victory 3/31, Voge 2/18,
-- Yamaha 19/92, Zero 1/14, Zontes 1/19.
-- 2827 catalogue slugs had no price_eur; 383 are staged below (0 from a manufacturer/importer
-- source, 383 from a listino aggregator), 2444 left out.
-- Price backfill for this gap is retrieval-bound, not source-quality-bound: 2,824 distinct
-- nameplates for 2,827 target slugs means almost no model amortises research across model years,
-- colour variants carry no tariff of their own, and pre-2016 slugs (structurally the largest chunk
-- of what remains uncovered, see below) sit outside every current European price list. Loosening the
-- acceptance bar would buy false positives, not volume - see tools/validate-price-research.mjs.
-- Of the 2444 omitted, 997 are 2015-or-older model years with no current tariff to cite.
--
-- Price band across the 383 staged rows: EUR 1739 to 80000, median 8190.
--
-- Keying: joins on motorcycles.slug, exact match only. Write is INSERT ... ON CONFLICT (motorcycle_id,
-- spec_key) DO NOTHING, the same idempotent pattern every per-brand seed already uses for this table
-- (see R__motorcycles_harley_davidson_specs_2026_08.sql). No other seed writes the
-- 'List price (EUR)' key, so first-writer-wins carries no risk of clobbering a richer import.
--
-- Ordering: "zzzz motorcycles list price 2026 09" sorts after "...engine specs..." ('e') and before
-- "...suspension..." ('s') - the same "zzzz, research-derived, gap-fill only" family as its
-- neighbours, kept for consistency even though no other seed currently disputes this EAV key.
--
-- Idempotent and repeatable: ON CONFLICT DO NOTHING means re-running this file changes nothing once
-- it has been applied.

BEGIN;

CREATE TEMP TABLE tmp_motorcycle_list_price (
    row_no        bigint PRIMARY KEY,
    slug          varchar(160) NOT NULL,
    price_value   varchar(20)  NOT NULL,
    motorcycle_id bigint,
    CONSTRAINT uk_tmp_motorcycle_list_price_slug UNIQUE (slug)
) ON COMMIT DROP;

INSERT INTO tmp_motorcycle_list_price (row_no, slug, price_value) VALUES
(1, 'aprilia-rs-125-gp-replica-2026', '6000.00'),
(2, 'aprilia-rs4-125-2018', '5150.00'),
(3, 'aprilia-sportcity-50-one-4t-2011', '2130.00'),
(4, 'aprilia-sr-gt-replica-200-2026', '3900.00'),
(5, 'benelli-bkx-125-s-2025', '3390.00'),
(6, 'benelli-bn-600-gt-2016', '6990.00'),
(7, 'benelli-bn-600-r-2016', '6990.00'),
(8, 'benelli-leoncino-250-2021', '3790.00'),
(9, 'benelli-leoncino-500-trail-2026', '5990.00'),
(10, 'benelli-leoncino-800-2026', '7490.00'),
(11, 'benelli-tornado-900-tre-2005', '16900.00'),
(12, 'benelli-tre-k-1130-amazonas-2016', '13900.00'),
(13, 'beta-ark-ac-skull-2011', '2160.00'),
(14, 'beta-ark-lc-rr-2011', '2370.00'),
(15, 'beta-ark-lc-skull-2011', '2460.00'),
(16, 'beta-evo-125-2t-factory-2024', '8090.00'),
(17, 'beta-evo-300-2t-ss-2024', '8290.00'),
(18, 'beta-rr-2t-200-2024', '8890.00'),
(19, 'beta-rr-2t-250-2024', '9290.00'),
(20, 'beta-rr-2t-300-2024', '9490.00'),
(21, 'beta-rr-430-2024', '10090.00'),
(22, 'beta-rr-50-sport-2020', '3440.00'),
(23, 'beta-rr-motard-400-4t-2011', '8550.00'),
(24, 'beta-rr-racing-2t-250-2024', '10090.00'),
(25, 'beta-rx-250-2t-2026', '8690.00'),
(26, 'beta-sincro-2t-125-2026', '7990.00'),
(27, 'beta-sincro-2t-250-2026', '8090.00'),
(28, 'beta-sincro-2t-300-ss-2026', '8390.00'),
(29, 'beta-sincro-factory-2t-125-2026', '8190.00'),
(30, 'beta-sincro-factory-2t-250-2026', '8490.00'),
(31, 'beta-sincro-factory-2t-300-2026', '8790.00'),
(32, 'bimota-kb998-rimini-2026', '44000.00'),
(33, 'bimota-tesi-3d-2015', '32330.00'),
(34, 'bmw-g-450-x-2011', '8890.00'),
(35, 'bmw-hp4-race-2019', '80000.00'),
(36, 'bmw-r-12-s-2026', '23750.00'),
(37, 'brixton-bx-125-haycroft-2019', '2999.00'),
(38, 'can-am-origin-2025', '13899.00'),
(39, 'can-am-pulse-2025', '13299.00'),
(40, 'can-am-pulse-73-2025', '15699.00'),
(41, 'cfmoto-450sr-world-champions-replica-2026', '6790.00'),
(42, 'cfmoto-700cl-x-adventure-2024', '6990.00'),
(43, 'derbi-gp1-250-2011', '3840.00'),
(44, 'derbi-rambla-250-2011', '3670.00'),
(45, 'derbi-senda-r-125-baja-2011', '2900.00'),
(46, 'derbi-senda-sm-drd-evo-2011', '3190.00'),
(47, 'ducati-1299-panigale-r-final-edition-2020', '39900.00'),
(48, 'ducati-749-2005', '13300.00'),
(49, 'ducati-749-dark-2006', '12000.00'),
(50, 'ducati-749-s-2006', '14700.00'),
(51, 'ducati-959-panigale-corse-2019', '19190.00'),
(52, 'ducati-999-2006', '17000.00'),
(53, 'ducati-999r-2006', '30000.00'),
(54, 'ducati-999s-2006', '21000.00'),
(55, 'ducati-desertx-discovery-2025', '19990.00'),
(56, 'ducati-desmo450-mx-factory-2026', '14990.00'),
(57, 'ducati-diavel-1260-lamborghini-2021', '31990.00'),
(58, 'ducati-diavel-titanium-2015', '28740.00'),
(59, 'ducati-hypermotard-698-mono-nera-2026', '13990.00'),
(60, 'ducati-multistrada-1100-2009', '12000.00'),
(61, 'ducati-multistrada-1100-s-2009', '13700.00'),
(62, 'ducati-multistrada-620-2006', '8800.00'),
(63, 'ducati-panigale-v4-lamborghini-2026', '74400.00'),
(64, 'ducati-panigale-v4-sp2-30-anniversario-916-2024', '44000.00'),
(65, 'ducati-scrambler-flat-track-pro-2016', '10950.00'),
(66, 'ducati-st-4-s-2005', '13200.00'),
(67, 'ducati-streetfighter-v4-lamborghini-2024', '63000.00'),
(68, 'fantic-caballero-deluxe-700-2026', '8990.00'),
(69, 'fantic-caballero-flat-track-250-2021', '6300.00'),
(70, 'fantic-caballero-rally-125-2026', '5990.00'),
(71, 'fantic-caballero-travel-700-2026', '9490.00'),
(72, 'fantic-scrambler-700-2025', '9290.00'),
(73, 'fantic-stealth-500-2026', '6490.00'),
(74, 'fantic-xe-50-competition-2026', '4490.00'),
(75, 'fantic-xe-50-performance-2026', '3940.00'),
(76, 'fantic-xef-125-performance-2026', '5090.00'),
(77, 'fantic-xef-310-2026', '11390.00'),
(78, 'fantic-xef-450-2026', '12290.00'),
(79, 'fb-mondial-smx-enduro-125-2026', '3490.00'),
(80, 'fb-mondial-smx-enduro-300-2026', '3990.00'),
(81, 'fb-mondial-smx-motard-125-2026', '3490.00'),
(82, 'gas-gas-ec-200-2020', '8790.00'),
(83, 'gas-gas-mc-250f-factory-edition-2025', '13290.00'),
(84, 'gas-gas-txt-125-pro-2014', '6100.00'),
(85, 'gas-gas-txt-125-pro-racing-2017', '6800.00'),
(86, 'gas-gas-txt-250-pro-racing-2017', '7100.00'),
(87, 'gas-gas-txt-80-racing-2020', '6500.00'),
(88, 'gas-gas-txt-gp-125-2021', '7650.00'),
(89, 'gas-gas-xc-250-2020', '8700.00'),
(90, 'gas-gas-xc-300-2020', '8800.00'),
(91, 'harley-davidson-sportster-s-2026', '16600.00'),
(92, 'harley-davidson-street-glide-limited-2026', '35900.00'),
(93, 'husqvarna-cr-125-2013', '6660.00'),
(94, 'husqvarna-fc-250-factory-edition-2025', '13390.00'),
(95, 'husqvarna-fc-450-factory-edition-2025', '14150.00'),
(96, 'husqvarna-sm-125-2009', '4590.00'),
(97, 'husqvarna-sm-450-r-2011', '8900.00'),
(98, 'husqvarna-sm-450-rr-2009', '13990.00'),
(99, 'husqvarna-sm-510r-2011', '9250.00'),
(100, 'husqvarna-sm-630-2013', '7490.00'),
(101, 'husqvarna-tc-449-2013', '8680.00'),
(102, 'husqvarna-tc-450-2011', '8190.00'),
(103, 'husqvarna-tc-510-2009', '8174.00'),
(104, 'husqvarna-te-310-2013', '8780.00'),
(105, 'husqvarna-te-449-2013', '8990.00'),
(106, 'husqvarna-te-511-2013', '9090.00'),
(107, 'husqvarna-te-610-2009', '7482.00'),
(108, 'husqvarna-tr-650-terra-2013', '6290.00'),
(109, 'husqvarna-tx-125-2019', '8145.00'),
(110, 'husqvarna-wr-250-2013', '7260.00'),
(111, 'husqvarna-wr-300-2013', '7570.00'),
(112, 'husqvarna-wre-125-2013', '4650.00'),
(113, 'indian-chief-vintage-125th-anniversary-edition-2026', '24990.00'),
(114, 'indian-chieftain-classic-2020', '30490.00'),
(115, 'indian-chieftain-elite-2021', '37990.00'),
(116, 'indian-ftr-s-2021', '16490.00'),
(117, 'indian-roadmaster-classic-2019', '30990.00'),
(118, 'indian-scout-sixty-2021', '12790.00'),
(119, 'kawasaki-j125-2020', '4690.00'),
(120, 'kawasaki-kx-85-i-2025', '5490.00'),
(121, 'kawasaki-kx250x-2026', '10140.00'),
(122, 'kawasaki-kx450x-2026', '11040.00'),
(123, 'kawasaki-kx65-2026', '4240.00'),
(124, 'kawasaki-ninja-h2-2020', '29700.00'),
(125, 'kawasaki-ninja-h2-carbon-2020', '32700.00'),
(126, 'kawasaki-ninja-h2r-2026', '56000.00'),
(127, 'kawasaki-ninja-zx-10r-se-2020', '23890.00'),
(128, 'kawasaki-vn-1700-classic-2014', '14300.00'),
(129, 'kawasaki-vn-1700-classic-tourer-2014', '16730.00'),
(130, 'kawasaki-vn-1700-voyager-2014', '20360.00'),
(131, 'kawasaki-vn-900-classic-special-edition-2014', '8850.00'),
(132, 'kawasaki-w800-cafe-2021', '10640.00'),
(133, 'kawasaki-z1000-r-2020', '14290.00'),
(134, 'kawasaki-z250sl-2015', '4290.00'),
(135, 'kawasaki-zzr-1400-performance-sport-2017', '19790.00'),
(136, 'keeway-k-light-125-2024', '2390.00'),
(137, 'keeway-logik-125-2018', '2090.00'),
(138, 'keeway-rkf-125-2025', '2990.00'),
(139, 'ktm-1190-rc8-r-track-2013', '17795.00'),
(140, 'ktm-150-exc-2025', '10750.00'),
(141, 'ktm-200-exc-2016', '8250.00'),
(142, 'ktm-250-exc-racing-2006', '7850.00'),
(143, 'ktm-250-sx-f-factory-edition-2025', '13290.00'),
(144, 'ktm-250-sx-f-prado-2020', '10725.00'),
(145, 'ktm-450-rally-2021', '31500.00'),
(146, 'ktm-450-rally-replica-2027', '31500.00'),
(147, 'ktm-500-exc-2016', '10150.00'),
(148, 'ktm-640-lc4-enduro-2006', '7920.00'),
(149, 'ktm-950-adventure-s-2005', '12980.00'),
(150, 'ktm-freeride-e-sm-2017', '11959.00'),
(151, 'ktm-freeride-e-sx-2017', '11360.00'),
(152, 'kymco-agility-carry-125-2021', '2290.00'),
(153, 'kymco-movie-125-2007', '2045.00'),
(154, 'kymco-visar-125i-2020', '2490.00'),
(155, 'kymco-xciting-500i-2011', '5399.00'),
(156, 'lambretta-v125-special-pirelli-edition-2020', '3499.00'),
(157, 'livewire-s2-alpinista-2025', '13620.00'),
(158, 'livewire-s2-del-mar-2026', '12590.00'),
(159, 'livewire-s2-del-mar-le-2023', '12590.00'),
(160, 'livewire-s2-mulholland-2026', '12590.00'),
(161, 'malaguti-madison-125-2026', '3499.00'),
(162, 'malaguti-spidermax-gt-500-2005', '5990.00'),
(163, 'malaguti-xsm-50-2026', '3999.00'),
(164, 'moto-guzzi-california-ev-touring-2005', '13590.00'),
(165, 'moto-guzzi-nevada-750-2005', '6470.00'),
(166, 'moto-guzzi-nevada-750-anniversario-2013', '8790.00'),
(167, 'moto-guzzi-norge-1200-2007', '13500.00'),
(168, 'moto-guzzi-v11-le-mans-rosso-corsa-2005', '12390.00'),
(169, 'moto-guzzi-v7-iii-racer-2020', '11170.00'),
(170, 'moto-guzzi-v7-iii-stone-night-pack-2020', '8620.00'),
(171, 'moto-guzzi-v7-iii-stone-s-2020', '9320.00'),
(172, 'moto-guzzi-v7-racer-2014', '10220.00'),
(173, 'motomorini-corsaro-1200-2011', '12290.00'),
(174, 'motomorini-corsaro-1200-avio-2011', '10770.00'),
(175, 'mv-agusta-brutale-1000-abt-2026', '40990.00'),
(176, 'mv-agusta-brutale-1000-rr-assen-2025', '46399.00'),
(177, 'mv-agusta-brutale-1090-r-2015', '15010.00'),
(178, 'mv-agusta-brutale-800-rc-2019', '19990.00'),
(179, 'mv-agusta-brutale-800-rosso-2023', '15950.00'),
(180, 'mv-agusta-brutale-800-rr-america-2019', '19590.00'),
(181, 'mv-agusta-dragster-800-rosso-2023', '17950.00'),
(182, 'mv-agusta-dragster-800-rr-america-2019', '19590.00'),
(183, 'mv-agusta-dragster-800-rr-pirelli-2019', '20390.00'),
(184, 'mv-agusta-dragster-800-rr-scs-2025', '19000.00'),
(185, 'mv-agusta-f3-675-rc-2019', '18990.00'),
(186, 'mv-agusta-f3-800-r-2024', '19500.00'),
(187, 'mv-agusta-f3-800-rc-2024', '23000.00'),
(188, 'mv-agusta-f3-800-rosso-2023', '19500.00'),
(189, 'mv-agusta-f4-1000-ago-2005', '29500.00'),
(190, 'mv-agusta-f4-1000-r-2013', '20160.00'),
(191, 'mv-agusta-f4-1000-r312-2009', '20890.00'),
(192, 'mv-agusta-f4-1000-rr-2013', '25200.00'),
(193, 'mv-agusta-f4-1000-tamburini-2006', '42825.00'),
(194, 'mv-agusta-f4-1078-rr312-2010', '21190.00'),
(195, 'mv-agusta-f4-750-s-1-1-2005', '16836.00'),
(196, 'mv-agusta-lxp-orioli-2026', '30600.00'),
(197, 'mv-agusta-rush-1000-2026', '51500.00'),
(198, 'mv-agusta-superveloce-1000-serie-oro-2026', '70700.00'),
(199, 'mv-agusta-turismo-veloce-800-rc-2019', '22390.00'),
(200, 'mv-agusta-turismo-veloce-800-rc-scs-2024', '21000.00'),
(201, 'mv-agusta-turismo-veloce-800-rosso-2023', '18250.00'),
(202, 'niu-fqi-500-2026', '3799.00'),
(203, 'niu-fqix-150-2026', '2499.00'),
(204, 'niu-fqix-300-2026', '2999.00'),
(205, 'niu-mqi-gt-45-2025', '3999.00'),
(206, 'niu-mqi-gt-70-2025', '3999.00'),
(207, 'niu-n-pro-2019', '4499.00'),
(208, 'niu-nqi-gt-cargo-er-2024', '5149.00'),
(209, 'niu-nqi-gt-cargo-sr-2024', '4249.00'),
(210, 'niu-nqix-150-2026', '2699.00'),
(211, 'niu-nqix-300-2026', '3599.00'),
(212, 'niu-nqix-500-2026', '4499.00'),
(213, 'niu-rqi-sport-2026', '7499.00'),
(214, 'niu-u-pro-2019', '1899.00'),
(215, 'niu-xqi3-street-2026', '4499.00'),
(216, 'niu-xqi3-wild-2026', '4499.00'),
(217, 'peugeot-belville-125-allure-2020', '3149.00'),
(218, 'peugeot-django-125-classic-dark-2025', '3599.00'),
(219, 'peugeot-django-125-classic-hot-color-2025', '3799.00'),
(220, 'peugeot-django-125-classic-shadow-2025', '3599.00'),
(221, 'peugeot-django-50-4t-allure-2018', '3090.00'),
(222, 'peugeot-kisbee-50-black-edition-2025', '2149.00'),
(223, 'peugeot-kisbee-50-gt-2025', '2149.00'),
(224, 'peugeot-kisbee-50-rs-2t-2020', '1948.00'),
(225, 'peugeot-kisbee-50-shadow-2025', '2199.00'),
(226, 'peugeot-kisbee-50-streetline-2024', '2199.00'),
(227, 'peugeot-kisbee-m-top-case-2026', '2149.00'),
(228, 'peugeot-kisbee-s-naked-2026', '2099.00'),
(229, 'peugeot-kisbee-se-2026', '3299.00'),
(230, 'peugeot-pulsion-125-active-2025', '4999.00'),
(231, 'peugeot-pulsion-125-allure-2024', '5299.00'),
(232, 'peugeot-pulsion-evo-125-2026', '3699.00'),
(233, 'peugeot-pulsion-evo-125-urban-2026', '4099.00'),
(234, 'peugeot-tweet-125-rs-2022', '2899.00'),
(235, 'peugeot-tweet-150-2020', '2649.00'),
(236, 'peugeot-tweet-150-rs-2018', '2530.00'),
(237, 'peugeot-tweet-200-active-2024', '3099.00'),
(238, 'peugeot-tweet-200-gt-2024', '3199.00'),
(239, 'peugeot-xp-400-allure-2026', '6499.00'),
(240, 'peugeot-xp6-enduro-r-2026', '3799.00'),
(241, 'peugeot-xp6-supermotard-2026', '3399.00'),
(242, 'peugeot-xp6-supermotard-r-2026', '3799.00'),
(243, 'piaggio-beverly-25th-anniversary-2026', '6050.00'),
(244, 'piaggio-fly-50-2t-2018', '2049.00'),
(245, 'piaggio-liberty-150-2017', '2610.00'),
(246, 'piaggio-medley-200-2026', '3600.00'),
(247, 'piaggio-mp3-350-2018', '8720.00'),
(248, 'piaggio-mp3-hybrid-125-2012', '9000.00'),
(249, 'piaggio-mp3-touring-400-lt-2011', '7560.00'),
(250, 'piaggio-x7-125-2011', '3860.00'),
(251, 'piaggio-zip-50-2t-2018', '1739.00'),
(252, 'qj-motor-atr-125-x-2026', '3200.00'),
(253, 'qj-motor-cov-125x-2026', '3390.00'),
(254, 'qj-motor-srv-550-2024', '5990.00'),
(255, 'rieju-aventura-500-2026', '7920.00'),
(256, 'rieju-marathon-125-europa-2026', '4499.00'),
(257, 'rieju-marathon-125-pro-2026', '5050.00'),
(258, 'rieju-mrt-50-pro-2026', '4280.00'),
(259, 'rieju-rs-sport-50-2018', '2599.00'),
(260, 'rieju-rs2-50-matrix-2010', '3090.00'),
(261, 'rieju-rs3-50-2020', '3825.00'),
(262, 'rieju-rs3-nkd-50-2020', '3745.00'),
(263, 'rieju-scrambler-125-2020', '3927.00'),
(264, 'rieju-strada-125-2020', '2190.00'),
(265, 'rieju-strada-gt-125-2020', '2190.00'),
(266, 'rieju-tango-125-2020', '3840.00'),
(267, 'rieju-tango-125i-2026', '3840.00'),
(268, 'rieju-xplora-557-2025', '5999.00'),
(269, 'rieju-xplora-557-s-2026', '5999.00'),
(270, 'rieju-xplora-557-x-2026', '6399.00'),
(271, 'rieju-xplora-707-2025', '7099.00'),
(272, 'rieju-xplora-707-s-2026', '6699.00'),
(273, 'rieju-xplora-707-x-2026', '7099.00'),
(274, 'royal-enfield-bullet-350-2026', '5200.00'),
(275, 'royal-enfield-guerrilla-450-apex-2026', '5490.00'),
(276, 'sherco-125-se-factory-2026', '9990.00'),
(277, 'sherco-125-se-six-days-2018', '8550.00'),
(278, 'sherco-125-st-2016', '6686.00'),
(279, 'sherco-125-ty-adventure-2024', '4990.00'),
(280, 'sherco-2-5i-racing-2013', '9160.00'),
(281, 'sherco-250-se-2015', '8499.00'),
(282, 'sherco-250-se-factory-2026', '11500.00'),
(283, 'sherco-250-se-six-days-2018', '9650.00'),
(284, 'sherco-250-sef-2015', '9295.00'),
(285, 'sherco-250-sef-factory-2026', '12600.00'),
(286, 'sherco-250-sef-six-days-2018', '10500.00'),
(287, 'sherco-290-st-2015', '7092.00'),
(288, 'sherco-3-0i-racing-2013', '9300.00'),
(289, 'sherco-300-se-2015', '8699.00'),
(290, 'sherco-300-se-racing-2026', '10500.00'),
(291, 'sherco-300-se-six-days-2018', '9780.00'),
(292, 'sherco-300-sef-2015', '9439.00'),
(293, 'sherco-300-sef-six-days-2018', '10700.00'),
(294, 'sherco-450-sef-2015', '9750.00'),
(295, 'sherco-450-sef-factory-2026', '13240.00'),
(296, 'sherco-450-sef-six-days-2018', '10900.00'),
(297, 'sherco-80-st-2016', '4697.00'),
(298, 'sherco-se-50-r-2018', '3310.00'),
(299, 'sherco-xy-125-2016', '4394.00'),
(300, 'suzuki-dr-125-sm-2010', '3410.00'),
(301, 'suzuki-gsx-r1000r-2026', '20490.00'),
(302, 'suzuki-rm-125-2011', '5940.00'),
(303, 'suzuki-rm-85-2013', '4501.00'),
(304, 'swm-hoku-125-2026', '2790.00'),
(305, 'swm-hoku-400-2026', '4290.00'),
(306, 'swm-rs-125-r-2026', '3690.00'),
(307, 'swm-rs-300-r-2026', '6490.00'),
(308, 'swm-rs-500-r-2026', '6990.00'),
(309, 'swm-rs-650-r-2018', '6500.00'),
(310, 'swm-silver-vase-440-2018', '5490.00'),
(311, 'swm-silver-vase-650-2026', '4690.00'),
(312, 'swm-silver-vase-t-650-2026', '4990.00'),
(313, 'swm-venturo-125-2026', '3190.00'),
(314, 'sym-clbcu-50-2026', '1999.00'),
(315, 'sym-fiddle-125-2026', '2799.00'),
(316, 'sym-joymax-z-300-2026', '4399.00'),
(317, 'sym-joyride-125-2021', '3999.00'),
(318, 'sym-nh-r-125-2026', '2799.00'),
(319, 'sym-symphony-50-2026', '2299.00'),
(320, 'sym-symphony-sr-125-2026', '2299.00'),
(321, 'sym-symphony-st-50-2018', '1965.00'),
(322, 'sym-ttlbt-2026', '9999.00'),
(323, 'tm-en-125-2013', '7400.00'),
(324, 'tm-en-144-2013', '7600.00'),
(325, 'tm-en-250-2013', '7765.00'),
(326, 'tm-en-300-2013', '7865.00'),
(327, 'tm-mx-125-2013', '7300.00'),
(328, 'tm-mx-144-2013', '7500.00'),
(329, 'tm-mx-250-2013', '7620.00'),
(330, 'tm-mx-300-2013', '7730.00'),
(331, 'tm-mx-85-junior-2013', '5250.00'),
(332, 'tm-smr-125-2013', '7150.00'),
(333, 'triumph-bonneville-newchurch-2016', '8890.00'),
(334, 'triumph-bonneville-t100-chrome-edition-2023', '11945.00'),
(335, 'triumph-bonneville-t100-icon-edition-2025', '12195.00'),
(336, 'triumph-bonneville-t214-2015', '10290.00'),
(337, 'triumph-daytona-955i-2006', '11990.00'),
(338, 'triumph-rocket-3-tfc-2020', '29000.00'),
(339, 'triumph-scrambler-1200-steve-mcqueen-edition-2021', '16800.00'),
(340, 'triumph-scrambler-1200-x-icon-edition-2025', '15995.00'),
(341, 'triumph-scrambler-1200-xe-icon-edition-2025', '17595.00'),
(342, 'triumph-scrambler-900-icon-edition-2025', '11995.00'),
(343, 'triumph-speed-twin-900-chrome-edition-2023', '9695.00'),
(344, 'triumph-speed-twin-breitling-limited-edition-2023', '19000.00'),
(345, 'triumph-street-scrambler-sandstorm-edition-2021', '11500.00'),
(346, 'triumph-thruxton-tfc-2020', '22500.00'),
(347, 'triumph-thunderbird-nightstorm-2015', '17850.00'),
(348, 'triumph-tiger-1200-xcx-2020', '19100.00'),
(349, 'triumph-tiger-1200-xr-2020', '16100.00'),
(350, 'triumph-tiger-800-xr-2019', '11400.00'),
(351, 'triumph-tiger-900-bond-edition-2023', '19200.00'),
(352, 'triumph-tiger-explorer-xca-2017', '19700.00'),
(353, 'triumph-tiger-explorer-xcx-2017', '17900.00'),
(354, 'triumph-tiger-explorer-xr-2017', '15300.00'),
(355, 'triumph-tiger-sport-800-tour-2026', '14195.00'),
(356, 'triumph-txp-24-2026', '4395.00'),
(357, 'vent-baja-rr-50-2023', '4398.00'),
(358, 'victory-hammer-8-ball-2016', '15990.00'),
(359, 'victory-kingpin-8-ball-2011', '14290.00'),
(360, 'victory-vegas-8-ball-2016', '13790.00'),
(361, 'voge-500r-2024', '5890.00'),
(362, 'voge-650dsx-2024', '6390.00'),
(363, 'yamaha-dt-125-re-2005', '3790.00'),
(364, 'yamaha-dt-125-x-2005', '3990.00'),
(365, 'yamaha-majesty-400-2011', '5790.00'),
(366, 'yamaha-majesty-400-abs-2013', '6290.00'),
(367, 'yamaha-sr-400-2016', '5990.00'),
(368, 'yamaha-tenere-700-world-rally-2024', '14399.00'),
(369, 'yamaha-tmax-sx-2019', '12090.00'),
(370, 'yamaha-tmax-sx-sport-edition-2019', '12990.00'),
(371, 'yamaha-tricity-155-2024', '4999.00'),
(372, 'yamaha-tricity-300-airbag-2026', '10299.00'),
(373, 'yamaha-tt-r125-2025', '4399.00'),
(374, 'yamaha-vity-2008', '2190.00'),
(375, 'yamaha-x-max-250-sport-2013', '5090.00'),
(376, 'yamaha-xenter-125-2020', '2899.00'),
(377, 'yamaha-xenter-150-2017', '2990.00'),
(378, 'yamaha-xjr-1300-racer-2017', '11590.00'),
(379, 'yamaha-yz125-2027', '8499.00'),
(380, 'yamaha-yz250f-70th-anniversary-edition-2026', '10399.00'),
(381, 'yamaha-yz450f-70th-anniversary-edition-2026', '11299.00'),
(382, 'zero-dsr-2025', '19890.00'),
(383, 'zontes-zt368-d-etc-2026', '5090.00');

-- ---------------------------------------------------------------------------
-- Resolve every staged row against the live catalogue. An exact slug match only: a slug with no
-- corresponding motorcycles.slug is counted and left alone rather than fuzzy-matched or inserted -
-- never insert a catalogue row here.
-- ---------------------------------------------------------------------------
UPDATE tmp_motorcycle_list_price t
SET motorcycle_id = m.id
FROM motorcycles m
WHERE m.slug = t.slug;

-- First writer wins: no other seed claims the 'List price (EUR)' key, so this is safe to run
-- anywhere in the ordering, but ON CONFLICT DO NOTHING still makes a re-run idempotent.
INSERT INTO motorcycle_additional_specs (motorcycle_id, spec_key, spec_value)
SELECT motorcycle_id, 'List price (EUR)', price_value
FROM tmp_motorcycle_list_price
WHERE motorcycle_id IS NOT NULL
ON CONFLICT (motorcycle_id, spec_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Defensive checks before commit.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad        bigint;
    unresolved bigint;
    missing    bigint;
BEGIN
    SELECT count(*) INTO bad
    FROM tmp_motorcycle_list_price
    WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$';
    IF bad <> 0 THEN
        RAISE EXCEPTION 'Motorcycle list price backfill: % staged slugs are not a shape the public routing can use', bad;
    END IF;

    SELECT count(*) INTO unresolved FROM tmp_motorcycle_list_price WHERE motorcycle_id IS NULL;
    RAISE NOTICE 'Motorcycle list price backfill: % of % staged rows matched a catalogue slug, % left unresolved',
        (SELECT count(*) FROM tmp_motorcycle_list_price WHERE motorcycle_id IS NOT NULL),
        (SELECT count(*) FROM tmp_motorcycle_list_price), unresolved;

    -- Proves ON CONFLICT DO NOTHING never silently swallowed a real insert failure: every resolved
    -- row must have a matching 'List price (EUR)' row now, whether this run wrote it or an earlier
    -- run already did.
    SELECT count(*) INTO missing
    FROM tmp_motorcycle_list_price t
    WHERE t.motorcycle_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM motorcycle_additional_specs s
          WHERE s.motorcycle_id = t.motorcycle_id AND s.spec_key = 'List price (EUR)'
      );
    IF missing <> 0 THEN
        RAISE EXCEPTION 'Motorcycle list price backfill: % resolved rows have no List price (EUR) spec after the insert', missing;
    END IF;
END $$;

COMMIT;
