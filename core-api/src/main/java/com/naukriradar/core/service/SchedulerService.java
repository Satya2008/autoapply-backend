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
import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.LockHandle;
import com.naukriradar.common.redis.lock.LockUnavailableException;
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
 * setting changes: no restart. Remembers each job's last run for the admin screen.
 *
 * <p>Every instance schedules every job, and a Redis lock per job lets one of them run it. A
 * job never overlaps itself, here or across instances; a trigger that finds it running is
 * skipped. After a scheduled run the lock is kept for at least {@link #MIN_HOLD}, so an
 * instance whose clock is a few seconds behind doesn't run the same trigger again.
 */
@Service
public class SchedulerService {

	private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

	static final Duration MIN_HOLD = Duration.ofSeconds(30);

	private static final String SCHEDULE = "schedule";

	private final ThreadPoolTaskScheduler scheduler;
	private final DistributedLock lock;
	private final Settings settings;
	private final ApplicationProperties properties;
	private final boolean schedulingEnabled;
	private final Map<String, JobState> jobs = new LinkedHashMap<>();
	private final Clock clock = Clock.systemUTC();

	public SchedulerService(ThreadPoolTaskScheduler jobScheduler, List<ScheduledJob> jobs, Settings settings,
			ApplicationProperties properties, DistributedLock lock,
			@Value("${naukriradar.scheduler.enabled:true}") boolean schedulingEnabled) {
		this.scheduler = jobScheduler;
		this.lock = lock;
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
	@TransactionalEventListener(fallbackExecution = true)
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
		if (state.running.get() || heldElsewhere(state)) {
			throw new ConflictException("Job " + name + " is already running, or has just run on schedule. "
					+ "Try again in a minute.");
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
		state.future = scheduler.schedule(() -> runGuarded(state, SCHEDULE), new CronTrigger(cron, properties.zone()));
		log.info("Job {} scheduled with '{}'", state.job.name(), cron);
	}

	private void runGuarded(JobState state, String trigger) {
		if (!state.running.compareAndSet(false, true)) {
			log.info("Job {} still running; skipping this {} trigger", state.job.name(), trigger);
			return;
		}
		LockHandle held;
		try {
			held = lock.tryAcquire(lockName(state)).orElse(null);
		}
		catch (LockUnavailableException ex) {
			// running unguarded could run the job on every instance at once
			log.warn("Job {} skipped: {}", state.job.name(), ex.getMessage());
			state.running.set(false);
			return;
		}
		if (held == null) {
			log.info("Job {} is running on another instance; skipping this {} trigger", state.job.name(), trigger);
			state.running.set(false);
			return;
		}
		try {
			run(state, trigger);
		}
		finally {
			held.releaseAfter(SCHEDULE.equals(trigger) ? MIN_HOLD : Duration.ZERO);
			state.running.set(false);
		}
	}

	private void run(JobState state, String trigger) {
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
		}
	}

	private boolean heldElsewhere(JobState state) {
		try {
			return lock.isHeld(lockName(state));
		}
		catch (LockUnavailableException ex) {
			throw new ServiceUnavailableException("Jobs can't be started right now: the lock service is unreachable.");
		}
	}

	private static String lockName(JobState state) {
		return "job:" + state.job.name();
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
