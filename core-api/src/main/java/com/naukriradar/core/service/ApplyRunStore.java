package com.naukriradar.core.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import com.naukriradar.common.events.OutboxWriter;
import com.naukriradar.common.events.Topics;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.common.redis.run.InterruptedRunCloser;
import com.naukriradar.common.redis.run.RunLeases;
import com.naukriradar.core.model.ApplyRun;
import com.naukriradar.core.model.ApplyRunStatus;
import com.naukriradar.core.repository.ApplyRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Each change to an apply run in its own short transaction. */
@Service
public class ApplyRunStore implements InterruptedRunCloser {

	private static final Logger log = LoggerFactory.getLogger(ApplyRunStore.class);

	/** Lease kind of an apply run; see {@link RunLeases}. */
	static final String LEASE = "apply";

	/** A run this young may not have its lease yet; leave it alone. */
	private static final Duration GRACE = Duration.ofMinutes(1);

	private final ApplyRunRepository repository;
	private final RunLeases leases;
	private final OutboxWriter outbox;
	private final Clock clock = Clock.systemUTC();

	public ApplyRunStore(ApplyRunRepository repository, RunLeases leases, OutboxWriter outbox) {
		this.repository = repository;
		this.leases = leases;
		this.outbox = outbox;
	}

	@Transactional
	public ApplyRun create(String userId) {
		return repository.saveAndFlush(new ApplyRun(userId, clock.instant()));
	}

	@Transactional(readOnly = true)
	public ApplyRun get(String runId, String userId) {
		return repository.findByIdAndUserId(runId, userId)
				.orElseThrow(() -> new NotFoundException("No apply run " + runId + "."));
	}

	@Transactional(readOnly = true)
	public String userOf(String runId) {
		return repository.findById(runId).map(ApplyRun::getUserId)
				.orElseThrow(() -> new NotFoundException("No apply run " + runId + "."));
	}

	@Transactional
	public void succeed(String runId, ApplyPlanner.PlanResult plan, ApplyExecutor.Outcome outcome) {
		repository.findById(runId).ifPresent(run -> {
			int needsYou = plan.needsYou() + outcome.handedOver();
			run.succeed(plan.considered(), plan.queued().size(), needsYou, outcome.sent(), outcome.failed(),
					plan.alreadyApplied(), plan.belowScore(), plan.deferred(), clock.instant());
			// announced with the result itself: the live screen and later the notifier hear about it
			outbox.publish(Topics.APPLY_COMPLETED, run.getUserId(), "ApplyRunCompleted", new ApplyRunCompleted(
					run.getUserId(), runId, plan.queued().size(), needsYou, outcome.sent(), outcome.failed()));
		});
	}

	/** Payload of {@link Topics#APPLY_COMPLETED}. */
	public record ApplyRunCompleted(String userId, String runId, int queued, int needsYou, int sent, int failed) {
	}

	@Transactional
	public void fail(String runId, String reason) {
		repository.findById(runId).ifPresent(run -> run.fail(reason, clock.instant()));
	}

	/**
	 * A run still RUNNING without a lease died with the process running it; close it and free
	 * the user to run again. Runs another instance is working on keep their lease.
	 */
	@Override
	@Transactional
	public int closeInterruptedRuns() {
		Instant now = clock.instant();
		Map<String, ApplyRun> running = repository.findByStatus(ApplyRunStatus.RUNNING).stream()
				.filter(run -> run.getStartedAt().isBefore(now.minus(GRACE)))
				.collect(Collectors.toMap(ApplyRun::getId, run -> run));
		if (running.isEmpty()) {
			return 0;
		}
		Collection<String> abandoned = leases.abandoned(LEASE, running.keySet());
		abandoned.forEach(id -> running.get(id).fail("Interrupted: the service stopped during the run.", now));
		if (!abandoned.isEmpty()) {
			log.warn("Marked {} interrupted apply run(s) as failed", abandoned.size());
		}
		return abandoned.size();
	}

}
