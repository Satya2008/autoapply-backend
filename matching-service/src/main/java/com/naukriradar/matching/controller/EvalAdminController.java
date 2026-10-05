package com.naukriradar.matching.controller;

import java.net.URI;
import java.util.List;

import com.naukriradar.matching.dto.request.EvalCaseRequest;
import com.naukriradar.matching.dto.request.EvalRunRequest;
import com.naukriradar.matching.dto.response.EvalCaseResponse;
import com.naukriradar.matching.dto.response.EvalRunResponse;
import com.naukriradar.matching.service.EvalCaseService;
import com.naukriradar.matching.service.EvalRunner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The golden set and the evals run on it. */
@RestController
@RequestMapping("/api/v1/admin/evals")
public class EvalAdminController {

	private final EvalCaseService cases;
	private final EvalRunner runner;

	public EvalAdminController(EvalCaseService cases, EvalRunner runner) {
		this.cases = cases;
		this.runner = runner;
	}

	@GetMapping("/cases")
	public List<EvalCaseResponse> cases() {
		return cases.list();
	}

	@PostMapping("/cases")
	@ResponseStatus(HttpStatus.CREATED)
	public EvalCaseResponse addCase(@Valid @RequestBody EvalCaseRequest request) {
		return cases.create(request);
	}

	@DeleteMapping("/cases/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteCase(@PathVariable String id) {
		cases.delete(id);
	}

	/** Starts an eval in the background: 202 with the run; poll it for the numbers. */
	@PostMapping("/runs")
	public ResponseEntity<EvalRunResponse> run(@Valid @RequestBody EvalRunRequest request) {
		EvalRunResponse run = runner.start(request);
		return ResponseEntity.accepted().location(URI.create("/api/v1/admin/evals/runs/" + run.id())).body(run);
	}

	@GetMapping("/runs")
	public List<EvalRunResponse> runs(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
		return runner.recent(limit);
	}

	@GetMapping("/runs/{id}")
	public EvalRunResponse runById(@PathVariable String id) {
		return runner.get(id);
	}

}
