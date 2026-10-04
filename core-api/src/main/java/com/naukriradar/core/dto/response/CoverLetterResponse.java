package com.naukriradar.core.dto.response;

import java.time.Instant;

/** @param writtenBy provider:model that wrote it */
public record CoverLetterResponse(String applicationId, String letter, Instant writtenAt, String writtenBy) {
}
