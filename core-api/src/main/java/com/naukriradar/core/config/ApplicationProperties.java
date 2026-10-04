package com.naukriradar.core.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Applying settings fixed at startup. The ones worth changing while running (risk lists,
 * limits, retries, schedules) are runtime settings instead; see {@code SettingDefinitions}.
 *
 * @param mode how queued applications are sent; only SIMULATE exists until the browser
 * engine (Phase 15), and nothing reaches an employer in that mode
 * @param zone the day used for the daily apply limit and the job schedules
 */
@ConfigurationProperties("naukriradar.applications")
public record ApplicationProperties(
		@DefaultValue("SIMULATE") ApplyMode mode,
		@DefaultValue("Asia/Kolkata") ZoneId zone,
		@DefaultValue("2") int workerThreads,
		@DefaultValue("20") int queueCapacity) {

	public ApplicationProperties {
		if (workerThreads < 1 || queueCapacity < 0) {
			throw new IllegalArgumentException("Need at least one worker thread");
		}
	}

	public enum ApplyMode {
		SIMULATE,
		BROWSER
	}

}
