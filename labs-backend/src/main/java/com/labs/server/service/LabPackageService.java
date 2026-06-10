package com.labs.server.service;

import com.labs.server.entity.LabPackage;
import com.labs.server.entity.LabPackageItem;
import com.labs.server.repository.LabPackageRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabPackageService {

    private final LabPackageRepository repository;

    public List<LabPackage> list(UUID hospitalId, boolean activeOnly) {
        return activeOnly
                ? repository.findActiveByHospitalId(hospitalId)
                : repository.findByHospitalId(hospitalId);
    }

    @Transactional
    public LabPackage save(UUID hospitalId, PackageRequest req) {
        LabPackage pkg = req.getId() != null
                ? repository.findById(req.getId()).orElse(new LabPackage())
                : new LabPackage();
        if (req.getId() != null && pkg.getHospitalId() != null
                && !hospitalId.equals(pkg.getHospitalId())) {
            throw new RuntimeException("Package does not belong to this hospital");
        }

        pkg.setHospitalId(hospitalId);
        pkg.setName(req.getName());
        pkg.setDescription(req.getDescription());
        pkg.setCategory(req.getCategory() != null ? req.getCategory() : "GENERAL");
        pkg.setPrice(req.getPrice());
        pkg.setTaxRate(req.getTaxRate() != null ? req.getTaxRate() : BigDecimal.ZERO);
        pkg.setValidityDays(req.getValidityDays() != null ? req.getValidityDays() : 1);
        pkg.setActive(req.isActive());

        pkg.getItems().clear();
        if (req.getItems() != null) {
            for (int i = 0; i < req.getItems().size(); i++) {
                ItemRequest it = req.getItems().get(i);
                pkg.getItems().add(LabPackageItem.builder()
                        .labPackage(pkg)
                        .investigationName(it.getInvestigationName())
                        .investigationType(it.getInvestigationType() != null
                                ? it.getInvestigationType() : "PATHOLOGY")
                        .category(it.getCategory())
                        .displayOrder(i)
                        .build());
            }
        }

        return repository.save(pkg);
    }

    @Transactional
    public void toggle(UUID hospitalId, UUID packageId) {
        repository.findById(packageId).ifPresent(p -> {
            if (!hospitalId.equals(p.getHospitalId())) {
                throw new RuntimeException("Package does not belong to this hospital");
            }
            p.setActive(!p.isActive());
            repository.save(p);
        });
    }

    @Transactional
    public void delete(UUID hospitalId, UUID packageId) {
        repository.findById(packageId).ifPresent(p -> {
            if (!hospitalId.equals(p.getHospitalId())) {
                throw new RuntimeException("Package does not belong to this hospital");
            }
            repository.delete(p);
        });
    }

    // ── Request DTOs (inner classes so the controller imports via
    // LabPackageService.* matching the HealthCheckupService pattern) ────

    @Data
    public static class PackageRequest {
        private UUID id;
        private String name;
        private String description;
        private String category;
        private BigDecimal price;
        private BigDecimal taxRate;
        private Integer validityDays;
        private boolean active = true;
        private List<ItemRequest> items;
    }

    @Data
    public static class ItemRequest {
        private String investigationName;
        private String investigationType;
        private String category;
    }
}
