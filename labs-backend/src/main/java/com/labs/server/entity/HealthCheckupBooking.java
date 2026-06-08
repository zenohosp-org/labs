package com.labs.server.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "health_checkup_bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthCheckupBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Hospital hospital;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "hospital"})
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "package_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "hospital", "tests"})
    private HealthPackage healthPackage;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "hospital", "user"})
    private Doctor assignedDoctor;

    @Column(name = "booking_number", unique = true, length = 30)
    private String bookingNumber;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    @Convert(converter = com.labs.server.converter.CheckupBookingStatusConverter.class)
    @Column(name = "status_id")
    @Builder.Default
    private CheckupBookingStatus status = CheckupBookingStatus.SCHEDULED;

    @jakarta.persistence.Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    // PENDING → BILLED (invoice produced on COMPLETED) → PAID (invoice fully
    // collected). Denormalised summary for booking list / detail UI.
    @Column(name = "payment_status", length = 20)
    @Builder.Default
    private String paymentStatus = "PENDING";

    // Invoice produced by auto-billing on COMPLETED transition; null until then.
    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "amount_paid", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "doctor_notes", columnDefinition = "TEXT")
    private String doctorNotes;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    private List<HealthCheckupResult> results = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
