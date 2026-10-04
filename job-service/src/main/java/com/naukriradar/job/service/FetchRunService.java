package com.naukriradar.job.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.common.redis.run.InterruptedRunCloser;
import com.naukriradar.common.redis.run.RunLeases;
import com.naukriradar.job.dto.response.FetchResultResponse;
import com.naukriradar.job.dto.response.FetchRunResponse;
import com.naukriradar.job.mapper.FetchRunMapper;
import com.naukriradar.job.model.FetchRun;
import com.naukriradar.job.model.FetchRunStatus;
import com.naukriradar.job.model.RunTrigger;
import com.naukriradar.job.repository.FetchRunRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the record of each fetch run: who started it, how each source did, the totals. */
@Service
@RequiredArgsConstructor
public class FetchRunService implements InterruptedRunCloser {

	private static final Logger log = LoggerFactory.getLogger(FetchRunService.class);

	/** Lease kind of a fetch run; see {@link RunLeases}. */
	static final String LEASE = "fetch";

	/** A run this young may not have its lease yet; leave it alone. */
	private static final Duration GRACE = Duration.ofMinutes(1);

	private final FetchRunRepository repository;
	private final FetchRunMapper mapper;
	private final RunLeases leases;

	@Transactional
	public String start(RunTrigger trigger, Instant at) {
		return repository.save(new FetchRun(trigger, at)).getId();
	}

	@Transactional
	public void finish(String runId, List<FetchResultResponse> results, Instant at) {
		FetchRun run = load(runId);
		results.forEach(result -> run.addSource(mapper.toSource(result)));
		run.finish(at);
	}

	@Transactional
	public void fail(String runId, String reason, Instant at) {
		load(runId).fail(at, reason);
	}

	@Transactional(readOnly = true)
	public FetchRunResponse get(String runId) {
		return mapper.toResponse(repository.findWithSourcesById(runId)
				.orElseThrow(() -> new NotFoundException("No fetch run " + runId + ".")), true);
	}

	@Transactional(readOnly = true)
	public List<FetchRunResponse> recent(int limit) {
		return repository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit)).stream()
				.map(run -> mapper.toResponse(run, false))
				.toList();
	}

	/**
	 * A run still RUNNING without a lease belonged to a process that died mid-run. Close it so
	 * it doesn't look in progress forever. Runs another instance is working on have a lease
	 * and are left alone.
	 */
	@Override
	@Transactional
	public int closeInterruptedRuns() {
		Instant now = Instant.now();
		Map<String, FetchRun> running = repository.findByStatus(FetchRunStatus.RUNNING).stream()
				.filter(run -> run.getStartedAt().isBefore(now.minus(GRACE)))
				.collect(Collectors.toMap(FetchRun::getId, run -> run));
		if (running.isEmpty()) {
			return 0;
		}
		Collection<String> abandoned = leases.abandoned(LEASE, running.keySet());
		abandoned.forEach(id -> running.get(id).fail(now, "Interrupted: the service stopped during the run."));
		if (!abandoned.isEmpty()) {
			log.warn("Marked {} interrupted fetch run(s) as failed", abandoned.size());
		}
		return abandoned.size();
	}

	private FetchRun load(String runId) {
		return repository.findById(runId).orElseThrow(() -> new NotFoundException("No fetch run " + runId + "."));
	}

}
