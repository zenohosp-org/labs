package com.labs.server.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** JSON shape must match HMS character-for-character. Do not rename fields. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadiologyOrderDTO {
    private Long id;
    private UUID hospitalId;
    private Integer patientId;
    private String patientName;
    private String patientUhid;
    private UUID admissionId;
    private String admissionNumber;
    private String serviceName;
    private String specializationName;
    private String referredByName;
    private UUID technicianId;
    private String technicianName;
    private String priority;
    private String status;
    private LocalDate scheduledDate;
    private String billNo;
    private BigDecimal price;
    private LocalDateTime scannedAt;
    private LocalDateTime reportedAt;
    private String findings;
    private String observation;
    private String reportId;
    private String createdByName;
    private LocalDateTime createdAt;

    // ── Payment surface (derived via JOIN to invoice_items + invoices) ──
    // Null while the order has never been billed (PENDING_SCAN / AWAITING_REPORT).
    // Once auto-bill fires (report generated + price set), these populate from
    // the linked invoice. Field names match HMS InvoiceStatus character-for-
    // character so the frontend can colour-code without a second lookup.
    private String invoiceStatus;
    private String invoiceNumber;
    private BigDecimal invoicePaid;
    private BigDecimal invoiceTotal;
}
