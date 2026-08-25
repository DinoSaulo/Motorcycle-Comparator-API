package com.motorcycle.comparison.controller;

import com.motorcycle.comparison.dto.request.CreateMotorcycleRequest;
import com.motorcycle.comparison.dto.request.MotorcycleFilter;
import com.motorcycle.comparison.dto.response.ApiError;
import com.motorcycle.comparison.dto.response.ComparisonResponse;
import com.motorcycle.comparison.dto.response.MotorcycleResponse;
import com.motorcycle.comparison.service.ComparisonService;
import com.motorcycle.comparison.service.MotorcycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Public catalogue and administrative CRUD. The controller stays thin on purpose: bind, delegate, map to a status
 * code — business rules and persistence concerns belong to the services below it.
 */
@RestController
@RequestMapping("/api/v1/motorcycles")
@RequiredArgsConstructor
@Tag(name = "Motorcycles", description = "Browse and administer the motorcycle catalogue")
public class MotorcycleController {

    private final MotorcycleService motorcycleService;
    private final ComparisonService comparisonService;

    @GetMapping
    @Operation(summary = "Search the catalogue", description = "Every filter is optional and combinable. Supports paging and sorting, e.g. `?sort=priceEur,asc&size=20`.")
    public Page<MotorcycleResponse> search(@ParameterObject @Valid @ModelAttribute MotorcycleFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "brand", direction = Sort.Direction.ASC) Pageable pageable) {
        return motorcycleService.search(filter, pageable);
    }

    @GetMapping("/brands")
    @Operation(summary = "List distinct brands", description = "Feeds the catalogue filter sidebar.")
    public List<String> brands() {
        return motorcycleService.listBrands();
    }

    /**
     * Comparison is exposed under the collection it operates on, and as GET, so it is a shareable, cacheable
     * URL — no second base path needed, and that is how people actually use these pages.
     */
    @GetMapping("/compare")
    @Operation(summary = "Compare motorcycles side by side", description = "Returns the comparison pre-shaped as table rows. Between 2 and 4 distinct ids.")
    @ApiResponse(responseCode = "200", description = "Comparison table")
    @ApiResponse(responseCode = "400", description = "Too few or too many ids", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "At least one id does not exist", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ComparisonResponse compare(
            @Parameter(description = "Motorcycle ids, in the order the columns should appear", example = "1,2,3")
            @RequestParam("ids") List<Long> ids) {
        return comparisonService.compare(ids);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one motorcycle with all its specifications")
    @ApiResponse(responseCode = "404", description = "Unknown id", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public MotorcycleResponse getById(@PathVariable Long id) {
        return motorcycleService.getById(id);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Fetch one motorcycle by its public slug")
    @ApiResponse(responseCode = "404", description = "Unknown slug", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public MotorcycleResponse getBySlug(@PathVariable String slug) {
        return motorcycleService.getBySlug(slug);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a motorcycle")
    @ApiResponse(responseCode = "201", description = "Created; Location header carries the new URI")
    public ResponseEntity<MotorcycleResponse> create(@Valid @RequestBody CreateMotorcycleRequest request, UriComponentsBuilder uriBuilder) {
        MotorcycleResponse created = motorcycleService.create(request);
        URI location = uriBuilder.path("/api/v1/motorcycles/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Replace a motorcycle", description = "Full replacement: omitted optional fields are cleared.")
    public MotorcycleResponse update(@PathVariable Long id, @Valid @RequestBody CreateMotorcycleRequest request) {
        return motorcycleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a motorcycle")
    @ApiResponse(responseCode = "204", description = "Deleted")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        motorcycleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Image upload is its own endpoint rather than a field on the JSON body: multipart and a full-replacement PUT do
     * not mix, and this way editing a specification never forces the client to re-send the binary it did not change.
     */
    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Upload or replace the image of a motorcycle",
            description = "Accepts JPEG, PNG or WebP. The declared content type must agree with the file's own bytes. Any previously uploaded image is discarded.")
    @ApiResponse(responseCode = "200", description = "Image stored; the motorcycle carries its new imageUrl")
    @ApiResponse(responseCode = "400", description = "Empty, oversized or unsupported image", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Unknown id", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "413", description = "Image exceeds the configured maximum size", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public MotorcycleResponse uploadImage(@PathVariable Long id,
            @Parameter(description = "The image file", required = true)
            @RequestPart("file") MultipartFile file) {
        return motorcycleService.updateImage(id, file);
    }

    /**
     * 200 with the record, not the 204 that deleting the motorcycle itself returns: this deletes a field of a resource
     * that survives the call, and the client needs the updated record anyway to re-render without the image.
     */
    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Remove the image of a motorcycle")
    @ApiResponse(responseCode = "200", description = "Image cleared")
    @ApiResponse(responseCode = "404", description = "Unknown id", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public MotorcycleResponse deleteImage(@PathVariable Long id) {
        return motorcycleService.removeImage(id);
    }
}
