package com.motorcycle.comparison.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Physical envelope, mass and capacities. Every field is nullable, since manufacturers publish wildly different
 *  subsets, and a missing value must render as "n/a" in the comparison table rather than a zero. */
@Entity
@Table(name = "dimensions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dimension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "length_mm")
    private Integer lengthMm;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "height_mm")
    private Integer heightMm;

    @Column(name = "wheelbase_mm")
    private Integer wheelbaseMm;

    @Column(name = "seat_height_mm")
    private Integer seatHeightMm;

    @Column(name = "ground_clearance_mm")
    private Integer groundClearanceMm;

    /** Wet weight, ready to ride, tank full. The figure most reviews quote. */
    @Column(name = "kerb_weight_kg", precision = 6, scale = 1)
    private BigDecimal kerbWeightKg;

    @Column(name = "dry_weight_kg", precision = 6, scale = 1)
    private BigDecimal dryWeightKg;

    @Column(name = "fuel_capacity_l", precision = 5, scale = 1)
    private BigDecimal fuelCapacityL;

    @Column(name = "payload_kg", precision = 6, scale = 1)
    private BigDecimal payloadKg;
}
