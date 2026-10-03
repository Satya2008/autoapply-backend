package com.naukriradar.job.normalizer;

import java.time.Instant;

/** A posting cleaned up and ready to store. Required fields are never null. */
public record NormalizedJob(
		String externalId,
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
