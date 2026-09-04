package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.entity.Motorcycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DisplayName("DimensionRepository Integration Tests")
class DimensionRepositoryIT {

    @Autowired
    private DimensionRepository dimensionRepository;

    @Autowired
    private MotorcycleRepository motorcycleRepository;

    @Test
    @DisplayName("should_countDimensions")
    void count_returnsTotal() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Honda", "CB500", 471);

        motorcycleRepository.saveAll(List.of(m1, m2));

        long count = dimensionRepository.count();

        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("should_countFieldGaps_forDimensions")
    void fieldGaps_countsNullFields() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        m1.getDimension().setLengthMm(null);
        m1.getDimension().setWidthMm(null);

        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Honda", "CB500", 471);

        motorcycleRepository.saveAll(List.of(m1, m2));

        DimensionRepository.DimensionFieldGaps gaps = dimensionRepository.fieldGaps();

        assertThat(gaps.getLengthMm()).isEqualTo(1L);
        assertThat(gaps.getWidthMm()).isEqualTo(1L);
        assertThat(gaps.getHeightMm()).isZero();
    }

    @Test
    @DisplayName("should_returnZeroGaps_whenAllFieldsAreFilled")
    void fieldGaps_zeroWhenComplete() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        motorcycleRepository.save(m1);

        DimensionRepository.DimensionFieldGaps gaps = dimensionRepository.fieldGaps();

        assertThat(gaps.getLengthMm()).isZero();
        assertThat(gaps.getWheelbaseMm()).isZero();
        assertThat(gaps.getKerbWeightKg()).isZero();
    }

    @Test
    @DisplayName("should_returnZero_forEmptyDatabase")
    void empty_database() {
        assertThat(dimensionRepository.count()).isZero();

        DimensionRepository.DimensionFieldGaps gaps = dimensionRepository.fieldGaps();

        assertThat(gaps.getLengthMm()).isZero();
    }
}
