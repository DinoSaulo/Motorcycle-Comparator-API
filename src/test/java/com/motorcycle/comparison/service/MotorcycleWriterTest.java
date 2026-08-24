package com.motorcycle.comparison.service;

import com.motorcycle.comparison.MotorcycleFixtures;
import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.repository.MotorcycleRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Thin as the class itself: the only behaviour worth locking in is that it delegates to
 * {@code saveAndFlush} — never plain {@code save} — and neither swallows nor wraps what comes back.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MotorcycleWriter")
class MotorcycleWriterTest {

    @Mock
    private MotorcycleRepository motorcycleRepository;

    private MotorcycleWriter motorcycleWriter;

    @Test
    @DisplayName("flushes through the repository and hands back exactly what it received")
    void savesAndFlushesTheSameInstance() {
        motorcycleWriter = new MotorcycleWriter(motorcycleRepository);
        Motorcycle toSave = MotorcycleFixtures.motorcycle(null, "Yamaha", "MT-09", 890);
        Motorcycle persisted = MotorcycleFixtures.motorcycle(1L, "Yamaha", "MT-09", 890);
        when(motorcycleRepository.saveAndFlush(toSave)).thenReturn(persisted);

        Motorcycle result = motorcycleWriter.save(toSave);

        assertThat(result).isSameAs(persisted);
        verify(motorcycleRepository).saveAndFlush(toSave);
        verifyNoMoreInteractions(motorcycleRepository);
    }

    @Test
    @DisplayName("lets a constraint violation propagate instead of swallowing it")
    void propagatesFlushFailure() {
        motorcycleWriter = new MotorcycleWriter(motorcycleRepository);
        Motorcycle toSave = MotorcycleFixtures.motorcycle(null, "Yamaha", "MT-09", 890);
        DataIntegrityViolationException violation = new DataIntegrityViolationException("duplicate key",
                new ConstraintViolationException("violates unique constraint", new SQLException("23505"), "uk_motorcycles_slug"));
        when(motorcycleRepository.saveAndFlush(toSave)).thenThrow(violation);

        assertThatThrownBy(() -> motorcycleWriter.save(toSave)).isSameAs(violation);
    }
}
