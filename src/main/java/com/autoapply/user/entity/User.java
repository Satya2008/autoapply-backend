package com.autoapply.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_role", columnList = "role")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    private String fullName;
    private String phone;
    private String location;

    @Column(name = "`current_role`")
    private String currentRole;

    private Integer experienceYears;
    private Long expectedSalary;
    private String preferredJobType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_skills", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "skill")
    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_target_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "target_role")
    @Builder.Default
    private List<String> targetRoles = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_preferred_locations", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "location_name")
    @Builder.Default
    private List<String> preferredLocations = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_excluded_companies", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "company")
    @Builder.Default
    private List<String> excludedCompanies = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_excluded_keywords", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "keyword")
    @Builder.Default
    private List<String> excludedKeywords = new ArrayList<>();

    private String resumeFilePath;
    private String resumeFileName;

    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String resumeText;

    private LocalDateTime resumeUploadedAt;

    @Column(columnDefinition = "TEXT")
    private String coverLetterTemplate;

    private String linkedinUrl;
    private String githubUrl;
    private String portfolioUrl;
    private String noticePeriod;
    private Boolean willingToRelocate;

    @Builder.Default
    private Boolean autoApplyEnabled = false;

    /** Overrides the global daily cap for this user when set. */
    private Integer dailyApplyLimit;

    /** Overrides the global minimum match score for this user when set. */
    private Integer minMatchScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Builder.Default
    private Boolean enabled = true;

    @Builder.Default
    private Integer failedLoginAttempts = 0;

    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;

    @Builder.Default
    private Boolean notificationsEnabled = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (autoApplyEnabled == null) autoApplyEnabled = false;
        if (role == null) role = Role.USER;
        if (enabled == null) enabled = true;
        if (failedLoginAttempts == null) failedLoginAttempts = 0;
        if (notificationsEnabled == null) notificationsEnabled = true;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Transient
    @JsonIgnore
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }
}
