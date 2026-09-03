package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.entity.Dimension;
import com.motorcycle.comparison.entity.Motorcycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Mirrors {@link EngineSpecificationRepositoryTest}: dimensions are reached only through their
 *  {@link Motorcycle}, so persisting the parent is enough to seed this repository too. */
@DataJpaTest(showSql = false)
@DisplayName("DimensionRepository")
class DimensionRepositoryTest {

    @Autowired
    private DimensionRepository dimensionRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void seed() {
        // MotorcycleFixtures#dimension leaves widthMm/heightMm/groundClearanceMm/dryWeightKg/payloadKg
        // null, so these two alone already cover half the columns' gap count.
        entityManager.persist(MotorcycleFixtures.motorcycle(null, "Yamaha", "MT-125", 125));
        entityManager.persist(MotorcycleFixtures.motorcycle(null, "Yamaha", "MT-09", 890));

        Motorcycle gs1300 = MotorcycleFixtures.motorcycle(null, "BMW", "R 1300 GS", 1300);
        gs1300.setDimension(fullyPopulatedDimension());
        entityManager.persist(gs1300);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("field gaps: zero for a column every seeded dimension fills in")
    void fieldGapsAreZeroForAFullyPopulatedColumn() {
        DimensionRepository.DimensionFieldGaps gaps = dimensionRepository.fieldGaps();

        assertThat(gaps.getLengthMm()).isZero();
        assertThat(gaps.getWheelbaseMm()).isZero();
        assertThat(gaps.getSeatHeightMm()).isZero();
        assertThat(gaps.getKerbWeightKg()).isZero();
        assertThat(gaps.getFuelCapacityL()).isZero();
    }

    @Test
    @DisplayName("field gaps: counts the two dimensions that leave a column unpublished")
    void fieldGapsCountMissingColumns() {
        DimensionRepository.DimensionFieldGaps gaps = dimensionRepository.fieldGaps();

        assertThat(gaps.getWidthMm()).isEqualTo(2L);
        assertThat(gaps.getHeightMm()).isEqualTo(2L);
        assertThat(gaps.getGroundClearanceMm()).isEqualTo(2L);
        assertThat(gaps.getDryWeightKg()).isEqualTo(2L);
        assertThat(gaps.getPayloadKg()).isEqualTo(2L);
    }

    private static Dimension fullyPopulatedDimension() {
        Dimension dimension = new Dimension();
        dimension.setLengthMm(2210);
        dimension.setWidthMm(990);
        dimension.setHeightMm(1450);
        dimension.setWheelbaseMm(1507);
        dimension.setSeatHeightMm(850);
        dimension.setGroundClearanceMm(210);
        dimension.setKerbWeightKg(new BigDecimal("237.0"));
        dimension.setDryWeightKg(new BigDecimal("219.0"));
        dimension.setFuelCapacityL(new BigDecimal("19.0"));
        dimension.setPayloadKg(new BigDecimal("220.0"));
        return dimension;
    }
}
