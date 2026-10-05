package com.naukriradar.core.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * @param writtenBy provider:model that wrote it, or "template" when AI wasn't available
 * @param draft written from a template without AI: correct but plain, worth editing
 * @param grounded every claim it makes was found in the resume or profile
 * @param unsupportedClaims claims not found there; check them before sending
 * @param evidence the resume sections the letter was built on
 * @param note why AI wasn't used, or null
 */
public record CoverLetterResponse(String applicationId, String letter, Instant writtenAt, String writtenBy, boolean draft,
		boolean grounded, List<String> unsupportedClaims, List<String> evidence, String note) {
}
