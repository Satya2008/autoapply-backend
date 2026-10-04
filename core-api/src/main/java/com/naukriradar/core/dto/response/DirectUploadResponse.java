package com.naukriradar.core.dto.response;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * Where and how to upload: {@code PUT uploadUrl} with these headers and the file as the body,
 * before {@code expiresAt}; then confirm with {@code key}.
 */
public record DirectUploadResponse(URI uploadUrl, String method, Map<String, String> headers, String key,
		Instant expiresAt) {
}
