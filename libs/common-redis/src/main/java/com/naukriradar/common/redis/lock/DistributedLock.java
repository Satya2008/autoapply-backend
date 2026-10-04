package com.naukriradar.common.redis.lock;

import java.time.Duration;
import java.util.Optional;

/** A lock shared by every instance of a service. Never waits: either you get it now or not. */
public interface DistributedLock {

	/** Uses the default lease. */
	Optional<LockHandle> tryAcquire(String name);

	/**
	 * @param lease how long the lock outlives this process if it dies while holding it
	 * @return the handle, or empty if someone else holds the lock
	 * @throws LockUnavailableException if Redis can't be reached
	 */
	Optional<LockHandle> tryAcquire(String name, Duration lease);

	/** @throws LockUnavailableException if Redis can't be reached */
	boolean isHeld(String name);

}
