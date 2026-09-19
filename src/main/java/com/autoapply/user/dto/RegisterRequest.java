package com.autoapply.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class RegisterRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String fullName;

    private String phone;
    private String location;
    private String currentRole;
    private Integer experienceYears;
    private Long expectedSalary;
    private String preferredJobType;
    private List<String> skills;
    private List<String> targetRoles;
}
