package com.labs.server.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mirror of HMS InvoiceItem. The {@code lab_order_id} column is added by labs
 * (alongside the existing radiology_order_id) so HMS billing can dedupe and
 * recompute totals when a lab line is auto-injected from the labs side.
 */
@Entity
@Table(name = "invoice_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonBackReference
    private Invoice invoice;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "radiology_order_id")
    private Long radiologyOrderId;

    @Column(name = "lab_order_id")
    private Long labOrderId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "ambulance_booking_id")
    private Long ambulanceBookingId;

    @Column(name = "item_type", length = 30)
    private String itemType;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "waiver_amount", precision = 10, scale = 2)
    private BigDecimal waiverAmount;

    @Column(name = "waiver_reason", length = 255)
    private String waiverReason;
}
