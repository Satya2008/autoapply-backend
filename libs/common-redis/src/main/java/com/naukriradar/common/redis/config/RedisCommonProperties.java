package com.naukriradar.common.redis.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param keyPrefix start of every key this service writes; defaults to
 *     {@code naukriradar:<spring.application.name>:}
 * @param lockLease how long a lock outlives a holder that died
 * @param runSweepInterval how often abandoned runs are looked for
 * @param stampedeMaxWait how long a cache miss waits for another instance's load before
 *     loading itself
 */
@ConfigurationProperties("naukriradar.redis")
public record RedisCommonProperties(
		String keyPrefix,
		@DefaultValue("30s") Duration lockLease,
		@DefaultValue("2m") Duration runSweepInterval,
		@DefaultValue("2s") Duration stampedeMaxWait,
		@DefaultValue("50ms") Duration stampedePoll) {
}
