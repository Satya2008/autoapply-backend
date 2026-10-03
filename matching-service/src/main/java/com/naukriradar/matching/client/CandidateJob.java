package com.naukriradar.matching.client;

import java.time.Instant;

/** A job as job-service shortlists it for scoring. */
public record CandidateJob(
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
