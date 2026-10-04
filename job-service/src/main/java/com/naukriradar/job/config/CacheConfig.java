package com.naukriradar.job.config;

import java.time.Duration;

import com.naukriradar.common.redis.cache.CacheSpec;
import com.naukriradar.job.dto.response.JobDetailResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * job-service's caches. A job changes only when a fetch or the cleanup touches it, and both
 * clear the cache, so the TTLs are a safety net rather than the way entries go stale.
 */
@Configuration
public class CacheConfig {

	public static final String JOB_DETAIL = "job-detail";

	@Bean
	CacheSpec jobDetailCache() {
		return new CacheSpec(JOB_DETAIL, JobDetailResponse.class, Duration.ofSeconds(30), 2_000, Duration.ofMinutes(10));
	}

}
