package com.motorcycle.comparison.service;

import com.motorcycle.comparison.entity.Motorcycle;
import com.motorcycle.comparison.repository.MotorcycleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** One insert, one transaction. Exists so {@link MotorcycleService#create} can retry a lost race on the slug unique
 *  index: PostgreSQL aborts the whole transaction on a constraint violation, so the retry needs a fresh one. */
@Component
@RequiredArgsConstructor
public class MotorcycleWriter {

    private final MotorcycleRepository motorcycleRepository;

    /** Persists and flushes in its own transaction, so a constraint violation surfaces here instead of at a commit
     *  the caller no longer controls. REQUIRES_NEW keeps an outer transaction, if any, out of the failure. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Motorcycle save(Motorcycle motorcycle) {
        return motorcycleRepository.saveAndFlush(motorcycle);
    }
}
