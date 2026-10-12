package com.naukriradar.job.controller;

import java.util.List;

import com.naukriradar.job.dto.request.JobSearchRequest;
import com.naukriradar.job.dto.response.JobDetailResponse;
import com.naukriradar.job.dto.response.JobSearchResponse;
import com.naukriradar.job.dto.response.JobSummaryResponse;
import com.naukriradar.job.dto.response.Suggestion;
import com.naukriradar.job.search.JobSearchService;
import com.naukriradar.job.search.SuggestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

	private final JobSearchService searchService;
	private final SuggestionService suggestions;

	/** {@code ?q=java spring&location=pune&remote=true&postedWithinDays=7&limit=20&cursor=...} */
	@GetMapping
	public JobSearchResponse search(@Valid @ModelAttribute JobSearchRequest request,
			@RequestHeader(name = ROLE_HEADER, required = false) String role) {
		if (ADMIN.equals(role)) {
			return searchService.search(request);
		}
		// users neither see nor filter by where jobs come from
		JobSearchResponse found = searchService.search(request.withoutSource());
		return new JobSearchResponse(found.items().stream().map(JobSummaryResponse::withoutSource).toList(), found.nextCursor());
	}

	@GetMapping("/{id}")
	public JobDetailResponse get(@PathVariable String id, @RequestHeader(name = ROLE_HEADER, required = false) String role) {
		JobDetailResponse job = searchService.get(id);
		return ADMIN.equals(role) ? job : job.withoutSource();
	}

	/** Set by the gateway from the signed-in session. */
	static final String ROLE_HEADER = "X-User-Role";

	private static final String ADMIN = "ADMIN";

	/** Places active jobs are in that start with what was typed, e.g. {@code ?q=ben}. */
	@GetMapping("/suggest/locations")
	public List<Suggestion> suggestLocations(@RequestParam(defaultValue = "") @Size(max = 60) String q) {
		return suggestions.locations(q);
	}

	/** Skills and job titles for the search box, e.g. {@code ?q=spr}. */
	@GetMapping("/suggest/keywords")
	public List<Suggestion> suggestKeywords(@RequestParam(defaultValue = "") @Size(max = 60) String q) {
		return suggestions.keywords(q);
	}

}
