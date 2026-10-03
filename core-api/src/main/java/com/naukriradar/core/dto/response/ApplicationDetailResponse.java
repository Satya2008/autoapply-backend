package com.naukriradar.core.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** An application with its full timeline, oldest event first. */
public record ApplicationDetailResponse(
		ApplicationSummaryResponse application,
		String riskReason,
		String needsYouReason,
		int attempts,
		String lastError,
		Instant nextAttemptAt,
		Map<String, String> prefill,
		List<ApplicationEventResponse> timeline) {
}
