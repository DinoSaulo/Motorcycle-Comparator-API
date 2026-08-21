package com.motorcycle.comparison.service;

import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.dto.request.CreateMotorcycleRequest;
import com.motorcycle.comparison.dto.response.MotorcycleResponse;
import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.exception.ResourceNotFoundException;
import com.motorcycle.comparison.repository.MotorcycleRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MotorcycleService")
class MotorcycleServiceTest {

    @Mock
    private MotorcycleRepository motorcycleRepository;

    @InjectMocks
    private MotorcycleService motorcycleService;

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
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("derives a slug from brand, model and year")
        void derivesSlug() {
            when(motorcycleRepository.existsBySlug(anyString())).thenReturn(false);
            when(motorcycleRepository.save(any(Motorcycle.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            motorcycleService.create(MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024));

            ArgumentCaptor<Motorcycle> captor = ArgumentCaptor.forClass(Motorcycle.class);
            verify(motorcycleRepository).save(captor.capture());
            assertThat(captor.getValue().getSlug()).isEqualTo("yamaha-mt-09-2024");
        }

        @Test
        @DisplayName("appends a numeric suffix when the derived slug is taken")
        void disambiguatesCollidingSlug() {
            when(motorcycleRepository.existsBySlug("yamaha-mt-09-2024")).thenReturn(true);
            when(motorcycleRepository.existsBySlug("yamaha-mt-09-2024-2")).thenReturn(false);
            when(motorcycleRepository.save(any(Motorcycle.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            motorcycleService.create(MotorcycleFixtures.createRequest("Yamaha", "MT-09", 2024));

            ArgumentCaptor<Motorcycle> captor = ArgumentCaptor.forClass(Motorcycle.class);
            verify(motorcycleRepository).save(captor.capture());
            assertThat(captor.getValue().getSlug()).isEqualTo("yamaha-mt-09-2024-2");
        }

        @Test
        @DisplayName("copies both specification blocks onto the new entity")
        void copiesSpecificationBlocks() {
            when(motorcycleRepository.existsBySlug(anyString())).thenReturn(false);
            when(motorcycleRepository.save(any(Motorcycle.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

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
}
