package com.naukriradar.core.client;

/** A match as matching-service hands it over for applying. */
public record MatchForApply(
		String jobId,
		int score,
		String title,
		String company,
		String location,
		String applyUrl) {
}
