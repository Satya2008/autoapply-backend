package com.naukriradar.job.model;

public enum FetchRunStatus {
	RUNNING,
	/** Every source succeeded. */
	SUCCESS,
	/** Some sources failed, the rest were saved. */
	PARTIAL,
	/** No source succeeded, or the run itself broke. */
	FAILED
}
