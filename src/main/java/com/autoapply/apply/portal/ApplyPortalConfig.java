package com.autoapply.apply.portal;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Teaches the browser engine how to fill one job portal's application form.
 * Every selector lives here, so supporting a new portal - or repairing one after a
 * site redesign - is an edit in the admin dashboard, never a code change.
 */
@Entity
@Table(name = "apply_portal_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplyPortalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false, length = 80)
    private String code;

    @Column(nullable = false)
    private String name;

    /** Substring or regex matched against the apply URL to pick this config. */
    @Column(nullable = false, length = 400)
    private String urlPattern;

    @Builder.Default
    private Boolean enabled = true;

    @Builder.Default
    private Integer priority = 100;

    /** CSS selector that must be present for the page to count as loaded. */
    @Column(length = 500)
    private String readySelector;

    /** Optional selector clicked first to reveal the form (an "Apply" button). */
    @Column(length = 500)
    private String openFormSelector;

    /**
     * JSON array describing each field to fill, in order. Example:
     * [{"selector":"#firstName","valueFrom":"firstName","type":"text","required":true},
     *  {"selector":"#resume","valueFrom":"resumeFile","type":"file"},
     *  {"selector":"#noticePeriod","valueFrom":"noticePeriod","type":"select"}]
     *
     * valueFrom names a candidate field: fullName, firstName, lastName, email, phone,
     * location, currentRole, experienceYears, expectedSalary, noticePeriod, linkedinUrl,
     * githubUrl, portfolioUrl, coverLetter, resumeFile.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String fieldMappingJson;

    /** Selector for the submit button. */
    @Column(length = 500)
    private String submitSelector;

    /** Selector that appears only on success - used to confirm the application landed. */
    @Column(length = 500)
    private String successSelector;

    /** Text that, if present on the page, means the attempt failed. */
    @Column(length = 500)
    private String failureText;

    /** Selectors clicked before filling, e.g. cookie banners. One per line. */
    @Column(columnDefinition = "TEXT")
    private String dismissSelectors;

    /** When true the engine fills the form but never clicks submit - useful for testing. */
    @Builder.Default
    private Boolean dryRun = false;

    @Builder.Default
    private Integer maxWaitSeconds = 20;

    // --- telemetry ---
    @Builder.Default
    private Integer successCount = 0;
    @Builder.Default
    private Integer failureCount = 0;
    private LocalDateTime lastUsedAt;
    @Column(length = 1000)
    private String lastError;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (enabled == null) enabled = true;
        if (dryRun == null) dryRun = false;
        if (priority == null) priority = 100;
        if (successCount == null) successCount = 0;
        if (failureCount == null) failureCount = 0;
        if (maxWaitSeconds == null) maxWaitSeconds = 20;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
