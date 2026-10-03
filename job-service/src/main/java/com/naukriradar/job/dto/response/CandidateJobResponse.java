package com.naukriradar.job.dto.response;

import java.time.Instant;

/** A job with enough text to score it; the description is cut to keep responses small. */
public record CandidateJobResponse(
		String id,
		String title,
		String company,
		String location,
		boolean remote,
		Long salaryMin,
		Long salaryMax,
		String currency,
		Instant postedAt,
		String applyUrl,
		String description) {
}
