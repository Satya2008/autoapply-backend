package com.naukriradar.core.settings;

/**
 * One runtime setting: what it is, how it's checked, and its value when nobody has changed it.
 *
 * @param min lowest allowed value for INT, or minutes for DURATION; null for no limit
 * @param max highest allowed value for INT, or minutes for DURATION; null for no limit
 */
public record SettingDefinition(
		String key,
		String category,
		SettingType type,
		String defaultValue,
		String description,
		Long min,
		Long max) {

	public boolean secret() {
		return type == SettingType.SECRET;
	}

	static SettingDefinition of(String key, String category, SettingType type, String defaultValue, String description) {
		return new SettingDefinition(key, category, type, defaultValue, description, null, null);
	}

	static SettingDefinition ranged(String key, String category, SettingType type, String defaultValue,
			String description, long min, long max) {
		return new SettingDefinition(key, category, type, defaultValue, description, min, max);
	}

}
