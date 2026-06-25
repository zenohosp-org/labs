package com.labs.server.service;

import com.labs.server.entity.LabTestCatalog;
import com.labs.server.repository.LabTestCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Separate bean for the test-catalogue lazy seed so the @Transactional proxy
 * is actually applied.
 *
 * Why this isn't inline in {@link LabTestCatalogService}: that service is
 * {@code @Transactional(readOnly = true)} at class level. A
 * {@code this.seedFor(...)} call from {@code list()} bypasses Spring's
 * AOP proxy and runs in the readOnly transaction — Hibernate sets
 * FlushMode.MANUAL on readOnly transactions, so {@code saveAll(...)}
 * queues inserts that are then silently discarded at commit. Net effect:
 * the catalog never seeds.
 *
 * Calling {@code seeder.seedFor(...)} from another bean DOES go through the
 * proxy, and {@link Propagation#REQUIRES_NEW} suspends the readOnly parent
 * transaction and opens a fresh writable one, so the inserts actually flush.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabTestCatalogSeeder {

    private final LabTestCatalogRepository repository;
    private final LabTestCatalogSeed defaults;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedFor(UUID hospitalId) {
        // Re-check inside the new transaction — another request may have raced
        // us to the seed and the unique index would otherwise fire.
        if (repository.countByHospitalId(hospitalId) > 0) {
            return;
        }
        List<LabTestCatalog> rows = defaults.defaults(hospitalId);
        log.info("Seeding {} default lab test catalogue rows for hospital {}", rows.size(), hospitalId);
        repository.saveAll(rows);
    }
}
