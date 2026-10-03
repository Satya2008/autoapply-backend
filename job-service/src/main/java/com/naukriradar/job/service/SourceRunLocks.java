package com.naukriradar.job.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Makes sure one source is never fetched twice at the same time in this instance, which
 * would double the load on the board and race on the same rows. Phase 10 swaps this for a
 * Redis lock that also works across instances.
 */
@Component
public class SourceRunLocks {

	private final Set<String> running = ConcurrentHashMap.newKeySet();

	/** Returns false if the source is already being fetched. */
	public boolean tryAcquire(String sourceId) {
		return running.add(sourceId);
	}

	public void release(String sourceId) {
		running.remove(sourceId);
	}

	public boolean isRunning(String sourceId) {
		return running.contains(sourceId);
	}

}
