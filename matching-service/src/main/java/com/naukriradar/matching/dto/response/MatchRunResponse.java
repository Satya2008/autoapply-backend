package com.naukriradar.matching.dto.response;

import java.time.Instant;

import com.naukriradar.matching.model.MatchRunStatus;

public record MatchRunResponse(
		String id,
		MatchRunStatus status,
		int jobsConsidered,
		int excluded,
		int matchesCreated,
		int matchesUpdated,
		int belowThreshold,
		String message,
		Instant startedAt,
		Instant finishedAt) {
}
