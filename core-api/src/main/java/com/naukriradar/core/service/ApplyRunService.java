package com.naukriradar.core.service;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.common.redis.run.RunLeases;
import com.naukriradar.core.dto.response.ApplyRunResponse;
import com.naukriradar.core.mapper.ApplicationMapper;
import com.naukriradar.core.model.ApplyRun;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Starts apply runs (one running per user, enforced by the database) and reports on them. */
@Service
public class ApplyRunService {

	private final ApplyRunStore store;
	private final ApplyRunWorker worker;
	private final ApplicationMapper mapper;
	private final RunLeases leases;

	public ApplyRunService(ApplyRunStore store, ApplyRunWorker worker, ApplicationMapper mapper, RunLeases leases) {
		this.store = store;
		this.worker = worker;
		this.mapper = mapper;
		this.leases = leases;
	}

	public ApplyRunResponse start(String userId) {
		ApplyRun run;
		try {
			run = store.create(userId);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("An apply run is already going for you. Wait for it to finish.");
		}
		// taken before queueing: a run waiting in the queue is alive too; the worker gives it back
		leases.begin(ApplyRunStore.LEASE, run.getId());
		try {
			worker.execute(run.getId());
		}
		catch (TaskRejectedException ex) {
			leases.end(ApplyRunStore.LEASE, run.getId());
			store.fail(run.getId(), "Applying was busy and couldn't start.");
			throw new ServiceUnavailableException("Applying is busy right now. Try again in a minute.");
		}
		return mapper.toResponse(run);
	}

	/**
	 * Runs on the calling thread and returns when done. Used by the auto-apply job.
	 *
	 * @throws ConflictException if the user already has a run going
	 */
	public ApplyRunResponse runNow(String userId) {
		ApplyRun run;
		try {
			run = store.create(userId);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("An apply run is already going for this user.");
		}
		leases.begin(ApplyRunStore.LEASE, run.getId());
		worker.runNow(run.getId());
		return mapper.toResponse(store.get(run.getId(), userId));
	}

	public ApplyRunResponse get(String userId, String runId) {
		return mapper.toResponse(store.get(runId, userId));
	}

}
