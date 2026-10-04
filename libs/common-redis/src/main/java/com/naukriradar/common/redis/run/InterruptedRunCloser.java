package com.naukriradar.common.redis.run;

/**
 * Implemented by each service's run store. Called at startup and then every few minutes by
 * {@link InterruptedRunSweeper}; closes runs that are still RUNNING but have no live lease.
 */
public interface InterruptedRunCloser {

	/** @return how many runs were closed */
	int closeInterruptedRuns();

}
