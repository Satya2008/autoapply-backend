package com.naukriradar.matching.dto.response;

import java.time.Instant;

public record PromptResponse(String code, int version, boolean active, String system, String template,
		String outputSchema, Instant createdAt) {
}
