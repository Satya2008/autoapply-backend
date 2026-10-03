package com.naukriradar.core.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Where an application stands, and where it may go next. Every status lists its allowed
 * next statuses here; anything else is refused, so an application can never jump from
 * SKIPPED to OFFER however the code calls it.
 *
 * <pre>
 * PLANNED ──► QUEUED ──► SIMULATED / SUBMITTED ──► INTERVIEW ──► OFFER
 *    │          │  ▲                 │                  │
 *    │          ▼  │                 ▼                  ▼
 *    │        FAILED ─────────►  NEEDS_YOU ──► APPLIED ─► REJECTED
 *    └────────────────────────────►  │
 *                                    ▼
 *                                 SKIPPED
 * </pre>
 */
public enum ApplicationStatus {

	/** Created from a match; about to be routed. */
	PLANNED,
	/** Waiting for the automatic apply engine. Only ever for LOW-risk portals. */
	QUEUED,
	/** The engine ran in simulate mode: nothing reached the employer. */
	SIMULATED,
	/** The engine really submitted it. */
	SUBMITTED,
	/** The automatic attempt failed; it will be retried. */
	FAILED,
	/** Answers are prepared; the candidate applies on the site themselves. */
	NEEDS_YOU,
	/** The candidate applied themselves. */
	APPLIED,
	INTERVIEW,
	OFFER,
	REJECTED,
	/** The candidate decided not to apply. */
	SKIPPED;

	private static final Map<ApplicationStatus, Set<ApplicationStatus>> NEXT = Map.ofEntries(
			Map.entry(PLANNED, EnumSet.of(QUEUED, NEEDS_YOU, SKIPPED)),
			Map.entry(QUEUED, EnumSet.of(SIMULATED, SUBMITTED, FAILED, NEEDS_YOU, SKIPPED)),
			Map.entry(FAILED, EnumSet.of(QUEUED, NEEDS_YOU, SKIPPED)),
			Map.entry(SIMULATED, EnumSet.of(APPLIED, NEEDS_YOU, SKIPPED)),
			Map.entry(NEEDS_YOU, EnumSet.of(APPLIED, SKIPPED)),
			Map.entry(SUBMITTED, EnumSet.of(INTERVIEW, REJECTED)),
			Map.entry(APPLIED, EnumSet.of(INTERVIEW, REJECTED)),
			Map.entry(INTERVIEW, EnumSet.of(OFFER, REJECTED)),
			Map.entry(OFFER, EnumSet.noneOf(ApplicationStatus.class)),
			Map.entry(REJECTED, EnumSet.noneOf(ApplicationStatus.class)),
			Map.entry(SKIPPED, EnumSet.noneOf(ApplicationStatus.class)));

	/** Statuses that mean the application reached the employer (or the employer answered). */
	public static final Set<ApplicationStatus> SENT = EnumSet.of(SUBMITTED, APPLIED, INTERVIEW, OFFER, REJECTED);

	public boolean canMoveTo(ApplicationStatus next) {
		return NEXT.get(this).contains(next);
	}

	public boolean isFinal() {
		return NEXT.get(this).isEmpty();
	}

}
