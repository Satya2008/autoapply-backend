package com.naukriradar.matching.controller;

import java.net.URI;

import com.naukriradar.common.security.CurrentUserProvider;
import com.naukriradar.matching.dto.response.MatchDetailResponse;
import com.naukriradar.matching.dto.response.MatchPageResponse;
import com.naukriradar.matching.dto.response.MatchRunResponse;
import com.naukriradar.matching.service.MatchQueryService;
import com.naukriradar.matching.service.MatchRunService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/matches")
public class MatchController {

	private final CurrentUserProvider currentUser;
	private final MatchRunService runService;
	private final MatchQueryService queryService;

	public MatchController(CurrentUserProvider currentUser, MatchRunService runService, MatchQueryService queryService) {
		this.currentUser = currentUser;
		this.runService = runService;
		this.queryService = queryService;
	}

	/** Scores fresh jobs for you in the background. 202 with the run; poll it for the result. */
	@PostMapping("/runs")
	public ResponseEntity<MatchRunResponse> startRun() {
		MatchRunResponse run = runService.start(currentUser.currentUserId());
		return ResponseEntity.accepted().location(URI.create("/api/v1/me/matches/runs/" + run.id())).body(run);
	}

	@GetMapping("/runs/{runId}")
	public MatchRunResponse run(@PathVariable String runId) {
		return runService.get(currentUser.currentUserId(), runId);
	}

	@GetMapping
	public MatchPageResponse list(
			@RequestParam(defaultValue = "0") @Min(0) @Max(100) int minScore,
			@RequestParam(required = false) @Size(max = 200) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
		return queryService.list(currentUser.currentUserId(), minScore, cursor, limit);
	}

	@GetMapping("/{id}")
	public MatchDetailResponse get(@PathVariable String id) {
		return queryService.get(currentUser.currentUserId(), id);
	}

}
