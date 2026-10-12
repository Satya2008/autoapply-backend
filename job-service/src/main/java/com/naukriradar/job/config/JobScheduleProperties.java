package com.naukriradar.job.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled run the cron jobs below; off in tests
 * @param fetchCron when to fetch the enabled sources (each may ask for fewer runs)
 * @param zone the time zone the crons are read in
 * @param cleanupCron when to close stale jobs and delete old ones
 * @param closeAfterDays a job no board has returned for this long is marked CLOSED
 * @param deleteAfterDays a job no board has returned for this long is deleted
 */
@ConfigurationProperties("naukriradar.jobs.schedule")
public record JobScheduleProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("0 0 9,13 * * *") String fetchCron,
		@DefaultValue("Asia/Kolkata") String zone,
		@DefaultValue("0 30 3 * * *") String cleanupCron,
		@DefaultValue("7") int closeAfterDays,
		@DefaultValue("60") int deleteAfterDays) {

	public JobScheduleProperties {
		if (closeAfterDays < 1 || deleteAfterDays <= closeAfterDays) {
			throw new IllegalArgumentException("Need 1 <= closeAfterDays < deleteAfterDays");
		}
	}

}
