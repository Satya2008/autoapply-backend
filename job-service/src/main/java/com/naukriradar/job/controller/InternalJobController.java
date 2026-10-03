package com.naukriradar.job.controller;

import java.util.List;

import com.naukriradar.job.dto.request.CandidateQuery;
import com.naukriradar.job.dto.response.CandidateJobResponse;
import com.naukriradar.job.search.CandidateJobFinder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

}
