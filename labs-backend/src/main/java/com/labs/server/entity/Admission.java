package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Shared HMS admissions table — read-only from labs' perspective. Lab orders
 * may be linked to an active admission so their charges roll into the IPD bill.
 */
@Entity
@Table(name = "admissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admission {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "admission_number", length = 30)
    private String admissionNumber;

    @Column(name = "ipd_id", length = 20)
    private String ipdId;

    @Column(name = "admission_date")
    private LocalDateTime admissionDate;

    @Column(name = "actual_discharge_date")
    private LocalDateTime actualDischargeDate;

    @Convert(converter = com.labs.server.converter.AdmissionStatusConverter.class)
    @Column(name = "status_id")
    private AdmissionStatus status;
}
