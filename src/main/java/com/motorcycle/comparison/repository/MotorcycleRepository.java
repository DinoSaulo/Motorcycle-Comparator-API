package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.entity.Motorcycle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link JpaSpecificationExecutor} is what powers the catalogue filters: the
 * facets a comparison site offers (brand, category, displacement range, price
 * range, free text) combine arbitrarily, and a derived query per combination
 * would not scale.
 */
@Repository
public interface MotorcycleRepository
        extends JpaRepository<Motorcycle, Long>, JpaSpecificationExecutor<Motorcycle> {

    @EntityGraph(attributePaths = {"engine", "dimension"})
    Optional<Motorcycle> findWithSpecificationsById(Long id);

    @EntityGraph(attributePaths = {"engine", "dimension"})
    Optional<Motorcycle> findWithSpecificationsBySlug(String slug);

    /**
     * Single round trip for the whole comparison set. The to-one joins keep this
     * to one query, which is the point: a side-by-side table of N bikes must not
     * cost N+1 selects.
     */
    @EntityGraph(attributePaths = {"engine", "dimension"})
    List<Motorcycle> findAllWithSpecificationsByIdIn(Collection<Long> ids);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = {"engine", "dimension"})
    Page<Motorcycle> findAllBy(Pageable pageable);

    /** Distinct brands, for populating the filter sidebar without a second table. */
    @Query(
            "SELECT DISTINCT m.brand FROM Motorcycle m ORDER BY m.brand")
    List<String> findDistinctBrands();
}
