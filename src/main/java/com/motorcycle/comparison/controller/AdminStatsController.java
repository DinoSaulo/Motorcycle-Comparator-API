package com.motorcycle.comparison.controller;

import com.motorcycle.comparison.dto.response.CatalogStatsResponse;
import com.motorcycle.comparison.service.CatalogStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operational visibility for administrators: catalogue totals and how complete the data behind it is. Lives under
 *  {@code /api/v1/admin} rather than {@code /motorcycles} so its {@code ROLE_ADMIN} rule never has to out-rank the
 *  public GET wildcard that path already carries — see {@code SecurityConfig#ADMIN_PATH}. */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Operational endpoints for administrators")
public class AdminStatsController {

    private final CatalogStatsService catalogStatsService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Catalogue health", description = "Totals, breakdowns by brand/category/model year, price stats, and how many motorcycles are missing each field.")
    public CatalogStatsResponse stats() {
        return catalogStatsService.getStats();
    }
}
