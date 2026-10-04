package com.naukriradar.job.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * A job with enough text to score it; the description is cut to keep responses small. The
 * last three come from the AI parse and are empty until the job is parsed.
 */
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
		String description,
		List<String> requiredSkills,
		Integer minYearsExperience,
		String seniority) {
}
