package com.naukriradar.core.settings;

/** Published after a setting change commits. The new value is not included: secrets stay out of events. */
public record SettingChangedEvent(String key) {
}
