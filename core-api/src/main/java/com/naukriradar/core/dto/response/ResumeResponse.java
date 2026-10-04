package com.naukriradar.core.dto.response;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

public record ResumeResponse(
		String id,
		String fileName,
		String contentType,
		long sizeBytes,
		Instant uploadedAt,
		boolean textExtracted,
		JsonNode aiParsed,
		Instant aiParsedAt) {
}
