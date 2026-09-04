package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.entity.Category;
import com.motorcycle.comparison.entity.Motorcycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest  // Já inclui @Transactional e rollback automático
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DisplayName("CatalogStatsRepository Integration Tests")
class CatalogStatsRepositoryIT {

    @Autowired
    private CatalogStatsRepository catalogStatsRepository;

    @Autowired
    private MotorcycleRepository motorcycleRepository;

    @Test
    @DisplayName("should_countMotorcyclesByBrand_ordered")
    void countByBrand_returnsOrderedCounts() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Yamaha", "YZF-R1", 998);
        Motorcycle m3 = MotorcycleFixtures.motorcycle(3L, "Honda", "CB500", 471);

        motorcycleRepository.saveAll(List.of(m1, m2, m3));

        List<CatalogStatsRepository.BrandCount> results = catalogStatsRepository.countByBrand();

        assertThat(results)
                .hasSize(2)
                .extracting(CatalogStatsRepository.BrandCount::getBrand)
                .containsExactly("Honda", "Yamaha"); // Alphabetical order
        assertThat(results)
                .extracting(CatalogStatsRepository.BrandCount::getTotal)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("should_countMotorcyclesByCategory")
    void countByCategory_returnsCounts() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Yamaha", "YZF-R1", 998);

        motorcycleRepository.saveAll(List.of(m1, m2));

        List<CatalogStatsRepository.CategoryCount> results = catalogStatsRepository.countByCategory();

        assertThat(results)
                .hasSize(1)
                .extracting(CatalogStatsRepository.CategoryCount::getCategory)
                .contains(Category.NAKED);
        assertThat(results)
                .extracting(CatalogStatsRepository.CategoryCount::getTotal)
                .contains(2L);
    }

    @Test
    @DisplayName("should_countMotorcyclesByModelYear_ordered")
    void countByModelYear_returnsOrderedCounts() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Honda", "CB500", 471);

        motorcycleRepository.saveAll(List.of(m1, m2));

        List<CatalogStatsRepository.ModelYearCount> results = catalogStatsRepository.countByModelYear();

        assertThat(results)
                .hasSize(1)
                .extracting(CatalogStatsRepository.ModelYearCount::getModelYear)
                .contains(2024);
        assertThat(results)
                .extracting(CatalogStatsRepository.ModelYearCount::getTotal)
                .contains(2L);
    }

    @Test
    @DisplayName("should_calculatePriceStats_minMaxAvgCount")
    void priceStats_calculatesCorrectly() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        m1.setPriceEur(new BigDecimal("10000.00"));

        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Honda", "CB500", 471);
        m2.setPriceEur(new BigDecimal("8000.00"));

        Motorcycle m3 = MotorcycleFixtures.motorcycle(3L, "Kawasaki", "Ninja", 650);
        m3.setPriceEur(new BigDecimal("12000.00"));

        motorcycleRepository.saveAll(List.of(m1, m2, m3));

        CatalogStatsRepository.PriceStats results = catalogStatsRepository.priceStats();

        assertThat(results.getMin()).isEqualByComparingTo("8000.00");
        assertThat(results.getMax()).isEqualByComparingTo("12000.00");
        assertThat(results.getAvg()).isCloseTo(10000.0, org.assertj.core.api.Assertions.offset(1.0));
        assertThat(results.getPricedCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("should_returnZeroPricedCount_whenNoPricesExist")
    void priceStats_withoutPrices() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        m1.setPriceEur(null);

        motorcycleRepository.save(m1);

        CatalogStatsRepository.PriceStats results = catalogStatsRepository.priceStats();

        assertThat(results.getMin()).isNull();
        assertThat(results.getAvg()).isNull();
        assertThat(results.getMax()).isNull();
        assertThat(results.getPricedCount()).isZero();
    }

    @Test
    @DisplayName("should_returnLatestUpdatedAt_timestamp")
    void lastUpdatedAt_returnsLatestTimestamp() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        motorcycleRepository.save(m1);

        Instant result = catalogStatsRepository.lastUpdatedAt();

        assertThat(result).isNotNull();
        assertThat(result).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    @DisplayName("should_countFieldGaps_forNullFields")
    void fieldGaps_countsNullFields() {
        // frameType/imageUrl/description are left null by MotorcycleFixtures#motorcycle on purpose
        // (see CatalogStatsRepositoryTest), so each is filled in explicitly here except the one gap
        // each motorcycle is meant to contribute.
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        m1.setFrameType("Aluminium Deltabox");
        m1.setDescription("A punchy naked triple");
        m1.setPriceEur(null);
        m1.setImageUrl(null);

        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Honda", "CB500", 471);
        m2.setFrameType("Steel diamond");
        m2.setImageUrl("https://cdn.example.com/cb500.jpg");
        m2.setDescription(null);

        motorcycleRepository.saveAll(List.of(m1, m2));

        CatalogStatsRepository.MotorcycleFieldGaps gaps = catalogStatsRepository.fieldGaps();

        assertThat(gaps.getPriceEur()).isEqualTo(1L);
        assertThat(gaps.getImageUrl()).isEqualTo(1L);
        assertThat(gaps.getDescription()).isEqualTo(1L);
        assertThat(gaps.getFrameType()).isEqualTo(0L);
    }

    @Test
    @DisplayName("should_returnZeroGaps_whenAllFieldsAreFilled")
    void fieldGaps_zeroWhenCompleted() {
        // imageUrl is left null by the fixture on purpose, so it needs an explicit value here.
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        m1.setImageUrl("https://cdn.example.com/mt-09.jpg");
        motorcycleRepository.save(m1);

        CatalogStatsRepository.MotorcycleFieldGaps gaps = catalogStatsRepository.fieldGaps();

        assertThat(gaps.getPriceEur()).isZero();
        assertThat(gaps.getImageUrl()).isZero();
    }

    @Test
    @DisplayName("should_returnZeroStats_forEmptyDatabase")
    void empty_database() {
        assertThat(motorcycleRepository.count()).isZero();

        CatalogStatsRepository.MotorcycleFieldGaps gaps = catalogStatsRepository.fieldGaps();

        // SUM(CASE ...) over zero rows is NULL, not zero - see CatalogStatsRepository#fieldGaps javadoc.
        assertThat(gaps.getPriceEur()).isNull();
        assertThat(gaps.getImageUrl()).isNull();
    }

    @Test
    @DisplayName("should_countAdditionalSpecEntries")
    void countAdditionalSpecEntries_returnsTotal() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        m1.getAdditionalSpecs().put("Rider modes", "4");
        m1.getAdditionalSpecs().put("ABS modes", "2");

        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Honda", "CB500", 471);
        m2.getAdditionalSpecs().put("Custom paint", "Yes");

        motorcycleRepository.saveAll(List.of(m1, m2));

        long count = catalogStatsRepository.countAdditionalSpecEntries();

        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("should_countMotorcyclesWithoutAdditionalSpecs")
    void countMotorcyclesWithoutAdditionalSpecs_returnsCount() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        m1.getAdditionalSpecs().clear();

        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Honda", "CB500", 471);
        m2.getAdditionalSpecs().put("Key", "Value");

        motorcycleRepository.saveAll(List.of(m1, m2));

        long count = catalogStatsRepository.countMotorcyclesWithoutAdditionalSpecs();

        assertThat(count).isEqualTo(1L);
    }
}
