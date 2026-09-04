package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.entity.EngineSpecification;
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
@DisplayName("EngineSpecificationRepository Integration Tests")
class EngineSpecificationRepositoryIT {

    @Autowired
    private EngineSpecificationRepository engineSpecificationRepository;

    @Autowired
    private MotorcycleRepository motorcycleRepository;

    @Test
    @DisplayName("should_countEngineSpecifications")
    void count_returnsTotal() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Honda", "CB500", 471);

        motorcycleRepository.saveAll(List.of(m1, m2));

        long count = engineSpecificationRepository.count();

        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("should_countFieldGaps_forEngineSpecifications")
    void fieldGaps_countsNullFields() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        m1.getEngine().setMaxPowerHp(null);
        m1.getEngine().setMaxTorqueNm(null);

        Motorcycle m2 = MotorcycleFixtures.motorcycle(2L, "Honda", "CB500", 471);

        motorcycleRepository.saveAll(List.of(m1, m2));

        EngineSpecificationRepository.EngineFieldGaps gaps = engineSpecificationRepository.fieldGaps();

        assertThat(gaps.getMaxPowerHp()).isEqualTo(1L);
        assertThat(gaps.getMaxTorqueNm()).isEqualTo(1L);
        assertThat(gaps.getDisplacementCc()).isZero();
    }

    @Test
    @DisplayName("should_returnZeroGaps_whenAllFieldsAreFilled")
    void fieldGaps_zeroWhenComplete() {
        Motorcycle m1 = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 889);
        motorcycleRepository.save(m1);

        EngineSpecificationRepository.EngineFieldGaps gaps = engineSpecificationRepository.fieldGaps();

        assertThat(gaps.getMaxPowerHp()).isZero();
        assertThat(gaps.getDisplacementCc()).isZero();
        assertThat(gaps.getGears()).isZero();
    }

    @Test
    @DisplayName("should_returnZero_forEmptyDatabase")
    void empty_database() {
        assertThat(engineSpecificationRepository.count()).isZero();

        EngineSpecificationRepository.EngineFieldGaps gaps = engineSpecificationRepository.fieldGaps();

        assertThat(gaps.getDisplacementCc()).isZero();
    }
}
