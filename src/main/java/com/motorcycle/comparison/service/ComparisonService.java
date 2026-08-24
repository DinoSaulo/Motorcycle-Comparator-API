package com.motorcycle.comparison.service;

import com.motorcycle.comparison.dto.response.ComparisonResponse;
import com.motorcycle.comparison.dto.response.ComparisonResponse.SpecGroup;
import com.motorcycle.comparison.dto.response.ComparisonResponse.SpecRow;
import com.motorcycle.comparison.dto.response.MotorcycleResponse;
import com.motorcycle.comparison.entity.Dimension;
import com.motorcycle.comparison.entity.EngineSpecification;
import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.exception.ResourceNotFoundException;
import com.motorcycle.comparison.repository.MotorcycleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns N motorcycles into the row-oriented table the frontend renders. All display knowledge — which specs
 * appear, in what order, heading, unit and "better" direction — lives in the {@link #GROUPS} registry below.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComparisonService {

    /** Below two columns there is nothing to compare. */
    public static final int MIN_ITEMS = 2;

    private final MotorcycleRepository motorcycleRepository;

    /**
     * Guard against someone asking for the whole catalogue side by side. The field
     * initialiser is the value plain unit tests see; Spring overwrites it at startup.
     */
    @Value("${app.comparison.max-items:4}")
    private int maxItems = 4;

    public ComparisonResponse compare(List<Long> ids) {
        List<Long> requested = new ArrayList<>(new LinkedHashSet<>(ids)); // de-dupe, keep order
        validateSize(requested);

        Map<Long, Motorcycle> byId = motorcycleRepository
                .findAllWithSpecificationsByIdIn(requested).stream()
                .collect(Collectors.toMap(Motorcycle::getId, Function.identity()));

        List<Long> missing = requested.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw ResourceNotFoundException.of("Motorcycle", missing);
        }

        // Column order must follow the request, not the database.
        List<Motorcycle> ordered = requested.stream().map(byId::get).toList();

        List<SpecGroup> groups = new ArrayList<>();
        for (GroupDef group : GROUPS) {
            List<SpecRow> rows = group.specs().stream().map(spec -> toRow(spec, ordered)).toList();
            groups.add(new SpecGroup(group.name(), rows));
        }
        additionalSpecsGroup(ordered).ifPresent(groups::add);

        return new ComparisonResponse(ordered.stream().map(MotorcycleResponse::from).toList(), List.copyOf(groups));
    }

    private void validateSize(List<Long> ids) {
        if (ids.size() < MIN_ITEMS) {
            throw new IllegalArgumentException("A comparison needs at least " + MIN_ITEMS + " distinct motorcycles");
        }
        if (ids.size() > maxItems) {
            throw new IllegalArgumentException("A comparison accepts at most " + maxItems + " motorcycles");
        }
    }

    // --- row construction -------------------------------------------------------

    private SpecRow toRow(SpecDef spec, List<Motorcycle> bikes) {
        List<Object> raw = bikes.stream().map(b -> spec.extractor().apply(b)).toList();
        List<String> values = raw.stream().map(ComparisonService::format).toList();

        long distinct = values.stream().filter(Objects::nonNull).distinct().count();
        boolean differing = distinct > 1;

        return new SpecRow(spec.label(), spec.unit(), values,
                winners(raw, spec.ranking()), differing);
    }

    /**
     * @return every index holding the best value, so the UI can highlight a tie
     *         instead of arbitrarily crowning the first bike
     */
    private static List<Integer> winners(List<Object> raw, Ranking ranking) {
        if (ranking == Ranking.NONE) {
            return List.of();
        }
        List<BigDecimal> numbers = raw.stream().map(ComparisonService::toNumber).toList();
        Comparator<BigDecimal> comparator = ranking == Ranking.HIGHER_IS_BETTER
                ? Comparator.naturalOrder()
                : Comparator.reverseOrder();
        BigDecimal best = numbers.stream().filter(Objects::nonNull).max(comparator).orElse(null);
        if (best == null) {
            return List.of(); // nothing published on this row: nothing to rank
        }

        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < numbers.size(); i++) {
            if (numbers.get(i) != null && numbers.get(i).compareTo(best) == 0) {
                indexes.add(i);
            }
        }
        // Counted by compareTo, not equals: 117 and 117.0 tie, and a row everybody ties on
        // has no winner worth showing — BigDecimal.equals would call those two a contest.
        return indexes.size() == numbers.stream().filter(Objects::nonNull).count() ? List.of() : List.copyOf(indexes);
    }

    private static BigDecimal toNumber(Object value) {
        return switch (value) {
            case null -> null;
            case BigDecimal b -> b;
            case Integer i -> BigDecimal.valueOf(i);
            case Number n -> BigDecimal.valueOf(n.doubleValue());
            default -> null;
        };
    }

    /** {@code null} travels to the client untouched so the table renders a dash, not a zero. */
    private static String format(Object value) {
        return switch (value) {
            case null -> null;
            case BigDecimal b -> b.stripTrailingZeros().toPlainString();
            case Enum<?> e -> e.name();
            default -> String.valueOf(value);
        };
    }

    /**
     * The long tail: the union of every compared bike's ad-hoc specs, alphabetised
     * so the section is stable across requests. Bikes missing a key get a null cell.
     */
    private Optional<SpecGroup> additionalSpecsGroup(List<Motorcycle> bikes) {
        Set<String> keys = new TreeSet<>();
        bikes.forEach(b -> keys.addAll(b.getAdditionalSpecs().keySet()));
        if (keys.isEmpty()) {
            return Optional.empty();
        }

        List<SpecRow> rows = keys.stream().map(key -> {
            List<String> values = bikes.stream()
                    .map(b -> b.getAdditionalSpecs().get(key))
                    .toList();
            boolean differing = values.stream().filter(Objects::nonNull).distinct().count() > 1;
            return new SpecRow(key, null, values, List.of(), differing);
        }).toList();

        return Optional.of(new SpecGroup("Other specifications", rows));
    }

    // --- spec registry ----------------------------------------------------------

    private enum Ranking { HIGHER_IS_BETTER, LOWER_IS_BETTER, NONE }

    private record SpecDef(String label, String unit,
                           Function<Motorcycle, Object> extractor, Ranking ranking) {}

    private record GroupDef(String name, List<SpecDef> specs) {}

    private static SpecDef spec(String label, String unit,
                                Function<Motorcycle, Object> extractor, Ranking ranking) {
        return new SpecDef(label, unit, extractor, ranking);
    }

    /** Null-safe accessor for the engine block, which may be absent. */
    private static Function<Motorcycle, Object> engine(Function<EngineSpecification, Object> get) {
        return m -> m.getEngine() == null ? null : get.apply(m.getEngine());
    }

    /** Null-safe accessor for the dimension block, which may be absent. */
    private static Function<Motorcycle, Object> dim(Function<Dimension, Object> get) {
        return m -> m.getDimension() == null ? null : get.apply(m.getDimension());
    }

    private static final List<GroupDef> GROUPS = List.of(
            new GroupDef("Overview", List.of(
                    spec("Brand", null, Motorcycle::getBrand, Ranking.NONE),
                    spec("Model", null, Motorcycle::getModel, Ranking.NONE),
                    spec("Model year", null, Motorcycle::getModelYear, Ranking.NONE),
                    spec("Category", null, Motorcycle::getCategory, Ranking.NONE),
                    spec("Price", "EUR", Motorcycle::getPriceEur, Ranking.LOWER_IS_BETTER)
            )),
            new GroupDef("Engine", List.of(
                    spec("Engine type", null, engine(EngineSpecification::getEngineType), Ranking.NONE),
                    spec("Displacement", "cc", engine(EngineSpecification::getDisplacementCc), Ranking.HIGHER_IS_BETTER),
                    spec("Cylinders", null, engine(EngineSpecification::getCylinders), Ranking.NONE),
                    spec("Valves per cylinder", null, engine(EngineSpecification::getValvesPerCylinder), Ranking.NONE),
                    spec("Bore", "mm", engine(EngineSpecification::getBoreMm), Ranking.NONE),
                    spec("Stroke", "mm", engine(EngineSpecification::getStrokeMm), Ranking.NONE),
                    spec("Compression ratio", null, engine(EngineSpecification::getCompressionRatio), Ranking.NONE),
                    spec("Cooling", null, engine(EngineSpecification::getCoolingSystem), Ranking.NONE),
                    spec("Fuel system", null, engine(EngineSpecification::getFuelSystem), Ranking.NONE),
                    spec("Emission standard", null, engine(EngineSpecification::getEmissionStandard), Ranking.NONE)
            )),
            new GroupDef("Performance", List.of(
                    spec("Max power", "hp", engine(EngineSpecification::getMaxPowerHp), Ranking.HIGHER_IS_BETTER),
                    spec("Power peak", "rpm", engine(EngineSpecification::getMaxPowerRpm), Ranking.NONE),
                    spec("Max torque", "Nm", engine(EngineSpecification::getMaxTorqueNm), Ranking.HIGHER_IS_BETTER),
                    spec("Torque peak", "rpm", engine(EngineSpecification::getMaxTorqueRpm), Ranking.NONE),
                    spec("Top speed", "km/h", engine(EngineSpecification::getTopSpeedKph), Ranking.HIGHER_IS_BETTER),
                    spec("Fuel consumption", "l/100km", engine(EngineSpecification::getFuelConsumptionL100km), Ranking.LOWER_IS_BETTER)
            )),
            new GroupDef("Transmission", List.of(
                    spec("Transmission", null, engine(EngineSpecification::getTransmissionType), Ranking.NONE),
                    spec("Gears", null, engine(EngineSpecification::getGears), Ranking.NONE),
                    spec("Final drive", null, engine(EngineSpecification::getFinalDrive), Ranking.NONE)
            )),
            new GroupDef("Chassis & brakes", List.of(
                    spec("Frame", null, Motorcycle::getFrameType, Ranking.NONE),
                    spec("Front suspension", null, Motorcycle::getFrontSuspension, Ranking.NONE),
                    spec("Rear suspension", null, Motorcycle::getRearSuspension, Ranking.NONE),
                    spec("Front brake", null, Motorcycle::getFrontBrake, Ranking.NONE),
                    spec("Rear brake", null, Motorcycle::getRearBrake, Ranking.NONE),
                    spec("ABS", null, Motorcycle::getAbsType, Ranking.NONE),
                    spec("Front tyre", null, Motorcycle::getFrontTyre, Ranking.NONE),
                    spec("Rear tyre", null, Motorcycle::getRearTyre, Ranking.NONE)
            )),
            new GroupDef("Dimensions & weight", List.of(
                    spec("Length", "mm", dim(Dimension::getLengthMm), Ranking.NONE),
                    spec("Width", "mm", dim(Dimension::getWidthMm), Ranking.NONE),
                    spec("Height", "mm", dim(Dimension::getHeightMm), Ranking.NONE),
                    spec("Wheelbase", "mm", dim(Dimension::getWheelbaseMm), Ranking.NONE),
                    spec("Seat height", "mm", dim(Dimension::getSeatHeightMm), Ranking.NONE),
                    spec("Ground clearance", "mm", dim(Dimension::getGroundClearanceMm), Ranking.HIGHER_IS_BETTER),
                    spec("Kerb weight", "kg", dim(Dimension::getKerbWeightKg), Ranking.LOWER_IS_BETTER),
                    spec("Dry weight", "kg", dim(Dimension::getDryWeightKg), Ranking.LOWER_IS_BETTER),
                    spec("Fuel capacity", "l", dim(Dimension::getFuelCapacityL), Ranking.HIGHER_IS_BETTER),
                    spec("Payload", "kg", dim(Dimension::getPayloadKg), Ranking.HIGHER_IS_BETTER)
            ))
    );
}
