package com.motorcycle.comparison;

import com.motorcycle.comparison.dto.request.CreateMotorcycleRequest;
import com.motorcycle.comparison.entity.Category;
import com.motorcycle.comparison.entity.Dimension;
import com.motorcycle.comparison.entity.EngineSpecification;
import com.motorcycle.comparison.entity.Motorcycle;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared test data builders. Every factory returns a fully valid object; each test then mutates only the one
 * field it is actually about, keeping the intent of a test visible instead of buried in twenty lines of setup.
 */
public final class MotorcycleFixtures {

    private MotorcycleFixtures() {
    }

    public static Motorcycle motorcycle(Long id, String brand, String model, int displacementCc) {
        Motorcycle motorcycle = new Motorcycle();
        motorcycle.setId(id);
        motorcycle.setBrand(brand);
        motorcycle.setModel(model);
        motorcycle.setModelYear(2024);
        motorcycle.setCategory(Category.NAKED);
        motorcycle.setSlug((brand + "-" + model + "-2024").toLowerCase().replace(' ', '-'));
        motorcycle.setPriceEur(new BigDecimal("10000.00"));
        motorcycle.setFrontBrake("Dual 298mm discs");
        motorcycle.setRearBrake("Single 245mm disc");
        motorcycle.setAbsType("Dual-channel ABS");
        motorcycle.setEngine(engine(displacementCc));
        motorcycle.setDimension(dimension());
        motorcycle.setAdditionalSpecs(new LinkedHashMap<>());
        return motorcycle;
    }

    public static EngineSpecification engine(int displacementCc) {
        EngineSpecification engine = new EngineSpecification();
        engine.setEngineType("Inline-4");
        engine.setDisplacementCc(displacementCc);
        engine.setCylinders(4);
        engine.setMaxPowerHp(new BigDecimal("100.0"));
        engine.setMaxTorqueNm(new BigDecimal("70.0"));
        engine.setTransmissionType("6-speed manual");
        engine.setGears(6);
        engine.setFinalDrive("Chain");
        engine.setTopSpeedKph(220);
        return engine;
    }

    public static Dimension dimension() {
        Dimension dimension = new Dimension();
        dimension.setLengthMm(2090);
        dimension.setWheelbaseMm(1430);
        dimension.setSeatHeightMm(825);
        dimension.setKerbWeightKg(new BigDecimal("190.0"));
        dimension.setFuelCapacityL(new BigDecimal("14.0"));
        return dimension;
    }

    public static CreateMotorcycleRequest createRequest(String brand, String model, int year) {
        return new CreateMotorcycleRequest(
                brand, model, year, Category.NAKED,
                new BigDecimal("10499.00"), null, "A test motorcycle",
                "Aluminium Deltabox", "41mm inverted fork", "Link-type monoshock",
                "Dual 298mm discs", "Single 245mm disc", "Dual-channel ABS",
                "120/70 ZR17", "180/55 ZR17",
                engineRequest(890),
                dimensionRequest(),
                Map.of("Rider modes", "4"));
    }

    public static CreateMotorcycleRequest.EngineRequest engineRequest(int displacementCc) {
        return new CreateMotorcycleRequest.EngineRequest(
                "Inline-3", displacementCc, 3, 4,
                new BigDecimal("117.0"), 10000,
                new BigDecimal("93.0"), 7000,
                "11.5:1", new BigDecimal("78.00"), new BigDecimal("62.00"),
                "Liquid", "Electronic fuel injection", "6-speed manual", 6, "Chain",
                220, new BigDecimal("5.00"), "Euro 5+");
    }

    /** Same as {@link #createRequest} but with a caller-chosen long-tail block, for the bounds tests. */
    /** A curated, externally hosted image — the only way imageUrl can reach the entity, since uploads go through /image. */
    public static CreateMotorcycleRequest createRequestWithImage(String imageUrl) {
        CreateMotorcycleRequest full = createRequest("Yamaha", "MT-09", 2024);
        return new CreateMotorcycleRequest(
                full.brand(), full.model(), full.modelYear(), full.category(),
                full.priceEur(), imageUrl, full.description(),
                full.frameType(), full.frontSuspension(), full.rearSuspension(),
                full.frontBrake(), full.rearBrake(), full.absType(),
                full.frontTyre(), full.rearTyre(),
                full.engine(), full.dimension(), full.additionalSpecs());
    }

    public static CreateMotorcycleRequest createRequestWithSpecs(Map<String, String> additionalSpecs) {
        CreateMotorcycleRequest full = createRequest("Yamaha", "MT-09", 2024);
        return new CreateMotorcycleRequest(
                full.brand(), full.model(), full.modelYear(), full.category(),
                full.priceEur(), full.imageUrl(), full.description(),
                full.frameType(), full.frontSuspension(), full.rearSuspension(),
                full.frontBrake(), full.rearBrake(), full.absType(),
                full.frontTyre(), full.rearTyre(),
                full.engine(), full.dimension(), additionalSpecs);
    }

    /** Same as {@link #createRequest} but with no dimension block, for full-replace tests. */
    public static CreateMotorcycleRequest createRequestWithoutDimension(String brand, String model, int year) {
        CreateMotorcycleRequest full = createRequest(brand, model, year);
        return new CreateMotorcycleRequest(
                full.brand(), full.model(), full.modelYear(), full.category(),
                full.priceEur(), full.imageUrl(), full.description(),
                full.frameType(), full.frontSuspension(), full.rearSuspension(),
                full.frontBrake(), full.rearBrake(), full.absType(),
                full.frontTyre(), full.rearTyre(),
                full.engine(), null, full.additionalSpecs());
    }

    /** Shaped like the seeded Zero SR/F: no displacement, no cylinders, no gearbox ratios. */
    public static CreateMotorcycleRequest.EngineRequest electricEngineRequest() {
        return new CreateMotorcycleRequest.EngineRequest(
                "Permanent magnet AC motor", null, null, null,
                new BigDecimal("100.0"), null,
                new BigDecimal("190.0"), null,
                null, null, null,
                "Air", "Battery electric, 14.4 kWh", "Single-speed direct drive", 1, "Belt",
                200, null, "Zero emission");
    }

    public static CreateMotorcycleRequest.DimensionRequest dimensionRequest() {
        return new CreateMotorcycleRequest.DimensionRequest(
                2090, 820, 1190, 1430, 825, 140,
                new BigDecimal("193.0"), null, new BigDecimal("14.0"), new BigDecimal("187.0"));
    }
}
