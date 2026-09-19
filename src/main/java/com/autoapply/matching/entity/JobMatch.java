package com.autoapply.matching.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "job_matches")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JobMatch {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    private String userId;
    private String jobId;
    private Double matchScore;
    private Boolean recommended;
    private String missingSkills;
    private String status;
    private LocalDateTime matchedAt;
    private LocalDateTime appliedAt;
    @PrePersist public void prePersist() { matchedAt = LocalDateTime.now(); }
}
