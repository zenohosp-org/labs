package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only projection of the HMS-owned {@code doctors} table. Labs only
 * needs (id, user_id, hospital_id) for booking FK validation. No CRUD here —
 * HMS owns the doctor lifecycle.
 *
 * HMS's PK is a plain UUID {@code id} (separate from {@code user_id}), so the
 * booking's {@code doctor_id} column references {@code doctors.id}.
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
