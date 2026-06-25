package com.labs.server.repository;

import com.labs.server.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByHospitalIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
            UUID hospitalId, String entityType, String entityId, Pageable pageable);

    Page<AuditLog> findByHospitalIdOrderByOccurredAtDesc(UUID hospitalId, Pageable pageable);

    Page<AuditLog> findByHospitalIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID hospitalId, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
