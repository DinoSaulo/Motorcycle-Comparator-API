package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.dto.request.MotorcycleFilter;
import com.motorcycle.comparison.entity.Category;
import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.service.MotorcycleService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the queries against a real (in-memory) database: Mockito cannot tell us whether an entity graph, a
 * criteria join or a JPQL projection actually compiles and returns the right rows — only a database can.
 */
@DataJpaTest
@DisplayName("MotorcycleRepository")
class MotorcycleRepositoryTest {

    @Autowired
    private MotorcycleRepository motorcycleRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long yamahaId;
    private Long bmwId;

    @BeforeEach
    void seed() {
        Motorcycle yamaha = MotorcycleFixtures.motorcycle(null, "Yamaha", "MT-09", 890);
        yamaha.setPriceEur(new BigDecimal("10499.00"));
        yamaha.getEngine().setMaxPowerHp(new BigDecimal("117.0"));
        yamaha.getAdditionalSpecs().put("Rider modes", "4");

        Motorcycle honda = MotorcycleFixtures.motorcycle(null, "Honda", "CB650R", 649);
        honda.setPriceEur(new BigDecimal("9290.00"));
        honda.getEngine().setMaxPowerHp(new BigDecimal("94.0"));

        Motorcycle bmw = MotorcycleFixtures.motorcycle(null, "BMW", "R 1300 GS", 1300);
        bmw.setCategory(Category.ADVENTURE);
        bmw.setPriceEur(new BigDecimal("20500.00"));
        bmw.getEngine().setMaxPowerHp(new BigDecimal("145.0"));

        yamahaId = entityManager.persistAndGetId(yamaha, Long.class);
        bmwId = entityManager.persistAndGetId(bmw, Long.class);
        entityManager.persist(honda);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("loads both specification blocks eagerly through the entity graph")
    void loadsSpecificationsEagerly() {
        Motorcycle found = motorcycleRepository.findWithSpecificationsById(yamahaId).orElseThrow();

        assertThat(found.getEngine().getDisplacementCc()).isEqualTo(890);
        assertThat(found.getDimension().getSeatHeightMm()).isEqualTo(825);
    }

    @Test
    @DisplayName("cascades the engine and dimension rows on save")
    void cascadesSpecificationBlocks() {
        EntityManager em = entityManager.getEntityManager();

        Long engineCount = em.createQuery("SELECT COUNT(e) FROM EngineSpecification e", Long.class).getSingleResult();
        Long dimensionCount = em.createQuery("SELECT COUNT(d) FROM Dimension d", Long.class).getSingleResult();

        assertThat(engineCount).isEqualTo(3);
        assertThat(dimensionCount).isEqualTo(3);
    }

    @Test
    @DisplayName("persists the ad-hoc spec map into its side table")
    void persistsAdditionalSpecs() {
        Motorcycle found = motorcycleRepository.findWithSpecificationsById(yamahaId).orElseThrow();

        assertThat(found.getAdditionalSpecs()).containsEntry("Rider modes", "4");
    }

    @Test
    @DisplayName("fetches a whole comparison set in one call")
    void fetchesComparisonSet() {
        List<Motorcycle> found = motorcycleRepository.findAllWithSpecificationsByIdIn(List.of(yamahaId, bmwId));

        assertThat(found).hasSize(2)
                .extracting(Motorcycle::getBrand)
                .containsExactlyInAnyOrder("Yamaha", "BMW");
    }

    @Test
    @DisplayName("reports slug availability")
    void reportsSlugAvailability() {
        assertThat(motorcycleRepository.existsBySlug("yamaha-mt-09-2024")).isTrue();
        assertThat(motorcycleRepository.existsBySlug("nothing-like-this")).isFalse();
    }

    @Test
    @DisplayName("puts an unpriced bike last whichever way the price sort runs")
    void unpricedBikesSortLastInBothDirections() {
        // The default is NULLS FIRST for DESC, which would open page 1 of "most expensive" with every unpriced bike.
        Motorcycle unpriced = MotorcycleFixtures.motorcycle(null, "Prototype", "No Price", 900);
        unpriced.setPriceEur(null);
        entityManager.persist(unpriced);
        entityManager.flush();

        assertThat(brandsSortedByPrice(Sort.Direction.DESC)).containsExactly("BMW", "Yamaha", "Honda", "Prototype");
        assertThat(brandsSortedByPrice(Sort.Direction.ASC)).containsExactly("Honda", "Yamaha", "BMW", "Prototype");
    }

    private List<String> brandsSortedByPrice(Sort.Direction direction) {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(direction, "priceEur"));
        return motorcycleRepository.findAll(MotorcycleService.toSpecification(null), pageable).map(Motorcycle::getBrand).getContent();
    }

    @Test
    @DisplayName("bumps the version counter on every update")
    void bumpsVersionOnUpdate() {
        Motorcycle managed = motorcycleRepository.findById(yamahaId).orElseThrow();
        assertThat(managed.getVersion()).isZero();

        managed.setDescription("Edited by the first admin");
        motorcycleRepository.saveAndFlush(managed);

        assertThat(managed.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("rejects a write based on a version another admin has already moved on from")
    void rejectsStaleWrite() {
        Motorcycle stale = motorcycleRepository.findById(yamahaId).orElseThrow();
        // Stands in for the other admin's PUT committing first, without a second persistence context.
        entityManager.getEntityManager().createNativeQuery("UPDATE motorcycles SET version = version + 1 WHERE id = :id").setParameter("id", yamahaId).executeUpdate();

        stale.setDescription("Edited by the loser of the race");

        assertThatThrownBy(() -> motorcycleRepository.saveAndFlush(stale)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("returns distinct brands in alphabetical order")
    void returnsDistinctBrands() {
        assertThat(motorcycleRepository.findDistinctBrands()).containsExactly("BMW", "Honda", "Yamaha");
    }

    // --- filters, driven through the criteria specification ---------------------

    @Test
    @DisplayName("filters by category")
    void filtersByCategory() {
        assertThat(search(new MotorcycleFilter(null, Category.ADVENTURE, null, null, null, null, null, null, null))).containsExactly("BMW");
    }

    @Test
    @DisplayName("filters by displacement range through the engine join")
    void filtersByDisplacementRange() {
        assertThat(search(new MotorcycleFilter(null, null, null, 600, 900, null, null, null, null))).containsExactlyInAnyOrder("Yamaha", "Honda");
    }

    @Test
    @DisplayName("filters by minimum power through the engine join")
    void filtersByMinimumPower() {
        assertThat(search(new MotorcycleFilter(null, null, null, null, null, new BigDecimal("100"), null, null, null))).containsExactlyInAnyOrder("Yamaha", "BMW");
    }

    @Test
    @DisplayName("filters by price ceiling")
    void filtersByPriceCeiling() {
        assertThat(search(new MotorcycleFilter(null, null, null, null, null, null, null, new BigDecimal("11000"), null))).containsExactlyInAnyOrder("Yamaha", "Honda");
    }

    @Test
    @DisplayName("free text matches brand, model or slug, case-insensitively")
    void freeTextSearch() {
        assertThat(search(new MotorcycleFilter(null, null, null, null, null, null, null, null, "cb650"))).containsExactly("Honda");
        assertThat(search(new MotorcycleFilter(null, null, null, null, null, null, null, null, "YAMAHA"))).containsExactly("Yamaha");
    }

    @Test
    @DisplayName("treats the client's own wildcard characters as literal text")
    void freeTextWildcardIsTreatedLiterally() {
        // None of the seeded brands/models/slugs contain a literal '%' or '_', so an unescaped LIKE would wrongly match everything.
        assertThat(search(new MotorcycleFilter(null, null, null, null, null, null, null, null, "%"))).isEmpty();
        assertThat(search(new MotorcycleFilter(null, null, null, null, null, null, null, null, "_"))).isEmpty();
    }

    @Test
    @DisplayName("combines facets with AND")
    void combinesFacets() {
        assertThat(search(new MotorcycleFilter("Yamaha", Category.NAKED, 2024, 800, 1000,
                new BigDecimal("100"), null, new BigDecimal("11000"), "mt")))
                .containsExactly("Yamaha");
    }

    @Test
    @DisplayName("an empty filter constrains nothing")
    void emptyFilterReturnsEverything() {
        assertThat(search(new MotorcycleFilter(null, null, null, null, null, null, null, null, null))).hasSize(3);
    }

    private List<String> search(MotorcycleFilter filter) {
        return motorcycleRepository.findAll(MotorcycleService.toSpecification(filter), PageRequest.of(0, 10)).map(Motorcycle::getBrand).getContent();
    }
}
