package com.labs.server.service;

import com.labs.server.entity.LabReferenceRange;
import com.labs.server.repository.LabReferenceRangeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Companion to {@link LabReferenceRangeService} that owns the lazy seed in
 * its own bean so the Spring AOP proxy is real.
 *
 * Same reasoning as {@link LabServiceSeeder}: the parent service is
 * {@code @Transactional(readOnly=true)} and {@code this.seedFor(...)}
 * bypasses the proxy, so Hibernate runs the inserts in a readOnly tx with
 * FlushMode.MANUAL and silently drops them. Moving the seed to a separate
 * bean with {@code REQUIRES_NEW} gives it a fresh writable transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabReferenceRangeSeeder {

    private final LabReferenceRangeRepository repository;
    private final LabReferenceRangeSeed defaults;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedFor(UUID hospitalId) {
        if (repository.countByHospitalId(hospitalId) > 0) {
            return;
        }
        log.info("Seeding {} default lab reference ranges for hospital {}",
                defaults.defaults().size(), hospitalId);
        for (LabReferenceRangeSeed.Default d : defaults.defaults()) {
            repository.save(LabReferenceRange.builder()
                    .hospitalId(hospitalId)
                    .testName(d.testName())
                    .category(d.category())
                    .sex(d.sex())
                    .minAgeYears(d.minAge())
                    .maxAgeYears(d.maxAge())
                    .minValue(d.minValue())
                    .maxValue(d.maxValue())
                    .unit(d.unit())
                    .rangeText(d.rangeText())
                    .isActive(true)
                    .build());
        }
    }
}
