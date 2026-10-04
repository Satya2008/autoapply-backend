package com.naukriradar.core.dto.response;

import java.time.Instant;

import com.naukriradar.core.settings.SettingType;

/**
 * @param value current value; for secrets "****" when set and null when not, never the secret
 * @param defaultValue null for secrets
 * @param overridden true when someone changed it from the default
 */
public record SettingResponse(
		String key,
		String category,
		SettingType type,
		String description,
		String value,
		String defaultValue,
		boolean overridden,
		String updatedBy,
		Instant updatedAt) {
}
