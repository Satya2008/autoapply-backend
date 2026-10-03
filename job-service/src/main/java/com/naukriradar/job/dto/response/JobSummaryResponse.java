package com.naukriradar.job.dto.response;

import java.time.Instant;

public record JobSummaryResponse(
		String id,
		String sourceCode,
		String title,
		String company,
		String location,
		boolean remote,
		Long salaryMin,
		Long salaryMax,
		String currency,
		Instant postedAt,
		String applyUrl) {
}
