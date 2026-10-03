package com.naukriradar.core.dto.response;

import java.time.Instant;
import java.util.Map;

import com.naukriradar.core.model.RiskBand;

public record PortalConfigResponse(
		String id,
		String domain,
		String name,
		RiskBand riskBand,
		boolean enabled,
		Map<String, String> selectors,
		Instant createdAt) {
}
