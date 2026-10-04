package com.naukriradar.core.settings;

/**
 * Published after a setting change commits. The new value is not included: secrets stay out
 * of events.
 *
 * @param remote true when the change was made on another instance and reached us through
 *     Redis; such an event is not broadcast again
 */
public record SettingChangedEvent(String key, boolean remote) {

	public SettingChangedEvent(String key) {
		this(key, false);
	}

}
