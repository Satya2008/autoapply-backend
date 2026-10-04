package com.naukriradar.matching.client;

import java.time.Instant;
import java.util.List;

/**
 * A job as job-service shortlists it for scoring. The last three come from the AI parse of
 * the posting and are null (or empty) until the job has been parsed.
 */
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
		String description,
		List<String> requiredSkills,
		Integer minYearsExperience,
		String seniority) {

	public CandidateJob {
		requiredSkills = requiredSkills == null ? List.of() : List.copyOf(requiredSkills);
	}

	/** A job not parsed yet. */
	public CandidateJob(String id, String title, String company, String location, boolean remote, Long salaryMin,
			Long salaryMax, String currency, Instant postedAt, String applyUrl, String description) {
		this(id, title, company, location, remote, salaryMin, salaryMax, currency, postedAt, applyUrl, description, null,
				null, null);
	}

}
