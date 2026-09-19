package com.autoapply.jobs.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "jobs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Job {
    @Id private String jobId;
    private String jobTitle;
    private String jobPublisher;
    private String employerName;
    private String employerLogo;
    private String employerWebsite;
    @Column(length = 2000) private String jobApplyLink;
    private Boolean jobApplyIsDirect;
    @Column(columnDefinition = "TEXT") private String jobDescription;
    private String jobCity;
    private String jobState;
    private String jobCountry;
    private Boolean jobIsRemote;
    private String jobEmploymentType;
    private Double jobMinSalary;
    private Double jobMaxSalary;
    private String jobSalaryCurrency;
    private String jobSalaryPeriod;
    @ElementCollection private List<String> jobRequiredSkills;
    private Integer requiredExperienceMonths;
    private LocalDateTime jobPostedAt;
    private LocalDateTime jobExpiresAt;
    private LocalDateTime fetchedAt;
    @PrePersist public void prePersist() { fetchedAt = LocalDateTime.now(); }
}
