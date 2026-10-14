package com.naukriradar.job.dto.request;

/** How to order job search results. */
public enum JobSort {

	/** Newest first (default). */
	NEWEST,

	/** Oldest first. */
	OLDEST,

	/** Highest salary first; jobs without a stated salary come after those with one. */
	SALARY

}
