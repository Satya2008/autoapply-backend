package com.autoapply.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_token", columnList = "token", unique = true),
        @Index(name = "idx_refresh_user", columnList = "userId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 200)
    private String token;

    @Column(nullable = false)
    private String userId;

    private LocalDateTime expiresAt;

    @Builder.Default
    private Boolean revoked = false;

    private String userAgent;
    private String ipAddress;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        if (revoked == null) revoked = false;
    }

    @Transient
    public boolean isUsable() {
        return !Boolean.TRUE.equals(revoked) && expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
    }
}
