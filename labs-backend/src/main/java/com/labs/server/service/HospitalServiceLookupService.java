package com.labs.server.service;

import com.labs.server.dto.HospitalServiceDTO;
import com.labs.server.repository.HospitalServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HospitalServiceLookupService {

    private final HospitalServiceRepository hospitalServiceRepository;

    public List<HospitalServiceDTO> list(UUID hospitalId) {
        return hospitalServiceRepository.findByHospitalIdOrderByName(hospitalId).stream()
                .map(s -> HospitalServiceDTO.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .price(s.getPrice())
                        .gstRate(s.getGstRate())
                        .isActive(s.getIsActive())
                        .build())
                .collect(Collectors.toList());
    }
}
