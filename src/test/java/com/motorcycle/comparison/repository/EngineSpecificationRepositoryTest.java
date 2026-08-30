package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.MotorcycleFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/** Untouched by any other test: this repository exists purely for the analytics-style read described in its javadoc,
 *  and {@code countByDisplacementCcBetween} is a derived query whose bound is easy to get off by one unnoticed. */
// showSql = false: @DataJpaTest defaults spring.jpa.show-sql to true regardless of application.yml.
@DataJpaTest(showSql = false)
@DisplayName("EngineSpecificationRepository")
class EngineSpecificationRepositoryTest {

    @Autowired
    private EngineSpecificationRepository engineSpecificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void seed() {
        // Each Motorcycle cascades its EngineSpecification, so persisting the parent is enough.
        entityManager.persist(MotorcycleFixtures.motorcycle(null, "Yamaha", "MT-125", 125));
        entityManager.persist(MotorcycleFixtures.motorcycle(null, "Yamaha", "MT-09", 890));
        entityManager.persist(MotorcycleFixtures.motorcycle(null, "BMW", "R 1300 GS", 1300));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("counts only the engines inside the requested displacement range")
    void countsWithinRange() {
        assertThat(engineSpecificationRepository.countByDisplacementCcBetween(600, 1000)).isEqualTo(1);
    }

    @Test
    @DisplayName("both bounds are inclusive")
    void boundsAreInclusive() {
        assertThat(engineSpecificationRepository.countByDisplacementCcBetween(890, 890)).isEqualTo(1);
    }

    @Test
    @DisplayName("counts every engine when the range covers all of them")
    void countsEverythingInAWideRange() {
        assertThat(engineSpecificationRepository.countByDisplacementCcBetween(0, 2000)).isEqualTo(3);
    }

    @Test
    @DisplayName("returns zero for a range nothing falls into")
    void returnsZeroOutsideTheRange() {
        assertThat(engineSpecificationRepository.countByDisplacementCcBetween(2000, 3000)).isZero();
    }
}
