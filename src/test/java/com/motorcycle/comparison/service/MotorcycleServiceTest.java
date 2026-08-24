package com.motorcycle.comparison.service;

import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.dto.request.CreateMotorcycleRequest;
import com.motorcycle.comparison.dto.response.MotorcycleResponse;
import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.exception.DuplicateResourceException;
import com.motorcycle.comparison.exception.ResourceNotFoundException;
import com.motorcycle.comparison.repository.MotorcycleRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MotorcycleService")
class MotorcycleServiceTest {

    @Mock
    private MotorcycleRepository motorcycleRepository;

    @Mock
    private MotorcycleWriter motorcycleWriter;

    @InjectMocks
    private MotorcycleService motorcycleService;

    /** The writer owns the insert now, so the create tests capture what it was handed. */
    private void writerEchoesBackWhatItIsGiven() {
        when(motorcycleWriter.save(any(Motorcycle.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Motorcycle savedByWriter() {
        ArgumentCaptor<Motorcycle> captor = ArgumentCaptor.forClass(Motorcycle.class);
        verify(motorcycleWriter).save(captor.capture());
        return captor.getValue();
    }

    private static DataIntegrityViolationException slugCollision() {
        return new DataIntegrityViolationException("duplicate key",
                new ConstraintViolationException("violates unique constraint", new SQLException("23505"), "uk_motorcycles_slug"));
    }

    @Nested
    @DisplayName("reads")
    class Reads {

        @Test
        @DisplayName("maps the entity and its specification blocks into the response")
        void getByIdMapsSpecifications() {
            Motorcycle yamaha = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 890);
            when(motorcycleRepository.findWithSpecificationsById(1L)).thenReturn(Optional.of(yamaha));

            MotorcycleResponse response = motorcycleService.getById(1L);

            assertThat(response.brand()).isEqualTo("Yamaha");
            assertThat(response.displayName()).isEqualTo("Yamaha MT-09 (2024)");
            assertThat(response.engine().displacementCc()).isEqualTo(890);
            assertThat(response.dimension().seatHeightMm()).isEqualTo(825);
        }

        @Test
        @DisplayName("reports the missing id rather than returning null")
        void getByIdThrowsWhenAbsent() {
            when(motorcycleRepository.findWithSpecificationsById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> motorcycleService.getById(404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("404");
        }

        @Test
        @DisplayName("rejects a sort property outside the allow-list instead of reaching Hibernate")
        void rejectsUnknownSortProperty() {
            Pageable pageable = PageRequest.of(0, 20, Sort.by("engine.displacementCc"));

            assertThatThrownBy(() -> motorcycleService.search(null, pageable))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("engine.displacementCc");
        }

        @Test
        @DisplayName("accepts a sort by an allow-listed property")
        void acceptsAllowedSortProperty() {
            Pageable pageable = PageRequest.of(0, 20, Sort.by("brand"));
            when(motorcycleRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of()));

            assertThatCode(() -> motorcycleService.search(null, pageable))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("passes the client's sort through untouched")
        void passesSortThroughUntouched() {
            // Null precedence is not set here on purpose: Spring Data throws on a criteria query, so the
            // unpriced-last rule lives in hibernate.order_by.default_null_ordering. See MotorcycleRepositoryTest.
            Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "priceEur"));
            when(motorcycleRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

            motorcycleService.search(null, pageable);

            verify(motorcycleRepository).findAll(any(Specification.class), eq(pageable));
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("derives a slug from brand, model and year")
        void derivesSlug() {
            when(motorcycleRepository.existsBySlug(anyString())).thenReturn(false);
            writerEchoesBackWhatItIsGiven();

            motorcycleService.create(MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024));

            assertThat(savedByWriter().getSlug()).isEqualTo("yamaha-mt-09-2024");
        }

        @Test
        @DisplayName("appends a numeric suffix when the derived slug is taken")
        void disambiguatesCollidingSlug() {
            when(motorcycleRepository.existsBySlug("yamaha-mt-09-2024")).thenReturn(true);
            when(motorcycleRepository.existsBySlug("yamaha-mt-09-2024-2")).thenReturn(false);
            writerEchoesBackWhatItIsGiven();

            motorcycleService.create(MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024));

            assertThat(savedByWriter().getSlug()).isEqualTo("yamaha-mt-09-2024-2");
        }

        @Test
        @DisplayName("retries once with the next suffix when a concurrent insert wins the slug")
        void retriesOnceAfterLosingTheSlugRace() {
            // The winner commits between our existsBySlug and our flush, so only the unique index ever sees it.
            when(motorcycleRepository.existsBySlug("yamaha-mt-09-2024")).thenReturn(false, true);
            when(motorcycleRepository.existsBySlug("yamaha-mt-09-2024-2")).thenReturn(false);
            when(motorcycleWriter.save(any(Motorcycle.class)))
                    .thenThrow(slugCollision())
                    .thenAnswer(invocation -> invocation.getArgument(0));

            MotorcycleResponse response = motorcycleService.create(
                    MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024));

            assertThat(response.slug()).isEqualTo("yamaha-mt-09-2024-2");
            verify(motorcycleWriter, times(2)).save(any(Motorcycle.class));
        }

        @Test
        @DisplayName("gives up after a second lost race instead of retrying forever")
        void retriesAtMostOnce() {
            when(motorcycleRepository.existsBySlug(anyString())).thenReturn(false);
            when(motorcycleWriter.save(any(Motorcycle.class))).thenThrow(slugCollision());

            assertThatThrownBy(() -> motorcycleService.create(
                    MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            verify(motorcycleWriter, times(2)).save(any(Motorcycle.class));
        }

        @Test
        @DisplayName("does not retry a violation of some other constraint")
        void doesNotRetryUnrelatedConstraint() {
            when(motorcycleRepository.existsBySlug(anyString())).thenReturn(false);
            DataIntegrityViolationException checkViolation = new DataIntegrityViolationException("bad row",
                    new ConstraintViolationException("violates check constraint", new SQLException("23514"), "ck_motorcycles_price_eur"));
            when(motorcycleWriter.save(any(Motorcycle.class))).thenThrow(checkViolation);

            assertThatThrownBy(() -> motorcycleService.create(
                    MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024)))
                    .isSameAs(checkViolation);
            verify(motorcycleWriter, times(1)).save(any(Motorcycle.class));
        }

        @Test
        @DisplayName("gives up deriving a unique slug after too many collisions")
        void givesUpAfterTooManyCollisions() {
            when(motorcycleRepository.existsBySlug(anyString())).thenReturn(true);

            assertThatThrownBy(() -> motorcycleService.create(
                    MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024)))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("yamaha-mt-09-2024");
        }

        @Test
        @DisplayName("copies both specification blocks onto the new entity")
        void copiesSpecificationBlocks() {
            when(motorcycleRepository.existsBySlug(anyString())).thenReturn(false);
            writerEchoesBackWhatItIsGiven();

            MotorcycleResponse response = motorcycleService.create(
                    MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024));

            assertThat(response.engine().displacementCc()).isEqualTo(890);
            assertThat(response.engine().finalDrive()).isEqualTo("Chain");
            assertThat(response.dimension().kerbWeightKg()).isEqualByComparingTo("193.0");
            assertThat(response.additionalSpecs()).containsEntry("Rider modes", "4");
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("keeps the existing slug when the identity fields are unchanged")
        void keepsSlugWhenIdentityUnchanged() {
            Motorcycle existing = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 890);
            existing.setSlug("yamaha-mt-09-2024");
            when(motorcycleRepository.findWithSpecificationsById(1L)).thenReturn(Optional.of(existing));

            CreateMotorcycleRequest request = MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024);
            motorcycleService.update(1L, request);

            assertThat(existing.getSlug()).isEqualTo("yamaha-mt-09-2024");
            // No collision probing needed when the slug does not move.
            verify(motorcycleRepository, never()).existsBySlug(anyString());
        }

        @Test
        @DisplayName("regenerates the slug when the model year changes")
        void regeneratesSlugWhenIdentityChanges() {
            Motorcycle existing = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 890);
            existing.setSlug("yamaha-mt-09-2024");
            when(motorcycleRepository.findWithSpecificationsById(1L)).thenReturn(Optional.of(existing));
            when(motorcycleRepository.existsBySlug("yamaha-mt-09-2025")).thenReturn(false);

            motorcycleService.update(1L, MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2025));

            assertThat(existing.getSlug()).isEqualTo("yamaha-mt-09-2025");
        }

        @Test
        @DisplayName("mutates the existing engine row instead of replacing it")
        void reusesEngineInstance() {
            Motorcycle existing = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 890);
            var originalEngine = existing.getEngine();
            when(motorcycleRepository.findWithSpecificationsById(1L)).thenReturn(Optional.of(existing));

            motorcycleService.update(1L, MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024));

            // Swapping the instance would make orphanRemoval delete and re-insert the row.
            assertThat(existing.getEngine()).isSameAs(originalEngine);
            assertThat(existing.getEngine().getEngineType()).isEqualTo("Inline-3");
        }

        @Test
        @DisplayName("clears the dimension block when a full-replace request omits it")
        void clearsDimensionWhenOmitted() {
            Motorcycle existing = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 890);
            existing.setSlug("yamaha-mt-09-2024");
            when(motorcycleRepository.findWithSpecificationsById(1L)).thenReturn(Optional.of(existing));

            CreateMotorcycleRequest request =
                    MotorcycleFixtures.createRequestWithoutDimension("Yamaha", "MT-09", 2024);
            motorcycleService.update(1L, request);

            // PUT is a full replace: an omitted optional block clears like any other
            // omitted field, it does not silently keep the previous value.
            assertThat(existing.getDimension()).isNull();
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("refuses to delete an unknown id")
        void deleteUnknownId() {
            when(motorcycleRepository.findWithSpecificationsById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> motorcycleService.delete(9L))
                    .isInstanceOf(ResourceNotFoundException.class);
            // Typed matcher: JpaSpecificationExecutor also declares delete(Specification).
            verify(motorcycleRepository, never()).delete(any(Motorcycle.class));
        }
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @DisplayName("slugify normalises accents, spaces and punctuation")
    @CsvSource({
            "Yamaha MT-09 2024, yamaha-mt-09-2024",
            "Ducati Multistrada V4 S, ducati-multistrada-v4-s",
            "Zero SR/F 2024, zero-sr-f-2024",
            "Bétaménos  Ção 125, betamenos-cao-125",
            "'  Honda   CB650R  ', honda-cb650r"
    })
    void slugify(String raw, String expected) {
        assertThat(MotorcycleService.slugify(raw)).isEqualTo(expected);
    }

    @Nested
    @DisplayName("slugMatchesIdentity")
    class SlugMatchesIdentity {

        @Test
        @DisplayName("treats a null slug as never matching, e.g. an entity created before slugs existed")
        void nullSlugNeverMatches() {
            assertThat(MotorcycleService.slugMatchesIdentity(null, "yamaha-mt-09-2024")).isFalse();
        }

        @Test
        @DisplayName("matches the base slug exactly")
        void matchesExactBase() {
            assertThat(MotorcycleService.slugMatchesIdentity("yamaha-mt-09-2024", "yamaha-mt-09-2024")).isTrue();
        }

        @Test
        @DisplayName("matches the base with a single-digit disambiguator")
        void matchesSingleDigitDisambiguator() {
            assertThat(MotorcycleService.slugMatchesIdentity("yamaha-mt-09-2024-2", "yamaha-mt-09-2024")).isTrue();
        }

        @Test
        @DisplayName("matches the base with a multi-digit disambiguator")
        void matchesMultiDigitDisambiguator() {
            assertThat(MotorcycleService.slugMatchesIdentity("yamaha-mt-09-2024-137", "yamaha-mt-09-2024")).isTrue();
        }

        @Test
        @DisplayName("rejects a non-numeric suffix instead of mistaking it for a disambiguator")
        void rejectsNonNumericSuffix() {
            assertThat(MotorcycleService.slugMatchesIdentity("yamaha-mt-09-2024-limited", "yamaha-mt-09-2024")).isFalse();
        }

        @Test
        @DisplayName("rejects trailing content after the disambiguator")
        void rejectsTrailingContentAfterDisambiguator() {
            assertThat(MotorcycleService.slugMatchesIdentity("yamaha-mt-09-2024-2-extra", "yamaha-mt-09-2024")).isFalse();
        }

        @Test
        @DisplayName("rejects a slug that merely starts with the base without the dash separator")
        void rejectsConcatenationWithoutSeparator() {
            assertThat(MotorcycleService.slugMatchesIdentity("yamaha-mt-09-20245", "yamaha-mt-09-2024")).isFalse();
        }

        @Test
        @DisplayName("rejects an unrelated slug entirely")
        void rejectsUnrelatedSlug() {
            assertThat(MotorcycleService.slugMatchesIdentity("honda-cb650r-2024", "yamaha-mt-09-2024")).isFalse();
        }
    }

    @Test
    @DisplayName("baseSlug combines brand, model and model year before slugifying")
    void baseSlugCombinesIdentityFields() {
        CreateMotorcycleRequest request = MotorcycleFixtures.createRequest("Ducati", "Multistrada V4 S", 2025);

        assertThat(MotorcycleService.baseSlug(request)).isEqualTo("ducati-multistrada-v4-s-2025");
    }
}
