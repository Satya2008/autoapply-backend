package com.autoapply.apply.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "applications", indexes = {
        @Index(name = "idx_app_user", columnList = "userId"),
        @Index(name = "idx_app_status", columnList = "status"),
        @Index(name = "idx_app_applied", columnList = "appliedAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;

    @Column(length = 300)
    private String jobId;

    private String jobTitle;
    private String employerName;

    @Column(length = 2000)
    private String applyLink;

    /**
     * PENDING, APPLIED, DRY_RUN, NEEDS_YOU, SKIPPED, FAILED, RETRY_SCHEDULED,
     * INTERVIEW, REJECTED, OFFER
     *
     * NEEDS_YOU means the site blocks automation, so the answers are prepared and the
     * candidate finishes it themselves in one click.
     */
    @Column(length = 24)
    private String status;

    /** How the application was submitted: AUTO, ASSISTED or SIMULATED. */
    @Column(length = 12)
    private String submittedVia;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private com.autoapply.apply.risk.BotRisk botRisk;

    @Column(length = 400)
    private String riskReason;

    /** Every answer this application needs, as JSON, ready for the candidate to paste. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String prefillJson;

    private String portal;
    private String portalCode;

    private Double matchScore;

    @Column(length = 2000)
    private String message;

    @Column(length = 1000)
    private String screenshotPath;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String coverLetter;

    @Builder.Default
    private Integer attemptCount = 0;

    private LocalDateTime nextRetryAt;

    private LocalDateTime appliedAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        appliedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (attemptCount == null) attemptCount = 0;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
