package com.naukriradar.job.controller;

import com.naukriradar.job.dto.request.JobSearchRequest;
import com.naukriradar.job.dto.response.JobDetailResponse;
import com.naukriradar.job.dto.response.JobSearchResponse;
import com.naukriradar.job.search.JobSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

	private final JobSearchService searchService;

	/** {@code ?q=java spring&location=pune&remote=true&postedWithinDays=7&limit=20&cursor=...} */
	@GetMapping
	public JobSearchResponse search(@Valid @ModelAttribute JobSearchRequest request) {
		return searchService.search(request);
	}

	@GetMapping("/{id}")
	public JobDetailResponse get(@PathVariable String id) {
		return searchService.get(id);
	}

}
