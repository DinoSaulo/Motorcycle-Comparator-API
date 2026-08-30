package com.motorcycle.comparison.service;

import com.motorcycle.comparison.dto.request.CreateMotorcycleRequest;
import com.motorcycle.comparison.dto.request.MotorcycleFilter;
import com.motorcycle.comparison.dto.response.MotorcycleResponse;
import com.motorcycle.comparison.entity.Dimension;
import com.motorcycle.comparison.entity.EngineSpecification;
import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.exception.ConstraintViolations;
import com.motorcycle.comparison.exception.DomainValidationException;
import com.motorcycle.comparison.exception.DuplicateResourceException;
import com.motorcycle.comparison.exception.ResourceNotFoundException;
import com.motorcycle.comparison.repository.MotorcycleRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Catalogue use cases: browse, read, and administer motorcycles. Reads are {@code readOnly} so Hibernate skips
 *  dirty checking and the connection can be routed to a replica later without touching this class. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MotorcycleService {

    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("(^-)|(-$)");
    private static final Pattern DISAMBIGUATOR = Pattern.compile("-\\d+$");

    private static final String FIELD_BRAND = "brand";
    private static final String FIELD_PRICE_EUR = "priceEur";

    /** Properties a client is allowed to sort the catalogue by. Anything else is rejected with a clean 400 instead
     *  of reaching Hibernate, whose own error would name the entity's fully-qualified class to an anonymous caller. */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of(
            "id", FIELD_BRAND, "model", "modelYear", "category", FIELD_PRICE_EUR,
            "createdAt", "updatedAt");

    private static final String SLUG_UNIQUE_CONSTRAINT = "uk_motorcycles_slug";

    private static final char LIKE_ESCAPE = '\\';

    /** Uploaded images are referenced by a host-relative path, never an absolute URL: the API cannot know its own origin
     *  behind a proxy, and a baked-in {@code localhost:8080} would outlive dev. External {@code http(s)://} values pass through. */
    private static final String IMAGE_URL_PREFIX = "/api/v1/images/motorcycles/";

    private final MotorcycleRepository motorcycleRepository;
    private final MotorcycleWriter motorcycleWriter;
    private final FileStorageService fileStorageService;

    public Page<MotorcycleResponse> search(MotorcycleFilter filter, Pageable pageable) {
        validateSort(pageable.getSort());
        return motorcycleRepository.findAll(toSpecification(filter), pageable)
                .map(MotorcycleResponse::from);
    }

    /** Null precedence is deliberately absent here: Spring Data rejects it on a criteria query, so unpriced bikes are
     *  pinned last by {@code hibernate.order_by.default_null_ordering} in application.yml instead. */
    private static void validateSort(Sort sort) {
        sort.forEach(order -> {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new DomainValidationException("Cannot sort by '" + order.getProperty() + "'. Allowed fields: " + String.join(", ", SORTABLE_PROPERTIES));
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

    /** Deliberately not transactional itself: a violated constraint aborts the transaction it happened in, so the retry
     *  below only works because {@link MotorcycleWriter} gives every attempt a transaction of its own. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MotorcycleResponse create(CreateMotorcycleRequest request) {
        Motorcycle saved;
        try {
            saved = motorcycleWriter.save(newMotorcycle(request));
        } catch (DataIntegrityViolationException ex) {
            if (!ConstraintViolations.isViolationOf(ex, SLUG_UNIQUE_CONSTRAINT)) {
                throw ex;
            }
            // Lost the race to a concurrent insert. The winner is committed by now, so re-deriving
            // moves on to the next free suffix. One retry only; a second loss is an honest 409.
            log.info("Slug collision on insert, retrying once with the next suffix");
            saved = motorcycleWriter.save(newMotorcycle(request));
        }

        log.info("Created motorcycle id={} slug={}", saved.getId(), saved.getSlug());
        return MotorcycleResponse.from(saved);
    }

    /** A full replacement of the specification, with one exception: the image is owned by the {@code /image} endpoints.
     *  Honouring {@code imageUrl} here would let an edit that omits it clear the column and strand the file on disk. */
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
        String orphan = storedFileNameOf(motorcycle.getImageUrl());

        motorcycleRepository.delete(motorcycle);
        // Queued, not yet committed — the same trade-off updateImage takes, and never fatal: deleteFile
        // reports rather than throws, so an unreadable directory cannot block an admin from deleting a row.
        if (orphan != null) {
            fileStorageService.deleteFile(orphan);
        }

        log.info("Deleted motorcycle id={}", id);
    }

    /** Replaces the image of an existing motorcycle. The upload is validated and written first: a rejected file (wrong
     *  type, oversized, bytes disagreeing with the declared type) must fail before the row is touched. */
    @Transactional
    public MotorcycleResponse updateImage(Long id, MultipartFile file) {
        Motorcycle motorcycle = requireById(id);
        String previous = storedFileNameOf(motorcycle.getImageUrl());

        String storedName = fileStorageService.storeFile(file);
        motorcycle.setImageUrl(IMAGE_URL_PREFIX + storedName);

        // The old file is dropped here rather than after commit, deliberately: this transaction updates one column,
        // so a rollback stranding the previous image is far less likely than the disk filling with superseded uploads.
        if (previous != null) {
            fileStorageService.deleteFile(previous);
        }

        log.info("Updated image of motorcycle id={} to {}", id, storedName);
        return MotorcycleResponse.from(motorcycle);
    }

    /** Clears the image. An externally hosted URL is unset but obviously not deleted — it was never ours. */
    @Transactional
    public MotorcycleResponse removeImage(Long id) {
        Motorcycle motorcycle = requireById(id);
        String stored = storedFileNameOf(motorcycle.getImageUrl());

        motorcycle.setImageUrl(null);
        if (stored != null) {
            fileStorageService.deleteFile(stored);
        }

        log.info("Removed image of motorcycle id={}", id);
        return MotorcycleResponse.from(motorcycle);
    }

    // --- internals --------------------------------------------------------------

    /** @return the stored file name behind an image URL this API issued, or {@code null} when the value is absent or
     *          points somewhere else: the caller must not delete a file it did not store. */
    private static String storedFileNameOf(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(IMAGE_URL_PREFIX)) {
            return null;
        }
        String fileName = imageUrl.substring(IMAGE_URL_PREFIX.length());
        return fileName.isBlank() ? null : fileName;
    }

    /** A fresh instance per attempt: an entity whose insert failed is bound to a dead session and cannot be reused. */
    private Motorcycle newMotorcycle(CreateMotorcycleRequest request) {
        Motorcycle motorcycle = new Motorcycle();
        apply(request, motorcycle);
        // Set on create only, and never by apply(): see update() for why a PUT must not carry the image.
        motorcycle.setImageUrl(request.imageUrl());
        motorcycle.setSlug(uniqueSlug(request, null));
        return motorcycle;
    }

    private Motorcycle requireById(Long id) {
        return motorcycleRepository.findWithSpecificationsById(id).orElseThrow(() -> ResourceNotFoundException.of("Motorcycle", id));
    }

    /** Composes only the predicates the caller actually supplied. The join to the engine table is added only when
     *  an engine facet is in play, so the plain "list everything" query stays join-free. */
    public static Specification<Motorcycle> toSpecification(MotorcycleFilter filter) {
        if (filter == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            addBrandPredicate(predicates, root, cb, filter);
            addCategoryPredicate(predicates, root, cb, filter);
            addModelYearPredicate(predicates, root, cb, filter);
            addPricePredicates(predicates, root, cb, filter);
            addFreeTextPredicate(predicates, root, cb, filter);
            addEnginePredicates(predicates, root, cb, filter);
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addBrandPredicate(List<Predicate> predicates, Root<Motorcycle> root, CriteriaBuilder cb,
                                            MotorcycleFilter filter) {
        if (hasText(filter.brand())) {
            predicates.add(cb.equal(cb.lower(root.get(FIELD_BRAND)), filter.brand().toLowerCase(Locale.ROOT)));
        }
    }

    private static void addCategoryPredicate(List<Predicate> predicates, Root<Motorcycle> root, CriteriaBuilder cb,
                                                MotorcycleFilter filter) {
        if (filter.category() != null) {
            predicates.add(cb.equal(root.get("category"), filter.category()));
        }
    }

    private static void addModelYearPredicate(List<Predicate> predicates, Root<Motorcycle> root, CriteriaBuilder cb,
                                                MotorcycleFilter filter) {
        if (filter.modelYear() != null) {
            predicates.add(cb.equal(root.get("modelYear"), filter.modelYear()));
        }
    }

    private static void addPricePredicates(List<Predicate> predicates, Root<Motorcycle> root, CriteriaBuilder cb,
                                                MotorcycleFilter filter) {
        if (filter.minPriceEur() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(FIELD_PRICE_EUR), filter.minPriceEur()));
        }
        if (filter.maxPriceEur() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(FIELD_PRICE_EUR), filter.maxPriceEur()));
        }
    }

    private static void addFreeTextPredicate(List<Predicate> predicates, Root<Motorcycle> root, CriteriaBuilder cb,
                                                MotorcycleFilter filter) {
        if (!hasText(filter.q())) {
            return;
        }
        // Escape the client's own '%'/'_' so free text is matched literally;
        // otherwise "q=%" would match every row instead of being a no-op search.
        String pattern = "%" + escapeLike(filter.q().toLowerCase(Locale.ROOT)) + "%";
        predicates.add(cb.or(
                cb.like(cb.lower(root.get(FIELD_BRAND)), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("model")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("slug")), pattern, LIKE_ESCAPE)));
    }

    private static void addEnginePredicates(List<Predicate> predicates, Root<Motorcycle> root, CriteriaBuilder cb,
                                                MotorcycleFilter filter) {
        boolean needsEngine = filter.minDisplacementCc() != null || filter.maxDisplacementCc() != null
                || filter.minPowerHp() != null;
        if (!needsEngine) {
            return;
        }
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

    private void apply(CreateMotorcycleRequest request, Motorcycle target) {
        target.setBrand(request.brand().trim());
        target.setModel(request.model().trim());
        target.setModelYear(request.modelYear());
        target.setCategory(request.category());
        target.setPriceEur(request.priceEur());
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

    /** Whether {@code currentSlug} was derived from {@code base}, exactly or with a numeric disambiguator appended.
     *  Not a trailing-{@code -\d+} strip: the base ends in the model year, so that would turn a rename into a no-op. */
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
