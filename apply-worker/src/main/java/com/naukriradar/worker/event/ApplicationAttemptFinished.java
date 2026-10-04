package com.naukriradar.worker.event;

import java.util.List;

/**
 * Payload of apply.completed for one application attempt; core-api moves the application on.
 *
 * @param outcome SUBMITTED, FAILED, NEEDS_YOU or UNKNOWN
 * @param screenshot key of the screenshot when something went wrong
 */
public record ApplicationAttemptFinished(String applicationId, String userId, int attempt, String outcome, String note,
		List<String> filled, List<String> missing, String screenshot) {

	public static final String TYPE = "ApplicationAttemptFinished";

}
