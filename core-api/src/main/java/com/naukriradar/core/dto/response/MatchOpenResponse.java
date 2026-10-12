package com.naukriradar.core.dto.response;

/** "Apply" on a match: the application now tracking it, and the link to apply on. */
public record MatchOpenResponse(String applicationId, String applyUrl) {
}
