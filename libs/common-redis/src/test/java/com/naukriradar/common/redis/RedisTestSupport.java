package com.naukriradar.common.redis;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Connections to the Redis the tests run against (localhost, database 1), and to one that isn't there. */
public final class RedisTestSupport {

	private RedisTestSupport() {
	}

	public static LettuceConnectionFactory connect() {
		return connect(Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379")));
	}

	/** Port 1 refuses connections: a Redis that is down. */
	public static LettuceConnectionFactory unreachable() {
		return connect(1);
	}

	public static StringRedisTemplate template(LettuceConnectionFactory factory) {
		StringRedisTemplate template = new StringRedisTemplate(factory);
		template.afterPropertiesSet();
		return template;
	}

	/** Each test class gets its own keys, so leftovers from an earlier run never interfere. */
	public static RedisKeys freshKeys() {
		return new RedisKeys("naukriradar-test:" + UUID.randomUUID().toString().substring(0, 8));
	}

	private static LettuceConnectionFactory connect(int port) {
		RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
				System.getenv().getOrDefault("REDIS_HOST", "localhost"), port);
		server.setDatabase(1);
		LettuceConnectionFactory factory = new LettuceConnectionFactory(server,
				LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build());
		factory.afterPropertiesSet();
		factory.start();
		return factory;
	}

}
