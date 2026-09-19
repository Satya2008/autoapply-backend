package com.autoapply.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
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

    @Column(nullable = false)
    private String password;

    private String fullName;
    private String phone;
    private String location;
    private String currentRole;
    private Integer experienceYears;
    private Long expectedSalary;
    private String preferredJobType;    // FULLTIME, REMOTE, HYBRID

    @ElementCollection
    private List<String> skills;

    @ElementCollection
    private List<String> targetRoles;

    private String resumeFilePath;
    private Boolean autoApplyEnabled;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        autoApplyEnabled = false;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
