package com.autoapply.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_actor", columnList = "actor"),
        @Index(name = "idx_audit_created", columnList = "createdAt"),
        @Index(name = "idx_audit_action", columnList = "action")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private AuditAction action;

    private String targetType;
    private String targetId;

    @Column(columnDefinition = "TEXT")
    private String detail;

    private String ipAddress;

    @Column(length = 400)
    private String userAgent;

    private Boolean success;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        if (success == null) success = true;
    }
}
