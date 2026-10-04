package com.naukriradar.core.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Applying settings fixed at startup. The ones worth changing while running (risk lists,
 * limits, retries, schedules) are runtime settings instead; see {@code SettingDefinitions}.
 *
 * @param mode how queued applications are sent: SIMULATE (the default) sends nothing to any
 * employer; BROWSER hands them to the apply worker, which fills and submits real forms
 * @param zone the day used for the daily apply limit and the job schedules
 * @param pacingMin shortest gap between two of one user's applications in browser mode
 * @param pacingMax longest gap; each gap is random in between, so applications don't go out
 * in a burst
 */
@ConfigurationProperties("naukriradar.applications")
public record ApplicationProperties(
		@DefaultValue("SIMULATE") ApplyMode mode,
		@DefaultValue("Asia/Kolkata") ZoneId zone,
		@DefaultValue("2") int workerThreads,
		@DefaultValue("20") int queueCapacity,
		@DefaultValue("3m") Duration pacingMin,
		@DefaultValue("8m") Duration pacingMax) {

	/** The one Spring binds; the shorter one is for tests and code. */
	@ConstructorBinding
	public ApplicationProperties {
		if (workerThreads < 1 || queueCapacity < 0) {
			throw new IllegalArgumentException("Need at least one worker thread");
		}
		if (pacingMin == null || pacingMax == null || pacingMin.isNegative() || pacingMax.compareTo(pacingMin) < 0) {
			throw new IllegalArgumentException("Application pacing needs 0 <= pacing-min <= pacing-max");
		}
	}

	public ApplicationProperties(ApplyMode mode, ZoneId zone, int workerThreads, int queueCapacity) {
		this(mode, zone, workerThreads, queueCapacity, Duration.ofMinutes(3), Duration.ofMinutes(8));
	}

	public enum ApplyMode {
		SIMULATE,
		BROWSER
	}

}
