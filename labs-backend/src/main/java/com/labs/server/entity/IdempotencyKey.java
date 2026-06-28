package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Phase 10 — caches a batch-create response keyed by (hospital_id,
 * idempotency_key) so a retried POST /api/investigations/batch returns the
 * original requisition + ids instead of spawning duplicates.
 *
 * Retention: 24h via {@link #expiresAt}; safe to prune with a cron later.
 */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKey.PK.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "requisition_number", nullable = false, length = 40)
    private String requisitionNumber;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "lab_order_ids", nullable = false, columnDefinition = "bigint[]")
    private Long[] labOrderIds;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "radiology_order_ids", nullable = false, columnDefinition = "bigint[]")
    private Long[] radiologyOrderIds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (expiresAt == null) expiresAt = createdAt.plusHours(24);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private UUID hospitalId;
        private String idempotencyKey;
    }
}
