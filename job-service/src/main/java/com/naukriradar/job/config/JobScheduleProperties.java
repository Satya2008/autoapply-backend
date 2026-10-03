package com.naukriradar.job.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled run the cron jobs below; off in tests
 * @param fetchCron when to fetch every enabled source
 * @param cleanupCron when to close stale jobs and delete old ones
 * @param closeAfterDays a job no board has returned for this long is marked CLOSED
 * @param deleteAfterDays a job no board has returned for this long is deleted
 */
@ConfigurationProperties("naukriradar.jobs.schedule")
public record JobScheduleProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("0 0 */6 * * *") String fetchCron,
		@DefaultValue("0 30 3 * * *") String cleanupCron,
		@DefaultValue("7") int closeAfterDays,
		@DefaultValue("60") int deleteAfterDays) {

	public JobScheduleProperties {
		if (closeAfterDays < 1 || deleteAfterDays <= closeAfterDays) {
			throw new IllegalArgumentException("Need 1 <= closeAfterDays < deleteAfterDays");
		}
	}

}
