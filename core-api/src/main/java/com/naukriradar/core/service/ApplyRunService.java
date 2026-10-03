package com.naukriradar.core.service;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.ServiceUnavailableException;
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

	public ApplyRunService(ApplyRunStore store, ApplyRunWorker worker, ApplicationMapper mapper) {
		this.store = store;
		this.worker = worker;
		this.mapper = mapper;
	}

	public ApplyRunResponse start(String userId) {
		ApplyRun run;
		try {
			run = store.create(userId);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("An apply run is already going for you. Wait for it to finish.");
		}
		try {
			worker.execute(run.getId());
		}
		catch (TaskRejectedException ex) {
			store.fail(run.getId(), "Applying was busy and couldn't start.");
			throw new ServiceUnavailableException("Applying is busy right now. Try again in a minute.");
		}
		return mapper.toResponse(run);
	}

	public ApplyRunResponse get(String userId, String runId) {
		return mapper.toResponse(store.get(runId, userId));
	}

}
