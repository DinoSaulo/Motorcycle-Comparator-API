package com.motorcycle.comparison.dto.response;

import com.motorcycle.comparison.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Read model for the admin catalogue dashboard: totals, breakdowns and how much of each specification block is
 *  still missing. {@code category}, {@code createdAt}, {@code updatedAt} and {@code version} are absent from
 *  {@code motorcycleFieldGaps} on purpose: all four are {@code NOT NULL} in the schema, so their gap count would
 *  always be zero. */
@Schema(description = "Catalogue-wide totals, breakdowns and data-completeness counts, for the admin dashboard")
public record CatalogStatsResponse(
        long totalMotorcycles,
        Map<String, Long> byBrand,
        Map<Category, Long> byCategory,
        Map<Integer, Long> byModelYear,
        PriceStats priceEur,
        Instant lastUpdatedAt,
        Map<String, Long> motorcycleFieldGaps,
        RelatedTableStats engineSpecifications,
        RelatedTableStats dimensions,
        AdditionalSpecsStats additionalSpecs
) {

    public record PriceStats(BigDecimal min, BigDecimal avg, BigDecimal max, long pricedCount) {
    }

    /** {@code motorcyclesWithoutRow} is never a second query: it is exactly the {@code engine}/{@code dimension}
     *  entry of {@link CatalogStatsResponse#motorcycleFieldGaps}, the same missing foreign key counted from the
     *  other side. */
    public record RelatedTableStats(long totalRows, long motorcyclesWithoutRow, Map<String, Long> fieldGaps) {
    }

    public record AdditionalSpecsStats(long totalEntries, long motorcyclesWithoutAny) {
    }
}
