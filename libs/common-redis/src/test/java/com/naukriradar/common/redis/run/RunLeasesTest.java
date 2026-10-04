package com.naukriradar.common.redis.run;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import com.naukriradar.common.redis.InstanceId;
import com.naukriradar.common.redis.RedisKeys;
import com.naukriradar.common.redis.RedisTestSupport;
import com.naukriradar.common.redis.lock.LockUnavailableException;
import com.naukriradar.common.redis.lock.RedisDistributedLock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunLeasesTest {

	private static final LettuceConnectionFactory factory = RedisTestSupport.connect();

	private static final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();

	private final RedisKeys keys = RedisTestSupport.freshKeys();

	private final RunLeases thisInstance = leases(factory, "a");

	private final RunLeases otherInstance = leases(factory, "b");

	@AfterAll
	static void close() {
		watchdog.shutdownNow();
		factory.destroy();
	}

	@Test
	void aRunIsAbandonedOnlyWhileNobodyHoldsItsLease() {
		thisInstance.begin("match", "run-1");

		assertThat(otherInstance.abandoned("match", List.of("run-1", "run-2"))).containsExactly("run-2");
		assertThat(otherInstance.abandoned("apply", List.of("run-1"))).containsExactly("run-1");

		thisInstance.end("match", "run-1");
		thisInstance.end("match", "run-1");

		assertThat(otherInstance.abandoned("match", List.of("run-1"))).containsExactly("run-1");
	}

	@Test
	void withRedisDownARunStillStartsButNothingIsDeclaredAbandoned() {
		LettuceConnectionFactory down = RedisTestSupport.unreachable();
		try {
			RunLeases offline = leases(down, "c");

			offline.begin("match", "run-3");
			offline.end("match", "run-3");
			assertThatThrownBy(() -> offline.abandoned("match", List.of("run-3")))
					.isInstanceOf(LockUnavailableException.class);
		}
		finally {
			down.destroy();
		}
	}

	@Test
	void theSweeperKeepsGoingWhenOneStoreFails() {
		AtomicInteger calls = new AtomicInteger();
		InterruptedRunCloser broken = () -> {
			throw new IllegalStateException("db down");
		};
		InterruptedRunCloser redisDown = () -> {
			throw new LockUnavailableException("x", null);
		};
		InterruptedRunCloser working = calls::incrementAndGet;

		new InterruptedRunSweeper(List.of(broken, redisDown, working), watchdog, Duration.ofMinutes(1)).sweep();

		assertThat(calls).hasValue(1);
	}

	private RunLeases leases(LettuceConnectionFactory connection, String instance) {
		return new RunLeases(new RedisDistributedLock(RedisTestSupport.template(connection), keys,
				new InstanceId(instance), watchdog, Duration.ofSeconds(5), Clock.systemUTC()), Duration.ofSeconds(5));
	}

}
