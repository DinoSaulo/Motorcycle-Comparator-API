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

/**
 * The only test that proves the migrations and the entities describe the same database: the slice tests run on H2
 * with {@code create-drop}, which cannot parse the functional and GIN indexes and so never sees the schema for real.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration,classpath:db/search,classpath:db/seed",
        // Boots against the migrated schema: any drift between an entity and a migration fails here, loudly.
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@DisplayName("Schema migrations")
class SchemaMigrationIT {

    /**
     * Testcontainers' singleton pattern: one static container started here and reaped by Ryuk at JVM exit, so the
     * image is pulled once and no junit-jupiter integration artifact is needed just to call start().
     */
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

        assertThat(applied).containsExactly("1", "2", "3");
        assertThat(repeatables).isEqualTo(1);
    }

    @Test
    @DisplayName("loads the dev seed")
    void loadsTheDevSeed() {
        assertThat(motorcycleRepository.count()).isEqualTo(53);
        assertThat(motorcycleRepository.findWithSpecificationsBySlug("yamaha-mt-09-2024")).isPresent();
    }

    @Test
    @DisplayName("backfills the version column the entity now maps")
    void backfillsTheVersionColumn() {
        assertThat(motorcycleRepository.findWithSpecificationsBySlug("yamaha-mt-09-2024").orElseThrow().getVersion()).isZero();
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
        // H2 hides this: PostgreSQL defaults DESC to NULLS FIRST, so page 1 of "most expensive" would be the unpriced ones.
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

    private String lastBrandSortedByPrice(Sort.Direction direction) {
        List<Motorcycle> page = motorcycleRepository.findAll(PageRequest.of(0, 100, Sort.by(direction, "priceEur"))).getContent();
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

    private void insertMotorcycle(String slug, int modelYear) {
        jdbcTemplate.update(
                "INSERT INTO motorcycles (slug, brand, model, model_year, category, created_at, updated_at) VALUES (?, 'Probe', 'Probe', ?, 'NAKED', now(), now())",
                slug, modelYear);
    }
}
