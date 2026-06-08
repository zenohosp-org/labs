package com.labs.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name;

    private String description;
}
