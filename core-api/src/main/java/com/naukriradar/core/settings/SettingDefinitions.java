package com.naukriradar.core.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.naukriradar.core.settings.SettingDefinition.of;
import static com.naukriradar.core.settings.SettingDefinition.ranged;

/**
 * Every setting that can be changed at runtime. A key not listed here can't be set, so a
 * typo in the admin screen is an error instead of a silently ignored row. Every default is
 * checked by the same validation an admin's value goes through (see the tests).
 */
public final class SettingDefinitions {

	public static final String HIGH_RISK_DOMAINS = "applications.high-risk-domains";
	public static final String LOW_RISK_DOMAINS = "applications.low-risk-domains";
	public static final String MATCH_LIMIT = "applications.match-limit";
	public static final String MAX_NEEDS_YOU_PER_RUN = "applications.max-needs-you-per-run";
	public static final String MAX_ATTEMPTS = "applications.max-attempts";
	public static final String RETRY_DELAY = "applications.retry-delay";
	public static final String AUTO_APPLY_CRON = "scheduler.auto-apply.cron";
	public static final String AUTO_APPLY_ENABLED = "scheduler.auto-apply.enabled";
	public static final String RETRY_FAILED_CRON = "scheduler.retry-failed.cron";
	public static final String RETRY_FAILED_ENABLED = "scheduler.retry-failed.enabled";
	public static final String AI_API_KEY = "ai.api-key";

	private static final List<SettingDefinition> ALL = List.of(
			of(HIGH_RISK_DOMAINS, "applications", SettingType.DOMAIN_LIST,
					"linkedin.com, naukri.com, indeed.com, glassdoor.com, monster.com, foundit.in, instahyre.com, "
							+ "shine.com, timesjobs.com, wellfound.com, myworkdayjobs.com, workday.com, taleo.net, "
							+ "icims.com, successfactors.com",
					"Sites that ban automated applications. Never applied to automatically; subdomains included."),
			of(LOW_RISK_DOMAINS, "applications", SettingType.DOMAIN_LIST,
					"greenhouse.io, lever.co, workable.com, ashbyhq.com, recruitee.com, smartrecruiters.com, breezy.hr, "
							+ "teamtailor.com, personio.de",
					"Applicant tracking systems built for direct applications; may be applied to automatically."),
			ranged(MATCH_LIMIT, "applications", SettingType.INT, "200",
					"Matches read from matching-service per apply run.", 1, 500),
			ranged(MAX_NEEDS_YOU_PER_RUN, "applications", SettingType.INT, "50",
					"New \"needs you\" items per run, so a first run doesn't bury the user.", 1, 500),
			ranged(MAX_ATTEMPTS, "applications", SettingType.INT, "3",
					"Automatic tries before an application is handed to the user.", 1, 10),
			ranged(RETRY_DELAY, "applications", SettingType.DURATION, "30m",
					"Wait after the first failed try; doubles after each one. In minutes: 1 to 1440.", 1, 1440),
			of(AUTO_APPLY_CRON, "scheduler", SettingType.CRON, "0 0 10 * * *",
					"When to run applying for every user with auto apply on (India time)."),
			of(AUTO_APPLY_ENABLED, "scheduler", SettingType.BOOLEAN, "true",
					"Run the auto-apply task on its schedule."),
			of(RETRY_FAILED_CRON, "scheduler", SettingType.CRON, "0 */30 * * * *",
					"When to retry failed automatic applications whose wait is over."),
			of(RETRY_FAILED_ENABLED, "scheduler", SettingType.BOOLEAN, "true",
					"Run the retry task on its schedule."),
			of(AI_API_KEY, "ai", SettingType.SECRET, "",
					"API key for the AI provider (used from Phase 11). Stored encrypted, never shown."));

	private static final Map<String, SettingDefinition> BY_KEY;

	static {
		Map<String, SettingDefinition> byKey = new LinkedHashMap<>();
		ALL.forEach(definition -> {
			if (byKey.put(definition.key(), definition) != null) {
				throw new IllegalStateException("Setting defined twice: " + definition.key());
			}
		});
		BY_KEY = Map.copyOf(byKey);
	}

	private SettingDefinitions() {
	}

	public static List<SettingDefinition> all() {
		return ALL;
	}

	public static Optional<SettingDefinition> find(String key) {
		return Optional.ofNullable(BY_KEY.get(key));
	}

	public static SettingDefinition get(String key) {
		return find(key).orElseThrow(() -> new IllegalArgumentException("Unknown setting " + key));
	}

}
