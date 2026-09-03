package com.motorcycle.comparison.repository;

import com.motorcycle.comparison.entity.Category;
import com.motorcycle.comparison.entity.Motorcycle;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Catalogue-wide aggregates for the admin dashboard only: no create/read-one/update/delete, so this extends the
 *  bare marker interface instead of {@link org.springframework.data.jpa.repository.JpaRepository}. */
@Repository
public interface CatalogStatsRepository extends org.springframework.data.repository.Repository<Motorcycle, Long> {

    @Query("SELECT m.brand AS brand, COUNT(m) AS total FROM Motorcycle m GROUP BY m.brand ORDER BY m.brand")
    List<BrandCount> countByBrand();

    @Query("SELECT m.category AS category, COUNT(m) AS total FROM Motorcycle m GROUP BY m.category")
    List<CategoryCount> countByCategory();

    @Query("SELECT m.modelYear AS modelYear, COUNT(m) AS total FROM Motorcycle m GROUP BY m.modelYear ORDER BY m.modelYear")
    List<ModelYearCount> countByModelYear();

    /** MIN/AVG/MAX collapse to one row of NULLs, and pricedCount to zero, when no motorcycle has a price: no
     *  empty-catalogue special case needed here, only downstream where a NULL average needs rounding. */
    @Query("SELECT MIN(m.priceEur) AS min, AVG(m.priceEur) AS avg, MAX(m.priceEur) AS max, COUNT(m) AS pricedCount FROM Motorcycle m WHERE m.priceEur IS NOT NULL")
    PriceStats priceStats();

    @Query("SELECT MAX(m.updatedAt) FROM Motorcycle m")
    Instant lastUpdatedAt();

    /** One row, one round trip: every nullable {@code motorcycles} column in a single {@code SUM(CASE ...)} rather
     *  than thirteen separate {@code COUNT(*) WHERE x IS NULL} queries. A SUM over zero rows is NULL, not zero —
     *  every getter below is a boxed {@code Long} for exactly that reason; see {@code CatalogStatsService.orZero}. */
    @Query("""
            SELECT
                SUM(CASE WHEN m.priceEur IS NULL THEN 1L ELSE 0L END) AS priceEur,
                SUM(CASE WHEN m.imageUrl IS NULL THEN 1L ELSE 0L END) AS imageUrl,
                SUM(CASE WHEN m.description IS NULL THEN 1L ELSE 0L END) AS description,
                SUM(CASE WHEN m.frameType IS NULL THEN 1L ELSE 0L END) AS frameType,
                SUM(CASE WHEN m.frontSuspension IS NULL THEN 1L ELSE 0L END) AS frontSuspension,
                SUM(CASE WHEN m.rearSuspension IS NULL THEN 1L ELSE 0L END) AS rearSuspension,
                SUM(CASE WHEN m.frontBrake IS NULL THEN 1L ELSE 0L END) AS frontBrake,
                SUM(CASE WHEN m.rearBrake IS NULL THEN 1L ELSE 0L END) AS rearBrake,
                SUM(CASE WHEN m.absType IS NULL THEN 1L ELSE 0L END) AS absType,
                SUM(CASE WHEN m.frontTyre IS NULL THEN 1L ELSE 0L END) AS frontTyre,
                SUM(CASE WHEN m.rearTyre IS NULL THEN 1L ELSE 0L END) AS rearTyre,
                SUM(CASE WHEN m.engine IS NULL THEN 1L ELSE 0L END) AS engine,
                SUM(CASE WHEN m.dimension IS NULL THEN 1L ELSE 0L END) AS dimension
            FROM Motorcycle m
            """)
    MotorcycleFieldGaps fieldGaps();

    @Query("SELECT COUNT(spec) FROM Motorcycle m JOIN m.additionalSpecs spec")
    long countAdditionalSpecEntries();

    @Query("SELECT COUNT(m) FROM Motorcycle m WHERE m.additionalSpecs IS EMPTY")
    long countMotorcyclesWithoutAdditionalSpecs();

    interface BrandCount {
        String getBrand();

        long getTotal();
    }

    interface CategoryCount {
        Category getCategory();

        long getTotal();
    }

    interface ModelYearCount {
        Integer getModelYear();

        long getTotal();
    }

    interface PriceStats {
        BigDecimal getMin();

        // JPA's AVG on a numeric path is specified to return Double, not the source type.
        Double getAvg();

        BigDecimal getMax();

        long getPricedCount();
    }

    interface MotorcycleFieldGaps {
        Long getPriceEur();

        Long getImageUrl();

        Long getDescription();

        Long getFrameType();

        Long getFrontSuspension();

        Long getRearSuspension();

        Long getFrontBrake();

        Long getRearBrake();

        Long getAbsType();

        Long getFrontTyre();

        Long getRearTyre();

        Long getEngine();

        Long getDimension();
    }
}
