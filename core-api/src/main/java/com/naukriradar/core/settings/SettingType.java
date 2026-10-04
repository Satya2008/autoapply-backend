package com.naukriradar.core.settings;

/** How a setting's text value is read and checked. */
public enum SettingType {
	STRING,
	INT,
	BOOLEAN,
	/** Spring style: 30m, 2h, 1d. */
	DURATION,
	/** Six-field Spring cron: second minute hour day month weekday. */
	CRON,
	/** Comma-separated domains such as "linkedin.com, naukri.com". */
	DOMAIN_LIST,
	/** A string stored encrypted and never shown back. */
	SECRET
}
