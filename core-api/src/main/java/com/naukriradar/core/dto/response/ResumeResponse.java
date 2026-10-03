package com.naukriradar.core.dto.response;

import java.time.Instant;

public record ResumeResponse(
		String id,
		String fileName,
		String contentType,
		long sizeBytes,
		Instant uploadedAt,
		boolean textExtracted) {
}
