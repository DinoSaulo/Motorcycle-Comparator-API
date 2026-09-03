package com.motorcycle.comparison.service;

import com.motorcycle.comparison.dto.response.CatalogStatsResponse;
import com.motorcycle.comparison.dto.response.CatalogStatsResponse.AdditionalSpecsStats;
import com.motorcycle.comparison.dto.response.CatalogStatsResponse.PriceStats;
import com.motorcycle.comparison.dto.response.CatalogStatsResponse.RelatedTableStats;
import com.motorcycle.comparison.entity.Category;
import com.motorcycle.comparison.repository.CatalogStatsRepository;
import com.motorcycle.comparison.repository.CatalogStatsRepository.BrandCount;
import com.motorcycle.comparison.repository.CatalogStatsRepository.CategoryCount;
import com.motorcycle.comparison.repository.CatalogStatsRepository.ModelYearCount;
import com.motorcycle.comparison.repository.CatalogStatsRepository.MotorcycleFieldGaps;
import com.motorcycle.comparison.repository.DimensionRepository;
import com.motorcycle.comparison.repository.DimensionRepository.DimensionFieldGaps;
import com.motorcycle.comparison.repository.EngineSpecificationRepository;
import com.motorcycle.comparison.repository.EngineSpecificationRepository.EngineFieldGaps;
import com.motorcycle.comparison.repository.MotorcycleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Assembles the admin dashboard: catalogue totals/breakdowns plus how much of each specification block is still
 *  missing. Read-only by nature, and cheap: the whole catalogue is small enough that one query per figure, rather
 *  than a single mega-join, keeps each query readable without a measurable cost. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogStatsService {

    private static final int PRICE_SCALE = 2;

    private final MotorcycleRepository motorcycleRepository;
    private final CatalogStatsRepository catalogStatsRepository;
    private final EngineSpecificationRepository engineSpecificationRepository;
    private final DimensionRepository dimensionRepository;

    public CatalogStatsResponse getStats() {
        MotorcycleFieldGaps motorcycleGaps = catalogStatsRepository.fieldGaps();

        return new CatalogStatsResponse(
                motorcycleRepository.count(),
                toBrandMap(catalogStatsRepository.countByBrand()),
                toCategoryMap(catalogStatsRepository.countByCategory()),
                toModelYearMap(catalogStatsRepository.countByModelYear()),
                toPriceStats(catalogStatsRepository.priceStats()),
                catalogStatsRepository.lastUpdatedAt(),
                toMotorcycleFieldGapsMap(motorcycleGaps),
                new RelatedTableStats(engineSpecificationRepository.count(), orZero(motorcycleGaps.getEngine()),
                        toEngineFieldGapsMap(engineSpecificationRepository.fieldGaps())),
                new RelatedTableStats(dimensionRepository.count(), orZero(motorcycleGaps.getDimension()),
                        toDimensionFieldGapsMap(dimensionRepository.fieldGaps())),
                new AdditionalSpecsStats(catalogStatsRepository.countAdditionalSpecEntries(),
                        catalogStatsRepository.countMotorcyclesWithoutAdditionalSpecs()));
    }

    // --- internals --------------------------------------------------------------

    private static Map<String, Long> toBrandMap(List<BrandCount> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        rows.forEach(row -> map.put(row.getBrand(), row.getTotal()));
        return map;
    }

    private static Map<Category, Long> toCategoryMap(List<CategoryCount> rows) {
        Map<Category, Long> map = new LinkedHashMap<>();
        rows.forEach(row -> map.put(row.getCategory(), row.getTotal()));
        return map;
    }

    private static Map<Integer, Long> toModelYearMap(List<ModelYearCount> rows) {
        Map<Integer, Long> map = new LinkedHashMap<>();
        rows.forEach(row -> map.put(row.getModelYear(), row.getTotal()));
        return map;
    }

    private static PriceStats toPriceStats(CatalogStatsRepository.PriceStats row) {
        return new PriceStats(row.getMin(), roundAvg(row.getAvg()), row.getMax(), row.getPricedCount());
    }

    private static BigDecimal roundAvg(Double avg) {
        return avg == null ? null : BigDecimal.valueOf(avg).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /** Keys mirror the field names already used by {@code MotorcycleResponse}, so a frontend can correlate a gap
     *  count with the field it describes without a second naming scheme. */
    private static Map<String, Long> toMotorcycleFieldGapsMap(MotorcycleFieldGaps g) {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("priceEur", orZero(g.getPriceEur()));
        map.put("imageUrl", orZero(g.getImageUrl()));
        map.put("description", orZero(g.getDescription()));
        map.put("frameType", orZero(g.getFrameType()));
        map.put("frontSuspension", orZero(g.getFrontSuspension()));
        map.put("rearSuspension", orZero(g.getRearSuspension()));
        map.put("frontBrake", orZero(g.getFrontBrake()));
        map.put("rearBrake", orZero(g.getRearBrake()));
        map.put("absType", orZero(g.getAbsType()));
        map.put("frontTyre", orZero(g.getFrontTyre()));
        map.put("rearTyre", orZero(g.getRearTyre()));
        map.put("engine", orZero(g.getEngine()));
        map.put("dimension", orZero(g.getDimension()));
        return map;
    }

    private static Map<String, Long> toEngineFieldGapsMap(EngineFieldGaps g) {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("engineType", orZero(g.getEngineType()));
        map.put("displacementCc", orZero(g.getDisplacementCc()));
        map.put("cylinders", orZero(g.getCylinders()));
        map.put("valvesPerCylinder", orZero(g.getValvesPerCylinder()));
        map.put("maxPowerHp", orZero(g.getMaxPowerHp()));
        map.put("maxPowerRpm", orZero(g.getMaxPowerRpm()));
        map.put("maxTorqueNm", orZero(g.getMaxTorqueNm()));
        map.put("maxTorqueRpm", orZero(g.getMaxTorqueRpm()));
        map.put("compressionRatio", orZero(g.getCompressionRatio()));
        map.put("boreMm", orZero(g.getBoreMm()));
        map.put("strokeMm", orZero(g.getStrokeMm()));
        map.put("coolingSystem", orZero(g.getCoolingSystem()));
        map.put("fuelSystem", orZero(g.getFuelSystem()));
        map.put("transmissionType", orZero(g.getTransmissionType()));
        map.put("gears", orZero(g.getGears()));
        map.put("finalDrive", orZero(g.getFinalDrive()));
        map.put("topSpeedKph", orZero(g.getTopSpeedKph()));
        map.put("fuelConsumptionL100km", orZero(g.getFuelConsumptionL100km()));
        map.put("emissionStandard", orZero(g.getEmissionStandard()));
        return map;
    }

    private static Map<String, Long> toDimensionFieldGapsMap(DimensionFieldGaps g) {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("lengthMm", orZero(g.getLengthMm()));
        map.put("widthMm", orZero(g.getWidthMm()));
        map.put("heightMm", orZero(g.getHeightMm()));
        map.put("wheelbaseMm", orZero(g.getWheelbaseMm()));
        map.put("seatHeightMm", orZero(g.getSeatHeightMm()));
        map.put("groundClearanceMm", orZero(g.getGroundClearanceMm()));
        map.put("kerbWeightKg", orZero(g.getKerbWeightKg()));
        map.put("dryWeightKg", orZero(g.getDryWeightKg()));
        map.put("fuelCapacityL", orZero(g.getFuelCapacityL()));
        map.put("payloadKg", orZero(g.getPayloadKg()));
        return map;
    }

    /** A {@code SUM(CASE ...)} collapses to one NULL row, not zero, when the table behind it is empty. */
    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
