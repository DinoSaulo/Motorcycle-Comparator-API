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

/**
 * Powertrain specifications. Kept in its own table because it is the densest
 * and most frequently compared block of attributes, and because a motorcycle
 * catalogue listing rarely needs to load it.
 */
@Entity
@Table(name = "engine_specifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EngineSpecification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "Inline-4", "V-Twin", "Single-cylinder", "Permanent magnet AC motor". */
    @Column(name = "engine_type", length = 80)
    private String engineType;

    @Column(name = "displacement_cc")
    private Integer displacementCc;

    @Column(name = "cylinders")
    private Integer cylinders;

    @Column(name = "valves_per_cylinder")
    private Integer valvesPerCylinder;

    @Column(name = "max_power_hp", precision = 6, scale = 1)
    private BigDecimal maxPowerHp;

    @Column(name = "max_power_rpm")
    private Integer maxPowerRpm;

    @Column(name = "max_torque_nm", precision = 6, scale = 1)
    private BigDecimal maxTorqueNm;

    @Column(name = "max_torque_rpm")
    private Integer maxTorqueRpm;

    /** Free text because ratios are conventionally written as "13.0:1". */
    @Column(name = "compression_ratio", length = 20)
    private String compressionRatio;

    @Column(name = "bore_mm", precision = 6, scale = 2)
    private BigDecimal boreMm;

    @Column(name = "stroke_mm", precision = 6, scale = 2)
    private BigDecimal strokeMm;

    /** e.g. "Liquid", "Air", "Air/Oil". */
    @Column(name = "cooling_system", length = 40)
    private String coolingSystem;

    /** e.g. "Electronic fuel injection, 44mm throttle bodies". */
    @Column(name = "fuel_system", length = 120)
    private String fuelSystem;

    /** e.g. "6-speed manual", "CVT", "Single-speed". */
    @Column(name = "transmission_type", length = 60)
    private String transmissionType;

    @Column(name = "gears")
    private Integer gears;

    /** e.g. "Chain", "Belt", "Shaft". */
    @Column(name = "final_drive", length = 40)
    private String finalDrive;

    @Column(name = "top_speed_kph")
    private Integer topSpeedKph;

    @Column(name = "fuel_consumption_l_100km", precision = 5, scale = 2)
    private BigDecimal fuelConsumptionL100km;

    /** e.g. "Euro 5+". */
    @Column(name = "emission_standard", length = 30)
    private String emissionStandard;
}
