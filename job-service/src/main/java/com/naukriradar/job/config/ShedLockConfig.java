package com.naukriradar.job.config;

import com.naukriradar.common.redis.RedisKeys;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Where ShedLock keeps its locks: Redis, under this service's prefix. With two instances,
 * both fire each cron trigger; whoever takes the lock first runs the job, the other skips.
 */
@Configuration
public class ShedLockConfig {

	@Bean
	LockProvider lockProvider(StringRedisTemplate redis, RedisKeys keys) {
		return new RedisLockProvider(redis, "jobs", keys.key("shedlock"));
	}

}
