package com.naukriradar.common.redis.run;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.LockHandle;
import com.naukriradar.common.redis.lock.LockUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Marks a background run as alive while some instance is working on it. The lease is a lock
 * kept fresh by the watchdog; when the process dies the lease expires. A run that is still
 * RUNNING in the database but has no lease was abandoned and can be closed, even when the
 * instance that started it is a different one still serving traffic.
 */
public class RunLeases {

	private static final Logger log = LoggerFactory.getLogger(RunLeases.class);

	private final DistributedLock lock;
	private final Duration lease;
	private final Map<String, LockHandle> held = new ConcurrentHashMap<>();

	public RunLeases(DistributedLock lock, Duration lease) {
		this.lock = lock;
		this.lease = lease;
	}

	/**
	 * Best effort: if Redis is down the run still goes ahead, without a lease. The sweeper
	 * can't check leases then either, so it won't close the run by mistake meanwhile.
	 */
	public void begin(String kind, String runId) {
		String name = name(kind, runId);
		try {
			lock.tryAcquire(name, lease).ifPresentOrElse(handle -> {
				LockHandle previous = held.put(name, handle);
				if (previous != null) {
					previous.close();
				}
			}, () -> log.warn("Run {} {} already has a lease", kind, runId));
		}
		catch (LockUnavailableException ex) {
			log.warn("No lease for run {} {}: {}", kind, runId, ex.getMessage());
		}
	}

	public void end(String kind, String runId) {
		LockHandle handle = held.remove(name(kind, runId));
		if (handle != null) {
			handle.close();
		}
	}

	/**
	 * @return the runs nobody is working on
	 * @throws LockUnavailableException if Redis can't be reached; then nothing is known
	 */
	public Collection<String> abandoned(String kind, Collection<String> runIds) {
		return runIds.stream().filter(id -> !lock.isHeld(name(kind, id))).toList();
	}

	private static String name(String kind, String runId) {
		return "run:" + kind + ":" + runId;
	}

}
