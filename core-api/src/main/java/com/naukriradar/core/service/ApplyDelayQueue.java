package com.naukriradar.core.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.naukriradar.common.redis.RedisKeys;
import com.naukriradar.core.config.ApplicationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Applications waiting for their turn, in a Redis sorted set scored by when they are due.
 * One user's applications are spaced a random few minutes apart (each new one goes after the
 * user's last slot), so nothing goes out in a burst and nothing has to sleep.
 */
@Component
public class ApplyDelayQueue {

	private static final RedisScript<Long> SCHEDULE = RedisScript.of(new ClassPathResource("redis/apply-schedule.lua"),
			Long.class);

	private final StringRedisTemplate redis;
	private final String queue;
	private final RedisKeys keys;
	private final ApplicationProperties properties;
	private final Clock clock = Clock.systemUTC();

	public ApplyDelayQueue(StringRedisTemplate redis, RedisKeys keys, ApplicationProperties properties) {
		this.redis = redis;
		this.keys = keys;
		this.queue = keys.key("delayq", "apply");
		this.properties = properties;
	}

	/** @return when it is due, in epoch milliseconds */
	public long schedule(String applicationId, String userId) {
		long gap = randomGap().toMillis();
		Long due = redis.execute(SCHEDULE, List.of(queue, keys.key("apply-pace", userId)), applicationId,
				String.valueOf(clock.millis()), String.valueOf(gap));
		return due == null ? clock.millis() : due;
	}

	/** Up to {@code limit} applications whose time has come, oldest first. */
	public List<String> due(int limit) {
		var members = redis.opsForZSet().rangeByScore(queue, 0, clock.millis(), 0, limit);
		return members == null ? List.of() : List.copyOf(members);
	}

	public void remove(String applicationId) {
		redis.opsForZSet().remove(queue, applicationId);
	}

	public long size() {
		Long size = redis.opsForZSet().size(queue);
		return size == null ? 0 : size;
	}

	private Duration randomGap() {
		long min = properties.pacingMin().toMillis();
		long max = properties.pacingMax().toMillis();
		return Duration.ofMillis(min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1));
	}

}
