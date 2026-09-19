package com.autoapply.apply.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "applications")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Application {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    private String userId;
    private String jobId;
    private String jobTitle;
    private String employerName;
    private String applyLink;
    private String status;
    private String portal;
    private LocalDateTime appliedAt;
    private LocalDateTime updatedAt;
    private String failReason;
    @PrePersist public void prePersist() { appliedAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); status = "PENDING"; }
    @PreUpdate public void preUpdate() { updatedAt = LocalDateTime.now(); }
}
