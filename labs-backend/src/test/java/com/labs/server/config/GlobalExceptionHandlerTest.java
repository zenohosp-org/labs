package com.labs.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void wrongStateMapsToConflictWithMessage() {
        var res = handler.handleRuntime(new RuntimeException(
                "Order is not in AWAITING_REPORT or IN_PROGRESS state — current: REPORT_GENERATED"));
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody()).containsEntry("message",
                "Order is not in AWAITING_REPORT or IN_PROGRESS state — current: REPORT_GENERATED");
    }

    @Test
    void requiredFieldMapsToBadRequestWithMessage() {
        var res = handler.handleRuntime(new RuntimeException("testCode is required"));
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody()).containsEntry("message", "testCode is required");
    }

    @Test
    void notFoundMapsTo404() {
        var res = handler.handleRuntime(new RuntimeException("Lab order not found: 48"));
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void responseStatusExceptionPassesThroughUnchanged() {
        var res = handler.handleResponseStatus(new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "No signed report for order 48 — call /sign first"));
        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(res.getBody()).containsEntry("message", "No signed report for order 48 — call /sign first");
    }

    @Test
    void unexpectedExceptionStays500WithGenericMessage() {
        var res = handler.handleUnexpected(new NullPointerException("internal detail that must not leak"));
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().get("message").toString()).doesNotContain("internal detail");
    }
}
