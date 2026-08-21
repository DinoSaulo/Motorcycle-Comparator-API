package com.motorcycle.comparison.dto.request;

import com.motorcycle.comparison.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Catalogue facets, bound from the query string. Every field is optional and an
 * absent field means "no constraint" — the service composes only what is present.
 */
@Schema(description = "Optional catalogue filters; omitted fields are unconstrained")
public record MotorcycleFilter(
        @Schema(example = "Yamaha") String brand,
        @Schema(example = "NAKED") Category category,
        @Schema(example = "2024") Integer modelYear,
        @Schema(example = "600") Integer minDisplacementCc,
        @Schema(example = "1000") Integer maxDisplacementCc,
        @Schema(example = "80") BigDecimal minPowerHp,
        BigDecimal minPriceEur,
        @Schema(example = "15000") BigDecimal maxPriceEur,
        @Schema(description = "Free text matched against brand, model and slug", example = "mt-09")
        String q
) {}
