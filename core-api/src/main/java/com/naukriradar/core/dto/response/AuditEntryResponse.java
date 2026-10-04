package com.naukriradar.core.dto.response;

import java.time.Instant;

public record AuditEntryResponse(
		String id,
		String actor,
		String action,
		String targetType,
		String targetId,
		String detail,
		String ip,
		boolean success,
		String error,
		Instant at) {
}
