package com.naukriradar.job.controller;

import java.net.URI;
import java.util.List;

import com.naukriradar.job.dto.response.CleanupResponse;
import com.naukriradar.job.dto.response.FetchRunResponse;
import com.naukriradar.job.model.RunTrigger;
import com.naukriradar.job.service.FetchRunService;
import com.naukriradar.job.service.JobFetchOrchestrator;
import com.naukriradar.job.service.JobRetentionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin API for fetch runs and cleanup. Admin-only once security lands in Phase 8. */
@RestController
@RequestMapping("/api/v1/admin/jobs")
@RequiredArgsConstructor
public class JobAdminController {

	private final JobFetchOrchestrator orchestrator;
	private final FetchRunService runService;
	private final JobRetentionService retentionService;

	/**
	 * Starts fetching every enabled source and returns at once with 202 and the run. Poll
	 * the run (Location header) to see how it went.
	 */
	@PostMapping("/fetch-runs")
	public ResponseEntity<FetchRunResponse> startRun() {
		FetchRunResponse run = orchestrator.start(RunTrigger.MANUAL);
		return ResponseEntity.accepted()
				.location(URI.create("/api/v1/admin/jobs/fetch-runs/" + run.id()))
				.body(run);
	}

	@GetMapping("/fetch-runs")
	public List<FetchRunResponse> recentRuns(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
		return runService.recent(limit);
	}

	@GetMapping("/fetch-runs/{id}")
	public FetchRunResponse run(@PathVariable String id) {
		return runService.get(id);
	}

	/** Closes jobs no board has listed for a week and deletes ones gone for 60 days. */
	@PostMapping("/cleanup")
	public CleanupResponse cleanUp() {
		return retentionService.cleanUp();
	}

}
