package com.labs.server.service;

import com.labs.server.dto.AdmissionDTO;
import com.labs.server.dto.PatientDTO;
import com.labs.server.dto.QuickPatientRequest;
import com.labs.server.entity.Hospital;
import com.labs.server.entity.Patient;
import com.labs.server.repository.AdmissionRepository;
import com.labs.server.repository.HospitalRepository;
import com.labs.server.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientLookupService {

    private final PatientRepository patientRepository;
    private final HospitalRepository hospitalRepository;
    private final AdmissionRepository admissionRepository;

    public List<PatientDTO> search(UUID hospitalId, String q) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) return List.of();
        return patientRepository.search(hospitalId, query)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public PatientDTO get(Integer id) {
        return patientRepository.findById(id).map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
    }

    @Transactional
    public PatientDTO quickCreate(QuickPatientRequest req) {
        Hospital hospital = hospitalRepository.findById(req.getHospitalId())
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        Patient patient = Patient.builder()
                .hospital(hospital)
                .uhid(generateUhid(hospital.getId()))
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .gender(req.getGender() != null ? req.getGender() : "UNKNOWN")
                .phone(req.getPhone())
                .email(req.getEmail())
                .bloodGroup(req.getBloodGroup())
                .dob(req.getDob())
                .build();
        return toDTO(patientRepository.save(patient));
    }

    public List<AdmissionDTO> getAdmissionsByPatient(Integer patientId) {
        return admissionRepository.findByPatient_IdOrderByAdmissionDateDesc(patientId)
                .stream().map(a -> AdmissionDTO.builder()
                        .id(a.getId())
                        .admissionNumber(a.getAdmissionNumber())
                        .ipdId(a.getIpdId())
                        .admissionDate(a.getAdmissionDate())
                        .actualDischargeDate(a.getActualDischargeDate())
                        .status(a.getStatus() != null ? a.getStatus().name() : null)
                        .patientId(a.getPatient() != null ? a.getPatient().getId() : null)
                        .build())
                .collect(Collectors.toList());
    }

    private String generateUhid(UUID hospitalId) {
        String uhid;
        do {
            long value = 10_000_000_000_000L + ThreadLocalRandom.current().nextLong(90_000_000_000_000L);
            uhid = String.valueOf(value);
        } while (patientRepository.findByHospital_IdAndUhid(hospitalId, uhid).isPresent());
        return uhid;
    }

    private PatientDTO toDTO(Patient p) {
        return PatientDTO.builder()
                .id(p.getId())
                .uhid(p.getUhid())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .gender(p.getGender())
                .dob(p.getDob())
                .phone(p.getPhone())
                .email(p.getEmail())
                .bloodGroup(p.getBloodGroup())
                .build();
    }
}
