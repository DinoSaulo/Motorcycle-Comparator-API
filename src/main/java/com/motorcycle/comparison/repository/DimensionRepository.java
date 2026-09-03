package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.entity.Dimension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Dimensions are always reached through their {@link com.motorcycle.comparison.entity.Motorcycle} and cascade from
 *  it, so this repository exists for analytics-style reads, mirroring {@link EngineSpecificationRepository}. */
@Repository
public interface DimensionRepository extends JpaRepository<Dimension, Long> {

    /** One row, one round trip, mirroring {@link CatalogStatsRepository#fieldGaps()}: see its javadoc for why every
     *  getter here is a boxed {@code Long} rather than a primitive. */
    @Query("""
            SELECT
                SUM(CASE WHEN d.lengthMm IS NULL THEN 1L ELSE 0L END) AS lengthMm,
                SUM(CASE WHEN d.widthMm IS NULL THEN 1L ELSE 0L END) AS widthMm,
                SUM(CASE WHEN d.heightMm IS NULL THEN 1L ELSE 0L END) AS heightMm,
                SUM(CASE WHEN d.wheelbaseMm IS NULL THEN 1L ELSE 0L END) AS wheelbaseMm,
                SUM(CASE WHEN d.seatHeightMm IS NULL THEN 1L ELSE 0L END) AS seatHeightMm,
                SUM(CASE WHEN d.groundClearanceMm IS NULL THEN 1L ELSE 0L END) AS groundClearanceMm,
                SUM(CASE WHEN d.kerbWeightKg IS NULL THEN 1L ELSE 0L END) AS kerbWeightKg,
                SUM(CASE WHEN d.dryWeightKg IS NULL THEN 1L ELSE 0L END) AS dryWeightKg,
                SUM(CASE WHEN d.fuelCapacityL IS NULL THEN 1L ELSE 0L END) AS fuelCapacityL,
                SUM(CASE WHEN d.payloadKg IS NULL THEN 1L ELSE 0L END) AS payloadKg
            FROM Dimension d
            """)
    DimensionFieldGaps fieldGaps();

    interface DimensionFieldGaps {
        Long getLengthMm();

        Long getWidthMm();

        Long getHeightMm();

        Long getWheelbaseMm();

        Long getSeatHeightMm();

        Long getGroundClearanceMm();

        Long getKerbWeightKg();

        Long getDryWeightKg();

        Long getFuelCapacityL();

        Long getPayloadKg();
    }
}
