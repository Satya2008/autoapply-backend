package com.naukriradar.job.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.LockHandle;
import com.naukriradar.common.redis.lock.LockUnavailableException;
import org.springframework.stereotype.Component;

/**
 * Makes sure one source is never fetched twice at the same time, on any instance: that
 * would double the load on the board and race on the same rows. A Redis lock per source;
 * the handles of the locks this instance holds are kept here until released.
 */
@Component
public class SourceRunLocks {

	private final DistributedLock lock;
	private final Map<String, LockHandle> held = new ConcurrentHashMap<>();

	public SourceRunLocks(DistributedLock lock) {
		this.lock = lock;
	}

	/**
	 * Returns false if the source is already being fetched.
	 *
	 * @throws ServiceUnavailableException if Redis is down: fetching unguarded could run the
	 *     same source twice, so we don't
	 */
	public boolean tryAcquire(String sourceId) {
		Optional<LockHandle> handle;
		try {
			handle = lock.tryAcquire(name(sourceId));
		}
		catch (LockUnavailableException ex) {
			throw unavailable();
		}
		handle.ifPresent(h -> held.put(sourceId, h));
		return handle.isPresent();
	}

	public void release(String sourceId) {
		LockHandle handle = held.remove(sourceId);
		if (handle != null) {
			handle.close();
		}
	}

	public boolean isRunning(String sourceId) {
		try {
			return lock.isHeld(name(sourceId));
		}
		catch (LockUnavailableException ex) {
			throw unavailable();
		}
	}

	private static String name(String sourceId) {
		return "source-fetch:" + sourceId;
	}

	private static ServiceUnavailableException unavailable() {
		return new ServiceUnavailableException("Fetching is paused: the lock service is unreachable. Try again shortly.");
	}

}
