package com.labs.server.repository;

import com.labs.server.entity.LabSpecimen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabSpecimenRepository extends JpaRepository<LabSpecimen, Long> {

    List<LabSpecimen> findByLabOrderIdOrderByCreatedAtAsc(Long labOrderId);

    long countByLabOrderId(Long labOrderId);

    Optional<LabSpecimen> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);
}
