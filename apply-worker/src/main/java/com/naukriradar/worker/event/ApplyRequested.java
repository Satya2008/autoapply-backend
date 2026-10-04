package com.naukriradar.worker.event;

import java.util.Map;

/**
 * Payload of apply.requested, from core-api.
 *
 * @param attempt 1 for the first try; with the application id it names this attempt
 * @param answers the candidate's prepared answers, by field name
 */
public record ApplyRequested(String applicationId, String userId, String applyUrl, int attempt,
		Map<String, String> answers) {
}
