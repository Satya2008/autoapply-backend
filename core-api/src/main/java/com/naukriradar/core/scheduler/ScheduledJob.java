package com.naukriradar.core.scheduler;

/** A background job whose schedule and on/off switch are runtime settings. */
public interface ScheduledJob {

	/** Stable name used in the admin API, e.g. "auto-apply". */
	String name();

	String cronSetting();

	String enabledSetting();

	/** Does the work and returns a one-line summary for the admin screen. */
	String run();

}
