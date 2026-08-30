package com.motorcycle.comparison.dto.response;

import com.motorcycle.comparison.entity.Category;
import com.motorcycle.comparison.entity.Dimension;
import com.motorcycle.comparison.entity.EngineSpecification;
import com.motorcycle.comparison.entity.Motorcycle;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

/** Read model for a single motorcycle, including both specification blocks. The mapping lives here as static
 *  factories rather than a separate mapper: a pure, dependency-free projection next to the shape it produces. */
@Schema(description = "Full motorcycle record with its specification blocks")
public record MotorcycleResponse(
        Long id,
        String slug,
        String brand,
        String model,
        Integer modelYear,
        Category category,
        String displayName,
        BigDecimal priceEur,
        String imageUrl,
        String description,
        String frameType,
        String frontSuspension,
        String rearSuspension,
        String frontBrake,
        String rearBrake,
        String absType,
        String frontTyre,
        String rearTyre,
        EngineResponse engine,
        DimensionResponse dimension,
        Map<String, String> additionalSpecs
) {

    public static MotorcycleResponse from(Motorcycle m) {
        return new MotorcycleResponse(
                m.getId(),
                m.getSlug(),
                m.getBrand(),
                m.getModel(),
                m.getModelYear(),
                m.getCategory(),
                m.getDisplayName(),
                m.getPriceEur(),
                m.getImageUrl(),
                m.getDescription(),
                m.getFrameType(),
                m.getFrontSuspension(),
                m.getRearSuspension(),
                m.getFrontBrake(),
                m.getRearBrake(),
                m.getAbsType(),
                m.getFrontTyre(),
                m.getRearTyre(),
                EngineResponse.from(m.getEngine()),
                DimensionResponse.from(m.getDimension()),
                m.getAdditionalSpecs() == null ? Map.of() : Map.copyOf(m.getAdditionalSpecs())
        );
    }

    public record EngineResponse(
            String engineType,
            Integer displacementCc,
            Integer cylinders,
            Integer valvesPerCylinder,
            BigDecimal maxPowerHp,
            Integer maxPowerRpm,
            BigDecimal maxTorqueNm,
            Integer maxTorqueRpm,
            String compressionRatio,
            BigDecimal boreMm,
            BigDecimal strokeMm,
            String coolingSystem,
            String fuelSystem,
            String transmissionType,
            Integer gears,
            String finalDrive,
            Integer topSpeedKph,
            BigDecimal fuelConsumptionL100km,
            String emissionStandard
    ) {
        static EngineResponse from(EngineSpecification e) {
            if (e == null) {
                return null;
            }
            return new EngineResponse(
                    e.getEngineType(), e.getDisplacementCc(), e.getCylinders(),
                    e.getValvesPerCylinder(), e.getMaxPowerHp(), e.getMaxPowerRpm(),
                    e.getMaxTorqueNm(), e.getMaxTorqueRpm(), e.getCompressionRatio(),
                    e.getBoreMm(), e.getStrokeMm(), e.getCoolingSystem(), e.getFuelSystem(),
                    e.getTransmissionType(), e.getGears(), e.getFinalDrive(),
                    e.getTopSpeedKph(), e.getFuelConsumptionL100km(), e.getEmissionStandard());
        }
    }

    public record DimensionResponse(
            Integer lengthMm,
            Integer widthMm,
            Integer heightMm,
            Integer wheelbaseMm,
            Integer seatHeightMm,
            Integer groundClearanceMm,
            BigDecimal kerbWeightKg,
            BigDecimal dryWeightKg,
            BigDecimal fuelCapacityL,
            BigDecimal payloadKg
    ) {
        static DimensionResponse from(Dimension d) {
            if (d == null) {
                return null;
            }
            return new DimensionResponse(
                    d.getLengthMm(), d.getWidthMm(), d.getHeightMm(), d.getWheelbaseMm(),
                    d.getSeatHeightMm(), d.getGroundClearanceMm(), d.getKerbWeightKg(),
                    d.getDryWeightKg(), d.getFuelCapacityL(), d.getPayloadKg());
        }
    }
}
