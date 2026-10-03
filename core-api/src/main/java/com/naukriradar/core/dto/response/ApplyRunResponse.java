package com.naukriradar.core.dto.response;

import java.time.Instant;

import com.naukriradar.core.model.ApplyRunStatus;

public record ApplyRunResponse(
		String id,
		ApplyRunStatus status,
		int matchesConsidered,
		int queued,
		int needsYou,
		int simulated,
		int failed,
		int alreadyApplied,
		int belowScore,
		int deferred,
		String message,
		Instant startedAt,
		Instant finishedAt) {
}
