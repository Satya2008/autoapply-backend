package com.naukriradar.common.redis.lock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.naukriradar.common.redis.InstanceId;
import com.naukriradar.common.redis.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * {@code SET key token NX PX lease}: the key exists only while someone holds the lock, and
 * expires by itself if the holder dies. The value is a token unique to this acquisition, and
 * release and renewal are Lua scripts that act only if the token still matches, so we can
 * never delete or extend a lock that expired and went to someone else.
 *
 * <p>A watchdog renews every held lock at a third of its lease. Work can run longer than the
 * lease without losing the lock, and a crashed holder blocks others for one lease at most.
 *
 * <p>This is a single-Redis lock. It is not Redlock: if Redis fails over and loses the key,
 * two holders are possible for a moment. Everything locked here is also guarded by the
 * database (unique keys, row locks), so the lock prevents wasted work, not corruption.
 */
public class RedisDistributedLock implements DistributedLock {

	private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);

	private static final Duration MIN_LEASE = Duration.ofSeconds(1);

	private static final RedisScript<Long> RELEASE = RedisScript.of(new ClassPathResource("redis/lock-release.lua"), Long.class);

	private static final RedisScript<Long> EXTEND = RedisScript.of(new ClassPathResource("redis/lock-extend.lua"), Long.class);

	private final StringRedisTemplate redis;
	private final RedisKeys keys;
	private final InstanceId instance;
	private final ScheduledExecutorService watchdog;
	private final Duration defaultLease;
	private final Clock clock;

	public RedisDistributedLock(StringRedisTemplate redis, RedisKeys keys, InstanceId instance,
			ScheduledExecutorService watchdog, Duration defaultLease, Clock clock) {
		requireLease(defaultLease);
		this.redis = redis;
		this.keys = keys;
		this.instance = instance;
		this.watchdog = watchdog;
		this.defaultLease = defaultLease;
		this.clock = clock;
	}

	@Override
	public Optional<LockHandle> tryAcquire(String name) {
		return tryAcquire(name, defaultLease);
	}

	@Override
	public Optional<LockHandle> tryAcquire(String name, Duration lease) {
		requireName(name);
		requireLease(lease);
		String key = keys.key("lock", name);
		String token = instance + "/" + UUID.randomUUID();
		Boolean taken;
		try {
			taken = redis.opsForValue().setIfAbsent(key, token, lease);
		}
		catch (DataAccessException ex) {
			throw new LockUnavailableException(name, ex);
		}
		if (!Boolean.TRUE.equals(taken)) {
			return Optional.empty();
		}
		Handle handle = new Handle(name, key, token, lease, clock.instant());
		long every = Math.max(100, lease.toMillis() / 3);
		handle.renewal = watchdog.scheduleAtFixedRate(handle::renew, every, every, TimeUnit.MILLISECONDS);
		return Optional.of(handle);
	}

	@Override
	public boolean isHeld(String name) {
		requireName(name);
		try {
			return Boolean.TRUE.equals(redis.hasKey(keys.key("lock", name)));
		}
		catch (DataAccessException ex) {
			throw new LockUnavailableException(name, ex);
		}
	}

	private static void requireName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Lock name must not be blank");
		}
	}

	private static void requireLease(Duration lease) {
		if (lease == null || lease.compareTo(MIN_LEASE) < 0) {
			throw new IllegalArgumentException("Lock lease must be at least " + MIN_LEASE.toSeconds() + "s");
		}
	}

	private final class Handle implements LockHandle {

		private final String name;

		private final String key;

		private final String token;

		private final Duration lease;

		private final Instant acquiredAt;

		private final AtomicBoolean closed = new AtomicBoolean();

		private volatile boolean valid = true;

		private volatile Instant lastRenewed;

		private volatile ScheduledFuture<?> renewal;

		private Handle(String name, String key, String token, Duration lease, Instant acquiredAt) {
			this.name = name;
			this.key = key;
			this.token = token;
			this.lease = lease;
			this.acquiredAt = acquiredAt;
			this.lastRenewed = acquiredAt;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public boolean isValid() {
			return valid && !closed.get();
		}

		private void renew() {
			if (closed.get() || !valid) {
				stopRenewing();
				return;
			}
			try {
				Long extended = redis.execute(EXTEND, List.of(key), token, String.valueOf(lease.toMillis()));
				if (extended == null || extended == 0) {
					valid = false;
					stopRenewing();
					log.warn("Lost lock {}: it expired or was taken over", name);
					return;
				}
				lastRenewed = clock.instant();
			}
			catch (RuntimeException ex) {
				// keep trying while the lease may still be alive; give up once it surely isn't
				if (Duration.between(lastRenewed, clock.instant()).compareTo(lease) >= 0) {
					valid = false;
					stopRenewing();
					log.warn("Lost lock {}: Redis unreachable for longer than the lease", name);
				}
				else {
					log.debug("Renewing lock {} failed; will retry", name, ex);
				}
			}
		}

		@Override
		public void releaseAfter(Duration minHold) {
			if (!closed.compareAndSet(false, true)) {
				return;
			}
			stopRenewing();
			long remaining = Duration.between(clock.instant(), acquiredAt.plus(minHold)).toMillis();
			try {
				if (remaining > 0) {
					redis.execute(EXTEND, List.of(key), token, String.valueOf(remaining));
				}
				else {
					redis.execute(RELEASE, List.of(key), token);
				}
			}
			catch (RuntimeException ex) {
				// the key still expires on its own within one lease
				log.warn("Couldn't release lock {} (it will expire by itself): {}", name, ex.getMessage());
			}
		}

		@Override
		public void close() {
			releaseAfter(Duration.ZERO);
		}

		private void stopRenewing() {
			ScheduledFuture<?> task = renewal;
			if (task != null) {
				task.cancel(false);
			}
		}

	}

}
