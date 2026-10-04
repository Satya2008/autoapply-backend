package com.naukriradar.matching.config;

import java.time.Duration;

import com.naukriradar.common.redis.cache.CacheSpec;
import com.naukriradar.matching.dto.response.MatchPageResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * matching-service's caches. A user's matches change only when a match run of theirs
 * finishes, and that drops all their pages, so the TTLs only bound how long a missed
 * eviction message can matter.
 */
@Configuration
public class CacheConfig {

	public static final String MATCH_PAGES = "match-pages";

	@Bean
	CacheSpec matchPagesCache() {
		return new CacheSpec(MATCH_PAGES, MatchPageResponse.class, Duration.ofSeconds(30), 5_000, Duration.ofMinutes(10));
	}

}
