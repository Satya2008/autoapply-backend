package com.naukriradar.core.dto.response;

import java.util.Map;

/** One application the user should send themselves, with the answers ready to copy. */
public record NeedsYouResponse(
		ApplicationSummaryResponse application,
		String reason,
		Map<String, String> prefill) {
}
