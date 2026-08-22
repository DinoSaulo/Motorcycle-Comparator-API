package com.motorcycle.comparison.service;

import com.motorcycle.comparison.dto.request.CreateMotorcycleRequest;
import com.motorcycle.comparison.dto.request.MotorcycleFilter;
import com.motorcycle.comparison.dto.response.MotorcycleResponse;
import com.motorcycle.comparison.entity.Dimension;
import com.motorcycle.comparison.entity.EngineSpecification;
import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.exception.DuplicateResourceException;
import com.motorcycle.comparison.exception.ResourceNotFoundException;
import com.motorcycle.comparison.repository.MotorcycleRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Catalogue use cases: browse, read, and administer motorcycles. Reads are {@code readOnly} so Hibernate skips
 * dirty checking and the connection can be routed to a replica later without touching this class.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MotorcycleService {

    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-|-$");
    private static final Pattern DISAMBIGUATOR = Pattern.compile("-\\d+$");

    /**
     * Properties a client is allowed to sort the catalogue by. Anything else is rejected with a clean 400 instead
     * of reaching Hibernate, whose own error would name the entity's fully-qualified class to an anonymous caller.
     */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of(
            "id", "brand", "model", "modelYear", "category", "priceEur",
            "createdAt", "updatedAt");

    private static final char LIKE_ESCAPE = '\\';

    private final MotorcycleRepository motorcycleRepository;

    public Page<MotorcycleResponse> search(MotorcycleFilter filter, Pageable pageable) {
        validateSort(pageable.getSort());
        return motorcycleRepository.findAll(toSpecification(filter), pageable)
                .map(MotorcycleResponse::from);
    }

    private static void validateSort(Sort sort) {
        sort.forEach(order -> {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new IllegalArgumentException("Cannot sort by '" + order.getProperty() + "'. Allowed fields: " + String.join(", ", SORTABLE_PROPERTIES));
            }
        });
    }

    public MotorcycleResponse getById(Long id) {
        return MotorcycleResponse.from(requireById(id));
    }

    public MotorcycleResponse getBySlug(String slug) {
        return motorcycleRepository.findWithSpecificationsBySlug(slug).map(MotorcycleResponse::from).orElseThrow(() -> ResourceNotFoundException.of("Motorcycle", slug));
    }

    public List<String> listBrands() {
        return motorcycleRepository.findDistinctBrands();
    }

    @Transactional
    public MotorcycleResponse create(CreateMotorcycleRequest request) {
        Motorcycle motorcycle = new Motorcycle();
        apply(request, motorcycle);
        motorcycle.setSlug(uniqueSlug(request, null));

        Motorcycle saved = motorcycleRepository.save(motorcycle);
        log.info("Created motorcycle id={} slug={}", saved.getId(), saved.getSlug());
        return MotorcycleResponse.from(saved);
    }

    @Transactional
    public MotorcycleResponse update(Long id, CreateMotorcycleRequest request) {
        Motorcycle motorcycle = requireById(id);
        String currentSlug = motorcycle.getSlug();

        apply(request, motorcycle);
        // Regenerate the slug only when the identity fields actually moved, so that
        // existing links to a bike survive an edit to, say, its description.
        if (!slugMatchesIdentity(currentSlug, baseSlug(request))) {
            motorcycle.setSlug(uniqueSlug(request, currentSlug));
        }

        log.info("Updated motorcycle id={}", id);
        return MotorcycleResponse.from(motorcycle);
    }

    @Transactional
    public void delete(Long id) {
        Motorcycle motorcycle = requireById(id);
        motorcycleRepository.delete(motorcycle);
        log.info("Deleted motorcycle id={}", id);
    }

    // --- internals --------------------------------------------------------------

    private Motorcycle requireById(Long id) {
        return motorcycleRepository.findWithSpecificationsById(id).orElseThrow(() -> ResourceNotFoundException.of("Motorcycle", id));
    }

    /**
     * Composes only the predicates the caller actually supplied. The join to the engine table is added only when
     * an engine facet is in play, so the plain "list everything" query stays join-free.
     */
    public static Specification<Motorcycle> toSpecification(MotorcycleFilter filter) {
        return (root, query, cb) -> {
            if (filter == null) {
                return cb.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(filter.brand())) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), filter.brand().toLowerCase(Locale.ROOT)));
            }
            if (filter.category() != null) {
                predicates.add(cb.equal(root.get("category"), filter.category()));
            }
            if (filter.modelYear() != null) {
                predicates.add(cb.equal(root.get("modelYear"), filter.modelYear()));
            }
            if (filter.minPriceEur() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("priceEur"), filter.minPriceEur()));
            }
            if (filter.maxPriceEur() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("priceEur"), filter.maxPriceEur()));
            }
            if (hasText(filter.q())) {
                // Escape the client's own '%'/'_' so free text is matched literally;
                // otherwise "q=%" would match every row instead of being a no-op search.
                String pattern = "%" + escapeLike(filter.q().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("brand")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("model")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("slug")), pattern, LIKE_ESCAPE)));
            }

            boolean needsEngine = filter.minDisplacementCc() != null || filter.maxDisplacementCc() != null || filter.minPowerHp() != null;
            if (needsEngine) {
                var engine = root.join("engine", JoinType.INNER);
                if (filter.minDisplacementCc() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(engine.get("displacementCc"), filter.minDisplacementCc()));
                }
                if (filter.maxDisplacementCc() != null) {
                    predicates.add(cb.lessThanOrEqualTo(engine.get("displacementCc"), filter.maxDisplacementCc()));
                }
                if (filter.minPowerHp() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(engine.get("maxPowerHp"), filter.minPowerHp()));
                }
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void apply(CreateMotorcycleRequest request, Motorcycle target) {
        target.setBrand(request.brand().trim());
        target.setModel(request.model().trim());
        target.setModelYear(request.modelYear());
        target.setCategory(request.category());
        target.setPriceEur(request.priceEur());
        target.setImageUrl(request.imageUrl());
        target.setDescription(request.description());
        target.setFrameType(request.frameType());
        target.setFrontSuspension(request.frontSuspension());
        target.setRearSuspension(request.rearSuspension());
        target.setFrontBrake(request.frontBrake());
        target.setRearBrake(request.rearBrake());
        target.setAbsType(request.absType());
        target.setFrontTyre(request.frontTyre());
        target.setRearTyre(request.rearTyre());

        target.setEngine(mergeEngine(request.engine(), target.getEngine()));
        target.setDimension(mergeDimension(request.dimension(), target.getDimension()));

        Map<String, String> extras = new LinkedHashMap<>();
        if (request.additionalSpecs() != null) {
            extras.putAll(request.additionalSpecs());
        }
        // Mutate in place rather than swapping the reference: an element collection
        // tracked by Hibernate reacts badly to having its instance replaced.
        target.getAdditionalSpecs().clear();
        target.getAdditionalSpecs().putAll(extras);
    }

    private EngineSpecification mergeEngine(CreateMotorcycleRequest.EngineRequest src, EngineSpecification existing) {
        if (src == null) {
            return existing;
        }
        EngineSpecification e = existing != null ? existing : new EngineSpecification();
        e.setEngineType(src.engineType());
        e.setDisplacementCc(src.displacementCc());
        e.setCylinders(src.cylinders());
        e.setValvesPerCylinder(src.valvesPerCylinder());
        e.setMaxPowerHp(src.maxPowerHp());
        e.setMaxPowerRpm(src.maxPowerRpm());
        e.setMaxTorqueNm(src.maxTorqueNm());
        e.setMaxTorqueRpm(src.maxTorqueRpm());
        e.setCompressionRatio(src.compressionRatio());
        e.setBoreMm(src.boreMm());
        e.setStrokeMm(src.strokeMm());
        e.setCoolingSystem(src.coolingSystem());
        e.setFuelSystem(src.fuelSystem());
        e.setTransmissionType(src.transmissionType());
        e.setGears(src.gears());
        e.setFinalDrive(src.finalDrive());
        e.setTopSpeedKph(src.topSpeedKph());
        e.setFuelConsumptionL100km(src.fuelConsumptionL100km());
        e.setEmissionStandard(src.emissionStandard());
        return e;
    }

    private Dimension mergeDimension(CreateMotorcycleRequest.DimensionRequest src, Dimension existing) {
        if (src == null) {
            // Unlike engine (@NotNull), dimension is optional; this full-replacement PUT clears an omitted block
            // instead of keeping the previous value. orphanRemoval on Motorcycle#dimension deletes the row on flush.
            return null;
        }
        Dimension d = existing != null ? existing : new Dimension();
        d.setLengthMm(src.lengthMm());
        d.setWidthMm(src.widthMm());
        d.setHeightMm(src.heightMm());
        d.setWheelbaseMm(src.wheelbaseMm());
        d.setSeatHeightMm(src.seatHeightMm());
        d.setGroundClearanceMm(src.groundClearanceMm());
        d.setKerbWeightKg(src.kerbWeightKg());
        d.setDryWeightKg(src.dryWeightKg());
        d.setFuelCapacityL(src.fuelCapacityL());
        d.setPayloadKg(src.payloadKg());
        return d;
    }

    private String uniqueSlug(CreateMotorcycleRequest request, String slugToKeep) {
        String base = baseSlug(request);
        String candidate = base;
        int suffix = 2;
        while (!candidate.equals(slugToKeep) && motorcycleRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
            if (suffix > 100) {
                throw new DuplicateResourceException("Could not derive a unique slug from " + base);
            }
        }
        return candidate;
    }

    static String baseSlug(CreateMotorcycleRequest request) {
        return slugify(request.brand() + " " + request.model() + " " + request.modelYear());
    }

    /**
     * Whether {@code currentSlug} was derived from {@code base}, exactly or with a numeric disambiguator appended.
     * Not a trailing-{@code -\d+} strip: the base ends in the model year, so that would turn a rename into a no-op.
     */
    static boolean slugMatchesIdentity(String currentSlug, String base) {
        if (currentSlug == null) {
            return false;
        }
        if (currentSlug.equals(base)) {
            return true;
        }
        return currentSlug.startsWith(base + "-") && DISAMBIGUATOR.matcher(currentSlug.substring(base.length())).matches();
    }

    static String slugify(String raw) {
        String ascii = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return EDGE_DASHES.matcher(NON_SLUG_CHARS.matcher(ascii).replaceAll("-")).replaceAll("");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Escapes the LIKE metacharacters so free text is matched literally. */
    private static String escapeLike(String value) {
        return value.replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + "" + LIKE_ESCAPE).replace("%", LIKE_ESCAPE + "%").replace("_", LIKE_ESCAPE + "_");
    }
}
