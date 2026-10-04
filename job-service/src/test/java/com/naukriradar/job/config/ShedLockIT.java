package com.naukriradar.job.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Two instances firing the same cron trigger: the job runs once. */
@SpringBootTest
@ActiveProfiles("test")
class ShedLockIT {

	@Autowired
	private LockProvider lockProvider;

	@Autowired
	private StringRedisTemplate redis;

	@Test
	void aTriggerFiredOnTwoInstancesRunsTheJobOnce() {
		String job = "test-job-" + UUID.randomUUID().toString().substring(0, 8);
		AtomicInteger runs = new AtomicInteger();
		DefaultLockingTaskExecutor instanceA = new DefaultLockingTaskExecutor(lockProvider);
		DefaultLockingTaskExecutor instanceB = new DefaultLockingTaskExecutor(lockProvider);

		instanceA.executeWithLock((Runnable) runs::incrementAndGet, config(job));
		// B's clock is a little behind: it fires after A has already finished
		instanceB.executeWithLock((Runnable) runs::incrementAndGet, config(job));

		assertThat(runs).hasValue(1);
		Set<String> keys = redis.keys("naukriradar:job-service:shedlock*" + job);
		assertThat(keys).hasSize(1);
	}

	private static LockConfiguration config(String name) {
		return new LockConfiguration(Instant.now(), name, Duration.ofSeconds(30), Duration.ofSeconds(5));
	}

}
