package com.naukriradar.common.redis.lock;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.naukriradar.common.redis.InstanceId;
import com.naukriradar.common.redis.RedisKeys;
import com.naukriradar.common.redis.RedisTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisDistributedLockTest {

	private static final LettuceConnectionFactory factory = RedisTestSupport.connect();

	private static final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();

	private final StringRedisTemplate redis = RedisTestSupport.template(factory);

	private final RedisKeys keys = RedisTestSupport.freshKeys();

	/** Two instances of a service: same Redis and keys, separate processes in real life. */
	private final DistributedLock instanceA = lock(redis, "a");

	private final DistributedLock instanceB = lock(redis, "b");

	@AfterAll
	static void close() {
		watchdog.shutdownNow();
		factory.destroy();
	}

	@Test
	void onlyOneInstanceGetsTheLockUntilItIsReleased() {
		Optional<LockHandle> first = instanceA.tryAcquire("job");

		assertThat(first).isPresent();
		assertThat(instanceB.tryAcquire("job")).isEmpty();
		assertThat(instanceB.isHeld("job")).isTrue();
		assertThat(instanceB.tryAcquire("another-job")).isPresent().get().satisfies(LockHandle::close);

		first.get().close();
		first.get().close();

		assertThat(instanceB.isHeld("job")).isFalse();
		assertThat(instanceB.tryAcquire("job")).isPresent().get().satisfies(LockHandle::close);
	}

	@Test
	void theWatchdogKeepsTheLockPastItsLeaseWhileWorkGoesOn() throws Exception {
		try (LockHandle handle = instanceA.tryAcquire("long-work", Duration.ofSeconds(1)).orElseThrow()) {
			Thread.sleep(2500);

			assertThat(handle.isValid()).isTrue();
			assertThat(instanceB.tryAcquire("long-work", Duration.ofSeconds(1))).isEmpty();
		}
	}

	@Test
	void aHolderThatStopsRenewingLosesTheLockAfterTheLease() throws Exception {
		ScheduledExecutorService itsWatchdog = Executors.newSingleThreadScheduledExecutor();
		DistributedLock crashing = new RedisDistributedLock(redis, keys, new InstanceId("crashing"), itsWatchdog,
				Duration.ofSeconds(1), Clock.systemUTC());

		crashing.tryAcquire("orphan", Duration.ofSeconds(1)).orElseThrow();
		itsWatchdog.shutdownNow(); // the process dies: no release, no renewals
		assertThat(instanceB.tryAcquire("orphan")).isEmpty();

		Thread.sleep(1300);
		assertThat(instanceB.tryAcquire("orphan")).isPresent().get().satisfies(LockHandle::close);
	}

	@Test
	void aLostLockIsNoticedAndItsNewOwnerIsLeftAlone() throws Exception {
		LockHandle stale = instanceA.tryAcquire("taken-over", Duration.ofSeconds(1)).orElseThrow();
		// as if the lock had expired and B had taken it
		redis.opsForValue().set(keys.key("lock", "taken-over"), "someone-else");

		Thread.sleep(800);
		assertThat(stale.isValid()).isFalse();

		stale.close();
		assertThat(redis.opsForValue().get(keys.key("lock", "taken-over"))).isEqualTo("someone-else");
	}

	@Test
	void releaseAfterKeepsTheLockForTheMinimumHold() throws Exception {
		LockHandle handle = instanceA.tryAcquire("cron").orElseThrow();
		handle.releaseAfter(Duration.ofMillis(1500));

		assertThat(instanceB.tryAcquire("cron")).isEmpty();
		Long ttl = redis.getExpire(keys.key("lock", "cron"));
		assertThat(ttl).isBetween(0L, 2L);

		Thread.sleep(1700);
		assertThat(instanceB.tryAcquire("cron")).isPresent().get().satisfies(LockHandle::close);
	}

	@Test
	void releaseAfterAHoldThatAlreadyPassedReleasesAtOnce() {
		LockHandle handle = instanceA.tryAcquire("quick").orElseThrow();
		handle.releaseAfter(Duration.ZERO);

		assertThat(instanceB.isHeld("quick")).isFalse();
	}

	@Test
	void badArgumentsAreRejected() {
		assertThatThrownBy(() -> instanceA.tryAcquire(" ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> instanceA.tryAcquire("x", Duration.ofMillis(10))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> instanceA.isHeld(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void whenRedisIsDownCallersAreToldSoInsteadOfGettingAWrongAnswer() {
		LettuceConnectionFactory down = RedisTestSupport.unreachable();
		try {
			DistributedLock lock = lock(RedisTestSupport.template(down), "c");

			assertThatThrownBy(() -> lock.tryAcquire("job")).isInstanceOf(LockUnavailableException.class);
			assertThatThrownBy(() -> lock.isHeld("job")).isInstanceOf(LockUnavailableException.class);
		}
		finally {
			down.destroy();
		}
	}

	private DistributedLock lock(StringRedisTemplate template, String instance) {
		return new RedisDistributedLock(template, keys, new InstanceId(instance), watchdog, Duration.ofSeconds(5),
				Clock.systemUTC());
	}

}
