package com.naukriradar.matching.service;

import java.time.Clock;
import java.util.List;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.matching.model.MatchRun;
import com.naukriradar.matching.model.MatchRunStatus;
import com.naukriradar.matching.repository.MatchRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Each change to a run in its own short transaction. */
@Service
public class MatchRunStore {

	private static final Logger log = LoggerFactory.getLogger(MatchRunStore.class);

	private final MatchRunRepository repository;
	private final Clock clock = Clock.systemUTC();

	public MatchRunStore(MatchRunRepository repository) {
		this.repository = repository;
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

	@Transactional
	public void succeed(String runId, MatchEngine.Outcome outcome) {
		repository.findById(runId).ifPresent(run -> run.succeed(outcome.jobsConsidered(), outcome.excluded(),
				outcome.created(), outcome.updated(), outcome.belowThreshold(), clock.instant()));
	}

	@Transactional
	public void fail(String runId, String reason) {
		repository.findById(runId).ifPresent(run -> run.fail(reason, clock.instant()));
	}

	/** A run still RUNNING at startup died with the previous process; free the user to start again. */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void closeInterruptedRuns() {
		List<MatchRun> stuck = repository.findByStatus(MatchRunStatus.RUNNING);
		stuck.forEach(run -> run.fail("Interrupted: the service stopped during the run.", clock.instant()));
		if (!stuck.isEmpty()) {
			log.warn("Marked {} interrupted match run(s) as failed", stuck.size());
		}
	}

}
