package com.naukriradar.matching.dto.response;

import java.time.Instant;

/**
 * @param activatedAt when this version first went live; null if never
 * @param evalGated whether a new version of this prompt needs a passing eval before it can go live
 */
public record PromptResponse(String code, int version, boolean active, String system, String template,
		String outputSchema, Instant createdAt, Instant activatedAt, boolean evalGated) {
}
