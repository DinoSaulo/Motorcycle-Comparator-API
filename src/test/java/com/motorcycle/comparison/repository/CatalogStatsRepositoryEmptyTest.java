package com.motorcycle.comparison.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/** The one behaviour worth its own class: with no motorcycles at all, every {@code SUM(CASE ...)} in
 *  {@link CatalogStatsRepository#fieldGaps()} collapses to a single row of NULLs, not zeroes — {@code SUM} over zero
 *  rows is NULL under three-valued SQL logic, same as {@code AVG}/{@code MIN}/{@code MAX}. {@link CatalogStatsService}
 *  is what turns that NULL into the zero an empty catalogue should report; this test pins the raw shape it must handle. */
@DataJpaTest(showSql = false)
@DisplayName("CatalogStatsRepository, empty catalogue")
class CatalogStatsRepositoryEmptyTest {

    @Autowired
    private CatalogStatsRepository catalogStatsRepository;

    @Test
    @DisplayName("field gaps come back as a single row of nulls, not zeroes")
    void fieldGapsAreNullNotZero() {
        CatalogStatsRepository.MotorcycleFieldGaps gaps = catalogStatsRepository.fieldGaps();

        assertThat(gaps.getPriceEur()).isNull();
        assertThat(gaps.getEngine()).isNull();
        assertThat(gaps.getDimension()).isNull();
    }

    @Test
    @DisplayName("price stats and lastUpdatedAt are null, with pricedCount at zero")
    void priceStatsAndLastUpdatedAreNull() {
        CatalogStatsRepository.PriceStats stats = catalogStatsRepository.priceStats();

        assertThat(stats.getMin()).isNull();
        assertThat(stats.getAvg()).isNull();
        assertThat(stats.getMax()).isNull();
        assertThat(stats.getPricedCount()).isZero();
        assertThat(catalogStatsRepository.lastUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("brand/category/model-year breakdowns are simply empty, not a list of zeroes")
    void breakdownsAreEmpty() {
        assertThat(catalogStatsRepository.countByBrand()).isEmpty();
        assertThat(catalogStatsRepository.countByCategory()).isEmpty();
        assertThat(catalogStatsRepository.countByModelYear()).isEmpty();
    }

    @Test
    @DisplayName("additional-specs counts are zero, via COUNT rather than SUM, so no null collapse applies")
    void additionalSpecsCountsAreZero() {
        assertThat(catalogStatsRepository.countAdditionalSpecEntries()).isZero();
        assertThat(catalogStatsRepository.countMotorcyclesWithoutAdditionalSpecs()).isZero();
    }
}
