package com.naukriradar.core.dto.response;

import java.time.Instant;

/**
 * @param enabled the job's on/off setting
 * @param scheduled actually on the schedule (enabled, and scheduling not switched off)
 * @param lastTrigger "schedule" or "manual"
 */
public record ScheduledJobResponse(
		String name,
		String cron,
		boolean enabled,
		boolean scheduled,
		Instant nextRunAt,
		boolean running,
		String lastTrigger,
		Instant lastStartedAt,
		Instant lastFinishedAt,
		Long lastDurationMs,
		Boolean lastSuccess,
		String lastResult) {
}
