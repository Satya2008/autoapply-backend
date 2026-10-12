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

	/** For users: where a job came from is the app's business, not theirs. */
	public JobSummaryResponse withoutSource() {
		return new JobSummaryResponse(id, null, title, company, location, remote, salaryMin, salaryMax, currency, postedAt, applyUrl);
	}

}
