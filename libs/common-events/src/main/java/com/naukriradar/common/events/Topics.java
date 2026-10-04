package com.naukriradar.common.events;

/**
 * The event flow: fetch, parse, match, apply, notify. Each topic has a {@code .dlq} twin for
 * events that failed every retry.
 */
public final class Topics {

	/** job-service fetched new or changed jobs. Key: fetch run id. */
	public static final String JOBS_INGESTED = "jobs.ingested";

	/** job-service finished an AI parsing round. Key: round id. */
	public static final String JOBS_PARSED = "jobs.parsed";

	/** matching-service finished a user's match run. Key: user id, so one user's events stay in order. */
	public static final String MATCH_CREATED = "match.created";

	/** core-api queued an application for the apply engine (Phase 15). Key: user id. */
	public static final String APPLY_REQUESTED = "apply.requested";

	/** core-api finished an apply run. Key: user id. */
	public static final String APPLY_COMPLETED = "apply.completed";

	/** Something the user should hear about (Phase 16). Key: user id. */
	public static final String NOTIFY_REQUESTED = "notify.requested";

	public static final String DLQ_SUFFIX = ".dlq";

	private Topics() {
	}

	public static String dlq(String topic) {
		return topic + DLQ_SUFFIX;
	}

}
