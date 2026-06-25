package com.labs.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labs.server.context.AuthContext;
import com.labs.server.entity.AuditLog;
import com.labs.server.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes append-only audit rows for labs-owned mutations.
 *
 * Designed to be called from inside service methods at the moment of change
 * (rather than from a JPA EntityListener) so the caller can pass the actual
 * domain-meaningful operation name ({@code STATUS_CHANGE}, {@code AMENDED})
 * and a reason code where the workflow demands one.
 *
 * Every write happens in {@link Propagation#REQUIRES_NEW} so an audit failure
 * (rare — JSON serialisation issue, DB hiccup) does NOT roll back the
 * surrounding business transaction. Audit is observational; it must never
 * block a clinical action.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<AuthContext> authContextProvider;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public void record(String entityType,
                       String entityId,
                       String operation,
                       UUID hospitalId,
                       Object oldValue,
                       Object newValue) {
        record(entityType, entityId, operation, hospitalId, oldValue, newValue, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String entityType,
                       String entityId,
                       String operation,
                       UUID hospitalId,
                       Object oldValue,
                       Object newValue,
                       String reasonCode,
                       String reasonNotes) {
        try {
            AuditLog row = AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .operation(operation)
                    .hospitalId(hospitalId)
                    .oldValueJson(toJson(oldValue))
                    .newValueJson(toJson(newValue))
                    .reasonCode(reasonCode)
                    .reasonNotes(reasonNotes)
                    .build();
            populateRequestContext(row);
            repository.save(row);
        } catch (Exception e) {
            // Never throw out of audit — observational only.
            log.warn("Audit write failed for {}#{} op={}: {}", entityType, entityId, operation, e.getMessage());
        }
    }

    private void populateRequestContext(AuditLog row) {
        AuthContext auth = authContextProvider.getIfAvailable();
        if (auth != null) {
            try {
                String userId = auth.getUserId();
                if (userId != null) {
                    try { row.setUserId(UUID.fromString(userId)); } catch (IllegalArgumentException ignored) { }
                }
                row.setUserEmail(auth.getEmail());
                row.setUserRole(auth.getRole());
                if (row.getHospitalId() == null) {
                    row.setHospitalId(auth.getHospitalId());
                }
            } catch (Exception ignored) {
                // Auth context not request-scoped here (e.g. async path) — leave nulls.
            }
        }
        HttpServletRequest req = requestProvider.getIfAvailable();
        if (req != null) {
            String forwardedFor = req.getHeader("X-Forwarded-For");
            row.setSourceIp(forwardedFor != null ? forwardedFor.split(",")[0].trim() : req.getRemoteAddr());
            String ua = req.getHeader("User-Agent");
            if (ua != null && ua.length() > 500) ua = ua.substring(0, 500);
            row.setUserAgent(ua);
        }
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.debug("Audit JSON serialisation failed for {}: {}", value.getClass(), e.getMessage());
            return "{\"_audit_error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
