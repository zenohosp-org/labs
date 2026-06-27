package com.labs.server.service;

import com.labs.server.entity.LabService;
import com.labs.server.repository.LabServiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V14 — radiology lazy seed.
 *
 * Gated by two properties for safe per-tenant rollout ahead of the fleet GA:
 *   labs.seed.radiology.enabled=false                  (master switch; default off)
 *   labs.seed.radiology.allowed-hospital-ids=<csv>     (UUID list; empty=none; "ALL"=every tenant)
 *
 * Same REQUIRES_NEW + idempotency pattern as {@link LabServiceSeeder} so the
 * AOP proxy applies and the writable tx flushes regardless of the caller's
 * readOnly state. Count check is scoped to discipline=RADIOLOGY — a tenant
 * that already has PATHOLOGY rows seeded still gets RADIOLOGY rows added.
 */
@Slf4j
@Service
public class RadiologyServiceSeeder {

    private final LabServiceRepository repository;
    private final RadiologyServiceSeed defaults;
    private final boolean enabled;
    private final Set<UUID> allowedHospitalIds;
    private final boolean allowAll;

    public RadiologyServiceSeeder(LabServiceRepository repository,
                                  RadiologyServiceSeed defaults,
                                  @Value("${labs.seed.radiology.enabled:false}") boolean enabled,
                                  @Value("${labs.seed.radiology.allowed-hospital-ids:}") String allowedCsv) {
        this.repository = repository;
        this.defaults = defaults;
        this.enabled = enabled;
        String trimmed = allowedCsv == null ? "" : allowedCsv.trim();
        this.allowAll = "ALL".equalsIgnoreCase(trimmed);
        this.allowedHospitalIds = allowAll || trimmed.isEmpty()
                ? Set.of()
                : new HashSet<>(parseUuids(trimmed));
        log.info("RadiologyServiceSeeder ready — enabled={} allowAll={} explicitAllowList={}",
                this.enabled, this.allowAll, this.allowedHospitalIds);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedFor(UUID hospitalId) {
        if (!enabled) return;
        if (!allowAll && !allowedHospitalIds.contains(hospitalId)) {
            return;
        }
        if (repository.countByHospitalIdAndDiscipline(hospitalId, "RADIOLOGY") > 0) {
            return;
        }
        List<LabService> rows = defaults.defaults(hospitalId);
        log.info("V14 seeding {} radiology rows for hospital {}", rows.size(), hospitalId);
        repository.saveAll(rows);
    }

    private static List<UUID> parseUuids(String csv) {
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }
}
