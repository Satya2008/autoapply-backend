package com.naukriradar.job.service;

import java.time.Instant;
import java.util.List;

import com.naukriradar.common.exception.NotFoundException;
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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the record of each fetch run: who started it, how each source did, the totals. */
@Service
@RequiredArgsConstructor
public class FetchRunService {

	private static final Logger log = LoggerFactory.getLogger(FetchRunService.class);

	private final FetchRunRepository repository;
	private final FetchRunMapper mapper;

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
	 * A run still RUNNING at startup belonged to a process that died mid-run. Close it so it
	 * doesn't look in progress forever.
	 */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void closeInterruptedRuns() {
		List<FetchRun> stuck = repository.findByStatus(FetchRunStatus.RUNNING);
		stuck.forEach(run -> run.fail(Instant.now(), "Interrupted: the service stopped during the run."));
		if (!stuck.isEmpty()) {
			log.warn("Marked {} interrupted fetch run(s) as failed", stuck.size());
		}
	}

	private FetchRun load(String runId) {
		return repository.findById(runId).orElseThrow(() -> new NotFoundException("No fetch run " + runId + "."));
	}

}
