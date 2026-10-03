package com.naukriradar.job.dto.response;

import java.time.Instant;

public record JobPreview(
		String externalId,
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
