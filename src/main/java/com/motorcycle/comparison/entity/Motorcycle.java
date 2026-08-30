package com.motorcycle.comparison.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Aggregate root of the catalogue: structured columns ({@link EngineSpecification}, {@link Dimension}, chassis fields)
 *  back the comparison engine, while {@code additionalSpecs} holds the long tail until it earns a real column. */
@Entity
@Table(
        name = "motorcycles",
        uniqueConstraints = @UniqueConstraint(name = "uk_motorcycles_slug", columnNames = "slug"),
        // The functional lower(brand) index and the pg_trgm GIN indexes cannot be expressed
        // as @Index; they live in V1__initial_schema.sql and V2__pg_trgm_search_indexes.sql.
        indexes = {
                @Index(name = "idx_motorcycles_brand", columnList = "brand"),
                @Index(name = "idx_motorcycles_category", columnList = "category"),
                @Index(name = "idx_motorcycles_model_year", columnList = "model_year"),
                @Index(name = "idx_motorcycles_category_price_eur", columnList = "category, price_eur")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Motorcycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable, human-readable public identifier, e.g. "yamaha-mt-09-2024". */
    @Column(name = "slug", nullable = false, length = 160)
    private String slug;

    @Column(name = "brand", nullable = false, length = 60)
    private String brand;

    @Column(name = "model", nullable = false, length = 120)
    private String model;

    @Column(name = "model_year", nullable = false)
    private Integer modelYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private Category category;

    @Column(name = "price_eur", precision = 10, scale = 2)
    private BigDecimal priceEur;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "description", length = 2000)
    private String description;

    // --- Chassis & running gear -------------------------------------------------
    // Flat on the aggregate root: few fields, always shown with the bike; split out into its own entity if it grows.

    @Column(name = "frame_type", length = 120)
    private String frameType;

    @Column(name = "front_suspension", length = 160)
    private String frontSuspension;

    @Column(name = "rear_suspension", length = 160)
    private String rearSuspension;

    @Column(name = "front_brake", length = 160)
    private String frontBrake;

    @Column(name = "rear_brake", length = 160)
    private String rearBrake;

    /** e.g. "Dual-channel cornering ABS", "None". */
    @Column(name = "abs_type", length = 80)
    private String absType;

    @Column(name = "front_tyre", length = 60)
    private String frontTyre;

    @Column(name = "rear_tyre", length = 60)
    private String rearTyre;

    // --- Related specification blocks -------------------------------------------

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "engine_specification_id",
            foreignKey = @ForeignKey(name = "fk_motorcycles_engine"))
    private EngineSpecification engine;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "dimension_id",
            foreignKey = @ForeignKey(name = "fk_motorcycles_dimension"))
    private Dimension dimension;

    /** Open-ended extras. See the class javadoc for when to use this vs. a column. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "motorcycle_additional_specs",
            joinColumns = @JoinColumn(name = "motorcycle_id"),
            foreignKey = @ForeignKey(name = "fk_additional_specs_motorcycle"))
    @MapKeyColumn(name = "spec_key", length = 80)
    @Column(name = "spec_value", length = 500)
    @Builder.Default
    private Map<String, String> additionalSpecs = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Two admins editing the same bike: the second write is rejected instead of silently overwriting the first. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Convenience label for logs, comparison headers and OpenAPI examples. */
    public String getDisplayName() {
        return "%s %s (%d)".formatted(brand, model, modelYear);
    }
}
