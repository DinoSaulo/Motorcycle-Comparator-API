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
        AdditionalSpecsStats additionalSpecs,
        ListPriceResearchStats listPriceResearch
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

    /** Deliberately separate from {@code priceEur}: a researched manufacturer/importer list price lives in
     *  {@code motorcycle_additional_specs} under the key {@code 'List price (EUR)'} and never reaches
     *  {@code price_eur} - see {@code R__zzzz_motorcycles_list_price_2026_09.sql} for why merging the two would
     *  corrupt what {@code price_eur} means for sorting and range filters. {@code motorcyclesWithNoPriceInfo} is
     *  the number {@code motorcycleFieldGaps.priceEur} alone cannot answer: how many motorcycles have no price
     *  of any kind, comparable or not. */
    public record ListPriceResearchStats(long motorcyclesCovered, long motorcyclesWithNoPriceInfo) {
    }
}
