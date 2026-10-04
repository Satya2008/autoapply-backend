package com.naukriradar.common.redis.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.LockHandle;
import com.naukriradar.common.redis.lock.LockUnavailableException;

/**
 * When a popular entry expires, every request for it misses at once and they all hit the
 * database together: a cache stampede. Here only one caller per key loads:
 *
 * <ol>
 * <li>inside this instance, callers for the same key share one in-flight load;</li>
 * <li>across instances, the loader takes a short Redis lock; callers elsewhere poll Redis
 * for the value instead of loading it themselves.</li>
 * </ol>
 *
 * Waiting is bounded. If the value doesn't show up in time, or Redis is down, the caller
 * loads it itself: slower is fine, stuck is not.
 */
public class CacheStampedeGuard {

	private static final Duration LOAD_LEASE = Duration.ofSeconds(10);

	private final DistributedLock lock;
	private final Duration maxWait;
	private final Duration pollEvery;
	private final ConcurrentMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

	public CacheStampedeGuard(DistributedLock lock, Duration maxWait, Duration pollEvery) {
		this.lock = lock;
		this.maxWait = maxWait;
		this.pollEvery = pollEvery;
	}

	/**
	 * @param lockName unique per cache and key
	 * @param shared reads the value from Redis; empty when absent
	 * @param load runs the real method and stores the result in both levels
	 * @param waited called each time a caller got the value from someone else's load
	 * @throws Exception whatever the load threw
	 */
	public Object load(String lockName, Supplier<Optional<Object>> shared, Callable<Object> load, Runnable waited)
			throws Exception {
		CompletableFuture<Object> mine = new CompletableFuture<>();
		CompletableFuture<Object> running = inFlight.putIfAbsent(lockName, mine);
		if (running != null) {
			waited.run();
			return join(running);
		}
		try {
			mine.complete(loadOnce(lockName, shared, load, waited));
		}
		catch (Exception ex) {
			mine.completeExceptionally(ex);
		}
		finally {
			inFlight.remove(lockName, mine);
		}
		return join(mine);
	}

	private Object loadOnce(String lockName, Supplier<Optional<Object>> shared, Callable<Object> load, Runnable waited)
			throws Exception {
		Optional<LockHandle> handle;
		try {
			handle = lock.tryAcquire(lockName, LOAD_LEASE);
		}
		catch (LockUnavailableException ex) {
			return load.call();
		}
		if (handle.isPresent()) {
			try (LockHandle held = handle.get()) {
				// someone may have finished loading just before we took the lock
				Optional<Object> value = shared.get();
				return value.isPresent() ? value.get() : load.call();
			}
		}
		long deadline = System.nanoTime() + maxWait.toNanos();
		while (System.nanoTime() < deadline) {
			Thread.sleep(pollEvery.toMillis());
			Optional<Object> value = shared.get();
			if (value.isPresent()) {
				waited.run();
				return value.get();
			}
		}
		return load.call();
	}

	private static Object join(CompletableFuture<Object> future) throws Exception {
		try {
			return future.get();
		}
		catch (ExecutionException ex) {
			if (ex.getCause() instanceof Exception cause) {
				throw cause;
			}
			throw ex;
		}
	}

}
