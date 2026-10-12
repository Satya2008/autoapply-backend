package com.naukriradar.core.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * The candidate's resume rewritten for one job: same facts, the job's most relevant ones first.
 *
 * @param atsBefore share (0-100) of the job's skills the original resume shows
 * @param atsAfter the same for the tailored one; it can only show more of what the resume has
 * @param matched the job's skills the tailored resume shows
 * @param missing the job's skills the resume doesn't have; never added, so the candidate knows
 * @param unsupported claims in it that the resume and profile don't back (empty when grounded)
 * @param writtenBy provider:model, or null when AI wasn't available
 */
public record TailoredResumeResponse(String applicationId, Resume resume, int atsBefore, int atsAfter,
		List<String> matched, List<String> missing, List<String> unsupported, String writtenBy, Instant at) {

	public record Resume(String summary, List<String> skills, List<Job> experience, List<Project> projects,
			List<String> education) {
	}

	public record Job(String title, String company, String dates, List<String> bullets) {
	}

	public record Project(String name, List<String> bullets) {
	}

}
