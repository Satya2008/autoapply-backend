package com.naukriradar.core.audit;

import com.naukriradar.core.settings.SettingDefinitions;

/** Helpers called from {@link Audited#detail()} expressions. */
public final class AuditDetails {

	private AuditDetails() {
	}

	/** "value set to 45m", or "secret value changed" without the value. */
	public static String settingChange(String key, String value) {
		boolean secret = SettingDefinitions.find(key).map(d -> d.secret()).orElse(true);
		if (secret) {
			return "secret value changed";
		}
		String shown = value == null ? "" : value.strip();
		return "value set to " + (shown.length() > 300 ? shown.substring(0, 300) + "..." : shown);
	}

}
