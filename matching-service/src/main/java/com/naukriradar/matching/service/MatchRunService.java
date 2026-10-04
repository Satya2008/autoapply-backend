package com.naukriradar.matching.service;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.common.redis.run.RunLeases;
import com.naukriradar.matching.dto.response.MatchRunResponse;
import com.naukriradar.matching.mapper.MatchMapper;
import com.naukriradar.matching.model.MatchRun;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Starts a match run and hands it to the worker pool. Returns at once; the caller polls the
 * run. One running run per user, enforced by the database.
 */
@Service
public class MatchRunService {

	private final MatchRunStore store;
	private final MatchRunWorker worker;
	private final MatchMapper mapper;
	private final RunLeases leases;

	public MatchRunService(MatchRunStore store, MatchRunWorker worker, MatchMapper mapper, RunLeases leases) {
		this.store = store;
		this.worker = worker;
		this.mapper = mapper;
		this.leases = leases;
	}

	public MatchRunResponse start(String userId) {
		MatchRun run;
		try {
			run = store.create(userId);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("Matching is already running for you. Wait for it to finish.");
		}
		// taken before queueing: a run waiting in the queue is alive too
		leases.begin(MatchRunStore.LEASE, run.getId());
		try {
			worker.execute(run.getId());
		}
		catch (TaskRejectedException ex) {
			leases.end(MatchRunStore.LEASE, run.getId());
			store.fail(run.getId(), "Matching was busy and couldn't start.");
			throw new ServiceUnavailableException("Matching is busy right now. Try again in a minute.");
		}
		return mapper.toResponse(run);
	}

	public MatchRunResponse get(String userId, String runId) {
		return mapper.toResponse(store.get(runId, userId));
	}

}
