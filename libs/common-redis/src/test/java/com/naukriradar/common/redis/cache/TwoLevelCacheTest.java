package com.naukriradar.common.redis.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.naukriradar.common.redis.InstanceId;
import com.naukriradar.common.redis.RedisKeys;
import com.naukriradar.common.redis.RedisTestSupport;
import com.naukriradar.common.redis.lock.RedisDistributedLock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwoLevelCacheTest {

	private static final LettuceConnectionFactory factory = RedisTestSupport.connect();

	private static final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();

	private static final CacheSpec SPEC = new CacheSpec("jobs", Job.class, Duration.ofMinutes(1), 100,
			Duration.ofMinutes(5));

	private final StringRedisTemplate redis = RedisTestSupport.template(factory);

	private final RedisKeys keys = RedisTestSupport.freshKeys();

	private final List<RedisMessageListenerContainer> containers = new ArrayList<>();

	private final TwoLevelCache instanceA = instance("a", redis);

	private final TwoLevelCache instanceB = instance("b", redis);

	@AfterEach
	void stopListeners() throws Exception {
		for (RedisMessageListenerContainer container : containers) {
			container.destroy();
		}
	}

	@AfterAll
	static void close() {
		watchdog.shutdownNow();
		factory.destroy();
	}

	@Test
	void aValueLoadedOnOneInstanceIsServedFromRedisOnTheOther() {
		AtomicInteger loads = new AtomicInteger();
		Job job = new Job("j1", "Java Developer", Instant.parse("2026-10-01T10:00:00Z"));

		assertThat(instanceA.get("j1", counting(loads, job))).isEqualTo(job);
		assertThat(instanceA.get("j1", counting(loads, job))).isEqualTo(job);
		assertThat(instanceB.get("j1", counting(loads, job))).isEqualTo(job);
		assertThat(instanceB.get("j1", counting(loads, job))).isEqualTo(job);

		assertThat(loads).hasValue(1);
		assertThat(instanceA.stats()).satisfies(s -> {
			assertThat(s.misses()).isEqualTo(1);
			assertThat(s.localHits()).isEqualTo(1);
			assertThat(s.loads()).isEqualTo(1);
			assertThat(s.hitRatio()).isEqualTo(0.5);
		});
		assertThat(instanceB.stats()).satisfies(s -> {
			assertThat(s.sharedHits()).isEqualTo(1);
			assertThat(s.localHits()).isEqualTo(1);
			assertThat(s.loads()).isZero();
		});
	}

	@Test
	void anEvictionReachesTheOtherInstancesMemory() throws Exception {
		Job job = new Job("j2", "Old title", null);
		instanceA.put("j2", job);
		assertThat(instanceB.get("j2").get()).isEqualTo(job);

		instanceA.evict("j2");

		waitUntil(() -> !inMemory(instanceB, "j2"));
		assertThat(instanceB.get("j2")).isNull();
	}

	@Test
	void prefixEvictionDropsOnlyThatUsersEntriesEverywhere() throws Exception {
		instanceA.put("user-1:page-1", new Job("a", "A", null));
		instanceA.put("user-1:page-2", new Job("b", "B", null));
		instanceA.put("user-10:page-1", new Job("c", "C", null));
		instanceA.put("user-2:page-1", new Job("d", "D", null));
		instanceB.get("user-1:page-1");

		instanceA.evictByPrefix("user-1:");

		assertThat(instanceA.get("user-1:page-1")).isNull();
		assertThat(instanceA.get("user-1:page-2")).isNull();
		assertThat(instanceA.get("user-10:page-1")).isNotNull();
		assertThat(instanceA.get("user-2:page-1")).isNotNull();
		waitUntil(() -> !inMemory(instanceB, "user-1:page-1"));
		assertThatThrownBy(() -> instanceA.evictByPrefix("")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void keysWithGlobCharactersAreClearedLiterally() {
		instanceA.put("a*", new Job("1", "star", null));
		instanceA.put("ab", new Job("2", "plain", null));

		instanceA.evictByPrefix("a*");

		assertThat(redis.hasKey(keys.key("cache", "jobs", "a*"))).isFalse();
		assertThat(redis.hasKey(keys.key("cache", "jobs", "ab"))).isTrue();
	}

	@Test
	void clearEmptiesThisCacheButNotOthers() {
		instanceA.put("x", new Job("x", "X", null));
		redis.opsForValue().set(keys.key("cache", "other", "x"), "{}");

		instanceA.clear();

		assertThat(instanceA.get("x")).isNull();
		assertThat(redis.hasKey(keys.key("cache", "other", "x"))).isTrue();
	}

	@Test
	void nullResultsAreNotCached() {
		AtomicInteger loads = new AtomicInteger();

		assertThat(instanceA.get("missing", counting(loads, null))).isNull();
		assertThat(instanceA.get("missing", counting(loads, null))).isNull();

		assertThat(loads).hasValue(2);
	}

	@Test
	void aFailingLoadIsPassedOnAndNothingIsCached() {
		Callable<Job> failing = () -> {
			throw new IllegalStateException("db down");
		};

		assertThatThrownBy(() -> instanceA.get("bad", failing)).isInstanceOf(Cache.ValueRetrievalException.class)
				.hasRootCauseMessage("db down");
		assertThat(instanceA.get("bad")).isNull();
	}

	@Test
	void anEntryThatNoLongerReadsAsTheTypeIsDroppedAndReloaded() {
		redis.opsForValue().set(keys.key("cache", "jobs", "j3"), "{\"id\": [1, 2]}");

		Job fresh = new Job("j3", "Reloaded", null);
		assertThat(instanceA.get("j3", () -> fresh)).isEqualTo(fresh);
		assertThat(redis.opsForValue().get(keys.key("cache", "jobs", "j3"))).contains("Reloaded");
	}

	@Test
	void sharedEntriesExpireAfterTheirTtl() {
		instanceA.put("ttl", new Job("t", "T", null));

		assertThat(redis.getExpire(keys.key("cache", "jobs", "ttl"))).isBetween(290L, 300L);
	}

	@Test
	void manyConcurrentMissesOnTwoInstancesLoadOnlyOnce() throws Exception {
		AtomicInteger loads = new AtomicInteger();
		CountDownLatch start = new CountDownLatch(1);
		Job job = new Job("hot", "Hot job", null);
		Callable<Job> slowLoad = () -> {
			loads.incrementAndGet();
			Thread.sleep(300);
			return job;
		};
		ExecutorService pool = Executors.newFixedThreadPool(16);
		try {
			List<Future<Object>> results = new ArrayList<>();
			for (int i = 0; i < 16; i++) {
				TwoLevelCache cache = i % 2 == 0 ? instanceA : instanceB;
				results.add(pool.submit(() -> {
					start.await();
					return cache.get("hot", slowLoad);
				}));
			}
			start.countDown();
			for (Future<Object> result : results) {
				assertThat(result.get()).isEqualTo(job);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(loads).hasValue(1);
		assertThat(instanceA.stats().waits() + instanceB.stats().waits()).isEqualTo(15);
	}

	@Test
	void withRedisDownTheCacheStillWorksFromMemoryAndCountsErrors() {
		LettuceConnectionFactory down = RedisTestSupport.unreachable();
		try {
			TwoLevelCache offline = instance("c", RedisTestSupport.template(down), false);
			AtomicInteger loads = new AtomicInteger();
			Job job = new Job("o", "Offline", null);

			assertThat(offline.get("o", counting(loads, job))).isEqualTo(job);
			assertThat(offline.get("o", counting(loads, job))).isEqualTo(job);
			offline.evict("o");
			offline.clear();

			assertThat(loads).hasValue(1);
			assertThat(offline.stats().errors()).isPositive();
		}
		finally {
			down.destroy();
		}
	}

	@Test
	void specsAreValidated() {
		assertThatThrownBy(() -> new CacheSpec("Bad Name", Job.class, Duration.ofSeconds(1), 1, Duration.ofSeconds(1)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CacheSpec("ok", Job.class, Duration.ofMinutes(2), 1, Duration.ofMinutes(1)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CacheSpec("ok", Job.class, Duration.ZERO, 1, Duration.ofMinutes(1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private TwoLevelCache instance(String name, StringRedisTemplate template) {
		return instance(name, template, true);
	}

	private TwoLevelCache instance(String name, StringRedisTemplate template, boolean listen) {
		InstanceId id = new InstanceId(name);
		RedisDistributedLock lock = new RedisDistributedLock(template, keys, id, watchdog, Duration.ofSeconds(5),
				Clock.systemUTC());
		CacheEvictionBus bus = new CacheEvictionBus(template, keys.key("cache-evictions"), id);
		if (listen) {
			RedisMessageListenerContainer container = new RedisMessageListenerContainer();
			container.setConnectionFactory(factory);
			container.addMessageListener(bus, new ChannelTopic(bus.channel()));
			container.afterPropertiesSet();
			container.start();
			containers.add(container);
		}
		TwoLevelCacheManager manager = new TwoLevelCacheManager(List.of(SPEC), template, keys, JsonMapper.builder().build(),
				new CacheStampedeGuard(lock, Duration.ofSeconds(2), Duration.ofMillis(20)), bus);
		return manager.find("jobs").orElseThrow();
	}

	private static Callable<Job> counting(AtomicInteger loads, Job value) {
		return () -> {
			loads.incrementAndGet();
			return value;
		};
	}

	private static boolean inMemory(TwoLevelCache cache, String key) {
		return ((com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache()).asMap().containsKey(key);
	}

	private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
		for (int i = 0; i < 100 && !condition.getAsBoolean(); i++) {
			Thread.sleep(20);
		}
		assertThat(condition.getAsBoolean()).isTrue();
	}

	record Job(String id, String title, Instant postedAt) {
	}

}
