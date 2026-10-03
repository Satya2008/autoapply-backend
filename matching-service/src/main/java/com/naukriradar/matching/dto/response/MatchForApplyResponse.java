package com.naukriradar.matching.dto.response;

/** A match as core-api needs it to plan applications. */
public record MatchForApplyResponse(
		String jobId,
		int score,
		String title,
		String company,
		String location,
		String applyUrl) {
}
