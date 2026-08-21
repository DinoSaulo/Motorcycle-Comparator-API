package com.motorcycle.comparison.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Row-oriented projection of a comparison, shaped for a side-by-side table.
 *
 * <p>Design note: the frontend receives rows, not objects. Rendering a comparison
 * table from a list of {@link MotorcycleResponse} would force React to know every
 * spec name, its unit, its display order and which direction counts as "better" —
 * that knowledge would then live in two codebases and drift. Here the backend owns
 * it, and the table component becomes a dumb renderer over
 * {@code groups -> rows -> values[]}, where {@code values[i]} always belongs to
 * {@code motorcycles[i]}.
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
     * @param values      one entry per compared motorcycle, index-aligned with
     *                    {@code motorcycles}; {@code null} means "not published"
     *                    and must render as a dash, never as 0
     * @param winnerIndexes indexes of the best value(s) for this row, empty when the
     *                    spec is not rankable (free text) or when everything ties
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
