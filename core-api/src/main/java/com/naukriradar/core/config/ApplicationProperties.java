package com.naukriradar.core.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param mode how queued applications are sent; only SIMULATE exists until the browser
 * engine (Phase 15), and nothing reaches an employer in that mode
 * @param zone the day used for the daily apply limit
 * @param highRiskDomains sites that ban automation; never applied to automatically
 * @param lowRiskDomains applicant tracking systems meant for direct applications
 * @param matchLimit matches fetched from matching-service per run
 * @param maxNeedsYouPerRun new "needs you" items per run, so a first run doesn't bury the user
 * @param maxAttempts automatic tries before an application is handed to the user
 * @param retryDelay wait after the first failed try; doubles each time
 */
@ConfigurationProperties("naukriradar.applications")
public record ApplicationProperties(
		@DefaultValue("SIMULATE") ApplyMode mode,
		@DefaultValue("Asia/Kolkata") ZoneId zone,
		List<String> highRiskDomains,
		List<String> lowRiskDomains,
		@DefaultValue("200") int matchLimit,
		@DefaultValue("50") int maxNeedsYouPerRun,
		@DefaultValue("3") int maxAttempts,
		@DefaultValue("30m") Duration retryDelay,
		@DefaultValue("2") int workerThreads,
		@DefaultValue("20") int queueCapacity) {

	public ApplicationProperties {
		highRiskDomains = highRiskDomains == null ? List.of() : List.copyOf(highRiskDomains);
		lowRiskDomains = lowRiskDomains == null ? List.of() : List.copyOf(lowRiskDomains);
		if (maxAttempts < 1 || maxNeedsYouPerRun < 1 || matchLimit < 1) {
			throw new IllegalArgumentException("maxAttempts, maxNeedsYouPerRun and matchLimit must be at least 1");
		}
	}

	public enum ApplyMode {
		SIMULATE,
		BROWSER
	}

}
