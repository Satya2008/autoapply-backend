package com.naukriradar.common.redis.config;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.naukriradar.common.redis.InstanceId;
import com.naukriradar.common.redis.RedisKeys;
import com.naukriradar.common.redis.cache.CacheEvictionBus;
import com.naukriradar.common.redis.cache.CacheSpec;
import com.naukriradar.common.redis.cache.CacheStampedeGuard;
import com.naukriradar.common.redis.cache.TwoLevelCacheManager;
import com.naukriradar.common.redis.controller.CacheAdminController;
import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.RedisDistributedLock;
import com.naukriradar.common.redis.run.InterruptedRunCloser;
import com.naukriradar.common.redis.run.InterruptedRunSweeper;
import com.naukriradar.common.redis.run.RunLeases;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires locks, run leases and the two-level cache into every service that has this library
 * on its classpath. Runs after Spring's Redis and Jackson setup, so their beans exist.
 */
@AutoConfiguration(afterName = {
		"org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
		"org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration" },
		beforeName = "org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration")
@EnableConfigurationProperties(RedisCommonProperties.class)
@EnableCaching
public class RedisCommonAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	RedisKeys redisKeys(RedisCommonProperties properties, @Value("${spring.application.name:app}") String application) {
		String prefix = properties.keyPrefix();
		return new RedisKeys(prefix == null || prefix.isBlank() ? "naukriradar:" + application : prefix);
	}

	@Bean
	@ConditionalOnMissingBean
	InstanceId instanceId() {
		return InstanceId.create();
	}

	/** Renews held locks. Its own thread, so slow work elsewhere never delays a renewal. */
	@Bean(destroyMethod = "shutdownNow")
	ScheduledExecutorService lockWatchdog() {
		return Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("lock-watchdog").daemon().factory());
	}

	@Bean
	@ConditionalOnMissingBean
	DistributedLock distributedLock(StringRedisTemplate redis, RedisKeys keys, InstanceId instance,
			@Qualifier("lockWatchdog") ScheduledExecutorService lockWatchdog, RedisCommonProperties properties) {
		return new RedisDistributedLock(redis, keys, instance, lockWatchdog, properties.lockLease(), Clock.systemUTC());
	}

	@Bean
	RunLeases runLeases(DistributedLock lock, RedisCommonProperties properties) {
		return new RunLeases(lock, properties.lockLease());
	}

	@Bean(destroyMethod = "shutdownNow")
	ScheduledExecutorService runSweepExecutor() {
		return Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("run-sweeper").daemon().factory());
	}

	@Bean
	InterruptedRunSweeper interruptedRunSweeper(ObjectProvider<InterruptedRunCloser> closers,
			@Qualifier("runSweepExecutor") ScheduledExecutorService runSweepExecutor, RedisCommonProperties properties) {
		return new InterruptedRunSweeper(closers.orderedStream().toList(), runSweepExecutor, properties.runSweepInterval());
	}

	@Bean
	@ConditionalOnMissingBean
	RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		return container;
	}

	@Bean
	CacheEvictionBus cacheEvictionBus(StringRedisTemplate redis, RedisKeys keys, InstanceId instance,
			RedisMessageListenerContainer container) {
		CacheEvictionBus bus = new CacheEvictionBus(redis, keys.key("cache-evictions"), instance);
		container.addMessageListener(bus, new ChannelTopic(bus.channel()));
		return bus;
	}

	@Bean
	CacheStampedeGuard cacheStampedeGuard(DistributedLock lock, RedisCommonProperties properties) {
		return new CacheStampedeGuard(lock, properties.stampedeMaxWait(), properties.stampedePoll());
	}

	@Bean
	TwoLevelCacheManager cacheManager(ObjectProvider<CacheSpec> specs, StringRedisTemplate redis, RedisKeys keys,
			JsonMapper json, CacheStampedeGuard guard, CacheEvictionBus bus) {
		List<CacheSpec> declared = specs.orderedStream().toList();
		return new TwoLevelCacheManager(declared, redis, keys, json, guard, bus);
	}

	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	CacheAdminController cacheAdminController(TwoLevelCacheManager cacheManager) {
		return new CacheAdminController(cacheManager);
	}

}
