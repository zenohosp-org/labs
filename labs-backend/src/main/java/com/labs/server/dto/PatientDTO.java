package com.labs.server.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDTO {
    private Integer id;
    private String uhid;
    private String firstName;
    private String lastName;
    private String gender;
    private LocalDate dob;
    private String phone;
    private String email;
    private String bloodGroup;
}
