package com.motorcycle.comparison.service;

import com.motorcycle.comparison.dto.response.CatalogStatsResponse;
import com.motorcycle.comparison.dto.response.CatalogStatsResponse.AdditionalSpecsStats;
import com.motorcycle.comparison.dto.response.CatalogStatsResponse.RelatedTableStats;
import com.motorcycle.comparison.entity.Category;
import com.motorcycle.comparison.repository.CatalogStatsRepository;
import com.motorcycle.comparison.repository.CatalogStatsRepository.BrandCount;
import com.motorcycle.comparison.repository.CatalogStatsRepository.CategoryCount;
import com.motorcycle.comparison.repository.CatalogStatsRepository.ModelYearCount;
import com.motorcycle.comparison.repository.CatalogStatsRepository.MotorcycleFieldGaps;
import com.motorcycle.comparison.repository.DimensionRepository;
import com.motorcycle.comparison.repository.DimensionRepository.DimensionFieldGaps;
import com.motorcycle.comparison.repository.EngineSpecificationRepository;
import com.motorcycle.comparison.repository.EngineSpecificationRepository.EngineFieldGaps;
import com.motorcycle.comparison.repository.MotorcycleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogStatsService")
class CatalogStatsServiceTest {

    @Mock
    private MotorcycleRepository motorcycleRepository;

    @Mock
    private CatalogStatsRepository catalogStatsRepository;

    @Mock
    private EngineSpecificationRepository engineSpecificationRepository;

    @Mock
    private DimensionRepository dimensionRepository;

    @InjectMocks
    private CatalogStatsService catalogStatsService;

    @Nested
    @DisplayName("a populated catalogue")
    class PopulatedCatalogue {

        @Test
        @DisplayName("carries totals, breakdowns and price stats straight through, in the order the repository returned them")
        void assemblesTotalsAndBreakdowns() {
            List<BrandCount> brands = List.of(brandCount("Honda", 5), brandCount("Yamaha", 3));
            List<CategoryCount> categories = List.of(categoryCount(Category.NAKED, 6), categoryCount(Category.SPORT, 2));
            List<ModelYearCount> years = List.of(modelYearCount(2023, 4), modelYearCount(2024, 4));
            CatalogStatsRepository.PriceStats price = priceStats(new BigDecimal("3990"), 12430.5, new BigDecimal("45000"), 6);
            Instant lastUpdated = Instant.parse("2026-09-01T10:00:00Z");

            when(motorcycleRepository.count()).thenReturn(142L);
            when(catalogStatsRepository.countByBrand()).thenReturn(brands);
            when(catalogStatsRepository.countByCategory()).thenReturn(categories);
            when(catalogStatsRepository.countByModelYear()).thenReturn(years);
            when(catalogStatsRepository.priceStats()).thenReturn(price);
            when(catalogStatsRepository.lastUpdatedAt()).thenReturn(lastUpdated);
            when(catalogStatsRepository.fieldGaps()).thenReturn(mock(MotorcycleFieldGaps.class));
            when(engineSpecificationRepository.count()).thenReturn(8L);
            when(engineSpecificationRepository.fieldGaps()).thenReturn(mock(EngineFieldGaps.class));
            when(dimensionRepository.count()).thenReturn(7L);
            when(dimensionRepository.fieldGaps()).thenReturn(mock(DimensionFieldGaps.class));
            when(catalogStatsRepository.countAdditionalSpecEntries()).thenReturn(20L);
            when(catalogStatsRepository.countMotorcyclesWithoutAdditionalSpecs()).thenReturn(3L);

            CatalogStatsResponse result = catalogStatsService.getStats();

            assertThat(result.totalMotorcycles()).isEqualTo(142L);
            assertThat(result.byBrand()).containsExactly(Map.entry("Honda", 5L), Map.entry("Yamaha", 3L));
            assertThat(result.byCategory()).containsExactly(Map.entry(Category.NAKED, 6L), Map.entry(Category.SPORT, 2L));
            assertThat(result.byModelYear()).containsExactly(Map.entry(2023, 4L), Map.entry(2024, 4L));
            assertThat(result.lastUpdatedAt()).isEqualTo(lastUpdated);
            assertThat(result.additionalSpecs()).isEqualTo(new AdditionalSpecsStats(20L, 3L));
        }

        @Test
        @DisplayName("rounds the average price to 2 decimals, half-up, and passes min/max/pricedCount straight through")
        void roundsAveragePrice() {
            CatalogStatsRepository.PriceStats price = priceStats(new BigDecimal("3990.00"), 12430.567, new BigDecimal("45000.00"), 6);
            stubEmptyBreakdowns();
            when(catalogStatsRepository.priceStats()).thenReturn(price);
            stubEmptyGaps();

            CatalogStatsResponse.PriceStats result = catalogStatsService.getStats().priceEur();

            assertThat(result.min()).isEqualByComparingTo("3990.00");
            assertThat(result.avg()).isEqualByComparingTo("12430.57");
            assertThat(result.max()).isEqualByComparingTo("45000.00");
            assertThat(result.pricedCount()).isEqualTo(6L);
        }

        @Test
        @DisplayName("reuses the motorcycle-side engine/dimension gap counts as motorcyclesWithoutRow, instead of a second query")
        void reusesFieldGapCountsForRelatedTables() {
            MotorcycleFieldGaps gaps = mock(MotorcycleFieldGaps.class);
            when(gaps.getEngine()).thenReturn(3L);
            when(gaps.getDimension()).thenReturn(8L);
            CatalogStatsRepository.PriceStats emptyPrice = priceStats(null, null, null, 0);
            stubEmptyBreakdowns();
            when(catalogStatsRepository.priceStats()).thenReturn(emptyPrice);
            when(catalogStatsRepository.fieldGaps()).thenReturn(gaps);
            when(engineSpecificationRepository.fieldGaps()).thenReturn(mock(EngineFieldGaps.class));
            when(dimensionRepository.fieldGaps()).thenReturn(mock(DimensionFieldGaps.class));

            CatalogStatsResponse result = catalogStatsService.getStats();

            assertThat(result.engineSpecifications().motorcyclesWithoutRow()).isEqualTo(3L);
            assertThat(result.dimensions().motorcyclesWithoutRow()).isEqualTo(8L);
        }
    }

    @Nested
    @DisplayName("an empty catalogue")
    class EmptyCatalogue {

        @Test
        @DisplayName("every gap count comes back as zero, never null: SUM(CASE ...) over zero rows is NULL, not zero")
        void emptyCatalogueNeverLeaksANullGapCount() {
            CatalogStatsRepository.PriceStats emptyPrice = priceStats(null, null, null, 0);
            when(motorcycleRepository.count()).thenReturn(0L);
            stubEmptyBreakdowns();
            when(catalogStatsRepository.priceStats()).thenReturn(emptyPrice);
            when(catalogStatsRepository.lastUpdatedAt()).thenReturn(null);
            // An unstubbed mock getter on these interfaces returns null, exactly like every SUM(CASE ...)
            // column does when the table behind it has no rows: this is the real-world empty-table shape.
            stubEmptyGaps();
            when(catalogStatsRepository.countAdditionalSpecEntries()).thenReturn(0L);
            when(catalogStatsRepository.countMotorcyclesWithoutAdditionalSpecs()).thenReturn(0L);

            CatalogStatsResponse result = catalogStatsService.getStats();

            assertThat(result.motorcycleFieldGaps().values()).allMatch(value -> value == 0L);
            assertThat(result.engineSpecifications()).isEqualTo(new RelatedTableStats(0L, 0L, allZero(
                    "engineType", "displacementCc", "cylinders", "valvesPerCylinder", "maxPowerHp", "maxPowerRpm",
                    "maxTorqueNm", "maxTorqueRpm", "compressionRatio", "boreMm", "strokeMm", "coolingSystem",
                    "fuelSystem", "transmissionType", "gears", "finalDrive", "topSpeedKph", "fuelConsumptionL100km", "emissionStandard")));
            assertThat(result.dimensions()).isEqualTo(new RelatedTableStats(0L, 0L, allZero(
                    "lengthMm", "widthMm", "heightMm", "wheelbaseMm", "seatHeightMm", "groundClearanceMm",
                    "kerbWeightKg", "dryWeightKg", "fuelCapacityL", "payloadKg")));
            assertThat(result.priceEur()).isEqualTo(new CatalogStatsResponse.PriceStats(null, null, null, 0L));
            assertThat(result.lastUpdatedAt()).isNull();
        }
    }

    // --- helpers ------------------------------------------------------------

    private void stubEmptyBreakdowns() {
        when(catalogStatsRepository.countByBrand()).thenReturn(List.of());
        when(catalogStatsRepository.countByCategory()).thenReturn(List.of());
        when(catalogStatsRepository.countByModelYear()).thenReturn(List.of());
    }

    private void stubEmptyGaps() {
        when(catalogStatsRepository.fieldGaps()).thenReturn(mock(MotorcycleFieldGaps.class));
        when(engineSpecificationRepository.fieldGaps()).thenReturn(mock(EngineFieldGaps.class));
        when(dimensionRepository.fieldGaps()).thenReturn(mock(DimensionFieldGaps.class));
    }

    private static Map<String, Long> allZero(String... keys) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (String key : keys) {
            map.put(key, 0L);
        }
        return map;
    }

    // Every helper below only builds and stubs its own mock; none of them touches another mock, so it is
    // always safe to call one to completion before starting the next when(...) on the repository mocks above.

    private static BrandCount brandCount(String brand, long total) {
        BrandCount row = mock(BrandCount.class);
        when(row.getBrand()).thenReturn(brand);
        when(row.getTotal()).thenReturn(total);
        return row;
    }

    private static CategoryCount categoryCount(Category category, long total) {
        CategoryCount row = mock(CategoryCount.class);
        when(row.getCategory()).thenReturn(category);
        when(row.getTotal()).thenReturn(total);
        return row;
    }

    private static ModelYearCount modelYearCount(int modelYear, long total) {
        ModelYearCount row = mock(ModelYearCount.class);
        when(row.getModelYear()).thenReturn(modelYear);
        when(row.getTotal()).thenReturn(total);
        return row;
    }

    private static CatalogStatsRepository.PriceStats priceStats(BigDecimal min, Double avg, BigDecimal max, long pricedCount) {
        CatalogStatsRepository.PriceStats row = mock(CatalogStatsRepository.PriceStats.class);
        when(row.getMin()).thenReturn(min);
        when(row.getAvg()).thenReturn(avg);
        when(row.getMax()).thenReturn(max);
        when(row.getPricedCount()).thenReturn(pricedCount);
        return row;
    }
}
