package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.entity.EngineSpecification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Engine specs are always reached through their {@link com.motorcycle.comparison.entity.Motorcycle} and cascade
 * from it, so this repository exists for analytics-style reads (displacement distributions) rather than writes.
 */
@Repository
public interface EngineSpecificationRepository extends JpaRepository<EngineSpecification, Long> {

    long countByDisplacementCcBetween(Integer minCc, Integer maxCc);
}
