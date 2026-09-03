package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.entity.Category;
import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.repository.CatalogStatsRepository.BrandCount;
import com.motorcycle.comparison.repository.CatalogStatsRepository.CategoryCount;
import com.motorcycle.comparison.repository.CatalogStatsRepository.ModelYearCount;
import com.motorcycle.comparison.repository.CatalogStatsRepository.MotorcycleFieldGaps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Exercises the admin dashboard's aggregate queries against real seeded data. The empty-catalogue edge case (every
 *  {@code SUM(CASE ...)} collapsing to NULL) is covered separately in {@link CatalogStatsRepositoryEmptyTest}: mixing
 *  a seeded and an unseeded scenario in one class would mean re-seeding per test just to keep the other one clean. */
@DataJpaTest(showSql = false)
@DisplayName("CatalogStatsRepository")
class CatalogStatsRepositoryTest {

    @Autowired
    private CatalogStatsRepository catalogStatsRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void seed() {
        Motorcycle mt125 = MotorcycleFixtures.motorcycle(null, "Yamaha", "MT-125", 125);
        // frameType/frontSuspension/rearSuspension/frontTyre/rearTyre/imageUrl/description are left null by the
        // fixture on purpose, so this motorcycle alone already exercises every one of those gap counts.

        Motorcycle mt09 = MotorcycleFixtures.motorcycle(null, "Yamaha", "MT-09", 890);
        mt09.setCategory(Category.SPORT);
        mt09.setModelYear(2023);
        mt09.setPriceEur(null);

        Motorcycle gs1300 = MotorcycleFixtures.motorcycle(null, "BMW", "R 1300 GS", 1300);
        gs1300.setCategory(Category.ADVENTURE);
        gs1300.setPriceEur(new BigDecimal("20000.00"));
        gs1300.setEngine(null);
        gs1300.setDimension(null);
        gs1300.setAdditionalSpecs(Map.of("Heated grips", "Yes", "Cruise control", "Yes"));

        entityManager.persist(mt125);
        entityManager.persist(mt09);
        entityManager.persist(gs1300);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("counts by brand, alphabetically")
    void countsByBrand() {
        assertThat(catalogStatsRepository.countByBrand())
                .extracting(BrandCount::getBrand, BrandCount::getTotal)
                .containsExactly(tuple("BMW", 1L), tuple("Yamaha", 2L));
    }

    @Test
    @DisplayName("counts by category")
    void countsByCategory() {
        assertThat(catalogStatsRepository.countByCategory())
                .extracting(CategoryCount::getCategory, CategoryCount::getTotal)
                .containsExactlyInAnyOrder(tuple(Category.NAKED, 1L), tuple(Category.SPORT, 1L), tuple(Category.ADVENTURE, 1L));
    }

    @Test
    @DisplayName("counts by model year, ascending")
    void countsByModelYear() {
        assertThat(catalogStatsRepository.countByModelYear())
                .extracting(ModelYearCount::getModelYear, ModelYearCount::getTotal)
                .containsExactly(tuple(2023, 1L), tuple(2024, 2L));
    }

    @Test
    @DisplayName("price stats ignore the motorcycle with no price, both in the aggregates and in pricedCount")
    void priceStatsExcludeUnpricedMotorcycles() {
        CatalogStatsRepository.PriceStats stats = catalogStatsRepository.priceStats();

        assertThat(stats.getMin()).isEqualByComparingTo("10000.00");
        assertThat(stats.getMax()).isEqualByComparingTo("20000.00");
        assertThat(stats.getAvg()).isEqualTo(15000.0);
        assertThat(stats.getPricedCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("lastUpdatedAt reflects the most recently persisted row")
    void lastUpdatedAtIsPopulated() {
        assertThat(catalogStatsRepository.lastUpdatedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("field gaps: zero for a column every seeded motorcycle fills in")
    void fieldGapsAreZeroForAFullyPopulatedColumn() {
        MotorcycleFieldGaps gaps = catalogStatsRepository.fieldGaps();

        assertThat(gaps.getFrontBrake()).isZero();
        assertThat(gaps.getRearBrake()).isZero();
        assertThat(gaps.getAbsType()).isZero();
    }

    @Test
    @DisplayName("field gaps: counts the motorcycles actually missing each nullable column")
    void fieldGapsCountMissingColumns() {
        MotorcycleFieldGaps gaps = catalogStatsRepository.fieldGaps();

        assertThat(gaps.getPriceEur()).isEqualTo(1L);
        assertThat(gaps.getImageUrl()).isEqualTo(3L);
        assertThat(gaps.getFrameType()).isEqualTo(3L);
        assertThat(gaps.getEngine()).isEqualTo(1L);
        assertThat(gaps.getDimension()).isEqualTo(1L);
    }

    @Test
    @DisplayName("additional specs: total entries and how many motorcycles carry none")
    void additionalSpecsCounts() {
        assertThat(catalogStatsRepository.countAdditionalSpecEntries()).isEqualTo(2L);
        assertThat(catalogStatsRepository.countMotorcyclesWithoutAdditionalSpecs()).isEqualTo(2L);
    }
}
