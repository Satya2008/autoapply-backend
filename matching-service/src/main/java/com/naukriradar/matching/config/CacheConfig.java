package com.naukriradar.matching.config;

import java.time.Duration;

import com.naukriradar.common.redis.cache.CacheSpec;
import com.naukriradar.matching.ai.AiResult;
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

	public static final String AI_RESULTS = "ai-results";

	/** Paid AI answers; a week in Redis is long enough to cover re-runs, short enough to stay small. */
	@Bean
	CacheSpec aiResultsCache() {
		return new CacheSpec(AI_RESULTS, AiResult.class, Duration.ofMinutes(10), 1_000, Duration.ofDays(7));
	}

	@Bean
	CacheSpec matchPagesCache() {
		return new CacheSpec(MATCH_PAGES, MatchPageResponse.class, Duration.ofSeconds(30), 5_000, Duration.ofMinutes(10));
	}

}
