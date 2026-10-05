package com.naukriradar.core.client;

import java.util.List;

/** A job as job-service describes it; the description is cut to a few thousand characters. */
public record JobPosting(String id, String title, String company, String location, String description,
		List<String> requiredSkills) {

	public JobPosting {
		requiredSkills = requiredSkills == null ? List.of() : List.copyOf(requiredSkills);
	}

}
