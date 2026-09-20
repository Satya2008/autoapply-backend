package com.autoapply.settings;

import lombok.Builder;

@Builder
public record SettingDefinition(
        String key,
        String defaultValue,
        SettingType type,
        String category,
        String description,
        String allowedValues,
        boolean secret,
        boolean editable,
        boolean requiresRestart,
        int displayOrder
) {
    public AppSetting toEntity() {
        return AppSetting.builder()
                .key(key)
                .value(null)
                .defaultValue(defaultValue)
                .type(type)
                .category(category)
                .description(description)
                .allowedValues(allowedValues)
                .secret(secret)
                .editable(editable)
                .requiresRestart(requiresRestart)
                .displayOrder(displayOrder)
                .build();
    }
}
