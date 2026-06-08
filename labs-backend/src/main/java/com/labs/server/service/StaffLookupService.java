package com.labs.server.service;

import com.labs.server.dto.StaffDTO;
import com.labs.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffLookupService {

    private final UserRepository userRepository;

    public List<StaffDTO> list(UUID hospitalId) {
        return userRepository.findByHospital_IdOrderByFirstName(hospitalId).stream()
                .map(u -> StaffDTO.builder()
                        .id(u.getId())
                        .firstName(u.getFirstName())
                        .lastName(u.getLastName())
                        .email(u.getEmail())
                        .phone(u.getPhone())
                        .role(u.getRole() != null ? u.getRole().getName() : null)
                        .designation(u.getDesignation())
                        .build())
                .collect(Collectors.toList());
    }
}
