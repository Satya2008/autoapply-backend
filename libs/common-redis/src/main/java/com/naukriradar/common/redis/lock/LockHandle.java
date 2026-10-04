package com.naukriradar.common.redis.lock;

import java.time.Duration;

/**
 * A held lock. While held, its expiry is pushed forward in the background, so work longer
 * than the lease keeps the lock; if the process dies, renewals stop and the lock expires on
 * its own. Closing releases it; closing twice is harmless.
 */
public interface LockHandle extends AutoCloseable {

	String name();

	/**
	 * False once the lock was lost: a renewal found someone else owning it, or Redis was
	 * unreachable for longer than the lease. Long work should check this between steps.
	 */
	boolean isValid();

	/**
	 * Stops renewing, but keeps the lock until {@code minHold} has passed since it was taken.
	 * For scheduled jobs: two instances whose clocks differ by a few seconds would otherwise
	 * both run the same trigger, one after the other.
	 */
	void releaseAfter(Duration minHold);

	@Override
	void close();

}
