package com.labs.server.service;

import com.labs.server.entity.HospitalService;
import com.labs.server.repository.HospitalServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Mirror of HMS {@code HospitalServiceService}. The shared table is writable
 * from both apps; labs writes the same rows HMS does.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class HospitalServiceService {

    private final HospitalServiceRepository repository;

    public List<HospitalService> getServicesByHospital(UUID hospitalId) {
        return repository.findByHospitalIdOrderByName(hospitalId);
    }

    @Transactional
    public HospitalService createService(HospitalService service) {
        return repository.save(service);
    }

    @Transactional
    public HospitalService updateService(UUID id, HospitalService details) {
        HospitalService service = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));

        service.setName(details.getName());
        service.setSpecializationId(details.getSpecializationId());
        service.setPrice(details.getPrice());
        service.setGstRate(details.getGstRate());
        service.setIsActive(details.getIsActive());

        return repository.save(service);
    }

    @Transactional
    public void deleteService(UUID id) {
        repository.deleteById(id);
    }

    @Transactional
    public void toggleStatus(UUID id) {
        HospitalService service = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found"));
        service.setIsActive(!Boolean.TRUE.equals(service.getIsActive()));
        repository.save(service);
    }
}
