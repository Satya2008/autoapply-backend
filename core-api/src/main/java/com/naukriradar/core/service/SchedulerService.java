package com.naukriradar.core.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.audit.Audited;
import com.naukriradar.core.config.ApplicationProperties;
import com.naukriradar.core.dto.response.ScheduledJobResponse;
import com.naukriradar.core.scheduler.ScheduledJob;
import com.naukriradar.core.settings.SettingChangedEvent;
import com.naukriradar.core.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Schedules the background jobs from their cron settings, and moves them the moment a
 * setting changes: no restart. A job never overlaps itself; a trigger that finds it still
 * running is skipped. Remembers each job's last run for the admin screen.
 */
@Service
public class SchedulerService {

	private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

	private final ThreadPoolTaskScheduler scheduler;
	private final Settings settings;
	private final ApplicationProperties properties;
	private final boolean schedulingEnabled;
	private final Map<String, JobState> jobs = new LinkedHashMap<>();
	private final Clock clock = Clock.systemUTC();

	public SchedulerService(ThreadPoolTaskScheduler jobScheduler, List<ScheduledJob> jobs, Settings settings,
			ApplicationProperties properties, @Value("${naukriradar.scheduler.enabled:true}") boolean schedulingEnabled) {
		this.scheduler = jobScheduler;
		this.settings = settings;
		this.properties = properties;
		this.schedulingEnabled = schedulingEnabled;
		jobs.forEach(job -> {
			if (this.jobs.put(job.name(), new JobState(job)) != null) {
				throw new IllegalStateException("Two scheduled jobs named " + job.name());
			}
		});
	}

	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		if (!schedulingEnabled) {
			log.info("Scheduling is off (naukriradar.scheduler.enabled=false); jobs run only when triggered");
			return;
		}
		jobs.values().forEach(this::reschedule);
	}

	/** After the settings cache has dropped the old value (see SettingsService). */
	@TransactionalEventListener
	@Order(10)
	public void onSettingChanged(SettingChangedEvent event) {
		jobs.values().stream()
				.filter(state -> state.job.cronSetting().equals(event.key()) || state.job.enabledSetting().equals(event.key()))
				.forEach(this::reschedule);
	}

	public List<ScheduledJobResponse> list() {
		return jobs.values().stream().map(this::view).toList();
	}

	@Audited(action = "JOB_RUN", targetType = "job", targetId = "#name")
	public ScheduledJobResponse runNow(String name) {
		JobState state = jobs.get(name);
		if (state == null) {
			throw new NotFoundException("No job " + name + ".");
		}
		if (state.running.get()) {
			throw new ConflictException("Job " + name + " is already running.");
		}
		scheduler.execute(() -> runGuarded(state, "manual"));
		return view(state);
	}

	private synchronized void reschedule(JobState state) {
		if (state.future != null) {
			state.future.cancel(false);
			state.future = null;
		}
		if (!schedulingEnabled || !settings.getBoolean(state.job.enabledSetting())) {
			log.info("Job {} is not scheduled", state.job.name());
			return;
		}
		String cron = settings.getString(state.job.cronSetting());
		state.future = scheduler.schedule(() -> runGuarded(state, "schedule"), new CronTrigger(cron, properties.zone()));
		log.info("Job {} scheduled with '{}'", state.job.name(), cron);
	}

	private void runGuarded(JobState state, String trigger) {
		if (!state.running.compareAndSet(false, true)) {
			log.info("Job {} still running; skipping this {} trigger", state.job.name(), trigger);
			return;
		}
		Instant started = clock.instant();
		state.lastTrigger = trigger;
		state.lastStartedAt = started;
		try {
			state.lastResult = state.job.run();
			state.lastSuccess = true;
		}
		catch (RuntimeException ex) {
			log.error("Job {} failed", state.job.name(), ex);
			state.lastResult = "Failed: " + ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
			state.lastSuccess = false;
		}
		finally {
			state.lastFinishedAt = clock.instant();
			state.running.set(false);
		}
	}

	private ScheduledJobResponse view(JobState state) {
		String cron = settings.getString(state.job.cronSetting());
		boolean enabled = settings.getBoolean(state.job.enabledSetting());
		boolean scheduled = state.future != null;
		Instant next = null;
		if (scheduled) {
			ZonedDateTime after = CronExpression.parse(cron).next(ZonedDateTime.now(clock.withZone(properties.zone())));
			next = after == null ? null : after.toInstant();
		}
		Long duration = state.lastStartedAt != null && state.lastFinishedAt != null
				&& !state.lastFinishedAt.isBefore(state.lastStartedAt)
						? Duration.between(state.lastStartedAt, state.lastFinishedAt).toMillis() : null;
		return new ScheduledJobResponse(state.job.name(), cron, enabled, scheduled, next, state.running.get(),
				state.lastTrigger, state.lastStartedAt, state.lastFinishedAt, duration, state.lastSuccess, state.lastResult);
	}

	/** Mutable state per job; fields are volatile because the job's thread writes what the API reads. */
	private static final class JobState {

		private final ScheduledJob job;

		private final AtomicBoolean running = new AtomicBoolean();

		private volatile ScheduledFuture<?> future;

		private volatile String lastTrigger;

		private volatile Instant lastStartedAt;

		private volatile Instant lastFinishedAt;

		private volatile Boolean lastSuccess;

		private volatile String lastResult;

		private JobState(ScheduledJob job) {
			this.job = job;
		}

	}

}
