package com.naukriradar.job.dto.response;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

import com.naukriradar.job.model.JobStatus;

public record JobDetailResponse(
		String id,
		String sourceCode,
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
		String description,
		JobStatus status,
		Instant fetchedAt,
		Instant lastSeenAt,
		JsonNode requirements,
		Instant parsedAt) {

	/** For users: where a job came from is the app's business, not theirs. */
	public JobDetailResponse withoutSource() {
		return new JobDetailResponse(id, null, null, title, company, location, remote, salaryMin, salaryMax, currency, postedAt,
				applyUrl, description, status, fetchedAt, lastSeenAt, requirements, parsedAt);
	}

}
