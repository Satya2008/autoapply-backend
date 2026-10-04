package com.naukriradar.core.support;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.LockHandle;
import com.naukriradar.common.redis.lock.LockUnavailableException;

/** A lock for unit tests; remembers how each one was released. */
public class InMemoryLock implements DistributedLock {

	private final Map<String, String> held = new ConcurrentHashMap<>();

	private final List<Duration> holds = new CopyOnWriteArrayList<>();

	private volatile boolean down;

	@Override
	public Optional<LockHandle> tryAcquire(String name) {
		return tryAcquire(name, Duration.ofSeconds(30));
	}

	@Override
	public Optional<LockHandle> tryAcquire(String name, Duration lease) {
		checkUp(name);
		if (held.putIfAbsent(name, "us") != null) {
			return Optional.empty();
		}
		return Optional.of(new LockHandle() {

			@Override
			public String name() {
				return name;
			}

			@Override
			public boolean isValid() {
				return held.containsKey(name);
			}

			@Override
			public void releaseAfter(Duration minHold) {
				holds.add(minHold);
				held.remove(name, "us");
			}

			@Override
			public void close() {
				releaseAfter(Duration.ZERO);
			}
		});
	}

	@Override
	public boolean isHeld(String name) {
		checkUp(name);
		return held.containsKey(name);
	}

	/** As if another instance held the lock. */
	public void takenElsewhere(String name) {
		held.put(name, "elsewhere");
	}

	public void freeAll() {
		held.clear();
	}

	public void redisDown(boolean down) {
		this.down = down;
	}

	/** The minimum hold each release asked for, in order. */
	public List<Duration> holds() {
		return holds;
	}

	private void checkUp(String name) {
		if (down) {
			throw new LockUnavailableException(name, null);
		}
	}

}
