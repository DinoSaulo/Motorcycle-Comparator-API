package com.motorcycle.comparison.service;

import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.dto.response.ComparisonResponse;
import com.motorcycle.comparison.dto.response.ComparisonResponse.SpecGroup;
import com.motorcycle.comparison.dto.response.ComparisonResponse.SpecRow;
import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.exception.ResourceNotFoundException;
import com.motorcycle.comparison.repository.MotorcycleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ComparisonService")
class ComparisonServiceTest {

    @Mock
    private MotorcycleRepository motorcycleRepository;

    private ComparisonService comparisonService;

    private Motorcycle yamaha;
    private Motorcycle honda;

    @BeforeEach
    void setUp() {
        comparisonService = new ComparisonService(motorcycleRepository);

        yamaha = MotorcycleFixtures.motorcycleWithId(1L, "Yamaha", "MT-09", 890);
        yamaha.getEngine().setMaxPowerHp(new BigDecimal("117.0"));
        yamaha.getDimension().setKerbWeightKg(new BigDecimal("193.0"));
        yamaha.setPriceEur(new BigDecimal("10499.00"));
        yamaha.getAdditionalSpecs().put("Rider modes", "4");

        honda = MotorcycleFixtures.motorcycleWithId(2L, "Honda", "CB650R", 649);
        honda.getEngine().setMaxPowerHp(new BigDecimal("94.0"));
        honda.getDimension().setKerbWeightKg(new BigDecimal("202.0"));
        honda.setPriceEur(new BigDecimal("9290.00"));
        honda.getAdditionalSpecs().put("Quickshifter", "Optional");
    }

    @Test
    @DisplayName("returns columns in the order the ids were requested, not the DB order")
    void preservesRequestedOrder() {
        when(motorcycleRepository.findAllWithSpecificationsByIdIn(anyCollection()))
                .thenReturn(List.of(honda, yamaha)); // repository answers in its own order

        ComparisonResponse response = comparisonService.compare(List.of(yamaha.getId(), honda.getId()));

        assertThat(response.motorcycles()).extracting("id").containsExactly(yamaha.getId(), honda.getId());
    }

    @Test
    @DisplayName("marks the higher figure as winner for higher-is-better specs")
    void higherIsBetter() {
        when(motorcycleRepository.findAllWithSpecificationsByIdIn(anyCollection()))
                .thenReturn(List.of(yamaha, honda));

        SpecRow power = row(comparisonService.compare(List.of(yamaha.getId(), honda.getId())), "Performance", "Max power");

        assertThat(power.values()).containsExactly("117", "94");
        assertThat(power.winnerIndexes()).containsExactly(0);
        assertThat(power.differing()).isTrue();
    }

    @Test
    @DisplayName("marks the lower figure as winner for weight and price")
    void lowerIsBetter() {
        when(motorcycleRepository.findAllWithSpecificationsByIdIn(anyCollection()))
                .thenReturn(List.of(yamaha, honda));
        ComparisonResponse response = comparisonService.compare(List.of(yamaha.getId(), honda.getId()));

        assertThat(row(response, "Dimensions & weight", "Kerb weight").winnerIndexes())
                .containsExactly(0); // 193 kg beats 202 kg
        assertThat(row(response, "Overview", "Price").winnerIndexes())
                .containsExactly(1); // Honda is cheaper
    }

    @Test
    @DisplayName("declares no winner on free-text specs")
    void freeTextHasNoWinner() {
        when(motorcycleRepository.findAllWithSpecificationsByIdIn(anyCollection()))
                .thenReturn(List.of(yamaha, honda));

        SpecRow brake = row(comparisonService.compare(List.of(yamaha.getId(), honda.getId())),
                "Chassis & brakes", "Front brake");

        assertThat(brake.winnerIndexes()).isEmpty();
    }

    @Test
    @DisplayName("declares no winner when every bike ties")
    void tieHasNoWinner() {
        honda.getEngine().setMaxPowerHp(new BigDecimal("117.0"));
        when(motorcycleRepository.findAllWithSpecificationsByIdIn(anyCollection()))
                .thenReturn(List.of(yamaha, honda));

        SpecRow power = row(comparisonService.compare(List.of(yamaha.getId(), honda.getId())), "Performance", "Max power");

        assertThat(power.winnerIndexes()).isEmpty();
        assertThat(power.differing()).isFalse();
    }

    @Test
    @DisplayName("treats figures that differ only in scale as a tie, not a contest")
    void tieAcrossDecimalScales() {
        // 117 and 117.0 are not BigDecimal.equals, so counting distinct values used to declare
        // both bikes winners of a row nobody actually won.
        yamaha.getEngine().setMaxPowerHp(new BigDecimal("117"));
        honda.getEngine().setMaxPowerHp(new BigDecimal("117.00"));
        when(motorcycleRepository.findAllWithSpecificationsByIdIn(anyCollection()))
                .thenReturn(List.of(yamaha, honda));

        SpecRow power = row(comparisonService.compare(List.of(yamaha.getId(), honda.getId())), "Performance", "Max power");

        assertThat(power.values()).containsExactly("117", "117");
        assertThat(power.winnerIndexes()).isEmpty();
        assertThat(power.differing()).isFalse();
    }

    @Test
    @DisplayName("marks every bike tied for best, not just the first")
    void reportsEveryTiedWinner() {
        Motorcycle suzuki = MotorcycleFixtures.motorcycleWithId(3L, "Suzuki", "GSX-8S", 776);
        suzuki.getEngine().setMaxPowerHp(new BigDecimal("117.0"));
        when(motorcycleRepository.findAllWithSpecificationsByIdIn(anyCollection()))
                .thenReturn(List.of(yamaha, honda, suzuki));

        SpecRow power = row(comparisonService.compare(List.of(yamaha.getId(), honda.getId(), suzuki.getId())), "Performance", "Max power");

        assertThat(power.winnerIndexes()).containsExactly(0, 2); // Honda's 94 hp is the only loser
    }

    @Test
    @DisplayName("keeps a missing figure null so the table renders a dash, not a zero")
    void missingValueStaysNull() {
        honda.getEngine().setMaxPowerHp(null);
        when(motorcycleRepository.findAllWithSpecificationsByIdIn(anyCollection()))
                .thenReturn(List.of(yamaha, honda));

        SpecRow power = row(comparisonService.compare(List.of(yamaha.getId(), honda.getId())), "Performance", "Max power");

        assertThat(power.values()).containsExactly("117", null);
        assertThat(power.winnerIndexes()).isEmpty(); // only one comparable figure left
    }

    @Test
    @DisplayName("survives a motorcycle with no specification blocks at all")
    void toleratesMissingSpecificationBlocks() {
        honda.setEngine(null);
        honda.setDimension(null);
        when(motorcycleRepository.findAllWithSpecificationsByIdIn(anyCollection()))
                .thenReturn(List.of(yamaha, honda));

        SpecRow displacement = row(comparisonService.compare(List.of(yamaha.getId(), honda.getId())), "Engine", "Displacement");

        assertThat(displacement.values()).containsExactly("890", null);
    }

    @Test
    @DisplayName("unions the ad-hoc specs of every bike into one trailing group")
    void unionsAdditionalSpecs() {
        when(motorcycleRepository.findAllWithSpecificationsByIdIn(anyCollection()))
                .thenReturn(List.of(yamaha, honda));

        ComparisonResponse response = comparisonService.compare(List.of(yamaha.getId(), honda.getId()));
        SpecGroup other = group(response, "Other specifications");

        assertThat(other.rows()).extracting(SpecRow::label)
                .containsExactly("Quickshifter", "Rider modes"); // alphabetical, stable
        assertThat(row(response, "Other specifications", "Rider modes").values())
                .containsExactly("4", null);
    }

    @Test
    @DisplayName("omits the ad-hoc group entirely when nobody has extras")
    void omitsEmptyAdditionalSpecsGroup() {
        yamaha.getAdditionalSpecs().clear();
        honda.getAdditionalSpecs().clear();
        when(motorcycleRepository.findAllWithSpecificationsByIdIn(anyCollection()))
                .thenReturn(List.of(yamaha, honda));

        assertThat(comparisonService.compare(List.of(yamaha.getId(), honda.getId())).groups())
                .extracting(SpecGroup::name)
                .doesNotContain("Other specifications");
    }

    @Test
    @DisplayName("de-duplicates ids before applying the size rules")
    void deduplicatesIds() {
        assertThatThrownBy(() -> comparisonService.compare(List.of(1L, 1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2");
    }

    @Test
    @DisplayName("rejects more columns than a table can usefully show")
    void rejectsTooManyIds() {
        assertThatThrownBy(() -> comparisonService.compare(List.of(1000L, 1001L, 1002L, 1003L, 1004L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 4");
    }

    @Test
    @DisplayName("names the ids that do not exist")
    void reportsMissingIds() {
        when(motorcycleRepository.findAllWithSpecificationsByIdIn(anyCollection()))
                .thenReturn(List.of(yamaha));

        assertThatThrownBy(() -> comparisonService.compare(List.of(1L, 99L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- helpers ----------------------------------------------------------------

    private static SpecGroup group(ComparisonResponse response, String name) {
        return response.groups().stream()
                .filter(g -> g.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No group named " + name));
    }

    private static SpecRow row(ComparisonResponse response, String groupName, String label) {
        return group(response, groupName).rows().stream()
                .filter(r -> r.label().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row named " + label));
    }
}
