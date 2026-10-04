package com.naukriradar.matching.dto.response;

import java.time.Instant;

public record MatchSummaryResponse(
		String id,
		String jobId,
		int score,
		Integer aiScore,
		String title,
		String company,
		String location,
		boolean remote,
		Instant postedAt,
		String applyUrl,
		Instant matchedAt) {
}
