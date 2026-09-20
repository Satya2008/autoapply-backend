package com.autoapply.jobs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_job_source", columnList = "sourceCode"),
        @Index(name = "idx_job_posted", columnList = "jobPostedAt"),
        @Index(name = "idx_job_fingerprint", columnList = "fingerprint")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @Column(length = 300)
    private String jobId;

    @Column(length = 80)
    private String sourceCode;

    private String jobTitle;
    private String jobPublisher;
    private String employerName;

    @Column(length = 1000)
    private String employerLogo;

    @Column(length = 1000)
    private String employerWebsite;

    @Column(length = 2000)
    private String jobApplyLink;

    private Boolean jobApplyIsDirect;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    private String jobCity;
    private String jobState;
    private String jobCountry;
    private Boolean jobIsRemote;
    private String jobEmploymentType;
    private Double jobMinSalary;
    private Double jobMaxSalary;
    private String jobSalaryCurrency;
    private String jobSalaryPeriod;

    @Column(length = 2000)
    private String jobRequiredSkills;

    /** Hash of title+employer+location, used to skip near-duplicate postings across boards. */
    @Column(length = 80)
    private String fingerprint;

    private LocalDateTime jobPostedAt;
    private LocalDateTime jobExpiresAt;
    private LocalDateTime fetchedAt;

    @PrePersist
    void prePersist() {
        fetchedAt = LocalDateTime.now();
        if (jobPostedAt == null) jobPostedAt = LocalDateTime.now();
        if (fingerprint == null) fingerprint = computeFingerprint();
    }

    public String computeFingerprint() {
        String basis = (nullSafe(jobTitle) + "|" + nullSafe(employerName) + "|" + nullSafe(jobCity))
                .toLowerCase().replaceAll("\\s+", " ").trim();
        return Integer.toHexString(basis.hashCode()) + "-" + basis.length();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
