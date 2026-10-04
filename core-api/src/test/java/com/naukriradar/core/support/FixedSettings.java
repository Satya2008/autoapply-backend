package com.naukriradar.core.support;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.naukriradar.core.settings.SettingDefinitions;
import com.naukriradar.core.settings.SettingValues;
import com.naukriradar.core.settings.Settings;

/** Settings for unit tests: the real defaults, plus whatever a test sets. */
public final class FixedSettings implements Settings {

	private final Map<String, String> overrides = new ConcurrentHashMap<>();

	public FixedSettings with(String key, String value) {
		overrides.put(key, SettingValues.validate(SettingDefinitions.get(key), value));
		return this;
	}

	@Override
	public String getString(String key) {
		return overrides.getOrDefault(key, SettingDefinitions.get(key).defaultValue());
	}

	@Override
	public int getInt(String key) {
		return SettingValues.asInt(getString(key));
	}

	@Override
	public boolean getBoolean(String key) {
		return SettingValues.asBoolean(getString(key));
	}

	@Override
	public Duration getDuration(String key) {
		return SettingValues.asDuration(getString(key));
	}

	@Override
	public List<String> getDomains(String key) {
		return SettingValues.asDomains(getString(key));
	}

}
