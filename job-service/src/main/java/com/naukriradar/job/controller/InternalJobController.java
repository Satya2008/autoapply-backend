package com.naukriradar.job.controller;

import java.util.List;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.job.dto.request.CandidateQuery;
import com.naukriradar.job.dto.request.JobIdsRequest;
import com.naukriradar.job.dto.response.CandidateJobResponse;
import com.naukriradar.job.search.CandidateJobFinder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API; the gateway has no route to /internal. */
@RestController
@RequestMapping("/internal/v1/jobs")
@RequiredArgsConstructor
public class InternalJobController {

	private final CandidateJobFinder finder;

	@PostMapping("/candidates")
	public List<CandidateJobResponse> candidates(@Valid @RequestBody CandidateQuery query) {
		return finder.find(query);
	}

	/** Recent active jobs page by page (keyset on id), for matching's vector index. */
	@GetMapping("/recent")
	public List<CandidateJobResponse> recent(
			@RequestParam(defaultValue = "60") @Min(1) @Max(365) int days,
			@RequestParam(required = false) @Size(max = 36) String after,
			@RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit) {
		return finder.recent(days, after, limit);
	}

	/** Still-active jobs by id, in the order asked. */
	@PostMapping("/by-ids")
	public List<CandidateJobResponse> byIds(@Valid @RequestBody JobIdsRequest request) {
		return finder.activeByIds(request.ids());
	}

	/** One job with its description, active or not. */
	@GetMapping("/{id}")
	public CandidateJobResponse byId(@PathVariable String id) {
		return finder.byId(id).orElseThrow(() -> new NotFoundException("No job " + id + "."));
	}

}
