package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit trail for labs-owned entity mutations.
 *
 * Populated by {@link com.labs.server.service.AuditService}. Required for the
 * HIPAA / NABL audit-trail surface that Phase 7 will fully formalise; lands in
 * Phase 0 because every later phase (specimens, results, amendments) needs
 * somewhere to write its change history from day one.
 *
 * No @PreUpdate / @PreRemove on purpose — rows are written once and never
 * mutated. App code only calls save(); a future DB-level guard will revoke
 * UPDATE/DELETE for the app user once we cut over to an audit_writer role.
 */
@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id")
    private UUID hospitalId;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    /** CREATE | UPDATE | DELETE | STATUS_CHANGE | READ_SENSITIVE */
    @Column(name = "operation", nullable = false, length = 20)
    private String operation;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_email", length = 200)
    private String userEmail;

    @Column(name = "user_role", length = 50)
    private String userRole;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value_json", columnDefinition = "jsonb")
    private String oldValueJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value_json", columnDefinition = "jsonb")
    private String newValueJson;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(name = "reason_notes", columnDefinition = "TEXT")
    private String reasonNotes;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }
}
