package com.motorcycle.comparison.dto.response;

import com.motorcycle.comparison.entity.Category;
import com.motorcycle.comparison.entity.Dimension;
import com.motorcycle.comparison.entity.EngineSpecification;
import com.motorcycle.comparison.entity.Motorcycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure projection tests: no Spring context and no serialization, just {@link MotorcycleResponse#from} against the
 *  entity, covering the brakes/tyres columns the 2026-09 backfill seeds now populate. */
@DisplayName("MotorcycleResponse.from")
class MotorcycleResponseTest {

    /** Every field populated with a distinct value, so a mis-wired constructor argument cannot pass unnoticed. */
    private static Motorcycle fullyPopulated() {
        return Motorcycle.builder()
                .id(42L)
                .slug("honda-xre-300-2022")
                .brand("Honda")
                .model("XRE 300")
                .modelYear(2022)
                .category(Category.ADVENTURE)
                .priceEur(new BigDecimal("7499.00"))
                .imageUrl("https://cdn.example.com/honda-xre-300-2022.jpg")
                .description("Dual-sport single built for Brazilian roads")
                .frameType("Semi-double cradle")
                .frontSuspension("43mm telescopic fork")
                .rearSuspension("Pro-Link monoshock")
                .frontBrake("Single 256 mm disc")
                .rearBrake("Single 220 mm disc")
                .absType("Dual-channel ABS")
                .frontTyre("90/90-21")
                .rearTyre("120/80-18")
                .engine(fullEngine())
                .dimension(fullDimension())
                .additionalSpecs(new LinkedHashMap<>(Map.of("Rider modes", "2")))
                .availableCountries(new LinkedHashSet<>(Set.of("BR", "AR")))
                .build();
    }

    private static EngineSpecification fullEngine() {
        return EngineSpecification.builder()
                .engineType("Single-cylinder")
                .displacementCc(291)
                .cylinders(1)
                .valvesPerCylinder(4)
                .maxPowerHp(new BigDecimal("25.4"))
                .maxPowerRpm(8500)
                .maxTorqueNm(new BigDecimal("27.3"))
                .maxTorqueRpm(6500)
                .compressionRatio("10.0:1")
                .boreMm(new BigDecimal("76.80"))
                .strokeMm(new BigDecimal("63.00"))
                .coolingSystem("Liquid")
                .fuelSystem("Electronic fuel injection")
                .transmissionType("5-speed manual")
                .gears(5)
                .finalDrive("Chain")
                .topSpeedKph(140)
                .fuelConsumptionL100km(new BigDecimal("3.30"))
                .emissionStandard("Euro 5")
                .build();
    }

    private static Dimension fullDimension() {
        return Dimension.builder()
                .lengthMm(2185)
                .widthMm(825)
                .heightMm(1195)
                .wheelbaseMm(1455)
                .seatHeightMm(855)
                .groundClearanceMm(245)
                .kerbWeightKg(new BigDecimal("152.0"))
                .dryWeightKg(new BigDecimal("140.0"))
                .fuelCapacityL(new BigDecimal("13.5"))
                .payloadKg(new BigDecimal("180.0"))
                .build();
    }

    @Test
    @DisplayName("maps every top-level field of a fully populated motorcycle")
    void mapsEveryTopLevelField() {
        Motorcycle entity = fullyPopulated();

        MotorcycleResponse response = MotorcycleResponse.from(entity);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.slug()).isEqualTo("honda-xre-300-2022");
        assertThat(response.brand()).isEqualTo("Honda");
        assertThat(response.model()).isEqualTo("XRE 300");
        assertThat(response.modelYear()).isEqualTo(2022);
        assertThat(response.category()).isEqualTo(Category.ADVENTURE);
        assertThat(response.priceEur()).isEqualByComparingTo("7499.00");
        assertThat(response.imageUrl()).isEqualTo("https://cdn.example.com/honda-xre-300-2022.jpg");
        assertThat(response.description()).isEqualTo("Dual-sport single built for Brazilian roads");
        assertThat(response.frameType()).isEqualTo("Semi-double cradle");
        assertThat(response.frontSuspension()).isEqualTo("43mm telescopic fork");
        assertThat(response.rearSuspension()).isEqualTo("Pro-Link monoshock");
    }

    @Test
    @DisplayName("derives displayName from the entity rather than storing it")
    void derivesDisplayName() {
        MotorcycleResponse response = MotorcycleResponse.from(fullyPopulated());

        assertThat(response.displayName()).isEqualTo("Honda XRE 300 (2022)");
    }

    @Test
    @DisplayName("maps every engine field onto the nested EngineResponse")
    void mapsEveryEngineField() {
        MotorcycleResponse.EngineResponse engine = MotorcycleResponse.from(fullyPopulated()).engine();

        assertThat(engine.engineType()).isEqualTo("Single-cylinder");
        assertThat(engine.displacementCc()).isEqualTo(291);
        assertThat(engine.cylinders()).isEqualTo(1);
        assertThat(engine.valvesPerCylinder()).isEqualTo(4);
        assertThat(engine.maxPowerHp()).isEqualByComparingTo("25.4");
        assertThat(engine.maxPowerRpm()).isEqualTo(8500);
        assertThat(engine.maxTorqueNm()).isEqualByComparingTo("27.3");
        assertThat(engine.maxTorqueRpm()).isEqualTo(6500);
        assertThat(engine.compressionRatio()).isEqualTo("10.0:1");
        assertThat(engine.boreMm()).isEqualByComparingTo("76.80");
        assertThat(engine.strokeMm()).isEqualByComparingTo("63.00");
        assertThat(engine.coolingSystem()).isEqualTo("Liquid");
        assertThat(engine.fuelSystem()).isEqualTo("Electronic fuel injection");
        assertThat(engine.transmissionType()).isEqualTo("5-speed manual");
        assertThat(engine.gears()).isEqualTo(5);
        assertThat(engine.finalDrive()).isEqualTo("Chain");
        assertThat(engine.topSpeedKph()).isEqualTo(140);
        assertThat(engine.fuelConsumptionL100km()).isEqualByComparingTo("3.30");
        assertThat(engine.emissionStandard()).isEqualTo("Euro 5");
    }

    @Test
    @DisplayName("maps every dimension field onto the nested DimensionResponse")
    void mapsEveryDimensionField() {
        MotorcycleResponse.DimensionResponse dimension = MotorcycleResponse.from(fullyPopulated()).dimension();

        assertThat(dimension.lengthMm()).isEqualTo(2185);
        assertThat(dimension.widthMm()).isEqualTo(825);
        assertThat(dimension.heightMm()).isEqualTo(1195);
        assertThat(dimension.wheelbaseMm()).isEqualTo(1455);
        assertThat(dimension.seatHeightMm()).isEqualTo(855);
        assertThat(dimension.groundClearanceMm()).isEqualTo(245);
        assertThat(dimension.kerbWeightKg()).isEqualByComparingTo("152.0");
        assertThat(dimension.dryWeightKg()).isEqualByComparingTo("140.0");
        assertThat(dimension.fuelCapacityL()).isEqualByComparingTo("13.5");
        assertThat(dimension.payloadKg()).isEqualByComparingTo("180.0");
    }

    @Test
    @DisplayName("copies the long-tail blocks rather than aliasing the entity's own collections")
    void copiesLongTailBlocks() {
        Motorcycle entity = fullyPopulated();

        MotorcycleResponse response = MotorcycleResponse.from(entity);
        entity.getAdditionalSpecs().put("Rider modes", "4");
        entity.getAvailableCountries().add("CL");

        assertThat(response.additionalSpecs()).containsExactly(Map.entry("Rider modes", "2"));
        assertThat(response.availableCountries()).containsExactlyInAnyOrder("BR", "AR");
    }

    @Nested
    @DisplayName("brakes and tyres columns")
    class BrakesAndTyres {

        @Test
        @DisplayName("maps the five backfilled columns verbatim")
        void mapsBackfilledColumnsVerbatim() {
            MotorcycleResponse response = MotorcycleResponse.from(fullyPopulated());

            assertThat(response.frontBrake()).isEqualTo("Single 256 mm disc");
            assertThat(response.rearBrake()).isEqualTo("Single 220 mm disc");
            assertThat(response.absType()).isEqualTo("Dual-channel ABS");
            assertThat(response.frontTyre()).isEqualTo("90/90-21");
            assertThat(response.rearTyre()).isEqualTo("120/80-18");
        }

        @Test
        @DisplayName("passes a value with an apostrophe and a slash through untouched: no escaping at this layer")
        void passesPunctuationThrough() {
            Motorcycle entity = fullyPopulated();
            entity.setFrontBrake("Single 296 mm disc, Nissin's twin-piston caliper");
            entity.setFrontTyre("120/70ZR17 M/C 58W");

            MotorcycleResponse response = MotorcycleResponse.from(entity);

            assertThat(response.frontBrake()).isEqualTo("Single 296 mm disc, Nissin's twin-piston caliper");
            assertThat(response.frontTyre()).isEqualTo("120/70ZR17 M/C 58W");
        }

        @Test
        @DisplayName("leaves a not-yet-backfilled column null instead of defaulting it")
        void leavesUnbackfilledColumnsNull() {
            Motorcycle entity = fullyPopulated();
            entity.setFrontBrake(null);
            entity.setRearBrake(null);
            entity.setAbsType(null);
            entity.setFrontTyre(null);
            entity.setRearTyre(null);

            MotorcycleResponse response = MotorcycleResponse.from(entity);

            assertThat(response.frontBrake()).isNull();
            assertThat(response.rearBrake()).isNull();
            assertThat(response.absType()).isNull();
            assertThat(response.frontTyre()).isNull();
            assertThat(response.rearTyre()).isNull();
        }

        @Test
        @DisplayName("maps abs_type independently of the brake strings, which never carry the ABS wording")
        void mapsAbsTypeIndependently() {
            Motorcycle entity = fullyPopulated();
            entity.setAbsType(null);

            MotorcycleResponse response = MotorcycleResponse.from(entity);

            assertThat(response.absType()).isNull();
            assertThat(response.frontBrake()).isEqualTo("Single 256 mm disc").doesNotContain("ABS");
            assertThat(response.rearBrake()).isEqualTo("Single 220 mm disc").doesNotContain("ABS");
        }
    }

    @Nested
    @DisplayName("null propagation")
    class NullPropagation {

        @Test
        @DisplayName("maps a missing engine block to a null nested record")
        void mapsMissingEngineToNull() {
            Motorcycle entity = fullyPopulated();
            entity.setEngine(null);

            MotorcycleResponse response = MotorcycleResponse.from(entity);

            assertThat(response.engine()).isNull();
            assertThat(response.dimension()).isNotNull();
        }

        @Test
        @DisplayName("maps a missing dimension block to a null nested record")
        void mapsMissingDimensionToNull() {
            Motorcycle entity = fullyPopulated();
            entity.setDimension(null);

            MotorcycleResponse response = MotorcycleResponse.from(entity);

            assertThat(response.dimension()).isNull();
            assertThat(response.engine()).isNotNull();
        }

        @Test
        @DisplayName("maps null long-tail blocks to empty collections rather than null")
        void mapsNullLongTailBlocksToEmpty() {
            Motorcycle entity = fullyPopulated();
            entity.setAdditionalSpecs(null);
            entity.setAvailableCountries(null);

            MotorcycleResponse response = MotorcycleResponse.from(entity);

            assertThat(response.additionalSpecs()).isNotNull().isEmpty();
            assertThat(response.availableCountries()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("returns immutable long-tail blocks, so a caller cannot edit the read model")
        void returnsImmutableLongTailBlocks() {
            MotorcycleResponse response = MotorcycleResponse.from(fullyPopulated());

            assertThatThrownBy(() -> response.additionalSpecs().put("k", "v"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> response.availableCountries().add("CL"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("maps a brand-new, barely populated entity without throwing")
        void mapsBarelyPopulatedEntity() {
            Motorcycle entity = new Motorcycle();
            entity.setBrand("Yamaha");
            entity.setModel("MT-03");
            entity.setModelYear(2024);

            MotorcycleResponse response = MotorcycleResponse.from(entity);

            assertThat(response.id()).isNull();
            assertThat(response.engine()).isNull();
            assertThat(response.dimension()).isNull();
            assertThat(response.frontBrake()).isNull();
            assertThat(response.rearTyre()).isNull();
            assertThat(response.displayName()).isEqualTo("Yamaha MT-03 (2024)");
            assertThat(response.additionalSpecs()).isEmpty();
            assertThat(response.availableCountries()).isEmpty();
        }
    }
}
