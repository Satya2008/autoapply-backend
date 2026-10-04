package com.naukriradar.job.config;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.job.model.RunTrigger;
import com.naukriradar.job.service.JobFetchOrchestrator;
import com.naukriradar.job.service.JobParsingService;
import com.naukriradar.job.service.JobRetentionService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The cron jobs. Switched off with naukriradar.jobs.schedule.enabled=false (tests do).
 *
 * <p>Every instance fires every trigger; ShedLock lets only one of them through. The lock is
 * kept for at least a minute even when the job is quick, so an instance whose clock is a few
 * seconds behind finds it still taken instead of running the job a second time.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
@ConditionalOnProperty(prefix = "naukriradar.jobs.schedule", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SchedulingConfig {

	private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

	private final JobFetchOrchestrator orchestrator;
	private final JobRetentionService retention;
	private final JobParsingService parsing;

	/** Only starts the run; the run itself is guarded by the orchestrator's own lock. */
	@Scheduled(cron = "${naukriradar.jobs.schedule.fetch-cron:0 0 */6 * * *}")
	@SchedulerLock(name = "fetch-all-sources", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
	void fetchAllSources() {
		try {
			orchestrator.start(RunTrigger.SCHEDULED);
		}
		catch (ConflictException | ServiceUnavailableException ex) {
			log.info("Scheduled fetch skipped: {}", ex.getMessage());
		}
	}

	/** Picks up jobs a round after a fetch didn't reach (batch full, AI down at the time). */
	@Scheduled(cron = "${naukriradar.jobs.parsing.cron:0 */15 * * * *}")
	@SchedulerLock(name = "parse-jobs", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
	void parseJobs() {
		parsing.parsePending();
	}

	@Scheduled(cron = "${naukriradar.jobs.schedule.cleanup-cron:0 30 3 * * *}")
	@SchedulerLock(name = "clean-up-jobs", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
	void cleanUpJobs() {
		retention.cleanUp();
	}

}
