package com.motorcycle.comparison.dto.request;

import com.motorcycle.comparison.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Write model for the catalogue. Kept separate from the entity so that the
 * persistence model can evolve (new columns, split tables) without silently
 * changing the public contract.
 */
@Schema(description = "Payload to create or fully replace a motorcycle")
public record CreateMotorcycleRequest(

        @NotBlank @Size(max = 60)
        @Schema(example = "Yamaha")
        String brand,

        @NotBlank @Size(max = 120)
        @Schema(example = "MT-09")
        String model,

        @NotNull @Min(1885) @Max(2100)
        @Schema(example = "2024")
        Integer modelYear,

        @NotNull
        @Schema(example = "NAKED")
        Category category,

        @DecimalMin(value = "0.0", inclusive = false)
        @Schema(example = "10499.00")
        BigDecimal priceEur,

        @Size(max = 512)
        String imageUrl,

        @Size(max = 2000)
        String description,

        @Size(max = 120) String frameType,
        @Size(max = 160) String frontSuspension,
        @Size(max = 160) String rearSuspension,
        @Size(max = 160) String frontBrake,
        @Size(max = 160) String rearBrake,
        @Size(max = 80) String absType,
        @Size(max = 60) String frontTyre,
        @Size(max = 60) String rearTyre,

        @Valid @NotNull
        EngineRequest engine,

        @Valid
        DimensionRequest dimension,

        @Schema(description = "Long-tail specs that have no dedicated column",
                example = "{\"Rider modes\":\"4\",\"Display\":\"5-inch TFT\"}")
        Map<String, String> additionalSpecs
) {

    @Schema(description = "Powertrain block")
    public record EngineRequest(
            @Size(max = 80) String engineType,
            @NotNull @Positive Integer displacementCc,
            @Positive Integer cylinders,
            @Positive Integer valvesPerCylinder,
            @DecimalMin("0.0") BigDecimal maxPowerHp,
            @Positive Integer maxPowerRpm,
            @DecimalMin("0.0") BigDecimal maxTorqueNm,
            @Positive Integer maxTorqueRpm,
            @Size(max = 20) String compressionRatio,
            @DecimalMin("0.0") BigDecimal boreMm,
            @DecimalMin("0.0") BigDecimal strokeMm,
            @Size(max = 40) String coolingSystem,
            @Size(max = 120) String fuelSystem,
            @Size(max = 60) String transmissionType,
            @Positive Integer gears,
            @Size(max = 40) String finalDrive,
            @Positive Integer topSpeedKph,
            @DecimalMin("0.0") BigDecimal fuelConsumptionL100km,
            @Size(max = 30) String emissionStandard
    ) {}

    @Schema(description = "Physical envelope, mass and capacities")
    public record DimensionRequest(
            @Positive Integer lengthMm,
            @Positive Integer widthMm,
            @Positive Integer heightMm,
            @Positive Integer wheelbaseMm,
            @Positive Integer seatHeightMm,
            @Positive Integer groundClearanceMm,
            @DecimalMin("0.0") BigDecimal kerbWeightKg,
            @DecimalMin("0.0") BigDecimal dryWeightKg,
            @DecimalMin("0.0") BigDecimal fuelCapacityL,
            @DecimalMin("0.0") BigDecimal payloadKg
    ) {}
}
