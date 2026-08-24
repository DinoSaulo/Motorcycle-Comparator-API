package com.motorcycle.comparison.dto.request;

import com.motorcycle.comparison.MotorcycleFixtures;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure bean-validation tests: no Spring context, just {@link Validator} against the boundary values the
 * annotations encode as business decisions (1885 as the earliest model year, a strictly positive price, the
 * additional-specs bounds that mirror {@code motorcycle_additional_specs}). The HTTP-level equivalents in
 * {@code MotorcycleControllerTest} check that a violation becomes a 400; these check exactly where the line is.
 */
@DisplayName("CreateMotorcycleRequest validation")
class CreateMotorcycleRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static CreateMotorcycleRequest baseline() {
        return MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024);
    }

    private static Set<ConstraintViolation<CreateMotorcycleRequest>> violationsOf(CreateMotorcycleRequest request) {
        return validator.validate(request);
    }

    private static boolean violates(CreateMotorcycleRequest request, String property) {
        return violationsOf(request).stream().anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @Test
    @DisplayName("the fixture baseline is itself valid")
    void baselineIsValid() {
        assertThat(violationsOf(baseline())).isEmpty();
    }

    @Test
    @DisplayName("rejects a blank brand and a blank model")
    void rejectsBlankIdentity() {
        CreateMotorcycleRequest request = with(baseline(), b -> b.brand(""), b -> b.model(""));

        assertThat(violates(request, "brand")).isTrue();
        assertThat(violates(request, "model")).isTrue();
    }

    @Test
    @DisplayName("requires the engine block even though dimension is optional")
    void requiresEngineButNotDimension() {
        CreateMotorcycleRequest noEngine = with(baseline(), b -> b.engine(null));
        CreateMotorcycleRequest noDimension = with(baseline(), b -> b.dimension(null));

        assertThat(violates(noEngine, "engine")).isTrue();
        assertThat(violationsOf(noDimension)).isEmpty();
    }

    @Nested
    @DisplayName("model year bounds")
    class ModelYearBounds {

        @Test
        @DisplayName("accepts the earliest year a motorcycle could plausibly have been built")
        void acceptsLowerBound() {
            assertThat(violates(with(baseline(), b -> b.modelYear(1885)), "modelYear")).isFalse();
        }

        @Test
        @DisplayName("rejects one year before the lower bound")
        void rejectsBelowLowerBound() {
            assertThat(violates(with(baseline(), b -> b.modelYear(1884)), "modelYear")).isTrue();
        }

        @Test
        @DisplayName("accepts the upper bound")
        void acceptsUpperBound() {
            assertThat(violates(with(baseline(), b -> b.modelYear(2100)), "modelYear")).isFalse();
        }

        @Test
        @DisplayName("rejects one year past the upper bound")
        void rejectsAboveUpperBound() {
            assertThat(violates(with(baseline(), b -> b.modelYear(2101)), "modelYear")).isTrue();
        }

        @Test
        @DisplayName("rejects a missing year rather than defaulting it")
        void rejectsNullYear() {
            assertThat(violates(with(baseline(), b -> b.modelYear(null)), "modelYear")).isTrue();
        }
    }

    @Nested
    @DisplayName("price bounds")
    class PriceBounds {

        @Test
        @DisplayName("rejects a zero price: free is not a catalogue price")
        void rejectsZero() {
            assertThat(violates(with(baseline(), b -> b.priceEur(BigDecimal.ZERO)), "priceEur")).isTrue();
        }

        @Test
        @DisplayName("accepts the smallest positive price")
        void acceptsSmallestPositive() {
            assertThat(violates(with(baseline(), b -> b.priceEur(new BigDecimal("0.01"))), "priceEur")).isFalse();
        }

        @Test
        @DisplayName("rejects a negative price")
        void rejectsNegative() {
            assertThat(violates(with(baseline(), b -> b.priceEur(new BigDecimal("-1"))), "priceEur")).isTrue();
        }

        @Test
        @DisplayName("leaves an unpublished price unconstrained")
        void toleratesNullPrice() {
            assertThat(violates(with(baseline(), b -> b.priceEur(null)), "priceEur")).isFalse();
        }
    }

    @Nested
    @DisplayName("additional specs bounds")
    class AdditionalSpecsBounds {

        @Test
        @DisplayName("accepts a key and value at the exact size cap")
        void acceptsExactCap() {
            CreateMotorcycleRequest request = with(baseline(), b -> b.additionalSpecs(Map.of("k".repeat(80), "v".repeat(500))));

            assertThat(violationsOf(request)).isEmpty();
        }

        @Test
        @DisplayName("rejects a key one character past the cap")
        void rejectsOversizedKey() {
            CreateMotorcycleRequest request = with(baseline(), b -> b.additionalSpecs(Map.of("k".repeat(81), "v")));

            assertThat(violationsOf(request)).isNotEmpty();
        }

        @Test
        @DisplayName("rejects a value one character past the cap")
        void rejectsOversizedValue() {
            CreateMotorcycleRequest request = with(baseline(), b -> b.additionalSpecs(Map.of("k", "v".repeat(501))));

            assertThat(violationsOf(request)).isNotEmpty();
        }

        @Test
        @DisplayName("rejects a blank key even when the value is fine")
        void rejectsBlankKey() {
            CreateMotorcycleRequest request = with(baseline(), b -> b.additionalSpecs(Map.of(" ", "4")));

            assertThat(violationsOf(request)).isNotEmpty();
        }

        @Test
        @DisplayName("rejects more than fifty entries")
        void rejectsTooManyEntries() {
            Map<String, String> specs = new java.util.LinkedHashMap<>();
            for (int i = 0; i < 51; i++) {
                specs.put("key-" + i, "value");
            }
            CreateMotorcycleRequest request = with(baseline(), b -> b.additionalSpecs(specs));

            assertThat(violates(request, "additionalSpecs")).isTrue();
        }
    }

    @Nested
    @DisplayName("engine block")
    class EngineBlock {

        @Test
        @DisplayName("allows a missing displacement for an electric motor")
        void allowsNullDisplacement() {
            CreateMotorcycleRequest request = with(baseline(), b -> b.engine(MotorcycleFixtures.electricEngineRequest()));

            assertThat(violationsOf(request)).isEmpty();
        }

        @Test
        @DisplayName("rejects a zero or negative displacement when it is present at all")
        void rejectsNonPositiveDisplacement() {
            CreateMotorcycleRequest.EngineRequest zero = withDisplacement(0);
            CreateMotorcycleRequest.EngineRequest negative = withDisplacement(-1);

            assertThat(violationsOf(with(baseline(), b -> b.engine(zero)))).isNotEmpty();
            assertThat(violationsOf(with(baseline(), b -> b.engine(negative)))).isNotEmpty();
        }

        private CreateMotorcycleRequest.EngineRequest withDisplacement(int cc) {
            CreateMotorcycleRequest.EngineRequest e = MotorcycleFixtures.engineRequest(890);
            return new CreateMotorcycleRequest.EngineRequest(
                    e.engineType(), cc, e.cylinders(), e.valvesPerCylinder(),
                    e.maxPowerHp(), e.maxPowerRpm(), e.maxTorqueNm(), e.maxTorqueRpm(),
                    e.compressionRatio(), e.boreMm(), e.strokeMm(), e.coolingSystem(), e.fuelSystem(),
                    e.transmissionType(), e.gears(), e.finalDrive(), e.topSpeedKph(),
                    e.fuelConsumptionL100km(), e.emissionStandard());
        }
    }

    // --- a tiny builder over the record, so each test overrides only the field it is about ------------------

    @SafeVarargs
    private static CreateMotorcycleRequest with(CreateMotorcycleRequest base, java.util.function.UnaryOperator<Builder>... mutations) {
        Builder builder = new Builder(base);
        for (var mutation : mutations) {
            builder = mutation.apply(builder);
        }
        return builder.build();
    }

    /** Copies every field of a {@link CreateMotorcycleRequest}, letting a test override exactly one. */
    private record Builder(
            String brand, String model, Integer modelYear, com.motorcycle.comparison.entity.Category category,
            BigDecimal priceEur, String imageUrl, String description,
            String frameType, String frontSuspension, String rearSuspension,
            String frontBrake, String rearBrake, String absType, String frontTyre, String rearTyre,
            CreateMotorcycleRequest.EngineRequest engine, CreateMotorcycleRequest.DimensionRequest dimension,
            Map<String, String> additionalSpecs) {

        Builder(CreateMotorcycleRequest r) {
            this(r.brand(), r.model(), r.modelYear(), r.category(), r.priceEur(), r.imageUrl(), r.description(),
                    r.frameType(), r.frontSuspension(), r.rearSuspension(), r.frontBrake(), r.rearBrake(),
                    r.absType(), r.frontTyre(), r.rearTyre(), r.engine(), r.dimension(), r.additionalSpecs());
        }

        Builder brand(String v) { return new Builder(v, model, modelYear, category, priceEur, imageUrl, description, frameType, frontSuspension, rearSuspension, frontBrake, rearBrake, absType, frontTyre, rearTyre, engine, dimension, additionalSpecs); }
        Builder model(String v) { return new Builder(brand, v, modelYear, category, priceEur, imageUrl, description, frameType, frontSuspension, rearSuspension, frontBrake, rearBrake, absType, frontTyre, rearTyre, engine, dimension, additionalSpecs); }
        Builder modelYear(Integer v) { return new Builder(brand, model, v, category, priceEur, imageUrl, description, frameType, frontSuspension, rearSuspension, frontBrake, rearBrake, absType, frontTyre, rearTyre, engine, dimension, additionalSpecs); }
        Builder priceEur(BigDecimal v) { return new Builder(brand, model, modelYear, category, v, imageUrl, description, frameType, frontSuspension, rearSuspension, frontBrake, rearBrake, absType, frontTyre, rearTyre, engine, dimension, additionalSpecs); }
        Builder engine(CreateMotorcycleRequest.EngineRequest v) { return new Builder(brand, model, modelYear, category, priceEur, imageUrl, description, frameType, frontSuspension, rearSuspension, frontBrake, rearBrake, absType, frontTyre, rearTyre, v, dimension, additionalSpecs); }
        Builder dimension(CreateMotorcycleRequest.DimensionRequest v) { return new Builder(brand, model, modelYear, category, priceEur, imageUrl, description, frameType, frontSuspension, rearSuspension, frontBrake, rearBrake, absType, frontTyre, rearTyre, engine, v, additionalSpecs); }
        Builder additionalSpecs(Map<String, String> v) { return new Builder(brand, model, modelYear, category, priceEur, imageUrl, description, frameType, frontSuspension, rearSuspension, frontBrake, rearBrake, absType, frontTyre, rearTyre, engine, dimension, v); }

        CreateMotorcycleRequest build() {
            return new CreateMotorcycleRequest(brand, model, modelYear, category, priceEur, imageUrl, description,
                    frameType, frontSuspension, rearSuspension, frontBrake, rearBrake, absType, frontTyre, rearTyre,
                    engine, dimension, additionalSpecs);
        }
    }
}
