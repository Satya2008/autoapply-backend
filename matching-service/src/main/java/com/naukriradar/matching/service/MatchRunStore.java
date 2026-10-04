package com.naukriradar.matching.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.naukriradar.common.events.OutboxWriter;
import com.naukriradar.common.events.Topics;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.common.redis.run.InterruptedRunCloser;
import com.naukriradar.common.redis.run.RunLeases;
import com.naukriradar.matching.model.MatchRun;
import com.naukriradar.matching.model.MatchRunStatus;
import com.naukriradar.matching.repository.MatchRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Each change to a run in its own short transaction. */
@Service
public class MatchRunStore implements InterruptedRunCloser {

	private static final Logger log = LoggerFactory.getLogger(MatchRunStore.class);

	/** Lease kind of a match run; see {@link RunLeases}. */
	static final String LEASE = "match";

	/** A run this young may not have its lease yet; leave it alone. */
	private static final Duration GRACE = Duration.ofMinutes(1);

	private final MatchRunRepository repository;
	private final RunLeases leases;
	private final OutboxWriter outbox;
	private final Clock clock = Clock.systemUTC();

	public MatchRunStore(MatchRunRepository repository, RunLeases leases, OutboxWriter outbox) {
		this.repository = repository;
		this.leases = leases;
		this.outbox = outbox;
	}

	@Transactional
	public MatchRun create(String userId) {
		return repository.saveAndFlush(new MatchRun(userId, clock.instant()));
	}

	@Transactional(readOnly = true)
	public MatchRun get(String runId, String userId) {
		return repository.findByIdAndUserId(runId, userId)
				.orElseThrow(() -> new NotFoundException("No match run " + runId + "."));
	}

	@Transactional(readOnly = true)
	public String userOf(String runId) {
		return repository.findById(runId).map(MatchRun::getUserId)
				.orElseThrow(() -> new NotFoundException("No match run " + runId + "."));
	}

	/** Also announces match.created, keyed by user so one user's runs stay in order. */
	@Transactional
	public void succeed(String runId, MatchEngine.Outcome outcome) {
		repository.findById(runId).ifPresent(run -> {
			run.succeed(outcome.jobsConsidered(), outcome.excluded(), outcome.created(), outcome.updated(),
					outcome.belowThreshold(), outcome.aiReviewed(), outcome.aiNote(), clock.instant());
			outbox.publish(Topics.MATCH_CREATED, run.getUserId(), "MatchRunCompleted", new MatchRunCompleted(run.getUserId(),
					runId, outcome.created(), outcome.updated(), outcome.aiReviewed()));
		});
	}

	/** Users who ran matching lately: the ones worth rematching when new jobs arrive. */
	@Transactional(readOnly = true)
	public List<String> activeUsers(Duration within) {
		return repository.findUsersWithRunsSince(clock.instant().minus(within));
	}

	/** Payload of {@link Topics#MATCH_CREATED}. */
	public record MatchRunCompleted(String userId, String runId, int newMatches, int updatedMatches, int aiReviewed) {
	}

	@Transactional
	public void fail(String runId, String reason) {
		repository.findById(runId).ifPresent(run -> run.fail(reason, clock.instant()));
	}

	/**
	 * A run still RUNNING without a lease died with the process running it; close it and free
	 * the user to start again. Runs another instance is working on keep their lease.
	 */
	@Override
	@Transactional
	public int closeInterruptedRuns() {
		Instant now = clock.instant();
		Map<String, MatchRun> running = repository.findByStatus(MatchRunStatus.RUNNING).stream()
				.filter(run -> run.getStartedAt().isBefore(now.minus(GRACE)))
				.collect(Collectors.toMap(MatchRun::getId, run -> run));
		if (running.isEmpty()) {
			return 0;
		}
		Collection<String> abandoned = leases.abandoned(LEASE, running.keySet());
		abandoned.forEach(id -> running.get(id).fail("Interrupted: the service stopped during the run.", now));
		if (!abandoned.isEmpty()) {
			log.warn("Marked {} interrupted match run(s) as failed", abandoned.size());
		}
		return abandoned.size();
	}

}
