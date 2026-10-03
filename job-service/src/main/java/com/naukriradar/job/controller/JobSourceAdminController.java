package com.naukriradar.job.controller;

import java.util.List;

import com.naukriradar.job.dto.request.JobSourceRequest;
import com.naukriradar.job.dto.response.DryRunResponse;
import com.naukriradar.job.dto.response.FetchResultResponse;
import com.naukriradar.job.dto.response.JobSourceResponse;
import com.naukriradar.job.service.JobIngestService;
import com.naukriradar.job.service.JobSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin API for job boards. Admin-only once security lands in Phase 8. */
@RestController
@RequestMapping("/api/v1/admin/job-sources")
@RequiredArgsConstructor
public class JobSourceAdminController {

	private final JobSourceService sourceService;
	private final JobIngestService ingestService;

	@GetMapping
	public List<JobSourceResponse> list() {
		return sourceService.list();
	}

	@GetMapping("/{id}")
	public JobSourceResponse get(@PathVariable String id) {
		return sourceService.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public JobSourceResponse create(@Valid @RequestBody JobSourceRequest request) {
		return sourceService.create(request);
	}

	@PutMapping("/{id}")
	public JobSourceResponse update(@PathVariable String id, @Valid @RequestBody JobSourceRequest request) {
		return sourceService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String id) {
		sourceService.delete(id);
	}

	/** Calls the board and shows what would be saved, without saving. */
	@PostMapping("/{id}/test")
	public DryRunResponse test(@PathVariable String id) {
		return ingestService.dryRun(id);
	}

	/** Fetches now and saves. A board failure comes back as status FAILED, not as an error. */
	@PostMapping("/{id}/fetch")
	public FetchResultResponse fetch(@PathVariable String id) {
		return ingestService.fetch(id);
	}

}
