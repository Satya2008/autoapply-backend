package com.autoapply.user.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProfileUpdateRequest {
    private String fullName;
    private String phone;
    private String location;
    private String currentRole;
    private Integer experienceYears;
    private Long expectedSalary;
    private String preferredJobType;
    private List<String> skills;
    private List<String> targetRoles;
    private List<String> preferredLocations;
    private List<String> excludedCompanies;
    private List<String> excludedKeywords;
    private String coverLetterTemplate;
    private String linkedinUrl;
    private String githubUrl;
    private String portfolioUrl;
    private String noticePeriod;
    private Boolean willingToRelocate;
    private Boolean autoApplyEnabled;
    private Integer dailyApplyLimit;
    private Integer minMatchScore;
    private Boolean notificationsEnabled;
}
