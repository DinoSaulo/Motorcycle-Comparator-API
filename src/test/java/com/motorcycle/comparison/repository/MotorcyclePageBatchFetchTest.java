package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.dto.response.MotorcycleResponse;
import com.motorcycle.comparison.service.MotorcycleService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code engine} and {@code dimension} are {@code FetchType.LAZY}, and the paginated search path — unlike
 * {@code findWithSpecificationsById} — has no {@code @EntityGraph}. Without {@code default_batch_fetch_size: 32}
 * (application.yml), mapping a page of N motorcycles to {@link MotorcycleResponse} would fire 2N extra selects,
 * one lazy load at a time. This proves the batch setting actually catches it, not just that it is present in a
 * config file nobody re-reads once it works.
 */
// showSql = false: @DataJpaTest defaults spring.jpa.show-sql to true regardless of application.yml.
@DataJpaTest(showSql = false, properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("Catalogue page fetch")
class MotorcyclePageBatchFetchTest {

    private static final int PAGE_SIZE = 20;

    @Autowired
    private MotorcycleRepository motorcycleRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("batches the lazy specification blocks of a whole page instead of loading them one row at a time")
    void batchFetchesLazySpecificationBlocks() {
        for (int i = 0; i < PAGE_SIZE; i++) {
            entityManager.persist(MotorcycleFixtures.motorcycle(null, "Brand" + i, "Model" + i, 600 + i));
        }
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<MotorcycleResponse> page = motorcycleRepository
                .findAll(MotorcycleService.toSpecification(null), PageRequest.of(0, PAGE_SIZE, Sort.by("brand")))
                .map(MotorcycleResponse::from)
                .getContent();

        assertThat(page).hasSize(PAGE_SIZE);
        assertThat(page).allSatisfy(response -> {
            assertThat(response.engine()).isNotNull();
            assertThat(response.dimension()).isNotNull();
        });
        // The honest minimum is 4: the page select, the count select Page<> needs, and one batch
        // select per lazy association. A true N+1 here would cost roughly 2 * PAGE_SIZE more than that.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);
    }
}
