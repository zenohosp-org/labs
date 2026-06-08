package com.labs.server.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "health_checkup_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthCheckupResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    @JsonIgnore
    private HealthCheckupBooking booking;

    @Column(name = "test_name", nullable = false, length = 150)
    private String testName;

    @Column(name = "test_category", length = 50)
    private String testCategory;

    @Column(name = "normal_range", length = 100)
    private String normalRange;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "result_value", columnDefinition = "TEXT")
    private String resultValue;

    @Column(name = "result_status", length = 20)
    @Builder.Default
    private String resultStatus = "PENDING";

    @Column(name = "result_notes", columnDefinition = "TEXT")
    private String resultNotes;

    @Column(name = "mandatory")
    @Builder.Default
    private boolean mandatory = true;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
