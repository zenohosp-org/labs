package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only projection of the shared HMS hospitals table.
 * Only fields used by the lab billing/auto-bill flow are mapped.
 */
@Entity
@Table(name = "hospitals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hospital {

    @Id
    private UUID id;

    private String name;
    private String subdomain;
    private String code;

    @Column(name = "numeric_code", length = 4)
    private String numericCode;

    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String logoUrl;
    private String description;
    private Boolean isActive;
    private Boolean isListed;
    private String subscriptionPlan;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
