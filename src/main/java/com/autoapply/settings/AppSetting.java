package com.autoapply.settings;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 160)
    private String key;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String value;

    @Column(name = "default_setting_value", columnDefinition = "TEXT")
    private String defaultValue;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SettingType type;

    @Column(length = 60)
    private String category;

    @Column(length = 500)
    private String description;

    /** Comma separated allowed values, rendered as a dropdown in the admin UI. */
    @Column(length = 1000)
    private String allowedValues;

    /** Stored encrypted and never returned in plain text over the API. */
    private Boolean secret;

    /** When false the admin UI shows it read-only (derived/system values). */
    private Boolean editable;

    /** Restart needed for the change to take effect - purely informational for the UI. */
    private Boolean requiresRestart;

    private Integer displayOrder;

    private String updatedBy;
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
        if (secret == null) secret = false;
        if (editable == null) editable = true;
        if (requiresRestart == null) requiresRestart = false;
        if (displayOrder == null) displayOrder = 100;
        if (type == null) type = SettingType.STRING;
    }
}
