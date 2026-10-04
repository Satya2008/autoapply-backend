package com.naukriradar.common.redis.lock;

/** Redis could not be reached, so we can't tell whether the lock is free. */
public class LockUnavailableException extends RuntimeException {

	public LockUnavailableException(String name, Throwable cause) {
		super("Lock " + name + " can't be checked: Redis is unavailable", cause);
	}

}
