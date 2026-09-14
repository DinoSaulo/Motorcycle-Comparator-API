package com.motorcycle.comparison;

import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.repository.MotorcycleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The only test that proves the migrations and the entities describe the same database: the slice tests run on H2
 *  with {@code create-drop}, which cannot parse the functional and GIN indexes and so never sees the schema for real. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration,classpath:db/search,classpath:db/seed",
        // Boots against the migrated schema: any drift between an entity and a migration fails here, loudly.
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@DisplayName("Schema migrations")
class SchemaMigrationIT {

    /** Testcontainers' singleton pattern: one static container started here and reaped by Ryuk at JVM exit, so the
     *  image is pulled once and no junit-jupiter integration artifact is needed just to call start(). */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private MotorcycleRepository motorcycleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("applies every versioned migration and the repeatable seed")
    void appliesEveryMigration() {
        List<String> applied = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true AND version IS NOT NULL ORDER BY installed_rank", String.class);
        Integer repeatables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true AND version IS NULL", Integer.class);

        // V4 normalises brand casing and was added without this list being updated, so the assertion has been failing
        // since. See the seed header: V4 runs before any repeatable seed, so rows those seeds insert never reach it.
        // V5 adds motorcycle_available_countries and, like V4, runs before every repeatable seed below.
        assertThat(applied).containsExactly("1", "2", "3", "4", "5");
        // R__dev_seed.sql, R__motorcycles_brazil_fipe_2026_08.sql, displacement_cc_2026_09, the harley_davidson, honda,
        // kawasaki (specs and specs_research), royal_enfield, specs_bmw, triumph and yamaha *_2026_0[89].sql seeds,
        // zz_*_specs_gapfill, zzz_motorcycle_available_countries_brazil, zzzz_motorcycles_1000ps_specs_2026_09,
        // zzzz_motorcycles_engine_specs_2026_09, zzzz_motorcycles_list_price_2026_09,
        // zzzz_motorcycles_suspension_2026_09, zzzzz_motorcycles_brakes_honda_2026_09,
        // zzzzz_motorcycles_brakes_honda_residual_2026_09, zzzzz_motorcycles_brakes_kawasaki_2026_09,
        // zzzzz_motorcycles_brakes_ktm_2026_09, zzzzz_motorcycles_brakes_suzuki_2026_09,
        // zzzzz_motorcycles_brakes_yamaha_2026_09, zzzzzz_motorcycles_tyres_honda_2026_09,
        // zzzzzz_motorcycles_tyres_kawasaki_2026_09, zzzzzz_motorcycles_tyres_ktm_2026_09 and
        // zzzzzz_motorcycles_tyres_suzuki_2026_09.
        assertThat(repeatables).isEqualTo(27);
    }

    @Test
    @DisplayName("every brand import runs after the FIPE seed that creates the rows it fills")
    void brandImportsRunAfterTheFipeSeed() {
        // Flyway orders repeatable migrations by description, so the file names alone decide it: every brand import only
        // gap-fills and needs FIPE first. BMW is named R__motorcycles_specs_bmw_* to sort after "brazil"; renaming loses 200 rows.
        List<String> order = jdbcTemplate.queryForList(
                "SELECT description FROM flyway_schema_history WHERE success = true AND version IS NULL ORDER BY installed_rank", String.class);

        assertThat(order).containsExactly(
                "dev seed",
                "motorcycles brazil fipe 2026 08",
                "motorcycles displacement cc 2026 09",
                "motorcycles harley davidson specs 2026 08",
                "motorcycles honda specs 2026 08",
                "motorcycles kawasaki specs 2026 08",
                "motorcycles kawasaki specs research 2026 08",
                "motorcycles royal enfield specs 2026 08",
                "motorcycles specs bmw 2026 08",
                "motorcycles triumph specs 2026 09",
                "motorcycles yamaha specs 2026 08",
                "zz motorcycles specs gapfill",
                "zzz motorcycle available countries brazil",
                "zzzz motorcycles 1000ps specs 2026 09",
                // Sorts after 1000ps ('1' < 'e') so it cannot pre-empt any per-brand or 1000ps claim, and before
                // suspension ('e' < 's') though the two share no column, so their relative order has no correctness weight.
                "zzzz motorcycles engine specs 2026 09",
                // Writes a new motorcycle_additional_specs key ('List price (EUR)') no other seed claims, so its
                // ordering carries no real correctness weight either - kept in this "zzzz, research-derived" family
                // for consistency, sorting after engine specs ('e' < 'l') and before suspension ('l' < 's').
                "zzzz motorcycles list price 2026 09",
                // 'l' < 's', which is what made this the last seed to run until the brakes backfills below joined it:
                // its input is exactly the rows still NULL once every seed above has had its claim.
                "zzzz motorcycles suspension 2026 09",
                // Now the true tail: 'zzzzz' > 'zzzz_' because 'z' > '_', so every brakes seed sorts after suspension.
                // Same reasoning as suspension: the input list is exactly the rows still missing front_brake or
                // rear_brake once every seed above (including suspension, which shares no column with either) has run.
                // One file per brand rather than a shared file: tools/brakes-research.json is replaced wholesale
                // between research batches, not accumulated, so a fixed filename would silently overwrite an
                // already-applied batch for a brand no longer present in the current research pass. Honda ran
                // first, Kawasaki second, KTM third, Yamaha fourth; a second, later pass filled the handful of Honda
                // rows the first pass could not source and a first pass for Suzuki (which had none before) joined
                // them - "brakes_honda" < "brakes_honda_residual" < "brakes_kawasaki" < "brakes_ktm" <
                // "brakes_suzuki" < "brakes_yamaha" alphabetically, though their relative order carries no
                // correctness weight since each UPDATE is COALESCE-guarded, no file's WHERE clause can match
                // another brand's rows, and honda_residual's target list excludes every slug the original honda
                // file already resolved (it was generated from the live worklist after that file had already run).
                "zzzzz motorcycles brakes honda 2026 09",
                "zzzzz motorcycles brakes honda residual 2026 09",
                "zzzzz motorcycles brakes kawasaki 2026 09",
                "zzzzz motorcycles brakes ktm 2026 09",
                "zzzzz motorcycles brakes suzuki 2026 09",
                "zzzzz motorcycles brakes yamaha 2026 09",
                // 'zzzzzz' > 'zzzzz_' the same way 'zzzzz' > 'zzzz_', so the tyres backfills sort after every brakes
                // file. front_tyre/rear_tyre share no column with front_brake/rear_brake/abs_type, so - like
                // suspension versus the brakes tier - this ordering carries no correctness weight of its own; it
                // is here purely for consistency with "every dedicated backfill runs after every other seed".
                // One file per brand, same reasoning as the brakes tier: tools/tyres-research.json is replaced
                // wholesale per batch. Honda, Kawasaki, KTM and Suzuki ran this pass; Yamaha's tyre gap (still
                // open) is left for a follow-up batch, which is why there is no zzzzzz "tyres yamaha" file yet.
                "zzzzzz motorcycles tyres honda 2026 09",
                "zzzzzz motorcycles tyres kawasaki 2026 09",
                "zzzzzz motorcycles tyres ktm 2026 09",
                "zzzzzz motorcycles tyres suzuki 2026 09");
        assertThat(order.indexOf("motorcycles brazil fipe 2026 08"))
                .isLessThan(order.indexOf("motorcycles specs bmw 2026 08"));
        // The consolidated gap-fill covers brands that have a dedicated seed too, and COALESCE gives the first writer the
        // column for good, so a brand's own scrape has to claim it first. Its "zz" prefix puts it last of every spec seed.
        assertThat(order.indexOf("zz motorcycles specs gapfill")).isEqualTo(order.size() - 16);
        // The Brazil country backfill needs every motorcycle row that already existed, including ones dev seed and the
        // brand imports insert, so it runs after all of them.
        assertThat(order.indexOf("zzz motorcycle available countries brazil")).isEqualTo(order.size() - 15);
        // The 1000ps catalogue is not a Brazilian-market snapshot, so the rows it creates must not reach the backfill
        // above; its "zzzz" prefix runs it after that one, which is the only thing leaving those rows with no country.
        assertThat(order.indexOf("zzzz motorcycles 1000ps specs 2026 09")).isEqualTo(order.size() - 14);
        // The engine specs backfill stages exactly the core-incomplete rows sql-pro found, so it has to see every
        // per-brand and 1000ps claim first, same reasoning as the suspension backfill below.
        assertThat(order.indexOf("zzzz motorcycles engine specs 2026 09")).isEqualTo(order.size() - 13);
        // The list price backfill writes a key no other seed touches, so unlike its neighbours this ordering is not
        // load-bearing - it stays in this position purely for consistency with the rest of the "zzzz" family.
        assertThat(order.indexOf("zzzz motorcycles list price 2026 09")).isEqualTo(order.size() - 12);
        // The suspension backfill stages exactly the rows still NULL after every seed above, so it has to see all of
        // their claims first. Sorting it anywhere earlier would let it win columns a per-brand scrape should own.
        assertThat(order.indexOf("zzzz motorcycles suspension 2026 09")).isEqualTo(order.size() - 11);
        // The brakes backfills: same reasoning as suspension, and each has to see suspension's claim too even though
        // the two share no column, to stay coherent with "every dedicated backfill runs after every other seed"
        // rather than carve out a column-scoped exception. Split one file per brand rather than shared, since
        // tools/brakes-research.json is replaced wholesale per research batch instead of accumulated.
        assertThat(order.indexOf("zzzzz motorcycles brakes honda 2026 09")).isEqualTo(order.size() - 10);
        assertThat(order.indexOf("zzzzz motorcycles brakes honda residual 2026 09")).isEqualTo(order.size() - 9);
        assertThat(order.indexOf("zzzzz motorcycles brakes kawasaki 2026 09")).isEqualTo(order.size() - 8);
        assertThat(order.indexOf("zzzzz motorcycles brakes ktm 2026 09")).isEqualTo(order.size() - 7);
        assertThat(order.indexOf("zzzzz motorcycles brakes suzuki 2026 09")).isEqualTo(order.size() - 6);
        assertThat(order.indexOf("zzzzz motorcycles brakes yamaha 2026 09")).isEqualTo(order.size() - 5);
        // The tyres backfills are now the true tail: same "sees every prior claim" reasoning, one file per brand.
        assertThat(order.indexOf("zzzzzz motorcycles tyres honda 2026 09")).isEqualTo(order.size() - 4);
        assertThat(order.indexOf("zzzzzz motorcycles tyres kawasaki 2026 09")).isEqualTo(order.size() - 3);
        assertThat(order.indexOf("zzzzzz motorcycles tyres ktm 2026 09")).isEqualTo(order.size() - 2);
        assertThat(order.indexOf("zzzzzz motorcycles tyres suzuki 2026 09")).isEqualTo(order.size() - 1);
        // Both Kawasaki files gap-fill with COALESCE, so whichever runs first wins every column they share. The scraped
        // seed cites a page per model year and must precede the research seed, which generalises from the engine family.
        assertThat(order.indexOf("motorcycles kawasaki specs 2026 08"))
                .isLessThan(order.indexOf("motorcycles kawasaki specs research 2026 08"));
    }

    @Test
    @DisplayName("loads the dev seed")
    void loadsTheDevSeed() {
        // 53 curated dev-seed bikes plus the Brazil/FIPE 08/2026 snapshot came to 8454, and none of the specification
        // imports adds a row. The 1000ps catalogue import does: 4575 European models the FIPE table never carried.
        assertThat(motorcycleRepository.count()).isEqualTo(13029);
        assertThat(motorcycleRepository.findWithSpecificationsBySlug("yamaha-mt-09-2024")).isPresent();
    }

    @Test
    @DisplayName("the Honda import fills the specification blocks the FIPE seed leaves empty")
    void loadsTheHondaSpecifications() {
        // The FIPE snapshot carries price and model only, so before this import every Honda row had a near-empty engine
        // block and no dimension row. One known model keeps the check readable; the counts below catch a truncated import.
        Motorcycle hornet = motorcycleRepository.findWithSpecificationsBySlug("honda-cb-600f-hornet-2005").orElseThrow();

        assertThat(hornet.getFrontTyre()).isEqualTo("130/70ZR16 (61W) (Michelin Bridgestone)");
        assertThat(hornet.getEngine().getDisplacementCc()).isEqualTo(599);
        assertThat(hornet.getEngine().getGears()).isEqualTo(6);
        // 97.5 hp read from "97.5 hp / 71.1 kW @ 12000 rpm"; the parser prefers the hp figure over the kW one.
        assertThat(hornet.getEngine().getMaxPowerHp()).isEqualByComparingTo("97.5");
        assertThat(hornet.getDimension().getKerbWeightKg()).isEqualByComparingTo("198.0");

        // A floor rather than an equality: the cross-brand gap-fill runs after this seed and adds dimension blocks to
        // Honda rows this scrape had nothing for. What this seed claims is that all 898 of its own rows got one.
        Integer hondaWithDimensions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'honda' AND m.dimension_id IS NOT NULL"
                        + seededBefore1000ps("m"), Integer.class);
        assertThat(hondaWithDimensions).isGreaterThanOrEqualTo(898);
    }

    @Test
    @DisplayName("the Yamaha import fills the specification blocks and the images the FIPE seed leaves empty")
    void loadsTheYamahaSpecifications() {
        Motorcycle xt660 = motorcycleRepository.findWithSpecificationsBySlug("yamaha-xt-660-r-2010").orElseThrow();

        assertThat(xt660.getFrontTyre()).isEqualTo("90/90- 21");
        assertThat(xt660.getEngine().getDisplacementCc()).isEqualTo(659);
        assertThat(xt660.getEngine().getGears()).isEqualTo(5);
        assertThat(xt660.getDimension().getKerbWeightKg()).isEqualByComparingTo("181.0");

        // A floor rather than an equality: the cross-brand gap-fill runs after this seed and adds dimension blocks to
        // Yamaha rows this scrape had nothing for. What this seed claims is that all 617 of its own rows got one.
        Integer withDimensions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'yamaha' AND m.dimension_id IS NOT NULL"
                        + seededBefore1000ps("m"), Integer.class);
        assertThat(withDimensions).isGreaterThanOrEqualTo(617);

        // Every image URL stored must be a name ImageController can serve: FileStorageServiceImpl reads a UUID plus
        // jpg/png/webp and nothing else, so anything else is a silent 404. Files are not in the repo; see the seed header.
        Integer servableImages = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'yamaha'" + seededBefore1000ps("m") + "AND m.image_url ~ "
                        + "'^/api/v1/images/motorcycles/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$'",
                Integer.class);
        assertThat(servableImages).isEqualTo(523);
    }

    @Test
    @DisplayName("the Royal Enfield import fills the specification blocks and the images the FIPE seed leaves empty")
    void loadsTheRoyalEnfieldSpecifications() {
        Motorcycle interceptor = motorcycleRepository.findWithSpecificationsBySlug("royal-enfield-interceptor-650-standard-2023").orElseThrow();

        assertThat(interceptor.getFrontTyre()).isEqualTo("100/90 -18M");
        assertThat(interceptor.getEngine().getDisplacementCc()).isEqualTo(648);
        assertThat(interceptor.getEngine().getGears()).isEqualTo(6);
        // 46.8 hp converted from "(34.9kw)@7250RPM": this sheet publishes no horsepower figure of its own.
        assertThat(interceptor.getEngine().getMaxPowerHp()).isEqualByComparingTo("46.8");
        assertThat(interceptor.getDimension().getKerbWeightKg()).isEqualByComparingTo("217.0");

        Integer withDimensions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'royal enfield' AND m.dimension_id IS NOT NULL"
                        + seededBefore1000ps("m"), Integer.class);
        assertThat(withDimensions).isEqualTo(152);

        // See the seed header: the source prose captions the 648 cc twin a single and the 349 cc single a twin, and the
        // import overrules both from bore, stroke and displacement. Per engine family, since the 535 cc Continental is a single.
        assertThat(interceptor.getEngine().getCylinders()).isEqualTo(2);
        Integer miscountedCylinders = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m JOIN engine_specifications e ON e.id = m.engine_specification_id "
                        + "WHERE lower(m.brand) = 'royal enfield' "
                        + "AND ((e.displacement_cc = 648 AND e.cylinders <> 2) OR (e.displacement_cc = 349 AND e.cylinders <> 1))",
                Integer.class);
        assertThat(miscountedCylinders).isZero();

        // Every image URL stored must be a name ImageController can serve (a UUID plus jpg/png/webp), so anything else is a
        // silent 404; the files are not in the repo. The nine short of 152 are the Bullet 500 and the Classic Chrome 500 EFI.
        Integer servableImages = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'royal enfield'" + seededBefore1000ps("m") + "AND m.image_url ~ "
                        + "'^/api/v1/images/motorcycles/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$'",
                Integer.class);
        assertThat(servableImages).isEqualTo(143);
    }

    @Test
    @DisplayName("the Harley-Davidson import fills the specification blocks and the images the FIPE seed leaves empty")
    void loadsTheHarleyDavidsonSpecifications() {
        Motorcycle fatBoy = motorcycleRepository.findWithSpecificationsBySlug("harley-davidson-fat-boy-flstf-1999").orElseThrow();

        assertThat(fatBoy.getFrontTyre()).isEqualTo("D402F MT90B16 72H");
        assertThat(fatBoy.getEngine().getDisplacementCc()).isEqualTo(1449);
        assertThat(fatBoy.getEngine().getGears()).isEqualTo(5);
        // Every engine in the snapshot is a V-twin and none of the source prose ever writes "cylinder": the count is read
        // from "45° V-Twin" / "Twin Cam 88", and the 2 in "2 valves per cylinder" is not mistaken for it.
        assertThat(fatBoy.getEngine().getCylinders()).isEqualTo(2);
        assertThat(fatBoy.getDimension().getKerbWeightKg()).isEqualByComparingTo("324.0");

        // See the seed header. This row reads "Laden2 645.2 mm / 25.4 in Unladen 698.5mm / 27.5 in": the unladen figure is
        // the one stored, the trailing 2 on "Laden" is a footnote marker, and the laden figure is kept rather than discarded.
        assertThat(fatBoy.getDimension().getSeatHeightMm()).isEqualTo(699);
        String ladenSeatHeight = jdbcTemplate.queryForObject(
                "SELECT s.spec_value FROM motorcycle_additional_specs s JOIN motorcycles m ON m.id = s.motorcycle_id "
                        + "WHERE m.slug = 'harley-davidson-fat-boy-flstf-1999' AND s.spec_key = 'Altura do Assento (com piloto)'",
                String.class);
        assertThat(ladenSeatHeight).isEqualTo("645 mm");

        // These sheets are American and nine rows quote the seat height in inches only; mm is used wherever
        // mm was printed, so this is the only path that converts.
        Motorcycle sportster883 = motorcycleRepository.findWithSpecificationsBySlug("harley-davidson-xl-883-std-low-1991").orElseThrow();
        assertThat(sportster883.getDimension().getSeatHeightMm()).isEqualTo(655);

        // The one row of the 200 that R__dev_seed.sql curated by hand, and so the only one where gap-fill is not full
        // population: the import supplies the bore and compression ratio left NULL and does not touch the torque figure.
        Motorcycle sportsterS = motorcycleRepository.findWithSpecificationsBySlug("harley-davidson-sportster-s-2024").orElseThrow();
        assertThat(sportsterS.getEngine().getCompressionRatio()).isEqualTo("12:1");
        assertThat(sportsterS.getEngine().getBoreMm()).isEqualByComparingTo("105.00");
        assertThat(sportsterS.getEngine().getMaxTorqueNm()).isEqualByComparingTo("125.0");

        // 199 of the 200 scraped rows; the Softail Custom 1995 matched no source sheet and is not emitted. The bound is a
        // floor rather than an equality because the cross-brand gap-fill also reaches this brand and adds its own blocks.
        Integer withDimensions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'harley-davidson' AND m.dimension_id IS NOT NULL"
                        + seededBefore1000ps("m"), Integer.class);
        assertThat(withDimensions).isGreaterThanOrEqualTo(199);

        // Every image URL stored must be a name ImageController can serve: FileStorageServiceImpl reads a UUID plus
        // jpg/png/webp and nothing else, so anything else is a silent 404. Files are not in the repo; see the seed header.
        Integer servableImages = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'harley-davidson'" + seededBefore1000ps("m") + "AND m.image_url ~ "
                        + "'^/api/v1/images/motorcycles/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$'",
                Integer.class);
        assertThat(servableImages).isEqualTo(199);
    }

    @Test
    @DisplayName("the BMW import fills the specification blocks and the images the FIPE seed leaves empty")
    void loadsTheBmwSpecifications() {
        // The extractor split this row's capacity into "11 72 cc / 71.5 cu in"; reading the first fragment would store
        // 11 cc, small enough that no plausibility bound would question it, so cubic inches are converted instead. 10 rows.
        Motorcycle k1200lt = motorcycleRepository.findWithSpecificationsBySlug("bmw-k-1200-lt-1999").orElseThrow();
        assertThat(k1200lt.getEngine().getDisplacementCc()).isEqualTo(1172);
        assertThat(k1200lt.getEngine().getCylinders()).isEqualTo(4);
        assertThat(k1200lt.getEngine().getGears()).isEqualTo(5);
        assertThat(k1200lt.getDimension().getKerbWeightKg()).isEqualByComparingTo("378.0");

        // "6-speed gearbox" is how most of these sheets write it, so the gear count has to survive a hyphen; whitespace-only
        // reads "5 Speed" and misses every BMW written the other way. Six cylinders, from prose and arithmetic that agree.
        Motorcycle k1600gt = motorcycleRepository.findWithSpecificationsBySlug("bmw-k-1600-gt-2011").orElseThrow();
        assertThat(k1600gt.getEngine().getGears()).isEqualTo(6);
        assertThat(k1600gt.getEngine().getCylinders()).isEqualTo(6);
        // "C hill-cast" in the source is an extractor fault, not a word: repaired to "Chill-cast" on the way in. Only four
        // such forms are repaired, because "BMS-K with", "M forged" and "a hydraulic" are all correct as printed.
        assertThat(k1600gt.getFrameType()).startsWith("Chill-cast rear frame");

        // A Boxer is a flat twin and the prose never writes the count, so "Boxer" has to be read as two -
        // while the "Four" in "Four stroke" must not be read at all.
        Motorcycle r1100s = motorcycleRepository.findWithSpecificationsBySlug("bmw-r-1100-s-1998").orElseThrow();
        assertThat(r1100s.getEngine().getCylinders()).isEqualTo(2);
        // Source reads "80 0 mm / 31.4 in": the mm figure is split and refused, so the inch figure converts.
        assertThat(r1100s.getDimension().getSeatHeightMm()).isEqualTo(798);

        // 28 rows publish the tank only in the American spelling ("24 Liters / US 6.3 gal"), which is not a
        // rounding detail to leave out of the unit pattern - it is the whole figure for those rows.
        Motorcycle r1100gs = motorcycleRepository.findWithSpecificationsBySlug("bmw-r-1100-gs-1995").orElseThrow();
        assertThat(r1100gs.getDimension().getFuelCapacityL()).isEqualByComparingTo("24.0");

        // Trail and Castor are the same measurement under two source names and never share a row, so they share a
        // long-tail key instead of splitting one fact across two rows. 107 from this import, 9 more from the 1000ps one.
        Integer trailRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycle_additional_specs s JOIN motorcycles m ON m.id = s.motorcycle_id "
                        + "WHERE lower(m.brand) = 'bmw' AND s.spec_key = 'Trail'"
                        + seededBefore1000ps("m"), Integer.class);
        assertThat(trailRows).isEqualTo(116);

        // All 200 scraped rows carry something dimensional, and the FIPE seed gave none of them a price this
        // import could overwrite - it only ever gap-fills, so every BMW row keeps the price FIPE set. The bound is a
        // floor rather than an equality because the cross-brand gap-fill also reaches this brand and adds its own blocks.
        Integer withDimensions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'bmw' AND m.dimension_id IS NOT NULL"
                        + seededBefore1000ps("m"), Integer.class);
        assertThat(withDimensions).isGreaterThanOrEqualTo(200);
        Integer withoutPrice = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'bmw' AND m.price_eur IS NULL"
                        + seededBefore1000ps("m"), Integer.class);
        assertThat(withoutPrice).isZero();

        // Every image URL stored must be a name ImageController can serve: FileStorageServiceImpl reads a UUID plus
        // jpg/png/webp and nothing else, so anything else is a silent 404. Files are not in the repo; see the seed header.
        Integer servableImages = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'bmw'" + seededBefore1000ps("m") + "AND m.image_url ~ "
                        + "'^/api/v1/images/motorcycles/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$'",
                Integer.class);
        assertThat(servableImages).isEqualTo(200);
    }

    @Test
    @DisplayName("the suspension backfill writes per model year and leaves the unsourced rows NULL")
    void loadsTheSuspensionBackfill() {
        // The point of this seed is that research done per nameplate is written per exact year-suffixed slug. A row
        // count alone cannot tell correct expansion apart from stamping one nameplate value across its whole run, so
        // these assert the two ends of a real mid-nameplate change: identical slugs but for the year, different value.
        Motorcycle xre2024 = motorcycleRepository.findWithSpecificationsBySlug("honda-xre-190-flex-2024").orElseThrow();
        Motorcycle xre2025 = motorcycleRepository.findWithSpecificationsBySlug("honda-xre-190-flex-2025").orElseThrow();
        assertThat(xre2024.getFrontSuspension()).isEqualTo("Telescopic fork, 31 mm tubes, 180 mm travel");
        assertThat(xre2025.getFrontSuspension()).isEqualTo("Telescopic fork, 33 mm tubes, 180 mm travel");

        // Kawasaki's 2017 Ninja 650 redesign swapped the offset laydown shock for a horizontal back-link; the fork
        // did not change, so the front matching across the boundary is as much the point as the rear differing.
        Motorcycle ninja2016 = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-ninja-650r-649cc-2016").orElseThrow();
        Motorcycle ninja2017 = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-ninja-650r-649cc-2017").orElseThrow();
        assertThat(ninja2016.getRearSuspension()).startsWith("Offset laydown single shock");
        assertThat(ninja2017.getRearSuspension()).startsWith("Horizontal back-link shock");
        assertThat(ninja2016.getFrontSuspension()).isEqualTo(ninja2017.getFrontSuspension());

        // The 2014 Z1000 moved from a cartridge fork to Showa's SFF-BP.
        Motorcycle z1000of2013 = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-z-1000-2013").orElseThrow();
        Motorcycle z1000of2015 = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-z-1000-2015").orElseThrow();
        assertThat(z1000of2013.getFrontSuspension()).contains("inverted cartridge fork");
        assertThat(z1000of2015.getFrontSuspension()).contains("inverted SFF-BP fork");

        // No trustworthy published spec was found for this Brazilian-market scooter, so it is absent from the research
        // file entirely. A dash in the UI is the correct outcome; a plausible invented fork would not be.
        Motorcycle spacy = motorcycleRepository.findWithSpecificationsBySlug("honda-ch-125-r-spacy-1994").orElseThrow();
        assertThat(spacy.getFrontSuspension()).isNull();
        assertThat(spacy.getRearSuspension()).isNull();
    }

    @Test
    @DisplayName("the second-round brakes and tyres backfills fill the columns the first round left NULL")
    void loadsTheSecondRoundBrakesAndTyresBackfill() {
        // Suzuki had no brakes seed at all before this round: front_brake/rear_brake/abs_type are a full first population.
        Motorcycle bking = motorcycleRepository.findWithSpecificationsBySlug("suzuki-b-king-2010").orElseThrow();
        assertThat(bking.getFrontBrake()).isEqualTo("Dual 310mm floating discs, radial-mount 4-piston calipers");
        assertThat(bking.getRearBrake()).isEqualTo("Single 260mm disc, 1-piston caliper");
        Motorcycle bandit = motorcycleRepository.findWithSpecificationsBySlug("suzuki-bandit-1200-2006").orElseThrow();
        assertThat(bandit.getAbsType()).isEqualTo("ABS standard");
        assertThat(bandit.getFrontTyre()).isEqualTo("120/70ZR17");
        assertThat(bandit.getRearTyre()).isEqualTo("180/55ZR17");

        // Honda already had a brakes seed; the "residual" file only reaches slugs that first seed left NULL, so the
        // two never compete for the same row - a drum-brake Brazilian commuter the first pass never sourced.
        Motorcycle biz = motorcycleRepository.findWithSpecificationsBySlug("honda-biz-125-es-125-es-flex-2006").orElseThrow();
        assertThat(biz.getFrontBrake()).isEqualTo("Drum, 130mm");
        assertThat(biz.getRearBrake()).isEqualTo("Drum, 110mm");

        // Honda's, Kawasaki's and KTM's tyre columns were untouched by any seed before this round.
        Motorcycle innova = motorcycleRepository.findWithSpecificationsBySlug("honda-anf-125-innova-2011").orElseThrow();
        assertThat(innova.getFrontTyre()).isEqualTo("70/100-17");
        assertThat(innova.getRearTyre()).isEqualTo("80/90-17");
        Motorcycle concours = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-concours14-1352cc-2012").orElseThrow();
        assertThat(concours.getFrontTyre()).isEqualTo("120/70-ZR17");
        assertThat(concours.getRearTyre()).isEqualTo("190/50-ZR17");
        Motorcycle ktmSx = motorcycleRepository.findWithSpecificationsBySlug("ktm-125-sx-2027").orElseThrow();
        assertThat(ktmSx.getFrontTyre()).isEqualTo("80/100-21");
        assertThat(ktmSx.getRearTyre()).isEqualTo("100/90-19");

        // Every one of the six new seeds only ever gap-fills COALESCE(existing, staged); none inserts a row, so
        // loadsTheDevSeed's fixed catalogue count already covers that invariant for this batch too.
    }

    @Test
    @DisplayName("the brakes and tyres backfills never overwrite a column an earlier seed already claimed")
    void gapFillNeverOverwritesAnEarlierClaimOnTheBrakesAndTyresQuintet() {
        // Reading the COALESCE out of the SQL proves nothing: each row below is one an earlier seed had already filled on
        // ONE side while leaving the other NULL, so the merge has to split per column rather than per row.

        // The residual file stages "Dual 296mm discs, four-piston calipers" for this front. The earlier, terser "296 mm"
        // wins anyway - richer text losing to a poorer earlier claim is exactly what gap-fill means.
        Motorcycle cb1100ex = motorcycleRepository.findWithSpecificationsBySlug("honda-cb1100-ex-2020").orElseThrow();
        assertThat(cb1100ex.getFrontBrake()).isEqualTo("296 mm");
        assertThat(cb1100ex.getRearBrake()).isEqualTo("Single 256mm disc, single-piston caliper");
        assertThat(cb1100ex.getAbsType()).isEqualTo("ABS standard");

        // The Yamaha file stages "245mm disc" over Portuguese prose an earlier seed wrote, and stages no abs_type at all.
        // Both the prose and the earlier "ABS" survive; only the NULL rear is filled.
        Motorcycle fz15 = motorcycleRepository.findWithSpecificationsBySlug("yamaha-fz15-150-fazer-connected-flex-2025").orElseThrow();
        assertThat(fz15.getFrontBrake()).startsWith("Disco hidráulico com sistema anti bloqueio");
        assertThat(fz15.getRearBrake()).isEqualTo("130mm drum");
        assertThat(fz15.getAbsType()).isEqualTo("ABS");

        // Two more brakes rows where the staged front/rear differs from what was already there, one per remaining brand
        // that had a prior claim: "250mm disc, dual-piston caliper" and "Drum brake" are both refused.
        Motorcycle kx250f = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-kx-250-250-f-2005").orElseThrow();
        assertThat(kx250f.getFrontBrake()).isEqualTo("Single semi-floating 250mm disc, dual-piston");
        assertThat(kx250f.getRearBrake()).isEqualTo("240mm disc, single-piston caliper");
        Motorcycle intruder = motorcycleRepository.findWithSpecificationsBySlug("suzuki-intruder-m800z-2009").orElseThrow();
        assertThat(intruder.getRearBrake()).isEqualTo("Drum");
        assertThat(intruder.getFrontBrake()).isEqualTo("Single disc, 2-piston caliper");

        // The same guard on the tyres tier, which runs last of all and so has the most prior claims to trip over. The
        // notation differs between sources ("120/80 R18" against the staged "120/80-18"), which is what makes it visible.
        Motorcycle crf250Rally = motorcycleRepository.findWithSpecificationsBySlug("honda-crf250-rally-2020").orElseThrow();
        assertThat(crf250Rally.getRearTyre()).isEqualTo("120/80 R18");
        assertThat(crf250Rally.getFrontTyre()).isEqualTo("3.00-21");
        Motorcycle sxMini = motorcycleRepository.findWithSpecificationsBySlug("ktm-50-sx-mini-2023").orElseThrow();
        assertThat(sxMini.getFrontTyre()).isEqualTo("60/100 R12");
        assertThat(sxMini.getRearTyre()).isEqualTo("2.75x10");
    }

    @Test
    @DisplayName("the two Honda brakes files split one nameplate between them and never claim the same row")
    void theTwoHondaBrakesFilesSplitOneNameplateWithoutCompeting() {
        // brandImportsRunAfterTheFipeSeed asserts the two files' order but not that their target lists are disjoint. The
        // Biz 125 ES run is the proof: both files reached it, on different years, and each wrote its own vocabulary.

        // The residual pass, generated from the worklist after the original file had already run: no CBS in 2006 because
        // the model had none, which is the "never back-apply a system to years before it shipped" rule holding.
        Motorcycle biz2006 = motorcycleRepository.findWithSpecificationsBySlug("honda-biz-125-es-125-es-flex-2006").orElseThrow();
        assertThat(biz2006.getFrontBrake()).isEqualTo("Drum, 130mm");
        assertThat(biz2006.getRearBrake()).isEqualTo("Drum, 110mm");
        assertThat(biz2006.getAbsType()).isNull();

        // The original file's own claim on the same nameplate, nineteen years later: a different rear diameter and the CBS
        // the 2025 bike really ships. Had either file overwritten the other, these two rows would read alike.
        Motorcycle biz2025 = motorcycleRepository.findWithSpecificationsBySlug("honda-biz-125-es-125-es-flex-2025").orElseThrow();
        assertThat(biz2025.getFrontBrake()).isEqualTo("130 mm drum");
        assertThat(biz2025.getRearBrake()).isEqualTo("130 mm drum");
        assertThat(biz2025.getAbsType()).isEqualTo("CBS combined braking");

        // The split is per exact slug, not per nameplate: the run divides cleanly at the year the research does.
        Integer residualStyleRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE slug LIKE 'honda-biz-125-es-125-es-flex-%' AND front_brake = 'Drum, 130mm'",
                Integer.class);
        Integer originalStyleRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE slug LIKE 'honda-biz-125-es-125-es-flex-%' AND front_brake = '130 mm drum'",
                Integer.class);
        assertThat(residualStyleRows).isEqualTo(7);
        assertThat(originalStyleRows).isEqualTo(3);
    }

    @Test
    @DisplayName("ABS is recorded in abs_type, never only inside the brake strings, and never before it shipped")
    void absLivesInAbsTypeAndIsNeverBackApplied() {
        // tools/validate-brakes-research.mjs enforces this at research time; nothing proved the applied rows still respect
        // it once ten COALESCE seeds have merged into the same five columns. These are the post-migration guards.

        // No production motorcycle had ABS before the 1988 K100, so an abs_type on an older row is a back-applied system
        // rather than a fact - the failure mode the research rule exists to prevent.
        Integer absBeforeAbsShipped = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE abs_type IS NOT NULL AND model_year < 1988", Integer.class);
        assertThat(absBeforeAbsShipped).isZero();

        // Real ABS on a front that is drum-only is the same defect wearing a different hat, and it is the shape a
        // cross-source merge produces: one seed's abs_type landing beside another seed's brake string.
        Integer absOnADrumOnlyFront = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE front_brake ~* 'drum' AND front_brake !~* 'dis[ck]' "
                        + "AND abs_type ~* '(^|[^a-z])abs([^a-z]|$)'", Integer.class);
        assertThat(absOnADrumOnlyFront).isZero();

        // CBS is the opposite case and must NOT be swept up by the rule above: combined braking is genuinely fitted to
        // drum-braked commuters, and this batch writes it on 26 of them. A guard that zeroed this would be wrong.
        Integer cbsOnADrumOnlyFront = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE front_brake ~* 'drum' AND front_brake !~* 'dis[ck]' "
                        + "AND abs_type ~* '(^|[^a-z])cbs([^a-z]|$)'", Integer.class);
        assertThat(cbsOnADrumOnlyFront).isEqualTo(26);

        // One of those 26, pinned: the braking system is in abs_type and the brake strings stay pure specification, which
        // is the whole rule stated on a single row this batch is responsible for.
        Motorcycle biz110 = motorcycleRepository.findWithSpecificationsBySlug("honda-biz-110i-2016").orElseThrow();
        assertThat(biz110.getAbsType()).isEqualTo("CBS combined braking");
        assertThat(biz110.getFrontBrake()).isEqualTo("130 mm drum");
        assertThat(biz110.getRearBrake()).isEqualTo("110 mm drum");

        // Where a brake string names a system, abs_type must carry it too, or the UI loses the fact entirely: abs_type is
        // the only column it reads. 52 rows still fail this, all from prose imports that predate the brakes tier.
        List<String> absOnlyInTheBrakeString = jdbcTemplate.queryForList(
                "SELECT m.slug FROM motorcycles m WHERE m.abs_type IS NULL AND ("
                        + "m.front_brake ~* '(^|[^a-z])(abs|cbs|ubs|lbs)([^a-z]|$)' "
                        + "OR m.rear_brake ~* '(^|[^a-z])(abs|cbs|ubs|lbs)([^a-z]|$)') ORDER BY m.slug", String.class);
        // Pinned, not tolerated, the same way loadsTheResearchedKawasakiEngineSpecifications pins its 22: it fails if the
        // set grows, so a future backfill that writes a system into a brake string instead of abs_type is caught here.
        assertThat(absOnlyInTheBrakeString).hasSize(52);
        assertThat(absOnlyInTheBrakeString).allSatisfy(slug -> assertThat(slug).matches(
                "(bmw-k-1200-lt|bmw-r-1200-gs|honda-pcx-150-dlx|royal-enfield-(classic|continental-gt-120|interceptor-120)"
                        + "|triumph-bonneville-(bobber|speedmaster)|yamaha-(mt-09-tracer-900-gt|xt-1200-z|yzf-r-1m))-.*"));
    }

    @Test
    @DisplayName("every stored tyre value is a size code rather than prose or an empty string")
    void tyreValuesAreSizeCodesRatherThanProse() {
        // A blank or prose tyre value renders as a plausible-looking spec in the comparison table, so it is worse than the
        // NULL the research policy prefers. Catalogue-wide, not batch-scoped: this is meant to outlive this batch.
        Integer blankOrProse = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE (front_tyre IS NOT NULL AND (btrim(front_tyre) = '' OR front_tyre !~ '[0-9]')) "
                        + "OR (rear_tyre IS NOT NULL AND (btrim(rear_tyre) = '' OR rear_tyre !~ '[0-9]'))", Integer.class);
        assertThat(blankOrProse).isZero();

        // The same for the brake strings, which have no size-code shape to check beyond this.
        Integer blankBrake = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE btrim(coalesce(front_brake, 'x')) = '' "
                        + "OR btrim(coalesce(rear_brake, 'x')) = ''", Integer.class);
        assertThat(blankBrake).isZero();

        // The stricter half of a real size code is the separator between the two figures, in any of the notations the
        // sources use: "120/70ZR17", "4.60-18", "2.75x10". Legacy rows fail it, so it is scoped to the tyre tier's brands.
        Integer withoutASizeSeparator = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE lower(brand) IN ('honda', 'kawasaki', 'ktm', 'suzuki') "
                        + "AND ((front_tyre IS NOT NULL AND front_tyre !~ '[/x×–-]') "
                        + "OR (rear_tyre IS NOT NULL AND rear_tyre !~ '[/x×–-]'))", Integer.class);
        assertThat(withoutASizeSeparator).isZero();

        // The 78 that do fail it are all Harley-Davidson, Gas Gas, Triumph or Yamaha - "MT90 B16", a bare "16" - and none
        // of those four brands has a tyres file in this batch, which is what makes the scoping above honest.
        List<String> legacyBrandsWithoutASizeSeparator = jdbcTemplate.queryForList(
                "SELECT DISTINCT lower(brand) FROM motorcycles WHERE (front_tyre IS NOT NULL AND front_tyre !~ '[/x×–-]') "
                        + "OR (rear_tyre IS NOT NULL AND rear_tyre !~ '[/x×–-]') ORDER BY 1", String.class);
        assertThat(legacyBrandsWithoutASizeSeparator)
                .containsExactly("gas gas", "harley-davidson", "triumph", "yamaha");
    }

    @Test
    @DisplayName("every brand file in the backfill batch really landed, and Yamaha's missing tyres file shows as a gap")
    void everyBrandFileInTheBackfillBatchLanded() {
        // tools/import-brakes.mjs writes one seed file per brand; a regression to a shared output path would silently drop
        // whichever brand was written first, and nothing downstream would notice. These floors are that alarm.

        // Floors, not equalities, so a later batch can only push them up. Each sits far above the pre-batch coverage
        // (Kawasaki's front_brake was 303 before this tier ran, Suzuki's front_tyre 533), so a missing file cannot pass.
        assertThat(countNonNull("front_brake", "honda")).isGreaterThanOrEqualTo(1272);
        assertThat(countNonNull("front_brake", "kawasaki")).isGreaterThanOrEqualTo(684);
        assertThat(countNonNull("front_brake", "ktm")).isGreaterThanOrEqualTo(467);
        assertThat(countNonNull("front_brake", "suzuki")).isGreaterThanOrEqualTo(741);
        assertThat(countNonNull("front_brake", "yamaha")).isGreaterThanOrEqualTo(1002);
        assertThat(countNonNull("front_tyre", "honda")).isGreaterThanOrEqualTo(1257);
        assertThat(countNonNull("front_tyre", "kawasaki")).isGreaterThanOrEqualTo(672);
        assertThat(countNonNull("front_tyre", "ktm")).isGreaterThanOrEqualTo(460);
        assertThat(countNonNull("front_tyre", "suzuki")).isGreaterThanOrEqualTo(741);

        // Yamaha is the one brand with a brakes file and no tyres file, which is why brandImportsRunAfterTheFipeSeed lists
        // six brakes seeds against four tyres seeds. The open gap is the assertion: it closes when that file is added.
        assertThat(countNonNull("front_tyre", "yamaha")).isLessThan(countNonNull("front_brake", "yamaha"));
        Integer yamahaMissingATyre = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE lower(brand) = 'yamaha' AND (front_tyre IS NULL OR rear_tyre IS NULL)",
                Integer.class);
        assertThat(yamahaMissingATyre).isGreaterThanOrEqualTo(150);
    }

    private Integer countNonNull(String column, String brand) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE lower(brand) = ? AND " + column + " IS NOT NULL", Integer.class, brand);
    }

    @Test
    @DisplayName("the engine specs backfill fills the core fields it researched and leaves the unresearched rows NULL")
    void loadsTheEngineSpecsBackfill() {
        // cylinders is the one key field almost every one of this batch's 228 rows was missing pre-seed (226/228) - the
        // 1000ps catalogue seed (runs earlier, "1" < "e") had already claimed the other five key fields here, which is
        // exactly why sql-pro flagged this row as core-incomplete rather than core-empty. COALESCE preserves those five
        // earlier claims untouched while this seed fills the one this row was actually missing, plus fields no earlier
        // seed ever populated (engine_type, gears): verified against the live dev database before this seed ran.
        Motorcycle hornetSp = motorcycleRepository.findWithSpecificationsBySlug("honda-cb1000-hornet-sp-2026").orElseThrow();
        assertThat(hornetSp.getEngine().getMaxPowerHp()).isEqualByComparingTo("157.0");
        assertThat(hornetSp.getEngine().getMaxTorqueNm()).isEqualByComparingTo("107.0");
        assertThat(hornetSp.getEngine().getMaxPowerRpm()).isEqualTo(11000);
        assertThat(hornetSp.getEngine().getMaxTorqueRpm()).isEqualTo(9000);
        assertThat(hornetSp.getEngine().getCylinders()).isEqualTo(4);
        assertThat(hornetSp.getEngine().getEngineType()).isEqualTo("Liquid-cooled DOHC inline-four 4-stroke");
        assertThat(hornetSp.getEngine().getGears()).isEqualTo(6);

        // Same pattern on a bigger engine: max_power_hp (167) and max_torque_nm (221) were already claimed by an
        // earlier seed and are untouched by COALESCE - engine_specifications has a floor-only CHECK on max_torque_nm
        // (>= 0), no ceiling, so a value that high was never at risk of being clamped by this generator in the first
        // place. cylinders, engine_type and final_drive were genuinely NULL and come from this seed.
        Motorcycle rocket3 = motorcycleRepository.findWithSpecificationsBySlug("triumph-rocket-3-r-2024").orElseThrow();
        assertThat(rocket3.getEngine().getMaxPowerHp()).isEqualByComparingTo("167.0");
        assertThat(rocket3.getEngine().getMaxTorqueNm()).isEqualByComparingTo("221.0");
        assertThat(rocket3.getEngine().getCylinders()).isEqualTo(3);
        assertThat(rocket3.getEngine().getEngineType()).isEqualTo("Liquid-cooled DOHC inline-triple 4-stroke");
        assertThat(rocket3.getEngine().getFinalDrive()).isEqualTo("Shaft");

        // Competition motocross models are deliberately absent from the research: manufacturers do not publish power or
        // torque for them, so this seed's floor of 3 of the 6 key fields is never met. NULL is correct, not a gap to fill.
        Motorcycle kx250 = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-kx-250-2026").orElseThrow();
        assertThat(kx250.getEngine().getMaxPowerHp()).isNull();
        assertThat(kx250.getEngine().getMaxTorqueNm()).isNull();
    }

    @Test
    @DisplayName("the list price research writes to motorcycle_additional_specs, never to price_eur")
    void loadsThePriceResearchBackfill() {
        // additionalSpecs is FetchType.LAZY and findWithSpecificationsBySlug only eager-fetches engine/dimension, so this
        // reads the EAV table directly - the same pattern every other additional-specs assertion in this class already
        // uses (e.g. the BMW 'Trail' and Triumph 'Rodas' checks below).
        String benelliPrice = jdbcTemplate.queryForObject(
                "SELECT s.spec_value FROM motorcycle_additional_specs s JOIN motorcycles m ON m.id = s.motorcycle_id "
                        + "WHERE m.slug = 'benelli-bkx-125-s-2025' AND s.spec_key = 'List price (EUR)'", String.class);
        assertThat(benelliPrice).isEqualTo("3390.00");

        // price_eur itself must stay untouched: this backfill deliberately never writes it, so a slug that got a list
        // price here can still have no price_eur - the two are not the same number and must not be conflated.
        Motorcycle benelli = motorcycleRepository.findWithSpecificationsBySlug("benelli-bkx-125-s-2025").orElseThrow();
        assertThat(benelli.getPriceEur()).isNull();

        // No trustworthy tariff was found for this 2015 model - it is absent from the research file entirely, same
        // "NULL over an invented figure" policy as the suspension and engine specs backfills.
        Integer aeonPriceRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycle_additional_specs s JOIN motorcycles m ON m.id = s.motorcycle_id "
                        + "WHERE m.slug = 'aeon-elite-125-2015' AND s.spec_key = 'List price (EUR)'", Integer.class);
        assertThat(aeonPriceRows).isZero();
    }

    @Test
    @DisplayName("the Triumph import gap-fills existing rows and invents no new motorcycle")
    void loadsTheTriumphSpecifications() {
        // A FIPE-only row (no dev-seed curation): the import is a near-full population of both blocks.
        Motorcycle triple2017 = motorcycleRepository.findWithSpecificationsBySlug("triumph-street-triple-765-rs-2017").orElseThrow();
        assertThat(triple2017.getFrontTyre()).isEqualTo("120/70ZR17");
        assertThat(triple2017.getEngine().getDisplacementCc()).isEqualTo(765);
        // "8.0 kgf.m @ 9.500 RPM" converted: kgf.m to Nm, and "9.500" read as 9500 rpm, not 9.5.
        assertThat(triple2017.getEngine().getMaxTorqueNm()).isEqualByComparingTo("78.5");
        assertThat(triple2017.getEngine().getMaxTorqueRpm()).isEqualTo(9500);
        assertThat(triple2017.getDimension().getKerbWeightKg()).isEqualByComparingTo("189.0");
        assertThat(triple2017.getDimension().getFuelCapacityL()).isEqualByComparingTo("15.0");

        // The 2024 Tiger Sport 660 is one of the three Triumphs R__dev_seed.sql curated by hand, so gap-fill
        // here must leave every already-populated column alone (frame, brakes, tyres, seat height, tank)...
        Motorcycle tigerSport = motorcycleRepository.findWithSpecificationsBySlug("triumph-tiger-sport-660-2024").orElseThrow();
        assertThat(tigerSport.getFrontTyre()).isEqualTo("120/70 ZR17");
        assertThat(tigerSport.getFrameType()).isEqualTo("Tubular steel perimeter");
        assertThat(tigerSport.getDimension().getSeatHeightMm()).isEqualTo(835);
        assertThat(tigerSport.getDimension().getFuelCapacityL()).isEqualByComparingTo("17.2");
        // ...while filling the two dimension columns the curated row left NULL. The scraper writes a bare
        // trail figure ("97,1 mm") into "Chassis" instead of real chassis prose (memory: scraper-source-defects),
        // so frame_type keeps the curated value rather than being overwritten with a rejected measurement.
        assertThat(tigerSport.getDimension().getWidthMm()).isEqualTo(834);
        assertThat(tigerSport.getDimension().getHeightMm()).isEqualTo(1398);

        // Long-tail specs are additive: these keys do not collide with the dev seed's own ('Rider modes',
        // 'Display', ...), so both scraped values land as new rows rather than being suppressed.
        String rodas = jdbcTemplate.queryForObject(
                "SELECT s.spec_value FROM motorcycle_additional_specs s JOIN motorcycles m ON m.id = s.motorcycle_id "
                        + "WHERE m.slug = 'triumph-tiger-sport-660-2024' AND s.spec_key = 'Rodas'", String.class);
        assertThat(rodas).isEqualTo("Alumínio fundido, 17 x 5,5 pol.");

        // No new motorcycle row is ever created by this import; see loadsTheDevSeed's fixed catalogue
        // count, which would fail if this seed inserted rather than only gap-filled.
        Integer triumphCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'triumph'" + seededBefore1000ps("m"), Integer.class);
        assertThat(triumphCount).isEqualTo(456);

        // The scrape is far sparser than the other brand imports: only 117 of 456 scraped rows carry
        // anything usable, so only that many Triumphs gain a dimension block from this import. A floor
        // rather than an equality: the cross-brand gap-fill runs after this seed and reaches the rest.
        Integer withDimensions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'triumph' AND m.dimension_id IS NOT NULL"
                        + seededBefore1000ps("m"), Integer.class);
        assertThat(withDimensions).isGreaterThanOrEqualTo(117);

        // Chassis is documented in the seed header as a mislabelled trail figure, never real chassis text. Counting
        // the rows with one no longer isolates this seed - the gap-fill supplies real chassis prose for this brand -
        // so the invariant is asserted directly instead: no Triumph frame_type is a bare measurement.
        Integer measurementAsFrameType = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'triumph' AND m.frame_type IS NOT NULL " + seededBefore1000ps("m")
                        + "AND m.frame_type ~ '^[0-9.,\\s-]+\\s*(mm|cm|kg|cc)?\\.?$'", Integer.class);
        assertThat(measurementAsFrameType).isZero();

        // Bore, stroke, cylinders and displacement have one algebraic relation; this snapshot never publishes a bore,
        // so nothing this import contributes can fail it. The gap-fill that runs after it does supply the quartet for
        // this brand, and drops any pair that misses this same 5% - including the sheets that copied bore into stroke.
        Integer contradictoryGeometry = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM engine_specifications e JOIN motorcycles m ON m.engine_specification_id = e.id "
                        + "WHERE lower(m.brand) = 'triumph' AND e.bore_mm IS NOT NULL AND e.stroke_mm IS NOT NULL "
                        + "AND e.cylinders IS NOT NULL AND e.displacement_cc IS NOT NULL "
                        + "AND abs(pi() / 4 * e.bore_mm * e.bore_mm * e.stroke_mm * e.cylinders / 1000.0 - e.displacement_cc) "
                        + "     > 0.05 * e.displacement_cc",
                Integer.class);
        assertThat(contradictoryGeometry).isZero();
    }

    @Test
    @DisplayName("the cross-brand gap-fill fills what the per-brand seeds left NULL and invents no motorcycle")
    void loadsTheCrossBrandSpecificationGapFill() {
        // A FIPE-only row from a brand no per-brand seed covers, so every typed column below comes from this import.
        // The counts are deliberately absent: the consolidated scrape is still growing, and a count pinned here would
        // go stale on the next regeneration while these per-row values stay put.
        Motorcycle monster797 = motorcycleRepository.findWithSpecificationsBySlug("ducati-monster-797-2018").orElseThrow();
        // "V2, four-stroke" is the whole cylinder count: this source writes the layout in English, so the Portuguese
        // patterns the brand imports use find nothing and the "four" in "four-stroke" must not be read as four cylinders.
        assertThat(monster797.getEngine().getCylinders()).isEqualTo(2);
        assertThat(monster797.getEngine().getDisplacementCc()).isEqualTo(803);
        assertThat(monster797.getEngine().getBoreMm()).isEqualByComparingTo("88.00");
        assertThat(monster797.getEngine().getStrokeMm()).isEqualByComparingTo("66.00");
        // "73.0 HP (53.3 kW )) @ 8250 RPM" and "67.0 Nm (6.8 kgf-m or 49.4 ft.lbs) @ 5750 RPM": the printed HP and Nm
        // figures win over their own parenthesised conversions, and the rpm is read from the tail of the same string.
        assertThat(monster797.getEngine().getMaxPowerHp()).isEqualByComparingTo("73.0");
        assertThat(monster797.getEngine().getMaxPowerRpm()).isEqualTo(8250);
        assertThat(monster797.getEngine().getMaxTorqueNm()).isEqualByComparingTo("67.0");
        assertThat(monster797.getEngine().getMaxTorqueRpm()).isEqualTo(5750);
        // "6-speed / Chain (final drive)": the trailing label is the source's name for the field, not part of the value.
        assertThat(monster797.getEngine().getGears()).isEqualTo(6);
        assertThat(monster797.getEngine().getFinalDrive()).isEqualTo("Chain");
        assertThat(monster797.getEngine().getEmissionStandard()).isEqualTo("Euro 4");
        assertThat(monster797.getDimension().getKerbWeightKg()).isEqualByComparingTo("193.0");
        assertThat(monster797.getDimension().getFuelCapacityL()).isEqualByComparingTo("16.5");
        // No ABS field is published: the word appears inside the brake prose, and only the bare word is recorded from it.
        assertThat(monster797.getAbsType()).isEqualTo("ABS");
        assertThat(monster797.getDescription()).startsWith("The Monster 797 is the entrance to the Ducati world");

        // Long-tail keys are the normalised kebab-case this import was specified with, which is a different vocabulary
        // from the Portuguese Title Case every other seed stores ('Rodas' above, 'rodas' here). See the seed header.
        String rodas = jdbcTemplate.queryForObject(
                "SELECT s.spec_value FROM motorcycle_additional_specs s JOIN motorcycles m ON m.id = s.motorcycle_id "
                        + "WHERE m.slug = 'ducati-monster-797-2018' AND s.spec_key = 'rodas'", String.class);
        assertThat(rodas).startsWith("10-spoke in light alloy");

        // Gap-fill never overwrites: the FIPE price survives, and image_url is untouched because the snapshot records
        // images as local scraper paths ("./img/...jpg") that ImageController could not serve.
        assertThat(monster797.getPriceEur()).isNotNull();
        assertThat(monster797.getImageUrl()).isNull();

        // Bore, stroke, cylinders and displacement have one algebraic relation, and COALESCE merges the four columns one
        // at a time - so a row can end up holding one seed's bore beside another's displacement. Scoped to brands no
        // per-brand seed covers, which makes FIPE plus this import the only two sources that can have mixed on a row.
        // The tolerance matches the one the seed enforces over its own rows before COMMIT. It is not tighter because
        // R__dev_seed.sql curated benelli-tnt-25-2024 as 249 cc against a 61.2 x 72 mm single, which sweeps 212 cc -
        // a 15% contradiction predating this import, on a row it does not touch.
        List<String> contradictoryGeometry = jdbcTemplate.queryForList(
                "SELECT m.slug || ' cc=' || e.displacement_cc || ' bore=' || e.bore_mm || ' stroke=' || e.stroke_mm "
                        + "|| ' cyl=' || e.cylinders FROM engine_specifications e JOIN motorcycles m ON m.engine_specification_id = e.id "
                        + "WHERE lower(m.brand) IN ('ducati', 'bajaj', 'dafra', 'benelli', 'cagiva', 'agrale', 'buell') "
                        + "AND e.bore_mm IS NOT NULL AND e.stroke_mm IS NOT NULL AND e.cylinders IS NOT NULL "
                        + "AND e.displacement_cc IS NOT NULL AND e.displacement_cc > 0 "
                        + "AND abs(pi() / 4 * e.bore_mm * e.bore_mm * e.stroke_mm * e.cylinders / 1000.0 - e.displacement_cc) "
                        + "     > 0.25 * e.displacement_cc",
                String.class);
        assertThat(contradictoryGeometry).isEmpty();

        // Every slug this import stages is a shape the public routing can use; the seed raises before COMMIT otherwise.
        Integer unroutableSlugs = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$'", Integer.class);
        assertThat(unroutableSlugs).isZero();
    }

    @Test
    @DisplayName("the Kawasaki import fills the specification blocks and the images the FIPE seed leaves empty")
    void loadsTheKawasakiSpecifications() {
        // Many rows publish torque twice and most pairs agree; this one reads "3.6 kgf-m / 103 Nm @ 9000 rpm", where 103 Nm
        // is 10.5 kgf.m. Taking the first unit printed would have stored a third of the real figure, so Nm is read first.
        Motorcycle zx10 = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-ninja-zx-10-zx-10r-1000cc-30th-aniv-1990").orElseThrow();
        assertThat(zx10.getEngine().getMaxTorqueNm()).isEqualByComparingTo("103.0");
        assertThat(zx10.getEngine().getCylinders()).isEqualTo(4);
        // 1000, not the 997 this import carries, and that is the gap-fill rule working: displacement_cc is the one engine
        // column the FIPE seed fills, from the descriptor "ZX-10/ ZX-10R 1000cc", so COALESCE keeps the rounder number.
        assertThat(zx10.getEngine().getDisplacementCc()).isEqualTo(1000);

        // The ZX-11's sheet transposes two labels ("Bore x Stroke: 11.0:1", "Compression Ratio: 76 x 58 mm") and the snapshot
        // inherited half the swap, so the shape check refuses the pair and the errata table puts the published ratio back.
        Motorcycle zx11 = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-ninja-zx-11-1100cc-1990").orElseThrow();
        assertThat(zx11.getEngine().getCompressionRatio()).isEqualTo("11.0:1");
        assertThat(zx11.getEngine().getBoreMm()).isEqualByComparingTo("76.00");
        assertThat(zx11.getEngine().getStrokeMm()).isEqualByComparingTo("58.00");
        // 6.54 l/100km from "15.3 lm/lit": there is no such unit as lm, the denominator is intact, and
        // the figure sits among the km/lit readings its sibling rows carry, so only the "k" is restored.
        assertThat(zx11.getEngine().getFuelConsumptionL100km()).isEqualByComparingTo("6.54");

        // "337 .0 kg / 739 lbs": a space in front of the decimal point. Left alone the unit-anchored read walks past the
        // stranded "337" and takes "0 kg", which the weight bound drops, losing a figure the row published perfectly clearly.
        Motorcycle nomad = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-vulcan-nomad-1500cc-1998").orElseThrow();
        assertThat(nomad.getDimension().getDryWeightKg()).isEqualByComparingTo("337.0");

        // "V-Twin" names the cylinder count without ever writing it next to "cylinder", while the "Four" in "Four stroke"
        // that opens every sheet must not be read as one: a rule taking the first number word would call the catalogue a four.
        Motorcycle vulcan750 = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-vulcan-vn-750cc-1991").orElseThrow();
        assertThat(vulcan750.getEngine().getCylinders()).isEqualTo(2);
        assertThat(vulcan750.getEngine().getFinalDrive()).isEqualTo("Shaft");

        // Gear counts are spelled out on the motocross sheets and this row's seat height is published in inches only, so it
        // is the one path that converts. Its source names "Chassis" as final drive, so the shape check refuses it and errata restores the chain.
        Motorcycle kx250 = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-kx-250-250-f-2005").orElseThrow();
        assertThat(kx250.getEngine().getGears()).isEqualTo(5);
        assertThat(kx250.getEngine().getFinalDrive()).isEqualTo("Chain");
        assertThat(kx250.getDimension().getSeatHeightMm()).isEqualTo(950);

        // The remaining two errata: the KLX 650's stroke is published as "S3mm", so the bound drops the fragment and the
        // published 83 mm goes in; the Versys 650's "1 9.3 km/lit" is refused whole (19.3 km/l is 5.18 l/100km).
        Motorcycle klx650 = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-klx-650-1995").orElseThrow();
        assertThat(klx650.getEngine().getStrokeMm()).isEqualByComparingTo("83.00");
        assertThat(klx650.getEngine().getBoreMm()).isEqualByComparingTo("100.00");
        assertThat(klx650.getEngine().getCylinders()).isEqualTo(1);

        Motorcycle versys = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-versys-650cc-2010").orElseThrow();
        assertThat(versys.getEngine().getFuelConsumptionL100km()).isEqualByComparingTo("5.18");

        // One of the four rows R__dev_seed.sql curated by hand, so the only Kawasakis where gap-fill is not full population:
        // the engine block is untouched (the tyre stays the spaced "120/70 ZR17") and only the photo left NULL is added.
        Motorcycle zx6r = motorcycleRepository.findWithSpecificationsBySlug("kawasaki-ninja-zx-6r-2024").orElseThrow();
        assertThat(zx6r.getFrontTyre()).isEqualTo("120/70 ZR17");
        assertThat(zx6r.getEngine().getDisplacementCc()).isEqualTo(636);
        assertThat(zx6r.getImageUrl()).matches("/api/v1/images/motorcycles/[0-9a-f-]+\\.jpg");

        // 187 of the 194 emitted rows carry a measurement, and the 1000ps import later gap-fills a dimension block
        // onto 13 more Kawasakis it shares a slug with. The rest are FIPE rows neither scrape reached.
        Integer withDimensions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'kawasaki' AND m.dimension_id IS NOT NULL"
                        + seededBefore1000ps("m"), Integer.class);
        assertThat(withDimensions).isEqualTo(200);

        // No sheet names a Euro or Proconve level and nothing is inferred from the model year, so this import sets no emission
        // standard at all. The research seed sets it on 420 of its 424 slugs: a count of 420 and not 421 shows they are disjoint.
        Integer withEmission = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m JOIN engine_specifications e ON e.id = m.engine_specification_id "
                        + "WHERE lower(m.brand) = 'kawasaki' AND e.emission_standard IS NOT NULL " + seededBefore1000ps("m")
                        + "AND m.slug NOT IN ('kawasaki-ninja-zx-6r-2024', 'kawasaki-z900-2024', "
                        + "'kawasaki-versys-1000-se-2024', 'kawasaki-z900-2026')", Integer.class);
        assertThat(withEmission).isEqualTo(420);

        // Every image URL stored must be a name ImageController can serve (a UUID plus jpg/png/webp), so anything else is a
        // silent 404; files are not in the repo. The four short of 194 are the KX 250 F and KX 450 F of 2006 and 2007.
        Integer servableImages = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'kawasaki'" + seededBefore1000ps("m") + "AND m.image_url ~ "
                        + "'^/api/v1/images/motorcycles/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$'",
                Integer.class);
        assertThat(servableImages).isEqualTo(190);
    }

    @Test
    @DisplayName("the researched Kawasaki engine import fills the engine blocks the FIPE seed leaves empty")
    void loadsTheResearchedKawasakiEngineSpecifications() {
        // 424 model-years the FIPE snapshot left with a thin or empty engine block, imported in two passes: 348 first, then
        // the 76 whose FIPE slug carries the displacement. Both files gap-fill, so the scraped figures survive any overlap.
        Integer withDisplacement = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m JOIN engine_specifications e "
                        + "ON e.id = m.engine_specification_id "
                        + "WHERE lower(m.brand) = 'kawasaki' AND e.displacement_cc IS NOT NULL", Integer.class);
        assertThat(withDisplacement).isGreaterThanOrEqualTo(424);

        // The second pass sits beside displacements the FIPE seed derived from the model name, and COALESCE keeps those.
        // Where the name rounds, the imported bore and stroke land against the rounded figure: hence a 2% tolerance, not tighter.
        Integer roundedButConsistent = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM engine_specifications e "
                        + "JOIN motorcycles m ON m.engine_specification_id = e.id "
                        + "WHERE lower(m.brand) = 'kawasaki' AND m.slug ~ '-(649|650|1000|1352)cc-' "
                        + "AND e.bore_mm IS NOT NULL AND e.displacement_cc IS NOT NULL", Integer.class);
        assertThat(roundedButConsistent).isGreaterThan(0);

        // The point of the import: no Kawasaki in the catalogue is left without an engine block.
        Integer withoutEngine = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE lower(m.brand) = 'kawasaki' " + seededBefore1000ps("m")
                        + "AND m.engine_specification_id IS NULL", Integer.class);
        assertThat(withoutEngine).isZero();

        // Bore, stroke, cylinders and displacement have one algebraic relation, so the quartet proves itself. This caught the
        // Z 750 in the source data (63.4 mm across four 50.9 mm cylinders is 643 cc, not 748 cc). 2% absorbs published rounding.
        List<String> contradictoryGeometry = jdbcTemplate.queryForList(
                "SELECT m.slug FROM engine_specifications e "
                        + "JOIN motorcycles m ON m.engine_specification_id = e.id "
                        + "WHERE lower(m.brand) = 'kawasaki' "
                        + "AND e.bore_mm IS NOT NULL AND e.stroke_mm IS NOT NULL "
                        + "AND e.cylinders IS NOT NULL AND e.displacement_cc IS NOT NULL "
                        + "AND abs(pi() / 4 * e.bore_mm * e.bore_mm * e.stroke_mm * e.cylinders / 1000.0 "
                        + "        - e.displacement_cc) > 0.02 * e.displacement_cc "
                        + seededBefore1000ps("m")
                        + "ORDER BY m.slug", String.class);
        // 22 rows fail this catalogue-wide, all from R__motorcycles_kawasaki_specs_2026_08.sql and none among the 348 imported
        // here. Pinned, not tolerated: it fails if the set grows or if any row this import is responsible for appears in it.
        assertThat(contradictoryGeometry).hasSize(22);
        assertThat(contradictoryGeometry).allSatisfy(slug -> assertThat(slug)
                .matches("kawasaki-(ninja-zx-11|ninja-zx-6r|ninja-zz-r|zx-14)-.*"));

        // The Z 750 erratum specifically, so the fix is pinned rather than merely tolerated.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT e.bore_mm FROM engine_specifications e JOIN motorcycles m "
                        + "ON m.engine_specification_id = e.id WHERE m.slug = 'kawasaki-z-750-2010'",
                java.math.BigDecimal.class)).isEqualByComparingTo("68.40");

        // Peak torque above peak power in the rev range means a swapped pair, which renders as a
        // perfectly plausible number in the comparison table and is therefore worth failing over.
        Integer swappedRpm = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM engine_specifications e "
                        + "JOIN motorcycles m ON m.engine_specification_id = e.id "
                        + "WHERE lower(m.brand) = 'kawasaki' AND e.max_power_rpm IS NOT NULL "
                        + "AND e.max_torque_rpm IS NOT NULL AND e.max_torque_rpm > e.max_power_rpm",
                Integer.class);
        assertThat(swappedRpm).isZero();

        // A spot-check that the researched figures land intact: the supercharged Z H2 is the only Kawasaki here making 200 hp
        // from under a litre, so it is the row most likely to be mangled by a parser that assumed naturally aspirated.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT e.max_power_hp FROM engine_specifications e JOIN motorcycles m "
                        + "ON m.engine_specification_id = e.id WHERE m.slug = 'kawasaki-z-1000-h2-2025'",
                java.math.BigDecimal.class)).isEqualByComparingTo("200.0");
    }

    @Test
    @DisplayName("no imported figure violates the weight CHECK the source data would otherwise trip")
    void importedWeightsRespectTheDryBelowKerbCheck() {
        // 70 source rows publish a dry weight above their own kerb weight (the CG 125 is listed at 114 kg dry against 100 kg
        // wet). The import drops the dry figure rather than the whole row, so a regenerated seed that stopped would fail here.
        Integer impossible = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dimensions WHERE dry_weight_kg > kerb_weight_kg", Integer.class);
        assertThat(impossible).isZero();
    }

    @Test
    @DisplayName("the 1000ps import adds the models the FIPE snapshot never carried, with no country attributed to them")
    void loadsThe1000psCatalogue() {
        // This source is a European catalogue, not a Brazilian-market one, so its models claim no market at all. That is
        // what the "zzzz" prefix buys: running after the Brazil backfill is the only thing that leaves these rows alone.
        Integer withoutCountry = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE NOT EXISTS "
                        + "(SELECT 1 FROM motorcycle_available_countries c WHERE c.motorcycle_id = m.id)", Integer.class);
        assertThat(withoutCountry).isEqualTo(4575);

        // One model carries the whole import: an inserted row with its engine, dimension and long-tail blocks, none of
        // which existed in the catalogue before. Its bore, stroke and cylinders sweep 659 cc, which is what it declares.
        Motorcycle tuareg = motorcycleRepository.findWithSpecificationsBySlug("aprilia-tuareg-660-2026").orElseThrow();

        assertThat(tuareg.getBrand()).isEqualTo("APRILIA");
        assertThat(tuareg.getCategory()).hasToString("ADVENTURE");
        assertThat(tuareg.getFrameType()).isEqualTo("Steel, Tubular");
        assertThat(tuareg.getAbsType()).isEqualTo("ABS");
        assertThat(tuareg.getPriceEur()).isEqualByComparingTo("10875.00");
        assertThat(tuareg.getEngine().getDisplacementCc()).isEqualTo(659);
        assertThat(tuareg.getEngine().getCylinders()).isEqualTo(2);
        assertThat(tuareg.getEngine().getValvesPerCylinder()).isEqualTo(4);
        assertThat(tuareg.getEngine().getMaxPowerHp()).isEqualByComparingTo("80.0");
        assertThat(tuareg.getEngine().getMaxTorqueNm()).isEqualByComparingTo("70.0");
        // "Chain, 6 marchas, Gearshift" arrives as one field and is split across three columns.
        assertThat(tuareg.getEngine().getTransmissionType()).isEqualTo("6-speed manual");
        assertThat(tuareg.getEngine().getGears()).isEqualTo(6);
        assertThat(tuareg.getEngine().getFinalDrive()).isEqualTo("Chain");
        // The source prints the ratio as a bare "13.5", so the ":1" the column stores everywhere else is put back.
        assertThat(tuareg.getEngine().getCompressionRatio()).isEqualTo("13.5:1");
        assertThat(tuareg.getDimension().getSeatHeightMm()).isEqualTo(860);

        String habilitacao = jdbcTemplate.queryForObject(
                "SELECT s.spec_value FROM motorcycle_additional_specs s JOIN motorcycles m ON m.id = s.motorcycle_id "
                        + "WHERE m.slug = 'aprilia-tuareg-660-2026' AND s.spec_key = 'Habilitação'", String.class);
        assertThat(habilitacao).isEqualTo("A2, A");

        // Quads, UTVs, Aixam microcars, e-bikes, pocketbikes, trikes and sidecar outfits are staged for gap-fill but
        // never inserted: this is a motorcycle catalogue. Aixam and Arctic Cat build nothing else, so neither appears.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE lower(brand) IN ('aixam', 'arctic cat')", Integer.class)).isZero();

        // Brand spelling is aligned with the catalogue, so this import never opens a second bucket for a manufacturer
        // the facet already lists. The FIPE snapshot inserted its brands in the source's own upper case and these rows
        // adopt it, hence "APRILIA" above; a brand new to the catalogue keeps the source's own spelling and is not here.
        List<String> unalignedBrands = jdbcTemplate.queryForList(
                "SELECT DISTINCT n.brand FROM motorcycles n WHERE NOT EXISTS "
                        + "(SELECT 1 FROM motorcycle_available_countries c WHERE c.motorcycle_id = n.id) "
                        + "AND EXISTS (SELECT 1 FROM motorcycles o WHERE lower(o.brand) = lower(n.brand)" + seededBefore1000ps("o") + ") "
                        + "AND NOT EXISTS (SELECT 1 FROM motorcycles o WHERE o.brand = n.brand" + seededBefore1000ps("o") + ")", String.class);
        assertThat(unalignedBrands).isEmpty();

        // The two spellings that differ from the catalogue by more than case are mapped before the slug is derived.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles WHERE brand IN ('GASGAS', 'Moto Morini')", Integer.class)).isZero();
    }

    @Test
    @DisplayName("backfills every motorcycle seeded from a Brazilian source as available in Brazil")
    void backfillsAvailableCountriesWithBrazil() {
        // Runs after every seed that sources a Brazilian-market snapshot (see brandImportsRunAfterTheFipeSeed), so it
        // must reach every row those inserted - not just the FIPE snapshot most of them start from. The only rows left
        // without a country are the 4575 the 1000ps import creates after it, which loadsThe1000psCatalogue covers.
        Integer withoutBrazil = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycles m WHERE NOT EXISTS "
                        + "(SELECT 1 FROM motorcycle_available_countries c WHERE c.motorcycle_id = m.id AND c.country_code = 'BR')",
                Integer.class);
        assertThat(withoutBrazil).isEqualTo(4575);

        // availableCountries is LAZY and open-in-view is false, so it is read back through SQL here rather
        // than the entity getter, which would throw LazyInitializationException outside a transaction.
        String country = jdbcTemplate.queryForObject(
                "SELECT c.country_code FROM motorcycle_available_countries c JOIN motorcycles m ON m.id = c.motorcycle_id "
                        + "WHERE m.slug = 'yamaha-mt-09-2024'", String.class);
        assertThat(country).isEqualTo("BR");
    }

    @Test
    @DisplayName("backfills the version column the entity now maps")
    void backfillsTheVersionColumn() {
        // A Ducati on purpose: the specification imports bump version on every row they gap-fill, and this test
        // is about V3's DEFAULT 0 backfill, not about what a later seed did to the row afterwards.
        assertThat(motorcycleRepository.findWithSpecificationsBySlug("ducati-panigale-v4-2024").orElseThrow().getVersion()).isZero();
    }

    @Test
    @DisplayName("the pg_trgm indexes that H2 cannot express are really there")
    void createsTheTrigramIndexes() {
        List<String> indexes = jdbcTemplate.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'motorcycles' ORDER BY indexname", String.class);

        assertThat(indexes).contains(
                "idx_motorcycles_brand_lower", "idx_motorcycles_brand_trgm", "idx_motorcycles_model_trgm",
                "idx_motorcycles_slug_trgm", "idx_motorcycles_category_price_eur");
    }

    @Test
    @DisplayName("an unpriced bike sorts last on real PostgreSQL, whichever way the price sort runs")
    void unpricedBikesSortLast() {
        // H2 hides this: PostgreSQL defaults DESC to NULLS FIRST, so without hibernate.order_by.default_null_ordering
        // (application.yml) the unpriced ones would surface on page 1 of "most expensive" instead of sorting last.
        Motorcycle unpriced = MotorcycleFixtures.motorcycle(null, "Prototype", "No Price", 900);
        unpriced.setPriceEur(null);
        Long id = motorcycleRepository.save(unpriced).getId();

        try {
            assertThat(lastBrandSortedByPrice(Sort.Direction.DESC)).isEqualTo("Prototype");
            assertThat(lastBrandSortedByPrice(Sort.Direction.ASC)).isEqualTo("Prototype");
        } finally {
            motorcycleRepository.deleteById(id);
        }
    }

    // A fixed-size page can't be trusted to reach the tail once the catalogue outgrows it, so the page is sized to the whole
    // table. The dev seed ships unpriced 2026 models, so price alone ties: break it by id, and the sentinel has the highest.
    private String lastBrandSortedByPrice(Sort.Direction direction) {
        int total = (int) motorcycleRepository.count();
        Sort sort = Sort.by(direction, "priceEur").and(Sort.by(Sort.Direction.ASC, "id"));
        List<Motorcycle> page = motorcycleRepository.findAll(PageRequest.of(0, total, sort)).getContent();
        return page.get(page.size() - 1).getBrand();
    }

    @Test
    @DisplayName("a CHECK constraint rejects a model year no manufacturer could have built")
    void checkConstraintRejectsBadModelYear() {
        assertThatThrownBy(() -> insertMotorcycle("check-constraint-probe", 1700))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a CHECK constraint rejects a slug that would break frontend routing")
    void checkConstraintRejectsBadSlug() {
        assertThatThrownBy(() -> insertMotorcycle("Not A Slug", 2024))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a CHECK constraint rejects a country code that is not ISO 3166-1 alpha-2")
    void checkConstraintRejectsBadCountryCode() {
        Long id = insertProbeMotorcycle("country-code-probe");

        try {
            // Lower case is the mistake the column width cannot catch, which is the whole point of the CHECK:
            // "br" fits VARCHAR(2) perfectly and would otherwise sit beside "BR" as a second, invisible row.
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO motorcycle_available_countries (motorcycle_id, country_code) VALUES (?, ?)", id, "br"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO motorcycle_available_countries (motorcycle_id, country_code) VALUES (?, ?)", id, "B1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.update("DELETE FROM motorcycles WHERE id = ?", id);
        }
    }

    @Test
    @DisplayName("the FOREIGN KEY takes the country rows with the motorcycle, without Hibernate in the way")
    void deletingAMotorcycleCascadesIntoTheCountryTable() {
        // A throwaway probe rather than a seeded bike: loadsTheDevSeed pins the catalogue count, and the delete
        // below is raw SQL, so nothing would put a removed row back. Only ON DELETE CASCADE can clear the children.
        Long id = insertProbeMotorcycle("country-cascade-probe");
        jdbcTemplate.update("INSERT INTO motorcycle_available_countries (motorcycle_id, country_code) VALUES (?, 'BR'), (?, 'US')", id, id);
        assertThat(countCountryRows(id)).isEqualTo(2);

        jdbcTemplate.update("DELETE FROM motorcycles WHERE id = ?", id);

        assertThat(countCountryRows(id)).isZero();
    }

    /** A predicate for the rows the catalogue held before the 1000ps import ran. That import is the only seed that leaves a
     *  motorcycle with no country, because it deliberately runs after the Brazil backfill, so BR is what separates the two. */
    private static String seededBefore1000ps(String alias) {
        return " AND EXISTS (SELECT 1 FROM motorcycle_available_countries bfc WHERE bfc.motorcycle_id = " + alias + ".id AND bfc.country_code = 'BR') ";
    }

    private Long insertProbeMotorcycle(String slug) {
        insertMotorcycle(slug, 2024);
        return jdbcTemplate.queryForObject("SELECT id FROM motorcycles WHERE slug = ?", Long.class, slug);
    }

    private Integer countCountryRows(Long motorcycleId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM motorcycle_available_countries WHERE motorcycle_id = ?", Integer.class, motorcycleId);
    }

    private void insertMotorcycle(String slug, int modelYear) {
        jdbcTemplate.update(
                "INSERT INTO motorcycles (slug, brand, model, model_year, category, created_at, updated_at) VALUES (?, 'Probe', 'Probe', ?, 'NAKED', now(), now())",
                slug, modelYear);
    }
}
