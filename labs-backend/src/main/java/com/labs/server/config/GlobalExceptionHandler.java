package com.labs.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every service in this app signals a business-rule violation by throwing a
 * plain {@code RuntimeException} with a human-readable message ("Order is
 * not in AWAITING_REPORT or IN_PROGRESS state", "testCode is required",
 * "Only PRELIMINARY results can be verified", ...). Without this handler,
 * Spring Boot's default error controller turns every one of those into a
 * bare {@code 500 Internal Server Error} with NO message in the response
 * body (server.error.include-message defaults to "never") — so a lab tech
 * hitting a real, named validation rule (wrong order status, a blank
 * required field, a result that's already been verified) sees the exact
 * same opaque failure as an actual server crash. That's indistinguishable
 * from "it just doesn't save" with no way to tell why.
 *
 * This makes every existing (and future) domain-thrown RuntimeException
 * carry its message to the caller, with a sane status:
 *   - a RuntimeException whose message names a specific already-decided
 *     status (see {@link #classify}) maps to that status;
 *   - everything else defaults to 400 Bad Request — still far more
 *     actionable than a silent 500, without requiring every throw site in
 *     the codebase to be rewritten to a typed exception hierarchy.
 * {@link ResponseStatusException} (used where code already picked an
 * explicit status, e.g. ReportPdfService's 404 for an unsigned report)
 * passes through unchanged.
 *
 * Genuinely unexpected exceptions (NPEs, DB errors, etc.) still return 500,
 * logged in full server-side; the client-facing message stays generic so
 * internals never leak.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
        log.warn("{} — {}", ex.getStatusCode(), message);
        return ResponseEntity.status(ex.getStatusCode()).body(body(ex.getStatusCode().value(), message));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        HttpStatus status = classify(ex.getMessage());
        log.warn("{} — {}", status.value(), ex.getMessage());
        String message = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage() : "Request could not be completed.";
        return ResponseEntity.status(status).body(body(status.value(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(500, "Something went wrong. Please try again or contact support."));
    }

    /**
     * Best-effort status classification from the message text every existing
     * throw site already writes in plain English. Deliberately conservative:
     * only maps the patterns that are unambiguous across the codebase's
     * actual wording, defaults everything else to 400.
     */
    private static HttpStatus classify(String message) {
        if (message == null) return HttpStatus.BAD_REQUEST;
        String m = message.toLowerCase();
        if (m.contains("not found") || m.contains("vanished")) return HttpStatus.NOT_FOUND;
        if (m.contains("already") || m.contains("only ") || m.contains("not in ")
                || m.contains("does not belong") || m.contains("access denied")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static Map<String, Object> body(int status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("message", message);
        return m;
    }
}
