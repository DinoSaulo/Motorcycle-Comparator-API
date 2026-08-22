package com.motorcycle.comparison.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Row-oriented projection of a comparison, shaped for a side-by-side table so the backend — not React — owns every
 * spec name, unit, display order and "better" direction, keeping the table component a dumb renderer over rows.
 */
@Schema(description = "Side-by-side comparison, pre-shaped as table rows")
public record ComparisonResponse(

        @Schema(description = "Column headers, in request order")
        List<MotorcycleResponse> motorcycles,

        @Schema(description = "Table body, grouped into collapsible sections")
        List<SpecGroup> groups
) {

    @Schema(description = "A collapsible section of the comparison table")
    public record SpecGroup(String name, List<SpecRow> rows) {}

    /**
     * @param values one entry per compared motorcycle, index-aligned with {@code motorcycles}; {@code null} means "not published" and must render as a dash, never as 0
     * @param winnerIndexes indexes of the best value(s) for this row, empty when the spec is not rankable or everything ties
     */
    @Schema(description = "A single specification line across all compared bikes")
    public record SpecRow(
            String label,
            String unit,
            List<String> values,
            List<Integer> winnerIndexes,
            boolean differing
    ) {}
}
