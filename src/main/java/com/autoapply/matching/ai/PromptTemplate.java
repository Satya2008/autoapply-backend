package com.autoapply.matching.ai;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Prompts live in the database so the way the AI reasons can be tuned from the dashboard.
 */
@Entity
@Table(name = "prompt_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptTemplate {

    @Id
    @Column(length = 80)
    private String code;

    private String name;

    @Column(length = 500)
    private String description;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String template;

    /** Comma separated placeholder names available to this template, for the editor's help text. */
    @Column(length = 1000)
    private String variables;

    @Builder.Default
    private Boolean enabled = true;

    private String updatedBy;
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
        if (enabled == null) enabled = true;
    }
}
