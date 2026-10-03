package com.naukriradar.job.config;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.job.model.RunTrigger;
import com.naukriradar.job.service.JobFetchOrchestrator;
import com.naukriradar.job.service.JobRetentionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** The cron jobs. Switched off with naukriradar.jobs.schedule.enabled=false (tests do). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "naukriradar.jobs.schedule", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SchedulingConfig {

	private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

	private final JobFetchOrchestrator orchestrator;
	private final JobRetentionService retention;

	@Scheduled(cron = "${naukriradar.jobs.schedule.fetch-cron:0 0 */6 * * *}")
	void fetchAllSources() {
		try {
			orchestrator.start(RunTrigger.SCHEDULED);
		}
		catch (ConflictException ex) {
			log.info("Scheduled fetch skipped: {}", ex.getMessage());
		}
	}

	@Scheduled(cron = "${naukriradar.jobs.schedule.cleanup-cron:0 30 3 * * *}")
	void cleanUpJobs() {
		retention.cleanUp();
	}

}
