package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.entity.Motorcycle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** {@link JpaSpecificationExecutor} powers the catalogue filters: the facets a comparison site offers (brand, category,
 *  displacement range, price range, free text) combine arbitrarily, and a derived query per combination would not scale. */
@Repository
public interface MotorcycleRepository extends JpaRepository<Motorcycle, Long>, JpaSpecificationExecutor<Motorcycle> {

    @EntityGraph(attributePaths = {"engine", "dimension"})
    Optional<Motorcycle> findWithSpecificationsById(Long id);

    @EntityGraph(attributePaths = {"engine", "dimension"})
    Optional<Motorcycle> findWithSpecificationsBySlug(String slug);

    /** Single round trip for the whole comparison set: the to-one joins keep a side-by-side table of N bikes to one query, not N+1. */
    @EntityGraph(attributePaths = {"engine", "dimension"})
    List<Motorcycle> findAllWithSpecificationsByIdIn(Collection<Long> ids);

    boolean existsBySlug(String slug);

    /** Distinct brands, for populating the filter sidebar without a second table. */
    @Query("SELECT DISTINCT m.brand FROM Motorcycle m ORDER BY m.brand")
    List<String> findDistinctBrands();
}
