package com.autoapply.matching.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_matches", indexes = {
        @Index(name = "idx_match_user", columnList = "userId"),
        @Index(name = "idx_match_score", columnList = "matchScore"),
        @Index(name = "idx_match_user_job", columnList = "userId,jobId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;

    @Column(length = 300)
    private String jobId;

    private Double matchScore;
    private Double keywordScore;
    private Double aiScore;

    private Boolean recommended;

    @Column(length = 2000)
    private String matchingSkills;

    @Column(length = 2000)
    private String missingSkills;

    @Column(length = 1000)
    private String reasoning;

    /** KEYWORD when scored locally, AI when a model was consulted. */
    @Column(length = 20)
    private String scoredBy;

    @Column(length = 20)
    @Builder.Default
    private String status = "MATCHED";

    private LocalDateTime matchedAt;
    private LocalDateTime appliedAt;

    @PrePersist
    void prePersist() {
        matchedAt = LocalDateTime.now();
        if (status == null) status = "MATCHED";
    }
}
