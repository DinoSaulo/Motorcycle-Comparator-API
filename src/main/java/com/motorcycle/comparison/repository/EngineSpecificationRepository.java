package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.entity.EngineSpecification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Engine specs are always reached through their {@link com.motorcycle.comparison.entity.Motorcycle} and cascade
 *  from it, so this repository exists for analytics-style reads (displacement distributions, data-completeness
 *  gaps) rather than writes. */
@Repository
public interface EngineSpecificationRepository extends JpaRepository<EngineSpecification, Long> {

    long countByDisplacementCcBetween(Integer minCc, Integer maxCc);

    /** One row, one round trip, mirroring {@link CatalogStatsRepository#fieldGaps()}: see its javadoc for why every
     *  getter here is a boxed {@code Long} rather than a primitive. */
    @Query("""
            SELECT
                SUM(CASE WHEN e.engineType IS NULL THEN 1L ELSE 0L END) AS engineType,
                SUM(CASE WHEN e.displacementCc IS NULL THEN 1L ELSE 0L END) AS displacementCc,
                SUM(CASE WHEN e.cylinders IS NULL THEN 1L ELSE 0L END) AS cylinders,
                SUM(CASE WHEN e.valvesPerCylinder IS NULL THEN 1L ELSE 0L END) AS valvesPerCylinder,
                SUM(CASE WHEN e.maxPowerHp IS NULL THEN 1L ELSE 0L END) AS maxPowerHp,
                SUM(CASE WHEN e.maxPowerRpm IS NULL THEN 1L ELSE 0L END) AS maxPowerRpm,
                SUM(CASE WHEN e.maxTorqueNm IS NULL THEN 1L ELSE 0L END) AS maxTorqueNm,
                SUM(CASE WHEN e.maxTorqueRpm IS NULL THEN 1L ELSE 0L END) AS maxTorqueRpm,
                SUM(CASE WHEN e.compressionRatio IS NULL THEN 1L ELSE 0L END) AS compressionRatio,
                SUM(CASE WHEN e.boreMm IS NULL THEN 1L ELSE 0L END) AS boreMm,
                SUM(CASE WHEN e.strokeMm IS NULL THEN 1L ELSE 0L END) AS strokeMm,
                SUM(CASE WHEN e.coolingSystem IS NULL THEN 1L ELSE 0L END) AS coolingSystem,
                SUM(CASE WHEN e.fuelSystem IS NULL THEN 1L ELSE 0L END) AS fuelSystem,
                SUM(CASE WHEN e.transmissionType IS NULL THEN 1L ELSE 0L END) AS transmissionType,
                SUM(CASE WHEN e.gears IS NULL THEN 1L ELSE 0L END) AS gears,
                SUM(CASE WHEN e.finalDrive IS NULL THEN 1L ELSE 0L END) AS finalDrive,
                SUM(CASE WHEN e.topSpeedKph IS NULL THEN 1L ELSE 0L END) AS topSpeedKph,
                SUM(CASE WHEN e.fuelConsumptionL100km IS NULL THEN 1L ELSE 0L END) AS fuelConsumptionL100km,
                SUM(CASE WHEN e.emissionStandard IS NULL THEN 1L ELSE 0L END) AS emissionStandard
            FROM EngineSpecification e
            """)
    EngineFieldGaps fieldGaps();

    interface EngineFieldGaps {
        Long getEngineType();

        Long getDisplacementCc();

        Long getCylinders();

        Long getValvesPerCylinder();

        Long getMaxPowerHp();

        Long getMaxPowerRpm();

        Long getMaxTorqueNm();

        Long getMaxTorqueRpm();

        Long getCompressionRatio();

        Long getBoreMm();

        Long getStrokeMm();

        Long getCoolingSystem();

        Long getFuelSystem();

        Long getTransmissionType();

        Long getGears();

        Long getFinalDrive();

        Long getTopSpeedKph();

        Long getFuelConsumptionL100km();

        Long getEmissionStandard();
    }
}
