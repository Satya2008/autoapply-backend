package com.naukriradar.job.dto.response;

import com.naukriradar.job.model.RunStatus;

/**
 * Outcome of one run. A failed run is still a normal response: the board being down is
 * expected, not an error in this service.
 *
 * @param received items the board returned
 * @param skipped items dropped as incomplete, invalid or repeated
 * @param sourceDisabled true if this failure switched the source off
 */
public record FetchResultResponse(
		String sourceCode,
		RunStatus status,
		int pagesFetched,
		int received,
		int inserted,
		int updated,
		int skipped,
		String message,
		boolean sourceDisabled,
		long durationMs) {
}
