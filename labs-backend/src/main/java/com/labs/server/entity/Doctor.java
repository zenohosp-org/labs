package com.labs.server.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only projection of the HMS-owned {@code doctors} table. Labs only needs
 * (id, hospital_id, user_id, name fields) for booking validation + responses.
 * No CRUD here — HMS is the source of truth for doctor records.
 */
@Entity
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Doctor {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id", insertable = false, updatable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Hospital hospital;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "hospital_id")
    private UUID hospitalId;

    @Column(name = "specialization_id_1")
    private UUID specializationId1;

    @Column(name = "consultation_fee", precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Column(name = "follow_up_fee", precision = 10, scale = 2)
    private BigDecimal followUpFee;

    @Column(name = "is_active")
    private Boolean isActive;
}
