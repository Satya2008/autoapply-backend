package com.naukriradar.job.support;

import java.time.Instant;

import com.naukriradar.job.normalizer.NormalizedJob;

public final class TestJobs {

	private TestJobs() {
	}

	public static NormalizedJob job(String externalId, String title, String company, String location, boolean remote,
			Instant postedAt) {
		return new NormalizedJob(externalId, title, company, location, remote, null, null, null, postedAt,
				"https://jobs.example.com/" + externalId, title + " at " + company + ". Full description here.");
	}

	/** Same, but with a stated salary range (for salary-sort tests). */
	public static NormalizedJob job(String externalId, String title, String company, String location, boolean remote,
			Instant postedAt, Long salaryMin, Long salaryMax) {
		return new NormalizedJob(externalId, title, company, location, remote, salaryMin, salaryMax, "INR", postedAt,
				"https://jobs.example.com/" + externalId, title + " at " + company + ". Full description here.");
	}

}
