package com.naukriradar.matching.service;

import com.naukriradar.common.redis.cache.TwoLevelCache;
import com.naukriradar.common.redis.cache.TwoLevelCacheManager;
import com.naukriradar.matching.config.CacheConfig;
import org.springframework.stereotype.Component;

/**
 * Keys of the match-page cache start with the user id, so one user's pages can be dropped
 * together. Built here and in {@code @Cacheable} on {@link MatchQueryService#list}; the two
 * must agree.
 */
@Component
public class MatchCache {

	private final TwoLevelCache pages;

	public MatchCache(TwoLevelCacheManager caches) {
		this.pages = caches.find(CacheConfig.MATCH_PAGES)
				.orElseThrow(() -> new IllegalStateException("Cache " + CacheConfig.MATCH_PAGES + " is not declared"));
	}

	/** After a run: every page of this user may have changed. */
	public void forgetUser(String userId) {
		pages.evictByPrefix(userId + ":");
	}

}
