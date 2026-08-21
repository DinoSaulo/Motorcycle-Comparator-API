-- Development seed data (dev profile only; see application.yml).
--
-- Explicit ids keep the foreign keys readable. The identity sequences are
-- restarted at the bottom so rows created through the API do not collide with
-- these. Figures are indicative demo values, not an authoritative spec source.
--
-- PostgreSQL dialect. Replaced by Flyway migrations plus a real import pipeline
-- once the model settles.

INSERT INTO engine_specifications
    (id, engine_type, displacement_cc, cylinders, valves_per_cylinder, max_power_hp, max_power_rpm,
     max_torque_nm, max_torque_rpm, compression_ratio, bore_mm, stroke_mm, cooling_system,
     fuel_system, transmission_type, gears, final_drive, top_speed_kph,
     fuel_consumption_l_100km, emission_standard)
VALUES
    (1, 'Inline-3, DOHC', 890, 3, 4, 117.0, 10000, 93.0, 7000, '11.5:1', 78.00, 62.00, 'Liquid',
     'Electronic fuel injection', '6-speed manual', 6, 'Chain', 220, 5.00, 'Euro 5+'),
    (2, 'Inline-4, DOHC', 649, 4, 4, 94.0, 12000, 63.0, 9500, '11.6:1', 67.00, 46.00, 'Liquid',
     'PGM-FI electronic injection', '6-speed manual', 6, 'Chain', 210, 4.90, 'Euro 5+'),
    (3, 'Inline-4, DOHC', 636, 4, 4, 130.0, 13000, 69.0, 10800, '12.9:1', 67.00, 45.10, 'Liquid',
     'DFI, 38mm throttle bodies', '6-speed manual', 6, 'Chain', 260, 6.10, 'Euro 5+'),
    (4, 'Boxer-twin, DOHC', 1300, 2, 4, 145.0, 7750, 149.0, 6500, '13.3:1', 106.50, 73.00, 'Liquid',
     'Electronic fuel injection', '6-speed manual', 6, 'Shaft', 220, 4.80, 'Euro 5+'),
    (5, 'L-Twin, Testastretta', 937, 2, 4, 111.0, 9250, 93.0, 6500, '13.3:1', 94.00, 67.50, 'Liquid',
     'Electronic injection, 53mm throttle body', '6-speed manual', 6, 'Chain', 225, 5.30, 'Euro 5+'),
    (6, 'Permanent magnet AC motor', NULL, NULL, NULL, 100.0, NULL, 190.0, NULL, NULL, NULL, NULL,
     'Air', 'Battery electric, 14.4 kWh', 'Single-speed direct drive', 1, 'Belt', 200, NULL,
     'Zero emission');

INSERT INTO dimensions
    (id, length_mm, width_mm, height_mm, wheelbase_mm, seat_height_mm, ground_clearance_mm,
     kerb_weight_kg, dry_weight_kg, fuel_capacity_l, payload_kg)
VALUES
    (1, 2090, 820, 1190, 1430, 825, 140, 193.0, NULL, 14.0, 187.0),
    (2, 2120, 780, 1075, 1450, 810, 150, 202.0, NULL, 15.4, 180.0),
    (3, 2025, 705, 1110, 1400, 830, 130, 198.0, NULL, 17.0, 175.0),
    (4, 2212, 1000, 1305, 1518, 850, 230, 237.0, NULL, 19.0, 213.0),
    (5, 2060, 850, 1090, 1474, 820, 155, 188.0, 166.0, 14.0, 190.0),
    (6, 2110, 800, 1130, 1450, 787, 175, 220.0, NULL, NULL, 175.0);

INSERT INTO motorcycles
    (id, slug, brand, model, model_year, category, price_eur, image_url, description,
     frame_type, front_suspension, rear_suspension, front_brake, rear_brake, abs_type,
     front_tyre, rear_tyre, engine_specification_id, dimension_id, created_at, updated_at)
VALUES
    (1, 'yamaha-mt-09-2024', 'Yamaha', 'MT-09', 2024, 'NAKED', 10499.00, NULL,
     'Triple-cylinder hyper naked with a torque-rich CP3 engine and a full electronics package.',
     'Aluminium Deltabox', '41mm KYB inverted fork, fully adjustable',
     'Link-type monoshock, adjustable preload and rebound',
     'Dual 298mm discs, radial-mount 4-piston calipers', 'Single 245mm disc',
     'Dual-channel cornering ABS', '120/70 ZR17', '180/55 ZR17',
     1, 1, NOW(), NOW()),

    (2, 'honda-cb650r-2024', 'Honda', 'CB650R', 2024, 'NAKED', 9290.00, NULL,
     'Neo Sports Cafe middleweight built around a smooth, high-revving inline-four.',
     'Steel diamond', '41mm Showa SFF-BP inverted fork',
     'Monoshock, 10-step preload adjustment',
     'Dual 310mm discs, radial-mount 4-piston calipers', 'Single 240mm disc',
     'Dual-channel ABS', '120/70 ZR17', '180/55 ZR17',
     2, 2, NOW(), NOW()),

    (3, 'kawasaki-ninja-zx-6r-2024', 'Kawasaki', 'Ninja ZX-6R', 2024, 'SPORT', 12099.00, NULL,
     'Track-focused supersport with a 636cc four and a close-ratio gearbox.',
     'Aluminium perimeter', '41mm SFF-BP inverted fork, fully adjustable',
     'Uni-Trak monoshock, fully adjustable',
     'Dual 310mm discs, radial-mount 4-piston calipers', 'Single 220mm disc',
     'Dual-channel ABS', '120/70 ZR17', '180/55 ZR17',
     3, 3, NOW(), NOW()),

    (4, 'bmw-r-1300-gs-2024', 'BMW', 'R 1300 GS', 2024, 'ADVENTURE', 20500.00, NULL,
     'Flagship adventure tourer with the 1300cc boxer and a sheet-metal main frame.',
     'Sheet-metal shell main frame', 'EVO Telelever, central spring strut',
     'EVO Paralever, central spring strut',
     'Dual 310mm discs, radial-mount 4-piston calipers', 'Single 285mm disc',
     'Motorrad Integral ABS Pro', '120/70 R19', '170/60 R17',
     4, 4, NOW(), NOW()),

    (5, 'ducati-monster-2024', 'Ducati', 'Monster', 2024, 'NAKED', 12490.00, NULL,
     'Lightweight aluminium-framed Monster with the Testastretta 11-degree twin.',
     'Aluminium front frame', '43mm inverted fork',
     'Progressive linkage monoshock, adjustable preload',
     'Dual 320mm discs, Brembo M4.32 radial calipers', 'Single 245mm disc',
     'Bosch cornering ABS', '120/70 ZR17', '180/55 ZR17',
     5, 5, NOW(), NOW()),

    (6, 'zero-sr-f-2024', 'Zero', 'SR/F', 2024, 'ELECTRIC', 24900.00, NULL,
     'Electric naked with instant torque, a single-speed drivetrain and no clutch.',
     'Steel trellis', '43mm Showa Big Piston inverted fork, fully adjustable',
     'Showa monoshock, fully adjustable',
     'Dual 320mm discs, J-Juan 4-piston calipers', 'Single 240mm disc',
     'Bosch Advanced MSC cornering ABS', '120/70 ZR17', '180/55 ZR17',
     6, 6, NOW(), NOW());

INSERT INTO motorcycle_additional_specs (motorcycle_id, spec_key, spec_value)
VALUES
    (1, 'Rider modes', '4 (Sport, Street, Rain, Custom)'),
    (1, 'Display', '5-inch TFT with smartphone connectivity'),
    (1, 'Quickshifter', 'Bi-directional, standard'),
    (2, 'Rider modes', 'Not available'),
    (2, 'Display', '5-inch TFT'),
    (2, 'Quickshifter', 'Optional'),
    (3, 'Rider modes', '4 (Sport, Road, Rain, Rider)'),
    (3, 'Display', '4.3-inch TFT'),
    (3, 'Quickshifter', 'Upshift only, standard'),
    (4, 'Rider modes', '4 standard, 2 optional'),
    (4, 'Display', '6.5-inch TFT with navigation'),
    (4, 'Adaptive cruise control', 'Optional'),
    (5, 'Rider modes', '3 (Sport, Urban, Touring)'),
    (5, 'Display', '4.3-inch TFT'),
    (5, 'Quickshifter', 'Optional'),
    (6, 'Battery capacity', '14.4 kWh'),
    (6, 'Range (city)', '259 km'),
    (6, 'Charge time (Level 2)', '4.0 h to 95%');

-- Keep API-created rows clear of the seeded ids.
ALTER TABLE engine_specifications ALTER COLUMN id RESTART WITH 100;
ALTER TABLE dimensions ALTER COLUMN id RESTART WITH 100;
ALTER TABLE motorcycles ALTER COLUMN id RESTART WITH 100;
