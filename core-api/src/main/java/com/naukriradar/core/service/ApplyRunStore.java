package com.naukriradar.core.service;

import java.time.Clock;
import java.util.List;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.model.ApplyRun;
import com.naukriradar.core.model.ApplyRunStatus;
import com.naukriradar.core.repository.ApplyRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Each change to an apply run in its own short transaction. */
@Service
public class ApplyRunStore {

	private static final Logger log = LoggerFactory.getLogger(ApplyRunStore.class);

	private final ApplyRunRepository repository;
	private final Clock clock = Clock.systemUTC();

	public ApplyRunStore(ApplyRunRepository repository) {
		this.repository = repository;
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
		repository.findById(runId).ifPresent(run -> run.succeed(plan.considered(), plan.queued().size(),
				plan.needsYou() + outcome.handedOver(), outcome.sent(), outcome.failed(), plan.alreadyApplied(),
				plan.belowScore(), plan.deferred(), clock.instant()));
	}

	@Transactional
	public void fail(String runId, String reason) {
		repository.findById(runId).ifPresent(run -> run.fail(reason, clock.instant()));
	}

	/** A run still RUNNING at startup died with the old process; free the user to run again. */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void closeInterruptedRuns() {
		List<ApplyRun> stuck = repository.findByStatus(ApplyRunStatus.RUNNING);
		stuck.forEach(run -> run.fail("Interrupted: the service stopped during the run.", clock.instant()));
		if (!stuck.isEmpty()) {
			log.warn("Marked {} interrupted apply run(s) as failed", stuck.size());
		}
	}

}
