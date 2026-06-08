package com.labs.server.repository;

import com.labs.server.entity.Admission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdmissionRepository extends JpaRepository<Admission, UUID> {

    List<Admission> findByPatient_IdOrderByAdmissionDateDesc(Integer patientId);
}
