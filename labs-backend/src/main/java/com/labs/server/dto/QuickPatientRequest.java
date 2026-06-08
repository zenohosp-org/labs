package com.labs.server.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuickPatientRequest {
    private UUID hospitalId;
    private String firstName;
    private String lastName;
    private String phone;
    private String gender;
    private String bloodGroup;
    private LocalDate dob;
    private String email;
}
